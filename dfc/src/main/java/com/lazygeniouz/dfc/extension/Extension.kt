package com.lazygeniouz.dfc.extension

import android.util.Log
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.SerializedFile

/**
 * If you need a [Serializable] list, this extension should be used.
 *
 * Converts the existing [DocumentFileCompat] to [SerializedFile].
 */
fun Collection<DocumentFileCompat>.toSerializedList(): List<SerializedFile> {
    return this.map { SerializedFile.from(it) }
}

/**
 * Equivalent of **DocumentFile.findFile**, but faster!
 *
 * @param name Name of the File to find
 *
 * @return A Nullable DocumentFileCompat object.
 */
fun Collection<DocumentFileCompat>.findFile(name: String): DocumentFileCompat? {
    return this.firstOrNull { file -> file.name.isNotEmpty() && file.name == name }
}

/**
 * Find a File whose Uri contains searched characters.
 *
 * This explicitly checks against the Document's **Uri**.
 *
 * If you want to check against the Document's **Name**, use [findFile].
 *
 * @param uri Uri or a part of the Uri of the file to find.
 *
 * @return A Nullable DocumentFileCompat object.
 */
fun Collection<DocumentFileCompat>.findFileThatContains(uri: String): DocumentFileCompat? {
    return this.firstOrNull { file -> file.path.isNotEmpty() && file.path.contains(uri) }
}

// Print error logs to logcat.
internal fun logError(message: String?) {
    if (message == null) return
    Log.e("DocumentFileCompat", message)
}