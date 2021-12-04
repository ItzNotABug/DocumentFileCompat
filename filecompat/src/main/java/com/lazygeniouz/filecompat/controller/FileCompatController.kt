package com.lazygeniouz.filecompat.controller

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.lazygeniouz.filecompat.file.BaseFileCompat.FileCompat

/**
 * This controller handles [Uri] & [FileCompat] building via [FileHandler].
 *
 * @param context Required for queries to [ContentResolver]
 * @param treeUri uri which will be queried
 */
class FileCompatController(context: Context, private val treeUri: String) {

    // File Handler to call delegate functions.
    private val documentHandler by lazy { FileHandler(context, Uri.parse(treeUri)) }

    /**
     * This will return a list of [FileCompat] with all the defined fields.
     */
    fun listFiles(): List<FileCompat> {
        return documentHandler.listFiles()
    }

    /**
     * Delete a document.
     */
    internal fun delete(uri: String): Boolean {
        return documentHandler.delete(Uri.parse(uri))
    }

    /**
     * This will return a list of [FileCompat] with only the [Uri] field.
     *
     * Used internally for faster looping.
     */
    internal fun listUris(): List<FileCompat> {
        return documentHandler.listUris()
    }
}