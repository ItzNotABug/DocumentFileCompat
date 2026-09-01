package com.lazygeniouz.dfc.observer

import android.os.FileObserver
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.observer.internal.watcher.DirectoryWatcher
import java.io.Closeable

/**
 * A direct-child observation created by [com.lazygeniouz.dfc.file.DocumentFileCompat.observe].
 * Starting and stopping are idempotent and thread-safe; [stopWatching] is safe inside callbacks.
 */
class DirectoryObserver private constructor(
    directory: DocumentFileCompat,
    @DirectoryEventMask mask: Int,
    listener: (event: Int, document: DocumentFileCompat) -> Unit,
) : Closeable {

    private val watcher = DirectoryWatcher(directory, mask, listener)

    /**
     * Starts watching. [onReady] follows a successful baseline; [onError] reports terminal
     * startup, permission, or directory failures. Starts are ignored while active or until a
     * terminal callback returns.
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

        /** Same as [FileObserver.MODIFY]: a child's contents or metadata changed. */
        const val MODIFY = FileObserver.MODIFY

        /** Same as [FileObserver.MOVED_FROM]: a child was renamed, carrying its old name. */
        const val MOVED_FROM = FileObserver.MOVED_FROM

        /** Same as [FileObserver.MOVED_TO]: a child was renamed, carrying its new name. */
        const val MOVED_TO = FileObserver.MOVED_TO

        /** Same as [FileObserver.CREATE]: a child was created. */
        const val CREATE = FileObserver.CREATE

        /** Same as [FileObserver.DELETE]: a child was deleted. */
        const val DELETE = FileObserver.DELETE

        /** Every event directory observation can produce. */
        const val ALL_EVENTS = MODIFY or MOVED_FROM or MOVED_TO or CREATE or DELETE
    }
}