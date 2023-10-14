package com.lazygeniouz.dfc.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.provider.DocumentsContract.isDocumentUri
import com.lazygeniouz.dfc.controller.DocumentController
import com.lazygeniouz.dfc.file.internals.RawDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import java.io.File

/**
 * The base class that handles multiple URI cases like SingleDocumentUri,
 * TreeDocumentUri & even File API support.
 *
 * Use [DocumentFileCompat.serialize] to get a [Serializable] object.
 */
abstract class DocumentFileCompat constructor(
    internal val context: Context,
    uri: Uri, val name: String = "",
    open val length: Long = 0,
    val lastModified: Long = -1L,
    internal val documentFlags: Int = -1,
    internal val documentMimeType: String = "",
) {

    // Secondary constructor for java.io.File type
    constructor(context: Context, file: File) : this(
        context, Uri.fromFile(file), file.name, file.length(),
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
     * @return A DocumentFileCompat object if file was created successfully, **null** otherwise.
     */
    abstract fun createFile(mimeType: String, name: String): DocumentFileCompat?

    /**
     * Create a Directory.
     *
     * @param name The name of the file.
     *
     * @return A DocumentFileCompat object if directory was created successfully, **null** otherwise.
     */
    abstract fun createDirectory(name: String): DocumentFileCompat?

    /**
     * This will return a list of [DocumentFileCompat] with all the defined fields
     * only when the current document is a **Directory**.
     *
     * A [UnsupportedOperationException] is thrown if the uri is not a directory.
     */
    abstract fun listFiles(): List<DocumentFileCompat>

    /**
     * This will return the children count inside a **Directory** without creating [DocumentFileCompat] objects.
     *
     * Returns **0** if the uri is not a directory or if the object at uri is null.
     *
     * **Note: You should use [listFiles] if you are going to need the elements later.**
     */
    abstract fun count(): Int

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

    /**
     * Rename a Document File / Folder.
     *
     * Will throw [UnsupportedOperationException] when called on a [SingleDocumentFileCompat].
     *
     * @return True if the rename was successful, False otherwise.
     * @throws UnsupportedOperationException
     */
    abstract fun renameTo(name: String): Boolean

    var uri: Uri = uri
        internal set

    /**
     * Return the parent file of this document.
     *
     * This will be null if called inside the selected directory.\
     * Should return the correct parent file for child items inside the selected directory.
     */
    var parentFile: DocumentFileCompat? = null
        internal set

    /**
     * Get the extension of the Document **File**.
     */
    open val extension: String
        // taken from Kotlin extension
        get() = name.substringAfterLast('.', "")

    /**
     * Return the MIME type of this document.
     *
     * @return A concrete mime type from [COLUMN_MIME_TYPE] column.
     */
    @Suppress("unused")
    fun getType(): String? {
        return if (documentMimeType == MIME_TYPE_DIR) null else documentMimeType
    }

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
     * Converts a non serializable [DocumentFileCompat] to a serializable [SerializedFile].
     */
    @Suppress("MemberVisibilityCanBePrivate")
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
        @JvmStatic
        fun fromTreeUri(context: Context, uri: Uri): DocumentFileCompat? {
            return TreeDocumentFileCompat.make(context, uri, true)
        }

        /**
         * Build a Document File with this helper.
         *
         * @param context Required for queries to [ContentResolver]
         * @param uri Uri which will be queried
         */
        @JvmStatic
        fun fromSingleUri(context: Context, uri: Uri): DocumentFileCompat? {
            return SingleDocumentFileCompat.make(context, uri)
        }

        /**
         * Build an initial File with this helper.
         *
         * RawDocumentFileCompat serves as an alternative to the **RawDocumentFile**
         * which handles Documents using the native [File] api.
         */
        @JvmStatic
        fun fromFile(context: Context, file: File): DocumentFileCompat {
            return RawDocumentFileCompat(context, file)
        }

        /**
         * Test if given Uri is backed by a [android.provider.DocumentsProvider].
         */
        @JvmStatic
        @Suppress("unused")
        fun isDocument(context: Context, uri: Uri): Boolean {
            return isDocumentUri(context, uri)
        }
    }
}