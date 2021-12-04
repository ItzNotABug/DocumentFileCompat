package com.lazygeniouz.filecompat.extension

import android.database.Cursor
import com.lazygeniouz.filecompat.file.BaseFileCompat.FileCompat
import com.lazygeniouz.filecompat.file.BaseFileCompat.RealSerializableFileCompat

/**
 * If you need a [Serializable] list, this extension should be used.
 *
 * Converts the existing [FileCompat] to [RealSerializableFileCompat].
 */
fun Collection<FileCompat>.toSerializable()
        : ArrayList<RealSerializableFileCompat> {
    val serializedList = arrayListOf<RealSerializableFileCompat>()
    this.forEach { file -> serializedList.add(file.toSerializable()) }
    return serializedList
}

/**
 * Equivalent of **DocumentFile.findFile**, but faster!
 *
 * Can be null if not file found with passed predicate.
 *
 * @param name Name of the File to find
 */
fun Collection<FileCompat>.findFile(name: String): FileCompat? {
    return this.firstOrNull() { file -> file.uri.isNotEmpty() && file.uri == name }
}

/**
 * Find a File whose name contains some characters
 *
 * Can be null if not file found with passed predicate.
 *
 * @param name Name of the File to find
 */
fun Collection<FileCompat>.findFileThatContains(name: String): FileCompat? {
    return this.firstOrNull() { file -> file.uri.isNotEmpty() && file.uri.contains(name) }
}