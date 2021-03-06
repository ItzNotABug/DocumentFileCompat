package com.lazygeniouz.filecompat.file

import java.io.Serializable

/**
 * This can be used as a [Serializable] object to pass around the app
 * without creating your own Models as this only holds references to Primitive types.
 */
class SerializedFile private constructor(
    val uriPath: String, val name: String,
    val length: Long, val lastModified: Long,
    val mimeType: String, val flags: Int
) : Serializable {

    val extension: String
        get() = name.substringAfterLast('.', "")

    companion object {

        /**
         * Copy fields from a [DocumentFileCompat] object.
         * This is sort of a **Read Only** object.
         *
         * @param file [DocumentFileCompat] object for copying primitive data types.
         * @return A Serializable object.
         */
        internal fun from(file: DocumentFileCompat): SerializedFile {
            file.apply {
                return SerializedFile(
                    path, name, length,
                    lastModified, documentMimeType, documentFlags
                )
            }
        }
    }
}