package com.lazygeniouz.dfc.observer

import android.os.CancellationSignal
import com.lazygeniouz.dfc.file.DocumentFileCompat

/**
 * Conservative by design: an id-changing rename surfaces as DELETE + CREATE, similar
 * entries are never paired into moves. Order: DELETEs, then renames / modifies, then CREATEs.
 */
internal object SnapshotDiffer {

    /**
     * A single derived event; [document] is the **old** child for DELETE & MOVED_FROM
     * (last known metadata), the fresh one otherwise.
     */
    internal class DiffEvent(val event: Int, val document: DocumentFileCompat)

    /** Diff [old] against [new]; empty list when nothing changed. */
    internal fun diff(
        old: Map<String, DocumentFileCompat>,
        new: Map<String, DocumentFileCompat>,
        mask: Int = DocumentFileCompat.ALL_EVENTS,
        cancellationSignal: CancellationSignal? = null,
    ): List<DiffEvent> {
        if (old.isEmpty() && new.isEmpty()) return emptyList()

        val events = ArrayList<DiffEvent>()
        if (mask includes DocumentFileCompat.DELETE) {
            collectDeletions(old, new, events, cancellationSignal)
        }
        if (mask and CHANGE_EVENTS != 0) {
            collectChanges(old, new, mask, events, cancellationSignal)
        }
        if (mask includes DocumentFileCompat.CREATE) {
            collectCreations(old, new, events, cancellationSignal)
        }
        cancellationSignal?.throwIfCanceled()
        return events
    }

    // In old snapshot order.
    private fun collectDeletions(
        old: Map<String, DocumentFileCompat>,
        new: Map<String, DocumentFileCompat>,
        events: MutableList<DiffEvent>,
        cancellationSignal: CancellationSignal?,
    ) {
        var row = 0
        for ((documentId, oldChild) in old) {
            if ((row++ and CANCELLATION_CHECK_MASK) == 0) cancellationSignal?.throwIfCanceled()
            if (documentId !in new) {
                events.add(DiffEvent(DocumentFileCompat.DELETE, oldChild))
            }
        }
    }

    // Renames & modifications, in old snapshot order.
    private fun collectChanges(
        old: Map<String, DocumentFileCompat>,
        new: Map<String, DocumentFileCompat>,
        mask: Int,
        events: MutableList<DiffEvent>,
        cancellationSignal: CancellationSignal?,
    ) {
        var row = 0
        for ((documentId, oldChild) in old) {
            if ((row++ and CANCELLATION_CHECK_MASK) == 0) cancellationSignal?.throwIfCanceled()
            val newChild = new[documentId] ?: continue

            // Reused instance == unchanged row (snapshot reads reuse only identical fields).
            if (oldChild === newChild) continue

            if (oldChild.name != newChild.name) {
                if (mask includes DocumentFileCompat.MOVED_FROM) {
                    events.add(DiffEvent(DocumentFileCompat.MOVED_FROM, oldChild))
                }
                if (mask includes DocumentFileCompat.MOVED_TO) {
                    events.add(DiffEvent(DocumentFileCompat.MOVED_TO, newChild))
                }
            } else if (
                mask includes DocumentFileCompat.MODIFY && metadataChanged(oldChild, newChild)
            ) {
                events.add(DiffEvent(DocumentFileCompat.MODIFY, newChild))
            }
        }
    }

    // In new snapshot order.
    private fun collectCreations(
        old: Map<String, DocumentFileCompat>,
        new: Map<String, DocumentFileCompat>,
        events: MutableList<DiffEvent>,
        cancellationSignal: CancellationSignal?,
    ) {
        var row = 0
        for ((documentId, newChild) in new) {
            if ((row++ and CANCELLATION_CHECK_MASK) == 0) cancellationSignal?.throwIfCanceled()
            if (documentId !in old) {
                events.add(DiffEvent(DocumentFileCompat.CREATE, newChild))
            }
        }
    }

    // Flags intentionally excluded, see the class KDoc.
    private fun metadataChanged(old: DocumentFileCompat, new: DocumentFileCompat): Boolean {
        return old.length != new.length
                || old.lastModified != new.lastModified
                || old.documentMimeType != new.documentMimeType
    }

    private infix fun Int.includes(event: Int): Boolean = and(event) != 0

    private const val CHANGE_EVENTS =
        DocumentFileCompat.MOVED_FROM or DocumentFileCompat.MOVED_TO or DocumentFileCompat.MODIFY
    private const val CANCELLATION_CHECK_MASK = 63
}