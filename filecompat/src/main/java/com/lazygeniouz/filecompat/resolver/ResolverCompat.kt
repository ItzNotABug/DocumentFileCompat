package com.lazygeniouz.filecompat.resolver

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract.*
import android.provider.DocumentsContract.Document.*
import com.lazygeniouz.filecompat.file.DocumentFileCompat
import com.lazygeniouz.filecompat.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.filecompat.file.internals.TreeDocumentFileCompat

/**
 * This class calls relevant queries on the [ContentResolver]
 *
 * @param context Required to access the underlying **ContentResolver**
 */
internal class ResolverCompat(
    private val context: Context,
    private val uri: Uri
) {

    private val tag = "DocumentFileCompat"

    // Projections
    private val _idProjection = COLUMN_DOCUMENT_ID
    private val documentIdProjection = arrayOf(_idProjection)
    private val fullProjection = arrayOf(
        _idProjection,
        COLUMN_DISPLAY_NAME,
        COLUMN_SIZE,
        COLUMN_LAST_MODIFIED,
        COLUMN_MIME_TYPE,
        COLUMN_FLAGS
    )

    // The star of the show!
    private val contentResolver by lazy { context.contentResolver }

    /**
     * Delete the file.
     *
     * @return True if deletion succeeded, False otherwise
     */
    internal fun deleteDocument(): Boolean {
        return try {
            deleteDocument(contentResolver, uri)
        } catch (exception: Exception) {
            println("$tag: Exception while deleting document = ${exception.message}")
            false
        }
    }

    /**
     * Rename a Document File / Folder.
     *
     * Returns True if the rename was successful, False otherwise.
     */
    internal fun renameTo(name: String): Boolean {
        return try {
            (renameDocument(contentResolver, uri, name) != null)
        } catch (exception: Exception) {
            println("$tag: Exception while renaming document = ${exception.message}")
            false
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
    internal fun createFile(mimeType: String, name: String): Uri? {
        return try {
            createDocument(contentResolver, uri, mimeType, name)
        } catch (exception: Exception) {
            println("$tag: Exception while creating a document = ${exception.message}")
            null
        }
    }

    /**
     * Queries the ContentResolver & builds a relevant [DocumentFileCompat] all the fields.
     */
    internal fun queryForFileCompat(): List<DocumentFileCompat> {
        return runTreeQuery()
    }

    // Build relevant Tree Uri.
    private fun getTreeUri(uri: Uri): Uri {
        val isDocument = isDocumentUri(context, uri)
        return buildDocumentUriUsingTree(
            uri, if (isDocument) getDocumentId(uri)
            else getTreeDocumentId(uri)
        )
    }

    // Returns True if the Uri is a Tree Uri, false otherwise.
    internal fun isTreeUri(): Boolean {
        val paths = uri.pathSegments
        return paths.size >= 2 && "tree" == paths[0]
    }

    /**
     * Builds a complete [DocumentFileCompat] object
     * when a first call to building a [TreeDocumentFileCompat] or a [SingleDocumentFileCompat] is made.
     *
     * @param isTree Returns a [TreeDocumentFileCompat] if True, [SingleDocumentFileCompat] otherwise.
     *
     * @throws UnsupportedOperationException If `isTree` is True but the Uri is not Tree based.
     */
    internal fun getInitialFileCompat(isTree: Boolean): DocumentFileCompat? {
        if (isTree && !isTreeUri())
            throw UnsupportedOperationException("Document Uri is not a Directory.")
        return runInitialQuery(isTree)
    }

    /**
     * Returns True if the Document Folder / File exists, False otherwise.
     */
    internal fun exists(): Boolean {
        var exists = false
        getCursor(uri, documentIdProjection)?.let { cursor ->
            exists = try {
                cursor.count > 0
            } catch (exception: Exception) {
                false
            } finally {
                cursor.close()
            }
        }
        return exists
    }

    // Get a Cursor to query the given Uri against provided projection
    private fun getCursor(uri: Uri, projection: Array<String>): Cursor? {
        return contentResolver.query(
            uri, projection,
            null, null, null
        )
    }

    /**
     * Runs query to build initial [TreeDocumentFileCompat] or a [SingleDocumentFileCompat].
     *
     * @param isTree Returns a [TreeDocumentFileCompat] if True, [SingleDocumentFileCompat] otherwise.
     */
    private fun runInitialQuery(isTree: Boolean): DocumentFileCompat? {
        val uriToUse = if (!isTree) uri
        else buildDocumentUriUsingTree(getTreeUri(uri), getDocumentId(getTreeUri(uri)))

        var singleDocument: DocumentFileCompat? = null

        getCursor(uriToUse, fullProjection)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val documentId: String = cursor.getString(0)
                val documentUri: Uri = if (!isTree) uri
                else buildDocumentUriUsingTree(getTreeUri(uri), documentId)

                val documentName: String = cursor.getString(1)
                val documentSize: Long = cursor.getLong(2)
                val documentLastModified: Long = cursor.getLong(3)
                val documentMimeType: String = cursor.getString(4)
                val documentFlags: Int = cursor.getLong(5).toInt()
                singleDocument = SingleDocumentFileCompat(
                    context, documentUri.toString(),
                    documentName, documentSize,
                    documentLastModified,
                    documentMimeType, documentFlags
                )
            }
        }

        return singleDocument
    }

    /**
     * Run query on the passed uri with full projections.
     *
     * @return A list of [DocumentFileCompat] with all fields.
     */
    private fun runTreeQuery(): ArrayList<DocumentFileCompat> {
        val childrenUri = buildChildDocumentsUriUsingTree(
            getTreeUri(uri), getDocumentId(getTreeUri(uri))
        )

        // empty list
        val listOfDocuments = arrayListOf<DocumentFileCompat>()

        getCursor(childrenUri, fullProjection)?.use { cursor ->
            while (cursor.moveToNext()) {
                val documentId: String = cursor.getString(0)
                val documentUri: Uri = buildDocumentUriUsingTree(
                    getTreeUri(uri), documentId
                )

                val documentName: String = cursor.getString(1)
                val documentSize: Long = cursor.getLong(2)
                val documentLastModified: Long = cursor.getLong(3)
                val documentMimeType: String = cursor.getString(4)
                val documentFlags: Int = cursor.getLong(5).toInt()
                listOfDocuments.add(
                    TreeDocumentFileCompat(
                        context, documentUri.toString(),
                        documentName, documentSize,
                        documentLastModified, documentMimeType, documentFlags
                    )
                )
            }
        }

        return listOfDocuments
    }
}