package com.lazygeniouz.filecompat.file

/**
 * This can be used as a [Serializable] object to pass around
 * without creating your own Models as this only holds references to Primitive types.
 *
 */
class SerializableDocumentFile private constructor(
    val documentUri: String, val documentName: String,
    val documentSize: Long, val lastModifiedTime: Long,
    val documentMimeType: String, val documentFlags: Int
) {

    companion object {

        /**
         * Copy fields from a [DocumentFileCompat] object.
         * This is sort of a **Read Only** object.
         *
         * @param from [DocumentFileCompat] object for copying primitive data types.
         * @return A Serializable **BaseFileCompat** object.
         */
        internal fun fromFileCompat(from: DocumentFileCompat): SerializableDocumentFile {
            return SerializableDocumentFile(
                from.uri, from.name, from.length,
                from.lastModified, from.documentMimeType, from.documentFlags
            )
        }
    }
}