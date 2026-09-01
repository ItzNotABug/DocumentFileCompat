package com.lazygeniouz.dfc.observer

import android.database.ContentObserver
import android.database.Cursor
import android.os.Looper
import android.os.OperationCanceledException
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.logger.ErrorLogger
import com.lazygeniouz.dfc.resolver.ResolverCompat
import java.io.FileNotFoundException

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
    @param:DirectoryEventMask private val mask: Int,
    private val listener: (event: Int, document: DocumentFileCompat) -> Unit,
) {

    private val lock = Any()

    @Volatile
    // Writes are guarded by [lock]; volatile reads make stale sessions no-op.
    private var session: WatchSession? = null

    // A terminal session remains visible until its onError callback is completed or suppressed.
    private var terminatingSession: WatchSession? = null

    private val WatchSession.isCurrent get() = this@DirectoryWatcher.session === this

    /** Start watching; no-op if already watching. */
    internal fun startWatching(
        onError: (Throwable) -> Unit,
        onReady: () -> Unit,
    ) {
        synchronized(lock) {
            if (session != null || terminatingSession != null) return
            val started = WatchSession(::scheduleRefresh, onError, onReady)
            session = started
            started.handler.post { initialize(started) }
        }
    }

    /** Stop watching & release the session's cursor + worker thread; no-op if not watching. */
    internal fun stopWatching() {
        val stopped: WatchSession
        val teardownRequired: Boolean
        synchronized(lock) {
            val active = session
            if (active != null) {
                stopped = active
                session = null
                teardownRequired = true
            } else {
                stopped = terminatingSession ?: return
                teardownRequired = false
            }
            stopped.suppressTerminalCallback.set(true)
        }
        stopped.cancellationSignal.cancel()
        // An admitted callback finishes before stop returns; a pending terminal callback is skipped.
        synchronized(stopped.callbackLock) {}
        if (teardownRequired) {
            stopped.handler.post { teardown(stopped) }
        } else {
            // The old worker may still be cleaning up, but can no longer call user code.
            synchronized(lock) {
                if (terminatingSession === stopped) terminatingSession = null
            }
        }
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
            ?: throw FileNotFoundException("The provider returned no directory cursor")

        var attached = false
        try {
            session.cancellationSignal.throwIfCanceled()
            cursor.registerContentObserver(session.observer)
            val baseline = readSnapshot(session, cursor)
            ensureDirectoryAvailable(session, baseline)
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return

            session.cursor = cursor
            session.snapshot = baseline
            attached = true

            // A second snapshot closes the pre-registration blind window before readiness.
            // Failure is terminal: readiness must never be reported with a stale baseline.
            val startupEvents = refreshOnce(session, deliverEvents = false)
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
            session.consecutiveRefreshFailures = 0
            session.retryGeneration++
        } catch (cancelled: OperationCanceledException) {
            if (session.isCurrent) {
                logTransientFailure(session, "Directory refresh was cancelled", cancelled)
            }
        } catch (security: SecurityException) {
            ErrorLogger.logError("Permission revoked, releasing the observer", security)
            stopSelf(session, security)
        } catch (unavailable: FileNotFoundException) {
            ErrorLogger.logError("Observed directory is no longer available", unavailable)
            stopSelf(session, unavailable)
        } catch (exception: Exception) {
            logTransientFailure(session, "Directory refresh failed", exception)
        }
    }

    // Re-query, diff & swap cursors without a watch gap. Exceptions leave the old watch intact.
    private fun refreshOnce(
        session: WatchSession,
        deliverEvents: Boolean = true,
    ): List<SnapshotDiffer.DiffEvent> {
        val previousCursor = session.cursor ?: return emptyList()

        val freshCursor = query(session)
            ?: throw FileNotFoundException("The provider returned no directory cursor")

        var promoted = false
        try {
            session.cancellationSignal.throwIfCanceled()
            // Observe the fresh cursor BEFORE reading it & BEFORE closing the previous one.
            freshCursor.registerContentObserver(session.observer)
            val freshSnapshot = readSnapshot(session, freshCursor)
            ensureDirectoryAvailable(session, freshSnapshot)
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
                listener(diffEvent.event, ResolverCompat.copyForCallback(diffEvent.document))
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

    private fun ensureDirectoryAvailable(
        session: WatchSession,
        snapshot: Map<String, DocumentFileCompat>,
    ) {
        if (snapshot.isEmpty() && !ResolverCompat.isExistingDirectory(
                directory.context,
                directory.uri,
                session.cancellationSignal,
            )
        ) {
            throw FileNotFoundException("The observed directory no longer exists")
        }
    }

    private fun logTransientFailure(
        session: WatchSession,
        message: String,
        failure: Exception,
    ) {
        val retryScheduled = scheduleRetryAfterFailure(session)
        val outcome = if (retryScheduled) "retrying once" else "waiting for the next change"
        ErrorLogger.logError("$message; $outcome", failure)
    }

    private fun scheduleRetryAfterFailure(session: WatchSession): Boolean {
        val failureCount = ++session.consecutiveRefreshFailures
        val generation = ++session.retryGeneration
        if (failureCount > MAX_AUTOMATIC_REFRESH_RETRIES || !session.isCurrent) return false

        return session.handler.postDelayed({
            if (session.isCurrent && session.retryGeneration == generation) {
                scheduleRefresh(session)
            }
        }, REFRESH_RETRY_DELAY_MS)
    }

    // Worker-side stop for unrecoverable start failures; loses to an already issued stop.
    private fun stopSelf(session: WatchSession, failure: Throwable) {
        synchronized(lock) {
            if (!session.isCurrent) return
            this.session = null
            terminatingSession = session
        }
        session.cancellationSignal.cancel()
        try {
            teardown(session)
            synchronized(session.callbackLock) {
                if (!session.suppressTerminalCallback.get()) {
                    try {
                        session.onError(failure)
                    } catch (exception: Exception) {
                        ErrorLogger.logError("Observer error callback threw an exception", exception)
                    }
                }
            }
        } finally {
            synchronized(lock) {
                if (terminatingSession === session) terminatingSession = null
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

    private companion object {
        const val MAX_AUTOMATIC_REFRESH_RETRIES = 1
        const val REFRESH_RETRY_DELAY_MS = 200L
    }
}