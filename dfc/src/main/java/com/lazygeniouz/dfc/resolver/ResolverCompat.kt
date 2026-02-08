package com.lazygeniouz.dfc.resolver

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.DocumentFileCompat
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
        val uri = file.uri
        val childrenUri = createChildrenUri(uri)
        val listOfDocuments = arrayListOf<DocumentFileCompat>()

        // ensure `Document.COLUMN_DOCUMENT_ID` is always included
        val finalProjection = if (Document.COLUMN_DOCUMENT_ID !in projection) {
            arrayOf(Document.COLUMN_DOCUMENT_ID, *projection)
        } else projection

        val cursor = getCursor(context, childrenUri, finalProjection) ?: return emptyList()

        cursor.use {
            val itemCount = cursor.count
            /**
             * Pre-sizing the list to avoid resizing overhead.
             * This is especially beneficial for directories with a large number of files.
             *
             * Memory comparison for 8192 files:
             * 1. With pre-sizing: 3.10 MB
             * 2. Without pre-sizing: 9.60 MB
             */
            if (itemCount > 10) listOfDocuments.ensureCapacity(itemCount)

            // Resolve column indices dynamically
            val idIndex = cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)

            val nameIndex = cursor.getColumnIndex(Document.COLUMN_DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(Document.COLUMN_SIZE)
            val modifiedIndex = cursor.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            val mimeIndex = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE)
            val flagsIndex = cursor.getColumnIndex(Document.COLUMN_FLAGS)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex) ?: continue
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

                val documentName = getStringOrDefault(cursor, nameIndex)
                val documentSize = getLongOrDefault(cursor, sizeIndex)
                val lastModifiedTime = getLongOrDefault(cursor, modifiedIndex, -1L)
                val documentMimeType = getStringOrDefault(cursor, mimeIndex)

                /**
                 * Default flags to 0 (no capabilities) when not included.
                 * Using `-1` here would make bitwise checks behave as "all flags set".
                 */
                val documentFlags = getLongOrDefault(cursor, flagsIndex, 0L).toInt()

                TreeDocumentFileCompat(
                    context, documentUri, documentName,
                    documentSize, lastModifiedTime,
                    documentMimeType, documentFlags
                ).also { childFile ->
                    childFile.parentFile = file
                    listOfDocuments.add(childFile)
                }
            }
        }

        return listOfDocuments
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
    private fun createChildrenUri(uri: Uri): Uri {
        return DocumentsContract.buildChildDocumentsUriUsingTree(
            uri, DocumentsContract.getDocumentId(uri)
        )
    }
}