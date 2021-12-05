package com.lazygeniouz.filecompat.extension

import android.net.Uri
import com.lazygeniouz.filecompat.file.DocumentFileCompat
import com.lazygeniouz.filecompat.file.SerializableDocumentFile
import java.io.File

/**
 * If you need a [Serializable] list, this extension should be used.
 *
 * Converts the existing [DocumentFileCompat] to [SerializableDocumentFile].
 */
fun Collection<DocumentFileCompat>.toSerializable()
        : ArrayList<SerializableDocumentFile> {
    val serializedList = arrayListOf<SerializableDocumentFile>()
    this.forEach { file -> serializedList.add(file.toSerializable()) }
    return serializedList
}

/**
 * Equivalent of **DocumentFile.findFile**, but faster!
 *
 * @param name Name of the File to find
 *
 * @return A Nullable FileCompat object.
 */
fun Collection<DocumentFileCompat>.findFile(name: String): DocumentFileCompat? {
    return this.firstOrNull() { file -> file.name.isNotEmpty() && file.name == name }
}

/**
 * Find a File whose Uri contains searched characters.
 *
 * This explicitly checks against the Document's **Uri**.
 *
 * If you want to check against the Document's **Name**, use `Collection<FileCompat>.findFile`.
 *
 * @param name Name of the File to find
 *
 * @return A Nullable FileCompat object.
 */
fun Collection<DocumentFileCompat>.findFileThatContainsUri(name: String): DocumentFileCompat? {
    return this.firstOrNull() { file ->
        file.uri.isNotEmpty() && file.uri.contains(
            name
        )
    }
}

/**
 * [String] to [Uri], nothing fancy here.
 */
internal fun String.toUri(): Uri {
    return Uri.parse(this)
}


/**
 * [String] to [File], nothing fancy here.
 */
internal fun String.toFile(): File {
    return File(this)
}