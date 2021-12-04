package com.lazygeniouz.filecompat.resolver

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.*
import android.provider.DocumentsContract.Document.*
import com.lazygeniouz.filecompat.file.BaseFileCompat.FileCompat

/**
 * This class call relevant queries on the [ContentResolver]
 *
 * @param context Required to access the underlying **ContentResolver**
 */
internal class ResolverCompat(private val context: Context) {

    private val tag = "ResolverCompat"

    // Projections
    private val _idProjection = COLUMN_DOCUMENT_ID
    private val uriProjection = arrayOf(_idProjection)
    private val fullProjection = arrayOf(_idProjection, COLUMN_DISPLAY_NAME, COLUMN_LAST_MODIFIED)

    // The star of the show!
    private val contentResolver by lazy { context.contentResolver }


    /**
     * Delete the file.
     *
     * @param uri Uri of the File to delete.
     *
     * @return True if deletion succeeded, False otherwise
     */
    fun deleteDocument(uri: Uri): Boolean {
        return try {
            deleteDocument(contentResolver, uri)
        } catch (exception: Exception) {
            println("$tag: Exception while deleting document = ${exception.message}")
            false
        }
    }

    /**
     * Queries the ContentResolver with the passed [Uri] & builds [FileCompat] only with Uris.
     *
     * @param uri Uri to be queried
     */
    internal fun queryForUris(uri: Uri): List<FileCompat> {
        return runQuery(uri, uriProjection)
    }

    /**
     * Queries the ContentResolver with the passed [Uri] & builds [FileCompat] all the fields.
     *
     * @param uri Uri to be queried
     */
    internal fun queryForFileCompat(uri: Uri): List<FileCompat> {
        return runQuery(uri, fullProjection)
    }

    // Build relevant Tree Uri.
    private fun getTreeUri(uri: Uri): Uri {
        val isDocument = isDocumentUri(context, uri)
        return buildDocumentUriUsingTree(
            uri, if (isDocument) getDocumentId(uri)
            else getTreeDocumentId(uri)
        )
    }

    /**
     * Run query on the passed uri with relevant projections.
     *
     * @param uri Uri to query
     * @param projections Args for the ContentResolver projections
     *
     * @return A list of [FileCompat] whether with all fields or only uri field,
     * depending on the passed projections parameter.
     */
    private fun runQuery(
        uri: Uri,
        projections: Array<String>
    ): ArrayList<FileCompat> {
        val isOnlyUri = projections.contentEquals(uriProjection)
        val childrenUri = buildChildDocumentsUriUsingTree(
            getTreeUri(uri), getDocumentId(getTreeUri(uri))
        )

        // empty list
        val listOfDocuments = arrayListOf<FileCompat>()

        contentResolver.query(
            childrenUri, projections,
            null, null, null
        )?.let { cursor ->
            cursor.use {
                while (cursor.moveToNext()) {
                    val documentId: String = cursor.getString(0)
                    val documentUri: Uri = buildDocumentUriUsingTree(
                        getTreeUri(uri), documentId
                    )

                    if (isOnlyUri) {
                        listOfDocuments.add(FileCompat(context, documentUri.toString()))
                    } else {
                        val documentName: String = cursor.getString(1)
                        val documentLastModified: Long = cursor.getLong(2)
                        listOfDocuments.add(
                            FileCompat(
                                context, documentUri.toString(),
                                documentName, documentLastModified
                            )
                        )
                    }
                }
            }
        }

        return listOfDocuments
    }
}