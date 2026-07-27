package com.lazygeniouz.dfc.file.internals

import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.resolver.ResolverCompat
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class DocumentFileCompatMakeTest {

    @Test
    fun `single makeFromCursor returns null when cursor read throws`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://com.lazygeniouz.dfc.test.documents/document/root%2Fnotes.txt")
        val cursor = throwingCursor(documentCursor("text/plain"))

        assertNull(
            DocumentFileCompat.makeFromCursor(cursor, "test") { name, size, lastModified, mime, flags ->
                SingleDocumentFileCompat(context, uri, name, size, lastModified, mime, flags)
            }
        )
    }

    @Test
    fun `tree makeFromCursor returns null when cursor read throws`() {
        val context = RuntimeEnvironment.getApplication()
        val uri = Uri.parse("content://com.lazygeniouz.dfc.test.documents/tree/root/document/root")
        val cursor = throwingCursor(documentCursor(Document.MIME_TYPE_DIR))

        assertNull(
            DocumentFileCompat.makeFromCursor(cursor, "test") { name, size, lastModified, mime, flags ->
                TreeDocumentFileCompat(context, uri, name, size, lastModified, mime, flags)
            }
        )
    }

    private fun documentCursor(mimeType: String): Cursor {
        return MatrixCursor(ResolverCompat.fullProjection).apply {
            addRow(
                arrayOf<Any?>(
                    "root/notes.txt",
                    "notes.txt",
                    128L,
                    0L,
                    mimeType,
                    Document.FLAG_SUPPORTS_WRITE,
                )
            )
        }
    }

    private fun throwingCursor(cursor: Cursor): Cursor {
        return object : CursorWrapper(cursor) {

            override fun getString(columnIndex: Int): String? {
                throw IllegalStateException("getString failed")
            }
        }
    }
}
