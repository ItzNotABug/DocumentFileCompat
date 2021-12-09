package com.lazygeniouz.filecompat.file.internals

import android.content.Context
import com.lazygeniouz.filecompat.file.DocumentFileCompat

/**
 * SingleFileCompat serves as an alternative to the **SingleDocumentFile**
 * which handles Documents having **NO** Tree structure, i.e. Just Files.
 *
 *
 * @param context Context required by the FileCompat internally.
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

    internal constructor(
        context: Context,
        fileCompat: DocumentFileCompat,
    ) : this(
        context, fileCompat.path, fileCompat.name,
        fileCompat.length, fileCompat.lastModified,
        fileCompat.documentMimeType, fileCompat.documentFlags
    )

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
}