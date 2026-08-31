package com.lazygeniouz.dfc.resolver

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import com.lazygeniouz.dfc.logger.ErrorLogger

/**
 * Helper class for calling relevant methods on [DocumentsContract] & queries via [ContentResolver].
 */
internal object ResolverCompat {

    private val iconProjection = arrayOf(Document.COLUMN_ICON)
    private val idProjection = arrayOf(Document.COLUMN_DOCUMENT_ID)
    val fullProjection = arrayOf(
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_LAST_MODIFIED,
        Document.COLUMN_MIME_TYPE,
        Document.COLUMN_FLAGS
    )

    private fun getStringOrDefault(cursor: Cursor, index: Int, default: String = ""): String {
        if (index == -1 || cursor.isNull(index)) return default
        return cursor.getString(index) ?: default
    }

    private fun getLongOrDefault(cursor: Cursor, index: Int, default: Long = 0L): Long {
        if (index == -1 || cursor.isNull(index)) return default
        return cursor.getLong(index)
    }

    /**
     * Delete the file.
     *
     * @return True if deletion succeeded, False otherwise
     */
    internal fun deleteDocument(context: Context, uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (exception: Exception) {
            ErrorLogger.logError("Exception while deleting document", exception)
            false
        }
    }

    /**
     * Rename a Document File / Folder.
     *
     * Returns True if the rename was successful, False otherwise.
     */
    internal fun renameTo(context: Context, uri: Uri, name: String): Uri? {
        return try {
            DocumentsContract.renameDocument(context.contentResolver, uri, name)
        } catch (exception: Exception) {
            ErrorLogger.logError("Exception while renaming document", exception)
            null
        }
    }

    /**
     * Create a document file.
     *
     * @param mimeType Type of the file, e.g: text/plain.
     * @param name The name of the file.
     *
     * @return A Uri if file was created successfully, **null** if any exception was caught.
     */
    internal fun createFile(context: Context, uri: Uri, mimeType: String, name: String): Uri? {
        return try {
            DocumentsContract.createDocument(context.contentResolver, uri, mimeType, name)
        } catch (exception: Exception) {
            ErrorLogger.logError("Exception while creating a document", exception)
            null
        }
    }

    /**
     * Returns the children count without creating [DocumentFileCompat] objects.
     *
     * **Local Test Result**: [DocumentsContract.Document.COLUMN_ICON] was the fastest on a directory
     * of 824 items, more than 2x against `listFiles().size`.
     *
     * - Min: 0.275, Max: 0.613 (listFiles().size)
     * - Avg: 0.444, Diff: 0.338, % Change: 55.14
     */
    internal fun count(context: Context, uri: Uri): Int {
        val childrenUri = createChildrenUri(uri)
        return getCursor(
            context,
            childrenUri,
            iconProjection
        )?.use { cursor -> return cursor.count } ?: 0
    }

    /**
     * Returns True if the Document Folder / File exists, False otherwise.
     */
    internal fun exists(context: Context, uri: Uri): Boolean {
        return try {
            getCursor(context, uri, idProjection)?.use { cursor ->
                cursor.count > 0
            } ?: false
        } catch (exception: Exception) {
            ErrorLogger.logError("Exception while checking if the uri exists", exception)
            false
        }
    }

    /**
     * Queries the ContentResolver & builds a list of [DocumentFileCompat] with all the required fields.
     */
    internal fun listFiles(
        context: Context,
        file: DocumentFileCompat,
        projection: Array<String> = fullProjection,
    ): List<DocumentFileCompat> {
        val childrenUri = createChildrenUri(file.uri)

        val finalProjection = arrayOf(
            Document.COLUMN_DOCUMENT_ID, /* identifier */
            Document.COLUMN_MIME_TYPE, /* for supporting rename via `isDirectory` check */
            *projection
        ).distinct().toTypedArray()

        val cursor = getCursor(context, childrenUri, finalProjection) ?: return emptyList()
        return cursor.use { readChildren(context, it, file) }
    }

    /** Read direct children from [cursor], advancing it without closing it. */
    internal fun readChildren(
        context: Context,
        cursor: Cursor,
        parent: DocumentFileCompat,
    ): List<DocumentFileCompat> {
        val itemCount = cursor.count
        val listOfDocuments = arrayListOf<DocumentFileCompat>()
        if (itemCount > 10) listOfDocuments.ensureCapacity(itemCount)

        forEachChild(context, cursor, parent, emptyMap(), null, strictIds = false) { _, child ->
            listOfDocuments.add(child)
        }
        return listOfDocuments
    }

    /**
     * Reads an observer snapshot directly into a map keyed by document id. Unchanged rows reuse
     * their previous [DocumentFileCompat] instance; malformed or duplicate ids reject the entire
     * scan so malformed rows cannot be interpreted as deletions.
     */
    internal fun readChildSnapshot(
        context: Context,
        cursor: Cursor,
        parent: DocumentFileCompat,
        reusable: Map<String, DocumentFileCompat>,
        cancellationSignal: CancellationSignal,
    ): LinkedHashMap<String, DocumentFileCompat> {
        cancellationSignal.throwIfCanceled()
        val itemCount = cursor.count
        cancellationSignal.throwIfCanceled()
        val snapshot = LinkedHashMap<String, DocumentFileCompat>(mapCapacity(itemCount))
        forEachChild(
            context, cursor, parent, reusable, cancellationSignal, strictIds = true
        ) { documentId, child ->
            check(snapshot.put(documentId, child) == null) {
                "Directory query returned duplicate document id: $documentId"
            }
        }
        return snapshot
    }

