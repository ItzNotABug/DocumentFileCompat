package com.lazygeniouz.dfc.file.internals

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.resolver.ResolverCompat

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
    context: Context, documentUri: Uri, documentName: String = "", documentSize: Long = 0,
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
     * Cannot iterate a single document.
     *
     * @throws UnsupportedOperationException
     */
    override fun listFiles(projection: Array<String>): List<DocumentFileCompat> {
        throw UnsupportedOperationException()
    }

    /**
     * No [listFiles], no children, no count.
     *
     * @throws UnsupportedOperationException
     */
    override fun count(): Int {
        throw UnsupportedOperationException()
    }

    /**
     * No [listFiles] so no iterating to search for something..
     *
     * @throws UnsupportedOperationException
     */
    override fun findFile(name: String, ignoreCase: Boolean): DocumentFileCompat? {
        throw UnsupportedOperationException()
    }

    /**
     * [SingleDocumentFileCompat] has limited access and permissions to the [uri].
     *
     * @throws UnsupportedOperationException
     */
    override fun renameTo(name: String): Boolean {
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
         * Build a [SingleDocumentFileCompat] from a given [uri].
         */
        internal fun make(context: Context, self: Uri): SingleDocumentFileCompat? {
            if (isTreeUri(self)) return null
            if (!DocumentsContract.isDocumentUri(context, self)) return null

            ResolverCompat.getCursor(context, self, ResolverCompat.fullProjection)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val documentName: String = cursor.getString(1)
                        val documentSize: Long = cursor.getLong(2)
                        val documentLastModified: Long = cursor.getLong(3)
                        val documentMimeType: String = cursor.getString(4)
                        val documentFlags: Int = cursor.getLong(5).toInt()

                        return SingleDocumentFileCompat(
                            context, self,
                            documentName, documentSize,
                            documentLastModified, documentMimeType, documentFlags
                        )
                    }
                }

            return null
        }
    }
}