package com.lazygeniouz.filecompat.extension

import android.net.Uri
import com.lazygeniouz.filecompat.file.DocumentFileCompat
import com.lazygeniouz.filecompat.file.SerializedFile

/**
 * If you need a [Serializable] list, this extension should be used.
 *
 * Converts the existing [DocumentFileCompat] to [SerializedFile].
 */
fun Collection<DocumentFileCompat>.toSerializedList()
        : ArrayList<SerializedFile> {
    val serializedList = arrayListOf<SerializedFile>()
    this.forEach { file -> serializedList.add(file.serialize()) }
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
    return this.firstOrNull() { file -> file.path.isNotEmpty() && file.path.contains(name) }
}

/**
 * [String] to [Uri], nothing fancy here.
 */
internal fun String.toUri(): Uri {
    return Uri.parse(this)
}