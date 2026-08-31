package com.lazygeniouz.dfc.observer

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for the observer diff engine.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotDifferTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun child(
        id: String,
        name: String = "$id.txt",
        mime: String = "text/plain",
        size: Long = 10L,
        lastModified: Long = 100L,
        flags: Int = 0,
    ): DocumentFileCompat {
        val uri = Uri.parse("content://com.test.provider/tree/root/document/$id")
        val document = if (mime == "vnd.android.document/directory") {
            TreeDocumentFileCompat(context, uri, name, size, lastModified, mime, flags)
        } else {
            SingleDocumentFileCompat(context, uri, name, size, lastModified, mime, flags)
        }
        return document
    }

    private fun snapshotOf(vararg children: DocumentFileCompat): LinkedHashMap<String, DocumentFileCompat> {
        val map = LinkedHashMap<String, DocumentFileCompat>()
        children.forEach { map[DocumentsContract.getDocumentId(it.uri)] = it }
        return map
    }

    private fun events(vararg children: Pair<Int, String>) = children.toList()

    private fun List<SnapshotDiffer.DiffEvent>.simplified() =
        map { it.event to DocumentsContract.getDocumentId(it.document.uri) }

    // region no change

    @Test
    fun noChange_emitsNothing() {
        val old = snapshotOf(child("a"), child("b"))
        val new = snapshotOf(child("a"), child("b"))
        assertTrue(SnapshotDiffer.diff(old, new).isEmpty())
    }

    @Test
    fun bothEmpty_emitsNothing() {
        assertTrue(SnapshotDiffer.diff(LinkedHashMap(), LinkedHashMap()).isEmpty())
    }

    // endregion

    // region create / delete / modify

    @Test
    fun newId_emitsCreate() {
        val result = SnapshotDiffer.diff(snapshotOf(child("a")), snapshotOf(child("a"), child("b")))
        assertEquals(events(DocumentFileCompat.CREATE to "b"), result.simplified())
    }

    @Test
    fun emptyOldToPopulated_emitsCreateForEverything() {
        val result = SnapshotDiffer.diff(LinkedHashMap(), snapshotOf(child("a"), child("b")))
        assertEquals(
            events(DocumentFileCompat.CREATE to "a", DocumentFileCompat.CREATE to "b"),
            result.simplified()
        )
    }

    @Test
    fun missingId_emitsDeleteWithLastKnownDocument() {
        val result = SnapshotDiffer.diff(snapshotOf(child("a"), child("b")), snapshotOf(child("a")))
        assertEquals(events(DocumentFileCompat.DELETE to "b"), result.simplified())
        assertEquals("b.txt", result.single().document.name)
    }

    @Test
    fun sizeChange_emitsModify() {
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", size = 10L)),
            snapshotOf(child("a", size = 20L)),
        )
        assertEquals(events(DocumentFileCompat.MODIFY to "a"), result.simplified())
    }

    @Test
    fun lastModifiedChange_emitsModify() {
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", lastModified = 100L)),
            snapshotOf(child("a", lastModified = 200L)),
        )
        assertEquals(events(DocumentFileCompat.MODIFY to "a"), result.simplified())
    }

    @Test
    fun mimeTypeChange_emitsModify() {
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", mime = "text/plain")),
            snapshotOf(child("a", mime = "application/json")),
        )
        assertEquals(events(DocumentFileCompat.MODIFY to "a"), result.simplified())
    }

    @Test
    fun flagsOnlyChange_emitsNothing() {
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", flags = 0)),
            snapshotOf(child("a", flags = 1)),
        )
        assertTrue(result.isEmpty())
    }

    // endregion

    // region rename semantics

    @Test
    fun sameIdRename_emitsMovedFromWithOldNameThenMovedToWithNewName() {
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", name = "old.txt")),
            snapshotOf(child("a", name = "new.txt")),
        )

        assertEquals(
            events(DocumentFileCompat.MOVED_FROM to "a", DocumentFileCompat.MOVED_TO to "a"),
            result.simplified()
        )
        assertEquals("old.txt", result[0].document.name)
        assertEquals("new.txt", result[1].document.name)
    }

    @Test
    fun renamePlusMetadataChange_emitsOnlyTheMovePair() {
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", name = "old.txt", size = 10L, lastModified = 100L)),
            snapshotOf(child("a", name = "new.txt", size = 99L, lastModified = 999L)),
        )

        assertEquals(
            events(DocumentFileCompat.MOVED_FROM to "a", DocumentFileCompat.MOVED_TO to "a"),
            result.simplified()
        )
    }

    @Test
    fun idChangingRename_emitsDeletePlusCreateNeverAMove() {
        // Similar looking entries with different ids must NOT be paired into a move.
        val result = SnapshotDiffer.diff(
            snapshotOf(child("a", name = "file.txt")),
            snapshotOf(child("b", name = "file (renamed).txt")),
        )

        assertEquals(
            events(DocumentFileCompat.DELETE to "a", DocumentFileCompat.CREATE to "b"),
            result.simplified()
        )
    }

    // endregion

    // region ordering & multiple simultaneous changes

    @Test
    fun multipleChanges_orderedDeletesThenRenamesAndModifiesThenCreates() {
        val old = snapshotOf(
            child("gone1"),
            child("renamed", name = "before.txt"),
            child("changed", size = 1L),
            child("gone2"),
            child("stable"),
        )
        val new = snapshotOf(
            child("fresh1"),
            child("renamed", name = "after.txt"),
            child("changed", size = 2L),
            child("stable"),
            child("fresh2"),
        )

        val result = SnapshotDiffer.diff(old, new)

        assertEquals(
            events(
                DocumentFileCompat.DELETE to "gone1",
                DocumentFileCompat.DELETE to "gone2",
                DocumentFileCompat.MOVED_FROM to "renamed",
                DocumentFileCompat.MOVED_TO to "renamed",
                DocumentFileCompat.MODIFY to "changed",
                DocumentFileCompat.CREATE to "fresh1",
                DocumentFileCompat.CREATE to "fresh2",
            ),
            result.simplified()
        )
    }

    // endregion

    // region mask filtering

    @Test
    fun maskFiltering_keepsOnlyRequestedEvents() {
        val old = snapshotOf(
            child("gone"), child("renamed", name = "a.txt"), child("changed", size = 1L)
        )
        val new = snapshotOf(
            child("renamed", name = "b.txt"), child("changed", size = 2L), child("fresh")
        )

        assertEquals(
            events(DocumentFileCompat.DELETE to "gone", DocumentFileCompat.CREATE to "fresh"),
            SnapshotDiffer.diff(
                old, new, DocumentFileCompat.CREATE or DocumentFileCompat.DELETE
            ).simplified()
        )

        assertEquals(
            events(DocumentFileCompat.MODIFY to "changed"),
            SnapshotDiffer.diff(old, new, DocumentFileCompat.MODIFY).simplified()
        )

        // The move pair can be filtered to either half individually.
        assertEquals(
            events(DocumentFileCompat.MOVED_FROM to "renamed"),
            SnapshotDiffer.diff(old, new, DocumentFileCompat.MOVED_FROM).simplified()
        )
        assertEquals(
            events(DocumentFileCompat.MOVED_TO to "renamed"),
            SnapshotDiffer.diff(old, new, DocumentFileCompat.MOVED_TO).simplified()
        )
    }

    @Test
    fun cancelledDiff_abortsBeforeEmittingEvents() {
        val cancellationSignal = CancellationSignal().apply { cancel() }
        assertThrows(OperationCanceledException::class.java) {
            SnapshotDiffer.diff(
                snapshotOf(child("old")),
                snapshotOf(child("new")),
                cancellationSignal = cancellationSignal,
            )
        }
    }

    // endregion

    // region constants sanity

    @Test
    fun eventConstants_aliasFileObserverBitValues() {
        // inotify values are ABI-stable; guards against accidental constant edits.
        assertEquals(0x00000002, DocumentFileCompat.MODIFY)
        assertEquals(0x00000040, DocumentFileCompat.MOVED_FROM)
        assertEquals(0x00000080, DocumentFileCompat.MOVED_TO)
        assertEquals(0x00000100, DocumentFileCompat.CREATE)
        assertEquals(0x00000200, DocumentFileCompat.DELETE)
    }

    // endregion
}
