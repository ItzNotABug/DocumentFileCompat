package com.lazygeniouz.dfc.observer.internal.snapshot

/** Minimal immutable metadata retained for one observed child. */
internal class ChildState(
    val documentId: String,
    val name: String,
    val length: Long,
    val lastModified: Long,
    val mimeType: String,
    val flags: Int,
) {

    /**
     * Compares cursor values without allocating another [ChildState].
     */
    fun matches(
        name: String,
        length: Long,
        lastModified: Long,
        mimeType: String,
        flags: Int,
    ): Boolean {
        return this.name == name
                && this.length == length
                && this.lastModified == lastModified
                && this.mimeType == mimeType
                && this.flags == flags
    }

    /**
     * Name changes emit move events; flag-only changes emit no event.
     */
    fun isModified(other: ChildState): Boolean {
        return length != other.length
                || lastModified != other.lastModified
                || mimeType != other.mimeType
    }
}