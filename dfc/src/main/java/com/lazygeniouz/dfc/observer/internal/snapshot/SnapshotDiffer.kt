package com.lazygeniouz.dfc.observer.internal.snapshot

import android.os.CancellationSignal
import com.lazygeniouz.dfc.observer.DirectoryEventMask
import com.lazygeniouz.dfc.observer.DirectoryObserver

/**
 * Conservative by design: an id-changing rename surfaces as DELETE + CREATE, similar
 * entries are never paired into moves. Order: DELETEs, then renames / modifies, then CREATEs.
 */
internal object SnapshotDiffer {

    /** Diff [old] against [new]; empty list when nothing changed. */
    internal fun diff(
        old: Map<String, ChildState>,
        new: Map<String, ChildState>,
        @DirectoryEventMask mask: Int = DirectoryObserver.ALL_EVENTS,
        cancellationSignal: CancellationSignal? = null,
        scannedCreations: List<ChildState>? = null,
    ): List<DiffEvent> {
        if (old.isEmpty() && new.isEmpty()) return emptyList()

        val deletions = ArrayList<DiffEvent>()
        val changes = ArrayList<DiffEvent>()
        if (mask and OLD_SNAPSHOT_EVENTS != 0) {
            collectOldSnapshotEvents(
                old, new, mask, deletions, changes, cancellationSignal
            )
        }

        val creations: List<ChildState>
        if (mask includes DirectoryObserver.CREATE) {
            creations = scannedCreations
                ?: collectCreations(old, new, cancellationSignal)
        } else {
            creations = emptyList()
        }

        cancellationSignal?.throwIfCanceled()

        if (deletions.isEmpty() && changes.isEmpty() && creations.isEmpty()) return emptyList()
        val events = ArrayList<DiffEvent>(deletions.size + changes.size + creations.size)
        events.addAll(deletions)
        events.addAll(changes)
        for (child in creations) events.add(DiffEvent(DirectoryObserver.CREATE, child))
        return events
    }

    // Deletions and changes share one old-snapshot traversal but retain their group ordering.
    private fun collectOldSnapshotEvents(
        old: Map<String, ChildState>,
        new: Map<String, ChildState>,
        mask: Int,
        deletions: MutableList<DiffEvent>,
        changes: MutableList<DiffEvent>,
        cancellationSignal: CancellationSignal?,
    ) {
        var row = 0
        for ((documentId, oldChild) in old) {
            if ((row++ and CANCELLATION_CHECK_MASK) == 0) cancellationSignal?.throwIfCanceled()
            val newChild = new[documentId]
            if (newChild == null) {
                if (mask includes DirectoryObserver.DELETE) {
                    deletions.add(DiffEvent(DirectoryObserver.DELETE, oldChild))
                }
                continue
            }

            // Reused instance == unchanged row (snapshot reads reuse only identical fields).
            if (oldChild === newChild) continue

            if (oldChild.name != newChild.name) {
                if (mask includes DirectoryObserver.MOVED_FROM) {
                    changes.add(DiffEvent(DirectoryObserver.MOVED_FROM, oldChild))
                }
                if (mask includes DirectoryObserver.MOVED_TO) {
                    changes.add(DiffEvent(DirectoryObserver.MOVED_TO, newChild))
                }
            } else if (
                mask includes DirectoryObserver.MODIFY && oldChild.isModified(newChild)
            ) {
                changes.add(DiffEvent(DirectoryObserver.MODIFY, newChild))
            }
        }
    }

    // In new snapshot order.
    private fun collectCreations(
        old: Map<String, ChildState>,
        new: Map<String, ChildState>,
        cancellationSignal: CancellationSignal?,
    ): List<ChildState> {
        val creations = ArrayList<ChildState>()
        var row = 0
        for ((documentId, newChild) in new) {
            if ((row++ and CANCELLATION_CHECK_MASK) == 0) cancellationSignal?.throwIfCanceled()
            if (documentId !in old) {
                creations.add(newChild)
            }
        }
        return creations
    }

    private infix fun Int.includes(event: Int): Boolean = and(event) != 0

    private const val CHANGE_EVENTS =
        DirectoryObserver.MOVED_FROM or DirectoryObserver.MOVED_TO or DirectoryObserver.MODIFY
    private const val OLD_SNAPSHOT_EVENTS = CHANGE_EVENTS or DirectoryObserver.DELETE
    private const val CANCELLATION_CHECK_MASK = 63
}