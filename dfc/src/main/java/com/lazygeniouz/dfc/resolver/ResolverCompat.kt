package com.lazygeniouz.dfc.resolver

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import com.lazygeniouz.dfc.logger.ErrorLogger.logError

/**
 * This class calls relevant queries on the [ContentResolver]
 *
 * @param context Required to access the underlying **ContentResolver**
 */
internal class ResolverCompat(
    private val context: Context,
    private val uri: Uri,
) {

    // Projections
    private val _idProjection = DocumentsContract.Document.COLUMN_DOCUMENT_ID
    private val documentIdProjection = arrayOf(_idProjection)
    private val fullProjection = arrayOf(
        _idProjection,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS
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
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (exception: Exception) {
            logError("Exception while deleting document", exception)
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
            (DocumentsContract.renameDocument(contentResolver, uri, name) != null)
        } catch (exception: Exception) {
            logError("Exception while renaming document", exception)
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
            DocumentsContract.createDocument(contentResolver, uri, mimeType, name)
        } catch (exception: Exception) {
            logError("Exception while creating a document", exception)
            null
        }
    }

    /**
     * Queries the ContentResolver & builds a list of [DocumentFileCompat] with all the required fields.
     */
    internal fun queryAndMakeDocumentList(): List<DocumentFileCompat> {
        return runTreeQuery()
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
    internal fun count(): Int {
        val childrenUri = buildChildDocumentsUriUsingTree()
        val projection = arrayOf(DocumentsContract.Document.COLUMN_ICON)
        getCursor(childrenUri, projection)?.use { cursor -> return cursor.count }
        return 0
    }

    // Returns True if the Uri is a Tree Uri, False otherwise.
    private fun isTreeUri(): Boolean {
        val paths = uri.pathSegments
        return paths.size >= 2 && "tree" == paths[0]
    }

    // Create a child document uri from the tree uri.
    private fun buildChildDocumentsUriUsingTree(): Uri {
        return DocumentsContract.buildChildDocumentsUriUsingTree(
            getTreeUri(), DocumentsContract.getDocumentId(getTreeUri())
        )
    }

    // Build relevant Tree Uri.
    private fun getTreeUri(): Uri {
        val isDocument = DocumentsContract.isDocumentUri(context, uri)
        return DocumentsContract.buildDocumentUriUsingTree(
            uri, if (isDocument) DocumentsContract.getDocumentId(uri)
            else DocumentsContract.getTreeDocumentId(uri)
        )
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
            throw UnsupportedOperationException("Document Uri is not a Tree uri.")
        return runInitialQuery(isTree)
    }

    /**
     * Returns True if the Document Folder / File exists, False otherwise.
     */
    internal fun exists(): Boolean {
        getCursor(uri, documentIdProjection)?.use { cursor -> return (cursor.count > 0) }
        return false
    }

    // Get a Cursor to query the given Uri against provided projection
    private fun getCursor(uri: Uri, projection: Array<String>): Cursor? {
        return contentResolver.query(uri, projection, null, null, null)
    }

    /**
     * Runs query to build initial [TreeDocumentFileCompat] or a [SingleDocumentFileCompat].
     *
     * @param isTree Returns a [TreeDocumentFileCompat] if True, [SingleDocumentFileCompat] otherwise.
     */
    private fun runInitialQuery(isTree: Boolean): DocumentFileCompat? {
        val uriToQuery = if (!isTree) uri
        else DocumentsContract.buildDocumentUriUsingTree(
            getTreeUri(), DocumentsContract.getDocumentId(getTreeUri())
        )

        var document: DocumentFileCompat? = null

        getCursor(uriToQuery, fullProjection)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val documentId: String = cursor.getString(0)
                val documentUri: Uri = if (!isTree) uri
                else DocumentsContract.buildDocumentUriUsingTree(getTreeUri(), documentId)

                // Same logic but moved to separate classes for easy readability & understanding.
                document = if (!isTree) SingleDocumentFileCompat.make(context, cursor, documentUri)
                else TreeDocumentFileCompat.make(context, cursor, documentUri)
            }
        }

        return document
    }

    /**
     * Run query on the passed uri with full projections.
     *
     * @return A list of [DocumentFileCompat] with all fields.
     */
    private fun runTreeQuery(): List<DocumentFileCompat> {
        val childrenUri = buildChildDocumentsUriUsingTree()

        // empty list
        val listOfDocuments = arrayListOf<DocumentFileCompat>()

        getCursor(childrenUri, fullProjection)?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId: String = cursor.getString(0)
                val docUri = DocumentsContract.buildDocumentUriUsingTree(getTreeUri(), docId)
                listOfDocuments.add(TreeDocumentFileCompat.make(context, cursor, docUri))
            }
        }

        return listOfDocuments
    }
}