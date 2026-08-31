package com.lazygeniouz.dfc.observer

import android.database.ContentObserver
import android.database.Cursor
import android.os.Looper
import android.os.OperationCanceledException
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.logger.ErrorLogger
import com.lazygeniouz.dfc.resolver.ResolverCompat

/**
 * Event driven watcher for the **direct children** of a SAF directory, zero polling.
 *
 * AOSP refcounts its internal `FileObserver` against **active** directory cursors, so the
 * query [Cursor] stays alive for the lifetime of the watch & a replacement cursor is observed
 * **before** the previous one closes (no watch gap).
 *
 * Each start..stop cycle owns a [WatchSession] that can only release its own resources; cursor
 * & snapshot state is confined to its worker thread & the listener runs there too.
 * [startWatching] / [stopWatching] are idempotent & callable from any thread.
 */
internal class DirectoryWatcher(
    private val directory: DocumentFileCompat,
    private val mask: Int,
    private val listener: (event: Int, document: DocumentFileCompat) -> Unit,
) {

    private val lock = Any()

    @Volatile
    // Writes are guarded by [lock]; volatile reads make stale sessions no-op.
    private var session: WatchSession? = null

    private val WatchSession.isCurrent get() = this@DirectoryWatcher.session === this

    /** Start watching; no-op if already watching. */
    internal fun startWatching(
        onError: (Throwable) -> Unit,
        onReady: () -> Unit,
    ) {
        synchronized(lock) {
            if (session != null) return
            val started = WatchSession(::scheduleRefresh, onError, onReady)
            session = started
            started.handler.post { initialize(started) }
        }
    }

    /** Stop watching & release the session's cursor + worker thread; no-op if not watching. */
    internal fun stopWatching() {
        val stopped: WatchSession
        synchronized(lock) {
            stopped = session ?: return
            session = null
        }
        stopped.cancellationSignal.cancel()
        // An admitted callback finishes before stop returns; future ones see a stale session.
        synchronized(stopped.callbackLock) {}
        stopped.handler.post { teardown(stopped) }
    }

    private fun scheduleRefresh(session: WatchSession) {
        if (!session.isCurrent) return
        if (session.refreshScheduled.compareAndSet(false, true)) {
            session.handler.post { performRefresh(session) }
        }
    }

    // Initial query: baseline snapshot, no events emitted for pre-existing children.
    private fun initialize(session: WatchSession) {
        if (!session.isCurrent) return
        try {
            doInitialize(session)
        } catch (cancelled: OperationCanceledException) {
            if (session.isCurrent) {
                ErrorLogger.logError("Directory observer initialization was cancelled", cancelled)
                stopSelf(session, cancelled)
            }
        } catch (security: SecurityException) {
            ErrorLogger.logError("Permission revoked, observer is stopped", security)
            stopSelf(session, security)
        } catch (exception: Exception) {
            ErrorLogger.logError("Could not initialize the directory observer", exception)
            stopSelf(session, exception)
        }
    }

    private fun doInitialize(session: WatchSession) {
        val cursor = query(session)
            ?: throw IllegalStateException("The provider returned no directory cursor")

        var attached = false
        try {
            cursor.registerContentObserver(session.observer)
            val baseline = readSnapshot(session, cursor)
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return

            session.cursor = cursor
            session.snapshot = baseline
            attached = true

            // A second snapshot closes the pre-registration blind window before readiness.
            val startupEvents = reconcileBeforeReady(session)
            if (!session.isCurrent) return
            emitReady(session)
            for (event in startupEvents) {
                if (!emit(session, event)) return
            }
        } finally {
            if (!attached) release(cursor, session.observer)
        }
    }

    private fun performRefresh(session: WatchSession) {
        session.refreshScheduled.set(false)
        if (!session.isCurrent) return
        try {
            refreshOnce(session)
        } catch (cancelled: OperationCanceledException) {
            if (session.isCurrent) {
                ErrorLogger.logError("Directory refresh was cancelled, keeping the watch", cancelled)
            }
        } catch (security: SecurityException) {
            ErrorLogger.logError("Permission revoked, releasing the observer", security)
            stopSelf(session, security)
        } catch (exception: Exception) {
            ErrorLogger.logError("Directory refresh failed, keeping the existing watch", exception)
        }
    }

    private fun reconcileBeforeReady(session: WatchSession): List<SnapshotDiffer.DiffEvent> {
        return try {
            refreshOnce(session, deliverEvents = false)
        } catch (cancelled: OperationCanceledException) {
            throw cancelled
        } catch (security: SecurityException) {
            throw security
        } catch (exception: Exception) {
            // The initial cursor is already active; a transient reconciliation failure is safe.
            ErrorLogger.logError("Initial reconciliation failed, keeping the existing watch", exception)
            emptyList()
        }
    }

    // Re-query, diff & swap cursors without a watch gap. Exceptions leave the old watch intact.
    private fun refreshOnce(
        session: WatchSession,
        deliverEvents: Boolean = true,
    ): List<SnapshotDiffer.DiffEvent> {
        val previousCursor = session.cursor ?: return emptyList()

        val freshCursor = query(session)
            ?: throw IllegalStateException("The provider returned no directory cursor")

        var promoted = false
        try {
            // Observe the fresh cursor BEFORE reading it & BEFORE closing the previous one.
            freshCursor.registerContentObserver(session.observer)
            val freshSnapshot = readSnapshot(session, freshCursor)
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return emptyList()

            val events = SnapshotDiffer.diff(
                session.snapshot, freshSnapshot, mask, session.cancellationSignal
            )
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return emptyList()

            session.cursor = freshCursor
            session.snapshot = freshSnapshot
            promoted = true
            release(previousCursor, session.observer)

            if (deliverEvents) {
                for (diffEvent in events) {
                    if (!emit(session, diffEvent)) break
                }
            }
            return events
        } finally {
            if (!promoted) release(freshCursor, session.observer)
        }
    }

    private fun query(session: WatchSession): Cursor? {
        return directory.context.contentResolver.query(
            ResolverCompat.createChildrenUri(directory.uri),
            ResolverCompat.fullProjection,
            null, null, null,
            session.cancellationSignal,
        )
    }

    private fun emit(session: WatchSession, diffEvent: SnapshotDiffer.DiffEvent): Boolean {
        return synchronized(session.callbackLock) {
            if (!session.isCurrent) return@synchronized false
            try {
                listener(diffEvent.event, diffEvent.document)
            } catch (exception: Exception) {
                ErrorLogger.logError("Observer listener threw an exception", exception)
            }
            session.isCurrent
        }
    }

    private fun readSnapshot(
        session: WatchSession,
        cursor: Cursor,
    ): LinkedHashMap<String, DocumentFileCompat> {
        return ResolverCompat.readChildSnapshot(
            directory.context,
            cursor,
            directory,
            session.snapshot,
            session.cancellationSignal,
        )
    }

    private fun emitReady(session: WatchSession) {
        synchronized(session.callbackLock) {
            if (!session.isCurrent) return
            try {
                session.onReady()
            } catch (exception: Exception) {
                ErrorLogger.logError("Observer readiness callback threw an exception", exception)
            }
        }
    }

    // Worker-side stop for unrecoverable start failures; loses to an already issued stop.
    private fun stopSelf(session: WatchSession, failure: Throwable) {
        synchronized(lock) {
            if (!session.isCurrent) return
            this.session = null
        }
        session.cancellationSignal.cancel()
        teardown(session)
        synchronized(session.callbackLock) {
            try {
                session.onError(failure)
            } catch (exception: Exception) {
                ErrorLogger.logError("Observer error callback threw an exception", exception)
            }
        }
    }

    // Runs on the session's worker, always last: releases its resources & quits the looper.
    private fun teardown(session: WatchSession) {
        val cursor = session.cursor
        session.cursor = null
        session.snapshot = LinkedHashMap()

        if (cursor != null) release(cursor, session.observer)

        Looper.myLooper()?.quitSafely()
    }

    // The single, exception-safe cursor cleanup: unregister + close, never throws.
    private fun release(cursor: Cursor, observer: ContentObserver) {
        try {
            cursor.unregisterContentObserver(observer)
        } catch (_: Exception) {
        }
        try {
            cursor.close()
        } catch (_: Exception) {
        }
    }
}