package com.lazygeniouz.filecompat.controller

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.lazygeniouz.filecompat.file.BaseFileCompat.FileCompat
import com.lazygeniouz.filecompat.resolver.ResolverCompat

/**
 * [FileHandler] builds a list of [FileCompat] with all the defined fields &
 * provides initial support for deleting files.
 *
 * @param context Required for queries to [ContentResolver]
 * @param uri uri which will be queried
 */
internal class FileHandler(context: Context, private val uri: Uri) {

    // ResolverCompat for querying the ContentResolver.
    private val resolverCompat by lazy { ResolverCompat(context) }

    /**
     * Delete the file.
     *
     * @return True if deletion succeeded, False otherwise
     */
    fun delete(uri: Uri): Boolean {
        return resolverCompat.deleteDocument(uri)
    }

    /**
     * This will return a list of [FileCompat] with all the defined fields.
     */
    fun listFiles(): List<FileCompat> {
        return queryForFiles()
    }

    internal fun listUris(): List<FileCompat> {
        return queryForUri()
    }

    /**
     * Query the ContentResolver for Document Files &
     * returns a list of [FileCompat] with all the defined fields.
     */
    private fun queryForFiles(): List<FileCompat> {
        return resolverCompat.queryForFileCompat(uri)
    }

    private fun queryForUri(): List<FileCompat> {
        return resolverCompat.queryForUris(uri)
    }
}