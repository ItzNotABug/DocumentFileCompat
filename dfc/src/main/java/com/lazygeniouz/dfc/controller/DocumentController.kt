package com.lazygeniouz.dfc.controller

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.resolver.ResolverCompat


/**
 * This controller handles [Uri] & [DocumentFileCompat] building via [ResolverCompat].
 *
 * @param context Required for queries to [ContentResolver]
 * @param fileCompat For accessing DocumentFileCompat data
 */
internal class DocumentController(
    private val context: Context,
    private val fileCompat: DocumentFileCompat,
) {

    // DocumentsContract API level 24.
    private val flagVirtualDocument = 1 shl 9

    // Uri of the given [DocumentFileCompat]
    private val fileUri get() = fileCompat.uri

    /**
     * This will return a list of [DocumentFileCompat] with all the defined fields.
     */
    internal fun listFiles(
        projection: Array<String> = ResolverCompat.fullProjection,
    ): List<DocumentFileCompat> {
        return if (!isDirectory())
            throw UnsupportedOperationException("Selected document is not a Directory.")
        else ResolverCompat.listFiles(context, fileCompat, projection)
    }

    /**
     * This will return the children count in the directory.
     *
     * More optimised than using [List.size] via [listFiles].
     */
    internal fun count(): Int {
        return if (!isDirectory()) 0
        else ResolverCompat.count(context, fileUri)
    }

    /**
     * Returns True if the Document Folder / File exists, False otherwise.
     */
    internal fun exists(): Boolean {
        return ResolverCompat.exists(context, fileUri)
    }

    /**
     * Returns True if the Document Folder / File is Virtual, False otherwise.
     *
     * Indicates that a document is virtual,
     * and doesn't have byte representation in the MIME type specified as COLUMN_MIME_TYPE.
     */
    internal fun isVirtual(): Boolean {
        if (!DocumentsContract.isDocumentUri(context, fileUri)) return false
        if (fileCompat.documentFlags == -1) return false

        return fileCompat.documentFlags and flagVirtualDocument != 0
    }

    /**
     * Returns True if the Document is a File.
     */
    internal fun isFile(): Boolean {
        return !(Document.MIME_TYPE_DIR == fileCompat.documentMimeType || fileCompat.documentMimeType.isEmpty())
    }

    /**
     * Returns True if the Document is a Directory
     */
    internal fun isDirectory(): Boolean {
        return Document.MIME_TYPE_DIR == fileCompat.documentMimeType
    }

    /**
     * Returns True if the Document Folder / File is Readable.
     */
    internal fun canRead(): Boolean {
        if (context.checkCallingOrSelfUriPermission(
                fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        if (fileCompat.documentMimeType.isEmpty()) return false

        return true
    }

    /**
     * Returns `true` if the Document Folder / File is writable.
     */
    internal fun canWrite(): Boolean {
        if (context.checkCallingOrSelfUriPermission(
                fileUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) != PackageManager.PERMISSION_GRANTED
        ) return false

        if (fileCompat.documentMimeType.isEmpty()) return false
        if (fileCompat.documentFlags == -1) return false

        if (fileCompat.documentFlags and Document.FLAG_SUPPORTS_DELETE != 0)
            return true

        if (Document.MIME_TYPE_DIR == fileCompat.documentMimeType &&
            fileCompat.documentFlags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE != 0
        ) return true

        else if (fileCompat.documentMimeType.isNotEmpty() &&
            fileCompat.documentFlags and Document.FLAG_SUPPORTS_WRITE != 0
        ) return true

        return false
    }

    /**
     * Create a document file.
     *
     * @param mimeType Type of the file, e.g: text/plain.
     * @param name The name of the file.
     *
     * @return A Uri if file was created successfully, **null** otherwise.
     */
    internal fun createFile(mimeType: String, name: String): Uri? {
        return ResolverCompat.createFile(context, fileUri, mimeType, name)
    }

    /**
     * Rename a Document File / Folder.
     *
     * Returns True if the rename was successful, False otherwise.
     */
    internal fun renameTo(name: String): Uri? {
        return ResolverCompat.renameTo(context, fileUri, name)
    }

    /**
     * Delete a document.
     */
    internal fun delete(): Boolean {
        return ResolverCompat.deleteDocument(context, fileUri)
    }
}