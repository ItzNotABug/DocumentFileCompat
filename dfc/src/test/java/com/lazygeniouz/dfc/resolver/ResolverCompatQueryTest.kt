package com.lazygeniouz.dfc.resolver

import android.Manifest
import android.content.ContentResolver
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import com.lazygeniouz.dfc.file.Query
import com.lazygeniouz.dfc.file.internals.RawDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class ResolverCompatQueryTest {

    private lateinit var provider: TestDocumentsProvider

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        ShadowContentResolver.reset()
        provider = TestDocumentsProvider()
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = TestDocumentsProvider.AUTHORITY
                name = TestDocumentsProvider::class.java.name
                exported = true
                grantUriPermissions = true
                readPermission = Manifest.permission.MANAGE_DOCUMENTS
                writePermission = Manifest.permission.MANAGE_DOCUMENTS
            },
        )
        ShadowContentResolver.registerProviderInternal(TestDocumentsProvider.AUTHORITY, provider)
    }

    @Test
    fun `query forwards api 26 bundle arguments`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )

        val children = root.listFiles(
            Query.select(Document.COLUMN_DISPLAY_NAME),
            Query.filesOnly(),
            Query.limit(1),
        )

        assertEquals(
            listOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_FLAGS,
            ),
            provider.lastChildProjection?.toList(),
        )
        assertEquals(
            "(${Document.COLUMN_MIME_TYPE} != ?)",
            provider.lastQueryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SELECTION),
        )
        assertArrayEquals(
            arrayOf(Document.MIME_TYPE_DIR),
            provider.lastQueryArgs?.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS),
        )
        assertEquals(1, provider.lastQueryArgs?.getInt(ContentResolver.QUERY_ARG_LIMIT))
    }

    @Test
    fun `query select keeps internal projection and child document types`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )

        val children = root.listFiles(
            Query.select(Document.COLUMN_DISPLAY_NAME),
            Query.orderByAsc(Document.COLUMN_DISPLAY_NAME),
        )

        assertEquals(
            listOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_FLAGS,
            ),
            provider.lastChildProjection?.toList(),
        )

        val file = children.first { it.name == "notes.txt" }
        val directory = children.first { it.name == "photos" }

        assertTrue(file.isFile())
        assertFalse(file.isDirectory())
        assertEquals("text/plain", file.getType())
        assertEquals(Document.FLAG_SUPPORTS_WRITE, file.documentFlags)
        assertSame(root, file.parentFile)

        assertTrue(directory.isDirectory())
        assertFalse(directory.isFile())
        assertNull(directory.getType())
        assertEquals(Document.FLAG_DIR_SUPPORTS_CREATE, directory.documentFlags)
        assertSame(root, directory.parentFile)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `query falls back to legacy sort only before api 26`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )

        val children = root.listFiles(
            Query.select(Document.COLUMN_DISPLAY_NAME),
            Query.filesOnly(),
            Query.limit(1),
            Query.orderByDesc(Document.COLUMN_DISPLAY_NAME),
        )

        assertEquals(
            listOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_FLAGS,
            ),
            provider.lastChildProjection?.toList(),
        )
        assertNull(provider.lastQueryArgs)
        assertNull(provider.lastLegacySelection)
        assertNull(provider.lastLegacySelectionArgs)
        assertEquals(
            "${Document.COLUMN_DISPLAY_NAME} DESC",
            provider.lastLegacySortOrder,
        )
        assertEquals(2, children.size)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `legacy projection listFiles accepts provider specific columns`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )

        val children = root.listFiles(arrayOf("vendor.custom-column"))

        assertEquals(
            listOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                "vendor.custom-column",
                Document.COLUMN_FLAGS,
            ),
            provider.lastChildProjection?.toList(),
        )
        assertEquals(2, children.size)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.M])
    fun `legacy projection listFiles accepts empty projection`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )

        val children = root.listFiles(emptyArray<String>())

        assertEquals(
            listOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_MIME_TYPE,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_SIZE,
                Document.COLUMN_LAST_MODIFIED,
                Document.COLUMN_FLAGS,
            ),
            provider.lastChildProjection?.toList(),
        )
        assertEquals(2, children.size)
    }

    @Test
    fun `query returns empty list when provider omits required document id column`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )
        provider.omitDocumentIdColumn = true

        val children = root.listFiles(
            Query.select(Document.COLUMN_DISPLAY_NAME),
            Query.orderByAsc(Document.COLUMN_DISPLAY_NAME),
        )

        assertTrue(children.isEmpty())
    }

    @Test
    fun `query returns empty list when provider omits required mime type column`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )
        provider.omitMimeTypeColumn = true

        val children = root.listFiles(
            Query.select(Document.COLUMN_DISPLAY_NAME),
            Query.orderByAsc(Document.COLUMN_DISPLAY_NAME),
        )

        assertTrue(children.isEmpty())
    }

    @Test
    fun `query forwards raw selection and offset`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )

        root.listFiles(
            Query.rawSelection("${Document.COLUMN_DISPLAY_NAME} LIKE ?", "notes%"),
            Query.offset(2),
        )

        assertEquals(
            "(${Document.COLUMN_DISPLAY_NAME} LIKE ?)",
            provider.lastQueryArgs?.getString(ContentResolver.QUERY_ARG_SQL_SELECTION),
        )
        assertArrayEquals(
            arrayOf("notes%"),
            provider.lastQueryArgs?.getStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS),
        )
        assertEquals(2, provider.lastQueryArgs?.getInt(ContentResolver.QUERY_ARG_OFFSET))
    }

    @Test
    fun `count returns zero when child cursor count throws`() {
        val cursor = throwingCursor(childCursor(), throwOnCount = true)

        assertEquals(0, ResolverCompat.count(cursor))
    }

    @Test
    fun `build document list returns empty list when child cursor iteration throws`() {
        val context = RuntimeEnvironment.getApplication()
        val root = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "root",
            documentMimeType = Document.MIME_TYPE_DIR,
            documentFlags = Document.FLAG_DIR_SUPPORTS_CREATE,
        )
        val cursor = throwingCursor(childCursor(), throwOnGetString = true)

        val children = ResolverCompat.buildDocumentList(context, root, root.uri, cursor)

        assertTrue(children.isEmpty())
    }

    @Test
    fun `query listFiles rejects non-directory tree documents`() {
        val context = RuntimeEnvironment.getApplication()
        val file = TreeDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "notes.txt",
            documentMimeType = "text/plain",
        )

        try {
            file.listFiles(Query.limit(1))
            fail("Expected UnsupportedOperationException")
        } catch (exception: UnsupportedOperationException) {
            assertEquals("Selected document is not a Directory.", exception.message)
        }
    }

    @Test
    fun `query listFiles rejects single and raw documents`() {
        val context = RuntimeEnvironment.getApplication()
        val single = SingleDocumentFileCompat(
            context = context,
            documentUri = TestDocumentsProvider.rootDocumentUri(),
            documentName = "notes.txt",
            documentMimeType = "text/plain",
        )
        val raw = RawDocumentFileCompat(context, context.cacheDir)

        listOf(single, raw).forEach { file ->
            try {
                file.listFiles(Query.limit(1))
                fail("Expected UnsupportedOperationException")
            } catch (exception: UnsupportedOperationException) {
                assertEquals(
                    "Queries are only supported for DocumentsProvider-backed tree URIs.",
                    exception.message,
                )
            }
        }
    }

    private class TestDocumentsProvider : DocumentsProvider() {

        var lastChildProjection: Array<String>? = null
            private set

        var lastQueryArgs: Bundle? = null
            private set

        var lastLegacySelection: String? = null
            private set

        var lastLegacySelectionArgs: Array<String>? = null
            private set

        var lastLegacySortOrder: String? = null
            private set

        var omitDocumentIdColumn: Boolean = false

        var omitMimeTypeColumn: Boolean = false

        private val documents = listOf(
            TestDocument(
                id = ROOT_ID,
                name = "root",
                mimeType = Document.MIME_TYPE_DIR,
                flags = Document.FLAG_DIR_SUPPORTS_CREATE,
            ),
            TestDocument(
                id = FILE_ID,
                name = "notes.txt",
                mimeType = "text/plain",
                size = 128L,
                flags = Document.FLAG_SUPPORTS_WRITE,
            ),
            TestDocument(
                id = DIR_ID,
                name = "photos",
                mimeType = Document.MIME_TYPE_DIR,
                flags = Document.FLAG_DIR_SUPPORTS_CREATE,
            ),
        )

        override fun onCreate(): Boolean = true

        override fun queryRoots(projection: Array<out String>?): Cursor {
            return MatrixCursor(projection ?: emptyArray())
        }

        override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
            val document = documents.firstOrNull { it.id == documentId }
                ?: throw FileNotFoundException(documentId)

            return cursorOf(projection, listOf(document))
        }

        override fun queryChildDocuments(
            parentDocumentId: String?,
            projection: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            lastChildProjection = projection?.copyProjection()
            lastLegacySelection = null
            lastLegacySelectionArgs = null
            lastLegacySortOrder = sortOrder
            return cursorOf(projection, documents.filterNot { it.id == ROOT_ID })
        }

        override fun queryChildDocuments(
            parentDocumentId: String?,
            projection: Array<out String>?,
            queryArgs: Bundle?,
        ): Cursor {
            lastChildProjection = projection?.copyProjection()
            lastQueryArgs = queryArgs?.let(::Bundle)
            return cursorOf(projection, documents.filterNot { it.id == ROOT_ID })
        }

        override fun openDocument(
            documentId: String?,
            mode: String?,
            signal: CancellationSignal?,
        ): ParcelFileDescriptor {
            throw FileNotFoundException(documentId)
        }

        private fun cursorOf(
            projection: Array<out String>?,
            documents: List<TestDocument>,
        ): Cursor {
            val columns = (projection?.toList() ?: ResolverCompat.fullProjection.toList())
                .filterNot { omitDocumentIdColumn && it == Document.COLUMN_DOCUMENT_ID }
                .filterNot { omitMimeTypeColumn && it == Document.COLUMN_MIME_TYPE }
            return MatrixCursor(columns.toTypedArray()).apply {
                documents.forEach { document ->
                    addRow(columns.map { column -> document.valueFor(column) })
                }
            }
        }

        private data class TestDocument(
            val id: String,
            val name: String,
            val mimeType: String,
            val size: Long = 0L,
            val lastModified: Long = 0L,
            val flags: Int = 0,
        ) {

            fun valueFor(column: String): Any? {
                return when (column) {
                    Document.COLUMN_DOCUMENT_ID -> id
                    Document.COLUMN_DISPLAY_NAME -> name
                    Document.COLUMN_SIZE -> size
                    Document.COLUMN_LAST_MODIFIED -> lastModified
                    Document.COLUMN_MIME_TYPE -> mimeType
                    Document.COLUMN_FLAGS -> flags
                    else -> null
                }
            }
        }

        private fun Array<out String>.copyProjection(): Array<String> {
            return Array(size) { index -> this[index] }
        }

        companion object {
            const val AUTHORITY = "com.lazygeniouz.dfc.test.documents"
            private const val ROOT_ID = "root"
            private const val FILE_ID = "root/notes.txt"
            private const val DIR_ID = "root/photos"

            fun rootDocumentUri(): Uri {
                return DocumentsContract.buildDocumentUriUsingTree(
                    DocumentsContract.buildTreeDocumentUri(AUTHORITY, ROOT_ID),
                    ROOT_ID,
                )
            }
        }
    }
}

private fun childCursor(): Cursor {
    return MatrixCursor(ResolverCompat.fullProjection).apply {
        addRow(
            arrayOf<Any?>(
                "root/notes.txt",
                "notes.txt",
                128L,
                0L,
                "text/plain",
                Document.FLAG_SUPPORTS_WRITE,
            )
        )
    }
}

private fun throwingCursor(
    cursor: Cursor,
    throwOnCount: Boolean = false,
    throwOnGetString: Boolean = false,
): Cursor {
    return object : CursorWrapper(cursor) {

        override fun getCount(): Int {
            if (throwOnCount) throw IllegalStateException("count failed")
            return super.getCount()
        }

        override fun getString(columnIndex: Int): String? {
            if (throwOnGetString) throw IllegalStateException("getString failed")
            return super.getString(columnIndex)
        }
    }
}
