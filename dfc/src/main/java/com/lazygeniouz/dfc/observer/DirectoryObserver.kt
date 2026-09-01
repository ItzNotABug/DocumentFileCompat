package com.lazygeniouz.dfc.observer

import android.os.FileObserver
import androidx.annotation.IntDef
import java.io.Closeable

/** Restricts directory observation masks to the supported [DirectoryObserver] event flags. */
@MustBeDocumented
@Retention(AnnotationRetention.SOURCE)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.TYPE,
    AnnotationTarget.VALUE_PARAMETER,
)
@IntDef(
    DirectoryObserver.MODIFY,
    DirectoryObserver.MOVED_FROM,
    DirectoryObserver.MOVED_TO,
    DirectoryObserver.CREATE,
    DirectoryObserver.DELETE,
    flag = true,
)
annotation class DirectoryEventMask

/**
 * A direct-child observation created by [com.lazygeniouz.dfc.file.DocumentFileCompat.observe].
 * Starting and stopping are idempotent and thread-safe; [stopWatching] is safe inside callbacks.
 */
class DirectoryObserver internal constructor(
    private val watcher: DirectoryWatcher,
) : Closeable {

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