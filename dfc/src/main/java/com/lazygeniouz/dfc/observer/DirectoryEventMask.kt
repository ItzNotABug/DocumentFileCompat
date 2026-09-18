package com.lazygeniouz.dfc.observer

import androidx.annotation.IntDef

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