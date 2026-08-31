package com.lazygeniouz.dfc.resolver

import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for [ResolverCompat.readChildSnapshot] instance reuse: on a re-read of the same
 * directory, unchanged rows must return the previous instance (no new allocation),
 * changed rows must build a fresh object.
 */
@RunWith(AndroidJUnit4::class)
class ReadChildrenReuseTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private val parent = TreeDocumentFileCompat(
        context, Uri.parse("content://com.test.provider/tree/root/document/root"),
        "root", 0, 0, Document.MIME_TYPE_DIR, 0
    )

    private fun row(
        id: String,
        name: String = "$id.txt",
        size: Long = 10L,
        lastModified: Long = 100L,
        mime: String = "text/plain",
        flags: Int = 0,
    ): Array<Any?> = arrayOf(id, name, size, lastModified, mime, flags)

    private fun cursorOf(vararg rows: Array<Any?>): MatrixCursor {
        val cursor = MatrixCursor(
            arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_FLAGS,
            )
        )
        rows.forEach { cursor.addRow(it) }
        return cursor
    }

    private fun read(
        cursor: MatrixCursor,
        reusable: Map<String, DocumentFileCompat> = emptyMap(),
        cancellationSignal: CancellationSignal = CancellationSignal(),
    ): Map<String, DocumentFileCompat> {
        return cursor.use {
            ResolverCompat.readChildSnapshot(
                context, it, parent, reusable, cancellationSignal
            )
        }
    }

    @Test
    fun plainRead_keysByDocumentIdAndSetsParent() {
        val children = read(cursorOf(row("a"), row("b", mime = Document.MIME_TYPE_DIR)))

        assertEquals(2, children.size)
        assertEquals("a", DocumentsContract.getDocumentId(children.getValue("a").uri))
        assertSame(parent, children.getValue("a").parentFile)
        assertTrue(children.getValue("a") is SingleDocumentFileCompat)
        assertTrue(children.getValue("b") is TreeDocumentFileCompat)
    }

    @Test
    fun unchangedRows_reusePreviousInstances() {
        val first = read(cursorOf(row("a"), row("b")))
        val second = read(cursorOf(row("a"), row("b")), reusable = first)

        assertSame(first.getValue("a"), second.getValue("a"))
        assertSame(first.getValue("b"), second.getValue("b"))
    }

    @Test
    fun changedRow_buildsFreshInstance_othersReused() {
        val first = read(cursorOf(row("a"), row("b")))
        val second = read(
            cursorOf(row("a"), row("b", size = 99L, lastModified = 200L)),
            reusable = first
        )

        assertSame(first.getValue("a"), second.getValue("a"))
        assertNotSame(first.getValue("b"), second.getValue("b"))
        assertEquals(99L, second.getValue("b").length)
    }

    @Test
    fun renamedRow_buildsFreshInstance() {
        val first = read(cursorOf(row("a", name = "old.txt")))
        val second = read(cursorOf(row("a", name = "new.txt")), reusable = first)

        assertNotSame(first.getValue("a"), second.getValue("a"))
        assertEquals("old.txt", first.getValue("a").name)
        assertEquals("new.txt", second.getValue("a").name)
    }

    @Test
    fun flagsOnlyChange_buildsFreshInstance() {
        // Flags are excluded from MODIFY events, but capabilities changed —
        // the snapshot must carry the fresh flags even if no event is emitted.
        val first = read(cursorOf(row("a", flags = 0)))
        val second = read(cursorOf(row("a", flags = Document.FLAG_SUPPORTS_DELETE)), reusable = first)

        assertNotSame(first.getValue("a"), second.getValue("a"))
    }

    @Test
    fun mimeChange_buildsFreshInstanceOfCorrectType() {
        val first = read(cursorOf(row("a", mime = "text/plain")))
        val second = read(cursorOf(row("a", mime = Document.MIME_TYPE_DIR)), reusable = first)

        assertNotSame(first.getValue("a"), second.getValue("a"))
        assertTrue(first.getValue("a") is SingleDocumentFileCompat)
        assertTrue(second.getValue("a") is TreeDocumentFileCompat)
    }

    @Test
    fun newAndRemovedRows_dontConfuseReuse() {
        val first = read(cursorOf(row("a"), row("gone")))
        val second = read(cursorOf(row("a"), row("fresh")), reusable = first)

        assertSame(first.getValue("a"), second.getValue("a"))
        assertEquals(setOf("a", "fresh"), second.keys)
    }

    @Test
    fun malformedDocumentId_rejectsTheWholeSnapshot() {
        assertThrows(IllegalStateException::class.java) {
            read(cursorOf(row("")))
        }
    }

    @Test
    fun duplicateDocumentId_rejectsTheWholeSnapshot() {
        assertThrows(IllegalStateException::class.java) {
            read(cursorOf(row("a"), row("a", name = "duplicate.txt")))
        }
    }

    @Test
    fun cancelledRead_abortsBeforeBuildingTheSnapshot() {
        val cancellationSignal = CancellationSignal().apply { cancel() }
        assertThrows(OperationCanceledException::class.java) {
            read(cursorOf(row("a")), cancellationSignal = cancellationSignal)
        }
    }
}
