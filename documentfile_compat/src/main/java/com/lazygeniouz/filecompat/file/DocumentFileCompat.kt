package com.lazygeniouz.filecompat.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.isDocumentUri
import com.lazygeniouz.filecompat.controller.DocumentController
import com.lazygeniouz.filecompat.extension.toSerializedList
import com.lazygeniouz.filecompat.file.internals.RawDocumentFileCompat
import com.lazygeniouz.filecompat.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.filecompat.file.internals.TreeDocumentFileCompat
import com.lazygeniouz.filecompat.resolver.ResolverCompat
import java.io.File

/**
 * The base class that handles multiple URI cases like SingleDocumentUri,
 * TreeDocumentUri & even File API support.
 *
 * Use [DocumentFileCompat.serialize] to get a [Serializable] object.
 */
abstract class DocumentFileCompat constructor(
    internal val context: Context,
    internal val path: String,
    val name: String = "",
    open val length: Long = 0,
    val lastModified: Long = -1L,
    internal val documentFlags: Int = -1,
    internal val documentMimeType: String = ""
) {

    // Secondary constructor for java.io.File type
    constructor(context: Context, file: File) : this(
        context, file.absolutePath, file.name, file.length(),
        file.lastModified(), -1, RawDocumentFileCompat.getMimeType(file)
    )

    /**
     * Context is asserted as **NonNull** here because it is only required
     * by the [DocumentController] which is initialized **lazily** & would
     * never be initialized in the [RawDocumentFileCompat] as it uses the [File] api completely.
     */
    internal val fileController by lazy { DocumentController(context, this) }

    /**
     * Create a document file.
     *
     * @param mimeType Type of the file, e.g: text/plain.
     * @param name The name of the file.
     *
     * @return A FileCompat object if file was created successfully, **null** otherwise.
     */
    abstract fun createFile(mimeType: String, name: String): DocumentFileCompat?

    /**
     * Create a Directory.
     *
     * @param name The name of the file.
     *
     * @return A FileCompat object if directory was created successfully, **null** otherwise.
     */
    abstract fun createDirectory(name: String): DocumentFileCompat?

    /**
     * This will return a list of [DocumentFileCompat] with all the defined fields
     * only when the current document is a **Directory**, **null** otherwise
     */
    abstract fun listFiles(): List<DocumentFileCompat>

    /**
     * Find a File against the given name.
     *
     *
     * Do not use this if you are going to call `listFiles()` again on the same
     * DocumentFileCompat object because this method internally searches same list.
     *
     * Multiple calls to `listFiles()` can have a performance hit.
     */
    abstract fun findFile(name: String): DocumentFileCompat?

    /**
     * Copy a document to a given Uri.
     */
    abstract fun copyTo(destination: Uri)

    /**
     * Copy a document to this Uri from source.
     */
    abstract fun copyFrom(source: Uri)

    open val uri: Uri
        get() = Uri.parse(path)

    /**
     * Get the extension of the Document **File**.
     */
    open val extension: String
        // taken from Kotlin extension
        get() = name.substringAfterLast('.', "")

    /**
     * Delete the file.
     *
     * @return True if deletion succeeded, False otherwise.
     */
    open fun delete(): Boolean {
        return fileController.delete()
    }

    /**
     * Returns True if the Document Folder / File exists, False otherwise.
     */
    open fun exists(): Boolean {
        return fileController.exists()
    }

    /**
     * Returns True if the Document Folder / File is Virtual, False otherwise.
     *
     * Indicates that a document is virtual,
     * and doesn't have byte representation in the MIME type specified as COLUMN_MIME_TYPE.
     */
    open fun isVirtual(): Boolean {
        return fileController.isVirtual()
    }

    /**
     * Returns True if the Document Folder / File is Readable.
     */
    open fun canRead(): Boolean {
        return fileController.canRead()
    }

    /**
     * Returns True if the Document Folder / File is Writable.
     */
    open fun canWrite(): Boolean {
        return fileController.canWrite()
    }

    /**
     * Returns True if the Document is a File.
     */
    open fun isFile(): Boolean {
        return fileController.isFile()
    }

    /**
     * Returns True if the Document is a Directory
     */
    open fun isDirectory(): Boolean {
        return fileController.isDirectory()
    }

    /**
     * Rename a Document File / Folder.
     *
     * Returns True if the rename was successful, False otherwise.
     */
    open fun renameTo(name: String): Boolean {
        return fileController.renameTo(name)
    }

    /**
     * Converts a non serializable [DocumentFileCompat] to a serializable [SerializedFile].
     *
     * To convert a list of [DocumentFileCompat] to a list of [Serializable],
     * use [toSerializedList] on a `List<DocumentFileCompat>`
     */
    fun serialize(): SerializedFile {
        return SerializedFile.from(this)
    }

    companion object {

        /**
         * Build a Document Tree with this helper.
         *
         * @param context Required for queries to [ContentResolver]
         * @param uri uri which will be queried
         */
        fun fromTreeUri(context: Context, uri: Uri): DocumentFileCompat? {
            val fileCompat = ResolverCompat(context, uri).getInitialFileCompat(true)
            return if (fileCompat != null) TreeDocumentFileCompat(context, fileCompat)
            else null
        }

        /**
         * Build a Document File with this helper.
         *
         * @param context Required for queries to [ContentResolver]
         * @param uri Uri which will be queried
         */
        fun fromSingleUri(context: Context, uri: Uri): DocumentFileCompat? {
            val fileCompat = ResolverCompat(context, uri).getInitialFileCompat(false)
            return if (fileCompat != null) SingleDocumentFileCompat(context, fileCompat)
            else null
        }

        /**
         * Build an initial File with this helper.
         *
         * RawDocumentFileCompat serves as an alternative to the **RawDocumentFile**
         * which handles Documents using the native [File] api.
         */
        fun fromFile(context: Context, file: File): DocumentFileCompat {
            return RawDocumentFileCompat(context, file)
        }

        /**
         * Test if given Uri is backed by a [android.provider.DocumentsProvider].
         */
        fun isDocument(context: Context, uri: Uri): Boolean {
            return isDocumentUri(context, uri)
        }
    }
}