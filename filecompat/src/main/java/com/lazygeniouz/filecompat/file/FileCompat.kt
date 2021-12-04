package com.lazygeniouz.filecompat.file

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.lazygeniouz.filecompat.controller.FileCompatController
import java.io.Serializable

/**
 * Base data holder class.
 *
 * Though this class does not hold any reference to Context, it cannot be used directly because
 * **Sealed types cannot be instantiated**.
 *
 * @param uri Uri of the Document File / Folder
 * @param name Name of the Document File / Folder
 * @param lastModified Time when the Document File / Folder was last modified
 */
sealed class BaseFileCompat(
    val uri: String,
    val name: String = "",
    val lastModified: Long = -1L
) : Serializable {

    /**
     * This cannot be used as a Serializable
     * because this class holds a reference to [Context].
     *
     * Use [FileCompat.toSerializable] for a [Serializable] object.
     * @see com.lazygeniouz.filecompat.extension.toSerializable
     */
    class FileCompat(
        context: Context,
        private val documentUri: String,
        private val documentName: String = "",
        lastModifiedTime: Long = -1L
    ) : BaseFileCompat(documentUri, documentName, lastModifiedTime) {

        private val fileController by lazy { FileCompatController(context, documentUri) }

        /**
         * Get the extension of the Document **File**
         */
        fun getExtension(): String {
            return documentName.substring(documentName.lastIndexOf("."))
        }

        /**
         * Delete the file.
         *
         * @return True if deletion succeeded, False otherwise
         */
        fun delete(): Boolean {
            return fileController.delete(documentUri)
        }

        /**
         * This will return a list of [FileCompat] with all the defined fields.
         */
        fun listFiles(): List<FileCompat> {
            return fileController.listFiles()
        }

        /**
         * Converts a non serializable [FileCompat] to a serializable [RealSerializableFileCompat].
         *
         * @see com.lazygeniouz.filecompat.extension.toSerializable
         */
        fun toSerializable(): RealSerializableFileCompat {
            return RealSerializableFileCompat.fromFileCompat(this)
        }

        /**
         * This will return a list of [FileCompat] with only the [Uri] field.
         *
         * Used internally for faster looping.
         */
        internal fun listUris(): List<FileCompat> {
            return fileController.listUris()
        }

        companion object {
            /**
             * Build an initial Document File / Tree with this helper.
             *
             * @param context Required for queries to [ContentResolver]
             * @param uri uri which will be queried
             */
            fun initialTreeUri(context: Context, uri: String): FileCompatController {
                return FileCompatController(context, uri)
            }
        }
    }

    /**
     * This can be used as a Serializable because
     * this class only holds a reference to Primitive types.
     */
    class RealSerializableFileCompat private constructor(
        documentUri: String, documentName: String, lastModifiedTime: Long
    ) : BaseFileCompat(documentUri, documentName, lastModifiedTime) {

        companion object {
            /**
             * Copy fields from a [FileCompat] object.
             * This is sort of a **Read Only** object.
             *
             * If you want more control over it (e.g: delete, listFiles),
             * simply pass the [Uri] to [FileCompat.initialTreeUri].
             *
             * @param from [FileCompat] object for copying primitive data types.
             * @return A Serializable **BaseFileCompat** object.
             */
            internal fun fromFileCompat(from: FileCompat): RealSerializableFileCompat {
                return RealSerializableFileCompat(from.uri, from.name, from.lastModified)
            }
        }
    }
}