package com.lazygeniouz.dfc.file.internals

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import com.lazygeniouz.dfc.extension.findFile
import com.lazygeniouz.dfc.file.DocumentFileCompat

/**
 * TreeFileCompat serves as an alternative to the **TreeDocumentFile**
 * which handles Documents having a Tree structure.
 *
 * For example: Listing files in a Directory, Creating Files & Directories, etc.
 *
 * @param context Context required by the DocumentFileCompat internally.
 *
 * Other params same as [DocumentFileCompat].
 */
internal class TreeDocumentFileCompat constructor(
    context: Context, documentUri: String, documentName: String = "", documentSize: Long = 0,
    lastModifiedTime: Long = -1L, documentMimeType: String = "", documentFlags: Int = -1,
) : DocumentFileCompat(
    context, documentUri, documentName, documentSize,
    lastModifiedTime, documentFlags, documentMimeType
) {

    /**
     * Create a document file.
     *
     * @param mimeType Type of the file, e.g: text/plain.
     * @param name The name of the file.
     *
     * @return A DocumentFileCompat object if file was created successfully, **null** otherwise.
     */
    override fun createFile(mimeType: String, name: String): DocumentFileCompat? {
        val treeFileUri = fileController.createFile(mimeType, name)
        return treeFileUri?.let { fromTreeUri(context, treeFileUri) }
    }

    /**
     * Create a Directory.
     *
     * @param name The name of the file.
     *
     * @return A DocumentFileCompat object if directory was created successfully, **null** otherwise.
     */
    override fun createDirectory(name: String): DocumentFileCompat? {
        val treeFileUri = fileController.createFile(MIME_TYPE_DIR, name)
        return treeFileUri?.let { fromTreeUri(context, treeFileUri) }
    }

    /**
     * This will return a list of [DocumentFileCompat] with all the defined fields.
     */
    override fun listFiles(): List<DocumentFileCompat> {
        return fileController.listFiles()
    }

    /**
     * This will return the children count in the directory.
     *
     * More optimised than using [List.size] via [listFiles].
     *
     */
    override fun count(): Int {
        return fileController.count()
    }

    /**
     * Return a file if exists, else **null**.
     */
    override fun findFile(name: String): DocumentFileCompat? {
        return listFiles().findFile(name)
    }

    /**
     * Copy would work only if the underlying Uri is a SingleDocumentFile or a File.
     */
    override fun copyTo(destination: Uri) {
        throw UnsupportedOperationException("Cannot open a stream on a tree")
    }

    /**
     * Copy would work only if the underlying Uri is a SingleDocumentFile or a File.
     */
    override fun copyFrom(source: Uri) {
        throw UnsupportedOperationException("Cannot open a stream on a tree")
    }

    internal companion object {

        /**
         * Extracted in to a separate companion method to not clutter common code while running the
         * **ContentResolver Queries**.
         */
        internal fun make(
            context: Context,
            cursor: Cursor, documentUri: Uri,
        ): TreeDocumentFileCompat {
            // cursor.getString(0) is the documentId
            val documentName: String = cursor.getString(1)
            val documentSize: Long = cursor.getLong(2)
            val documentLastModified: Long = cursor.getLong(3)
            val documentMimeType: String = cursor.getString(4)
            val documentFlags: Int = cursor.getLong(5).toInt()

            return TreeDocumentFileCompat(
                context, documentUri.toString(),
                documentName, documentSize,
                documentLastModified, documentMimeType, documentFlags
            )
        }
    }
}