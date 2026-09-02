package com.lazygeniouz.dfc.observer

import android.os.FileObserver
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.observer.internal.watcher.DirectoryWatcher
import java.io.Closeable

/**
 * A direct-child observation created by [com.lazygeniouz.dfc.file.DocumentFileCompat.observe].
 * Starting and stopping are idempotent and thread-safe; [stopWatching] is safe inside callbacks.
 * Close it with the owning lifecycle.
 */
class DirectoryObserver private constructor(
    directory: DocumentFileCompat,
    @DirectoryEventMask mask: Int,
    listener: (event: Int, document: DocumentFileCompat) -> Unit,
) : Closeable {

    private val watcher = DirectoryWatcher(directory, mask, listener)

    /**
     * Starts watching. [onReady] follows a successful baseline; [onError] reports terminal
     * startup, refresh, permission, or directory failures. Starts are ignored while active, while
     * a stop drains a callback, or until a terminal callback returns.
     */
    fun startWatching(
        onError: (Throwable) -> Unit = {},
        onReady: () -> Unit = {},
    ) = watcher.startWatching(onError, onReady)

    /**
     * Invalidates the session and prevents further callbacks before returning. Cleanup
     * completes on the worker after any provider operation already in flight returns.
     */
    fun stopWatching() = watcher.stopWatching()

    override fun close() = stopWatching()

    companion object {

        @JvmSynthetic
        internal fun create(
            directory: DocumentFileCompat,
            @DirectoryEventMask mask: Int,
            listener: (event: Int, document: DocumentFileCompat) -> Unit,
        ) = DirectoryObserver(directory, mask, listener)

        /** A child's size, last-modified time, or MIME type changed. */
        const val MODIFY = FileObserver.MODIFY

        /** A rename's old state when its document ID stays stable; otherwise DELETE + CREATE. */
        const val MOVED_FROM = FileObserver.MOVED_FROM

        /** A rename's new state when its document ID stays stable; otherwise DELETE + CREATE. */
        const val MOVED_TO = FileObserver.MOVED_TO

        /** Same as [FileObserver.CREATE]: a child was created. */
        const val CREATE = FileObserver.CREATE

        /** Same as [FileObserver.DELETE]: a child was deleted. */
        const val DELETE = FileObserver.DELETE

        /** Every event directory observation can produce. */
        const val ALL_EVENTS = MODIFY or MOVED_FROM or MOVED_TO or CREATE or DELETE
    }
}