    /** Query the watched document itself when an empty child cursor cannot prove it still exists. */
    internal fun isExistingDirectory(
        context: Context,
        uri: Uri,
        cancellationSignal: CancellationSignal,
    ): Boolean {
        cancellationSignal.throwIfCanceled()
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(Document.COLUMN_MIME_TYPE),
            null, null, null,
            cancellationSignal,
        ) ?: return false

        return cursor.use {
            cancellationSignal.throwIfCanceled()
            val mimeIndex = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            it.moveToFirst() && getStringOrDefault(it, mimeIndex) == Document.MIME_TYPE_DIR
        }
    }

    /** Event payloads must not expose the mutable objects retained by the internal snapshot. */
    internal fun copyForCallback(document: DocumentFileCompat): DocumentFileCompat {
        val copy: DocumentFileCompat = if (document.documentMimeType == Document.MIME_TYPE_DIR) {
            TreeDocumentFileCompat(
                document.context, document.uri, document.name, document.length,
                document.lastModified, document.documentMimeType, document.documentFlags,
            )
        } else {
            SingleDocumentFileCompat(
                document.context, document.uri, document.name, document.length,
                document.lastModified, document.documentMimeType, document.documentFlags,
            )
        }
        copy.parentFile = document.parentFile
        return copy
    }

    private inline fun forEachChild(
        context: Context,
        cursor: Cursor,
        parent: DocumentFileCompat,
        reusable: Map<String, DocumentFileCompat>,
        cancellationSignal: CancellationSignal?,
        strictIds: Boolean,
        onChild: (documentId: String, child: DocumentFileCompat) -> Unit,
    ) {
        val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
        val nameIndex = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(Document.COLUMN_SIZE)
        val modifiedIndex = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
        val mimeIndex = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
        val flagsIndex = cursor.getColumnIndex(Document.COLUMN_FLAGS)

        var row = 0
        while (cursor.moveToNext()) {
            if ((row++ and CANCELLATION_CHECK_MASK) == 0) {
                cancellationSignal?.throwIfCanceled()
            }

            val documentId = cursor.getString(idIndex)
            if (documentId.isNullOrEmpty()) {
                check(!strictIds) { "Directory query returned an empty document id" }
                continue
            }

            val documentName = getStringOrDefault(cursor, nameIndex)
            val documentSize = getLongOrDefault(cursor, sizeIndex)
            val lastModifiedTime = getLongOrDefault(cursor, modifiedIndex, -1L)
            val documentMimeType = getStringOrDefault(cursor, mimeIndex)

            /**
             * Default flags to 0 (no capabilities) when not included.
             * Using `-1` here would make bitwise checks behave as "all flags set".
             */
            val documentFlags = getLongOrDefault(cursor, flagsIndex, 0L).toInt()

            val previous = reusable[documentId]
            val child = if (previous != null && canReuse(
                    previous, documentName, documentSize,
                    lastModifiedTime, documentMimeType, documentFlags
                )
            ) {
                previous
            } else {
                buildChild(
                    context, parent, documentId, documentName,
                    documentSize, lastModifiedTime, documentMimeType, documentFlags
                )
            }
            onChild(documentId, child)
        }
        cancellationSignal?.throwIfCanceled()
    }

    // Identical fields (flags included: capabilities may change without a diff event).
    private fun canReuse(
        previous: DocumentFileCompat,
        name: String, size: Long, lastModified: Long, mimeType: String, flags: Int,
    ): Boolean {
        return previous.name == name
                && previous.length == size
                && previous.lastModified == lastModified
                && previous.documentMimeType == mimeType
                && previous.documentFlags == flags
    }

    // Builds the correctly typed document for a child row.
    private fun buildChild(
        context: Context, parent: DocumentFileCompat, documentId: String,
        name: String, size: Long, lastModified: Long, mimeType: String, flags: Int,
    ): DocumentFileCompat {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(parent.uri, documentId)

        val childFile: DocumentFileCompat = if (mimeType == Document.MIME_TYPE_DIR) {
            TreeDocumentFileCompat(context, documentUri, name, size, lastModified, mimeType, flags)
        } else {
            SingleDocumentFileCompat(context, documentUri, name, size, lastModified, mimeType, flags)
        }

        childFile.parentFile = parent
        return childFile
    }

    private fun mapCapacity(expectedSize: Int): Int = when {
        expectedSize < 3 -> expectedSize + 1
        expectedSize < 1 shl 30 -> expectedSize + expectedSize / 3 + 1
        else -> Int.MAX_VALUE
    }

    /**
     * Get [Cursor] from [ContentResolver.query] with given [projection] on a given [uri].
     */
    fun getCursor(context: Context, uri: Uri, projection: Array<String>): Cursor? {
        return try {
            context.contentResolver.query(
                uri, projection, null, null, null
            )
        } catch (exception: Exception) {
            /**
             * This exception can occur in scenarios such as -
             *
             * - The Uri became invalid due to external changes (e.g., permissions revoked, storage unmounted, etc.).
             * - The file or directory represented by this Uri was probably deleted or became `inaccessible` after the Uri was obtained but before this operation was performed.
             */
            ErrorLogger.logError("Exception while building the Cursor", exception)
            null
        }
    }

    // Make children uri for query.
    internal fun createChildrenUri(uri: Uri): Uri {
        return DocumentsContract.buildChildDocumentsUriUsingTree(
            uri, DocumentsContract.getDocumentId(uri)
        )
    }

    private const val CANCELLATION_CHECK_MASK = 63
}
