package com.lazygeniouz.dfc.resolver

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import com.lazygeniouz.dfc.logger.ErrorLogger

/**
 * Helper class for calling relevant methods on [DocumentsContract] & queries via [ContentResolver].
 */
internal object ResolverCompat {

    private val iconProjection = arrayOf(DocumentsContract.Document.COLUMN_ICON)
    private val idProjection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
    val fullProjection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS
    )

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
            return DocumentsContract.renameDocument(context.contentResolver, uri, name)
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
    internal fun listFiles(context: Context, file: DocumentFileCompat): List<DocumentFileCompat> {
        val uri = file.uri
        val childrenUri = createChildrenUri(uri)
        val listOfDocuments = arrayListOf<DocumentFileCompat>()

        getCursor(context, childrenUri, fullProjection)?.use { cursor ->
            val itemCount = cursor.count
            /**
             * Pre-sizing the list to avoid resizing overhead.
             * This is especially beneficial for directories with a large number of files.
             */
            if (itemCount > 10) listOfDocuments.ensureCapacity(itemCount)

            while (cursor.moveToNext()) {
                val documentId: String = cursor.getString(0)
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)

                val documentName: String = cursor.getString(1)
                val documentSize: Long = cursor.getLong(2)
                val documentLastModified: Long = cursor.getLong(3)
                val documentMimeType: String = cursor.getString(4)
                val documentFlags: Int = cursor.getLong(5).toInt()

                TreeDocumentFileCompat(
                    context, documentUri, documentName,
                    documentSize, documentLastModified,
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
        return context.contentResolver.query(
            uri, projection, null, null, null
        )
    }

    // Make children uri for query.
    private fun createChildrenUri(uri: Uri): Uri {
        return DocumentsContract.buildChildDocumentsUriUsingTree(
            uri, DocumentsContract.getDocumentId(uri)
        )
    }
}