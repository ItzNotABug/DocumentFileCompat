package com.lazygeniouz.dfc.observer.internal.watcher

import android.database.ContentObserver
import android.database.Cursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import com.lazygeniouz.dfc.observer.internal.snapshot.ChildState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything owned by one start..stop cycle of a [DirectoryWatcher]: worker thread, observer,
 * cancellation signal, cursor & snapshot. A stale session can only ever release its own
 * resources, so rapid stop -> start cannot cross-close a newer session's cursor.
 *
 * [onChanged] is invoked on the notifier's thread for every provider notification.
 */
internal class WatchSession(
    onChanged: (WatchSession) -> Unit,
    val onError: (Throwable) -> Unit,
    val onReady: () -> Unit,
) {

    // Background priority: housekeeping work should not compete with UI-critical threads.
    private val thread = HandlerThread(
        THREAD_NAME,
        Process.THREAD_PRIORITY_BACKGROUND
    ).apply { start() }
    val handler = Handler(thread.looper)
    val cancellationSignal = CancellationSignal()

    // Gate: at most one pending refresh, even during notification storms.
    val refreshScheduled = AtomicBoolean(false)

    // Serializes event/readiness callbacks with stopWatching().
    val callbackLock = Any()

    // Set by an explicit stop that races a terminal worker-side stop.
    val suppressTerminalCallback = AtomicBoolean(false)

    // Handler-less: onChange only gates + posts, no per-notification worker messages.
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) = onChanged(this@WatchSession)
    }

    // Confined to [thread].
    var notificationCursor: Cursor? = null
    var lightweightCursorInstalled = false
    var snapshot: LinkedHashMap<String, ChildState> = LinkedHashMap()
    var baselineCaptured = false
    var ready = false
    var consecutiveRefreshFailures = 0
    var retryGeneration = 0L

    private companion object {
        const val THREAD_NAME = "dfc-observer"
    }
}