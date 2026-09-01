package com.lazygeniouz.dfc.observer.internal.watcher

import android.database.ContentObserver
import android.database.Cursor
import android.os.Looper
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.logger.ErrorLogger
import com.lazygeniouz.dfc.observer.DirectoryEventMask
import com.lazygeniouz.dfc.observer.DirectoryObserver
import com.lazygeniouz.dfc.observer.internal.snapshot.ChildState
import com.lazygeniouz.dfc.observer.internal.snapshot.DiffEvent
import com.lazygeniouz.dfc.observer.internal.snapshot.SnapshotDiffer
import com.lazygeniouz.dfc.observer.internal.snapshot.SnapshotScan
import com.lazygeniouz.dfc.resolver.ResolverCompat
import java.io.FileNotFoundException

/**
 * Event driven watcher for the **direct children** of a SAF directory, zero polling.
 *
 * AOSP refcounts its internal `FileObserver` against **active** directory cursors, so one
 * lightweight [Cursor] stays alive for notifications while full snapshot cursors are temporary.
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
        val cursor = query(session, ResolverCompat.fullProjection)
            ?: throw FileNotFoundException("The provider returned no directory cursor")

        var attached = false
        try {
            session.cancellationSignal.throwIfCanceled()
            cursor.registerContentObserver(session.observer)
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return

            session.notificationCursor = cursor
            attached = true

            if (cursor.isLoading()) {
                scheduleRefresh(session)
                return
            }
            captureBaseline(session, cursor)
            installLightweightCursor(session)
            finishStartup(session)
        } finally {
            if (!attached) release(cursor, session.observer)
        }
    }

    private fun performRefresh(session: WatchSession) {
        session.refreshScheduled.set(false)
        if (!session.isCurrent) return
        try {
            val completed = when {
                !session.baselineCaptured -> resumeInitialization(session)
                !session.ready -> finishStartup(session)
                else -> refreshOnce(session) != null
            }
            if (completed) {
                session.consecutiveRefreshFailures = 0
                session.retryGeneration++
            }
        } catch (cancelled: OperationCanceledException) {
            if (session.isCurrent) {
                if (session.ready) {
                    logTransientFailure(session, "Directory refresh was cancelled", cancelled)
                } else {
                    ErrorLogger.logError("Directory observer initialization was cancelled", cancelled)
                    stopSelf(session, cancelled)
                }
            }
        } catch (security: SecurityException) {
            ErrorLogger.logError("Permission revoked, releasing the observer", security)
            stopSelf(session, security)
        } catch (unavailable: FileNotFoundException) {
            ErrorLogger.logError("Observed directory is no longer available", unavailable)
            stopSelf(session, unavailable)
        } catch (exception: Exception) {
            if (session.ready) {
                logTransientFailure(session, "Directory refresh failed", exception)
            } else {
                ErrorLogger.logError("Could not initialize the directory observer", exception)
                stopSelf(session, exception)
            }
        }
    }

    private fun resumeInitialization(session: WatchSession): Boolean {
        val cursor = query(session, ResolverCompat.fullProjection)
            ?: throw FileNotFoundException("The provider returned no directory cursor")

        try {
            session.cancellationSignal.throwIfCanceled()
            if (cursor.isLoading()) return false
            captureBaseline(session, cursor)
        } finally {
            release(cursor)
        }

        installLightweightCursor(session)
        return finishStartup(session)
    }

    private fun captureBaseline(session: WatchSession, cursor: Cursor) {
        val baseline = readSnapshot(session, cursor, trackCreations = false).snapshot
        ensureDirectoryAvailable(session, baseline)
        session.cancellationSignal.throwIfCanceled()
        if (!session.isCurrent) return

        session.snapshot = baseline
        session.baselineCaptured = true
    }

    private fun installLightweightCursor(session: WatchSession) {
        if (session.lightweightCursorInstalled) return

        val cursor = query(session, ResolverCompat.notificationProjection)
            ?: throw FileNotFoundException("The provider returned no notification cursor")

        var attached = false
        try {
            session.cancellationSignal.throwIfCanceled()
            cursor.registerContentObserver(session.observer)
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return

            val previous = session.notificationCursor
                ?: throw IllegalStateException("The observer has no notification cursor")
            session.notificationCursor = cursor
            session.lightweightCursorInstalled = true
            attached = true
            release(previous, session.observer)
        } finally {
            if (!attached) release(cursor, session.observer)
        }
    }

    private fun finishStartup(session: WatchSession): Boolean {
        val startupEvents = refreshOnce(session, deliverEvents = false) ?: return false
        if (!session.isCurrent) return false

        session.ready = true
        emitReady(session)
        for (event in startupEvents) {
            if (!emit(session, event)) break
        }
        return session.isCurrent
    }

    // Re-query and atomically reconcile; incomplete results leave the committed state untouched.
    private fun refreshOnce(
        session: WatchSession,
        deliverEvents: Boolean = true,
    ): List<DiffEvent>? {
        if (session.notificationCursor == null) return emptyList()

        val freshCursor = query(session, ResolverCompat.fullProjection)
            ?: throw FileNotFoundException("The provider returned no directory cursor")

        try {
            session.cancellationSignal.throwIfCanceled()
            if (freshCursor.isLoading()) return null
            val freshScan = readSnapshot(
                session, freshCursor, trackCreations = mask includes DirectoryObserver.CREATE
            )
            ensureDirectoryAvailable(session, freshScan.snapshot)
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return emptyList()

            val events = SnapshotDiffer.diff(
                session.snapshot,
                freshScan.snapshot,
                mask,
                session.cancellationSignal,
                freshScan.creations,
            )
            session.cancellationSignal.throwIfCanceled()
            if (!session.isCurrent) return emptyList()

            session.snapshot = freshScan.snapshot

            if (deliverEvents) {
                for (diffEvent in events) {
                    if (!emit(session, diffEvent)) break
                }
            }
            return events
        } finally {
            release(freshCursor)
        }
    }

    private fun query(session: WatchSession, projection: Array<String>): Cursor? {
        return directory.context.contentResolver.query(
            ResolverCompat.createChildrenUri(directory.uri),
            projection,
            null, null, null,
            session.cancellationSignal,
        )
    }

    private fun emit(session: WatchSession, diffEvent: DiffEvent): Boolean {
        return synchronized(session.callbackLock) {
            if (!session.isCurrent) return@synchronized false
            try {
                listener(
                    diffEvent.event,
                    ResolverCompat.materializeChild(
                        directory.context, directory, diffEvent.child
                    )
                )
            } catch (exception: Exception) {
                ErrorLogger.logError("Observer listener threw an exception", exception)
            }
            session.isCurrent
        }
    }

    private fun readSnapshot(
        session: WatchSession,
        cursor: Cursor,
        trackCreations: Boolean,
    ): SnapshotScan {
        return ResolverCompat.readChildSnapshot(
            cursor,
            session.snapshot,
            session.cancellationSignal,
            trackCreations,
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
        snapshot: Map<String, ChildState>,
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
        val cursor = session.notificationCursor
        session.notificationCursor = null
        session.lightweightCursorInstalled = false
        session.snapshot = LinkedHashMap()
        session.baselineCaptured = false
        session.ready = false

        if (cursor != null) release(cursor, session.observer)

        Looper.myLooper()?.quitSafely()
    }

    // The single exception-safe cursor cleanup; unregisters when this observer was attached.
    private fun release(cursor: Cursor, observer: ContentObserver? = null) {
        if (observer != null) {
            try {
                cursor.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
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

    private infix fun Int.includes(event: Int): Boolean = and(event) != 0

    private fun Cursor.isLoading(): Boolean {
        return extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)
    }
}