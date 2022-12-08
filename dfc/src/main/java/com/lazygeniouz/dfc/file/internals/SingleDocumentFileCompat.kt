package com.lazygeniouz.dfc.file.internals

import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.lazygeniouz.dfc.file.DocumentFileCompat

/**
 * SingleFileCompat serves as an alternative to the **SingleDocumentFile**
 * which handles Documents having **NO** Tree structure, i.e. Just Files.
 *
 *
 * @param context Context required by the DocumentFileCompat internally.
 *
 * Other params same as [DocumentFileCompat].
 */
internal class SingleDocumentFileCompat(
    context: Context, documentUri: String, documentName: String = "", documentSize: Long = 0,
    lastModifiedTime: Long = -1L, documentMimeType: String = "", documentFlags: Int = -1,
) : DocumentFileCompat(
    context, documentUri, documentName, documentSize,
    lastModifiedTime, documentFlags, documentMimeType
) {

    /**
     * Cannot create a File without a Parent Directory Uri.
     *
     * @throws UnsupportedOperationException
     */
    override fun createFile(mimeType: String, name: String): DocumentFileCompat? {
        throw UnsupportedOperationException()
    }

    /**
     * Cannot create a Directory without a Parent Directory Uri.
     *
     * @throws UnsupportedOperationException
     */
    override fun createDirectory(name: String): DocumentFileCompat? {
        throw UnsupportedOperationException()
    }

    /**
     * Cannot iterate a File.
     *
     * @throws UnsupportedOperationException
     */
    override fun listFiles(): List<DocumentFileCompat> {
        throw UnsupportedOperationException()
    }

    /**
     * No [listFiles] so no iterating to search for something..
     *
     * @throws UnsupportedOperationException
     */
    override fun findFile(name: String): DocumentFileCompat? {
        throw UnsupportedOperationException()
    }

    // Copies current file to the destination uri.
    override fun copyTo(destination: Uri) {
        val inputStream = context.contentResolver.openInputStream(uri)!!
        val outputStream = context.contentResolver.openOutputStream(destination)!!
        inputStream.use { stream -> stream.copyTo(outputStream) }
    }

    // Copies current source uri at this uri's location.
    override fun copyFrom(source: Uri) {
        val inputStream = context.contentResolver.openInputStream(source)!!
        val outputStream = context.contentResolver.openOutputStream(uri)!!
        inputStream.use { stream -> stream.copyTo(outputStream) }
    }

    internal companion object {

        /**
         * Extracted in to a separate companion method to not clutter common code while running the
         * **ContentResolver Queries**.
         */
        internal fun make(
            context: Context,
            cursor: Cursor, documentUri: Uri,
        ): SingleDocumentFileCompat {
            // cursor.getString(0) is the documentId
            val documentName: String = cursor.getString(1)
            val documentSize: Long = cursor.getLong(2)
            val documentLastModified: Long = cursor.getLong(3)
            val documentMimeType: String = cursor.getString(4)
            val documentFlags: Int = cursor.getLong(5).toInt()

            return SingleDocumentFileCompat(
                context, documentUri.toString(),
                documentName, documentSize,
                documentLastModified, documentMimeType, documentFlags
            )
        }
    }
}