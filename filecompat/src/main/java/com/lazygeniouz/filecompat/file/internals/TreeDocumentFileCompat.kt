package com.lazygeniouz.filecompat.file.internals

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import com.lazygeniouz.filecompat.extension.findFile
import com.lazygeniouz.filecompat.file.DocumentFileCompat

/**
 * TreeFileCompat serves as an alternative to the **TreeDocumentFile**
 * which handles Documents having a Tree structure.
 *
 * For example: Listing files in a Directory, Creating Files & Directories, etc.
 *
 * @param context Context required by the FileCompat internally.
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
     * Secondary constructor for easy initialization from the [DocumentFileCompat.fromTreeUri].
     */
    internal constructor(
        context: Context,
        fileCompat: DocumentFileCompat,
    ) : this(
        context, fileCompat.path,
        fileCompat.name, fileCompat.length,
        fileCompat.lastModified, fileCompat.documentMimeType, fileCompat.documentFlags
    )

    /**
     * Create a document file.
     *
     * @param mimeType Type of the file, e.g: text/plain.
     * @param name The name of the file.
     *
     * @return A FileCompat object if file was created successfully, **null** otherwise.
     */
    override fun createFile(mimeType: String, name: String): DocumentFileCompat? {
        val treeFileUri = fileController.createFile(mimeType, name)
        return treeFileUri?.let { fromTreeUri(context!!, treeFileUri) }
    }

    /**
     * Create a Directory.
     *
     * @param name The name of the file.
     *
     * @return A FileCompat object if directory was created successfully, **null** otherwise.
     */
    override fun createDirectory(name: String): DocumentFileCompat? {
        val treeFileUri = fileController.createFile(MIME_TYPE_DIR, name)
        return treeFileUri?.let { fromTreeUri(context!!, treeFileUri) }
    }

    /**
     * This will return a list of [DocumentFileCompat] with all the defined fields.
     */
    override fun listFiles(): List<DocumentFileCompat> {
        return fileController.listFiles()
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
}