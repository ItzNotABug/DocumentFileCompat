package com.lazygeniouz.filecompat.file.internals

import android.content.Context
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import com.lazygeniouz.filecompat.extension.toUri
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
        context, fileCompat.uri,
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
        val treeFileUri = fileController.createFile(mimeType, name).toString()
        return fromTreeUri(context!!, treeFileUri.toUri())
    }

    /**
     * Create a Directory.
     *
     * @param name The name of the file.
     *
     * @return A FileCompat object if directory was created successfully, **null** otherwise.
     */
    override fun createDirectory(name: String): DocumentFileCompat? {
        val treeFileUri = fileController.createFile(MIME_TYPE_DIR, name).toString()
        return fromTreeUri(context!!, treeFileUri.toUri())
    }

    /**
     * This will return a list of [DocumentFileCompat] with all the defined fields.
     */
    override fun listFiles(): List<DocumentFileCompat> {
        return fileController.listFiles()
    }
}