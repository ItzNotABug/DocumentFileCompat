package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import kotlin.system.measureTimeMillis

object Performance {

    private val Long.toSeconds get() = (this / 1000.00)

    fun calculateDirectoryPerformance(context: Context, uri: Uri): String {
        return DirectoryPerformance.calculateDirectorySidePerformance(context, uri)
    }

    fun calculateFilesPerformance(context: Context, uri: Uri): String {
        return FilesPerformance.calculateFileSidePerformance(context, uri)
    }

    fun getUsablePath(uri: Uri): String {
        val path = uri.path!!
        return when {
            path.contains(":") -> path.split(":")[1]
            path.contains("tree") -> path.substringAfter("tree/")
            else -> path
        }
    }

    fun measureTimeSeconds(block: () -> Unit): Double {
        return measureTimeMillis(block).toSeconds
    }

    fun getSizeInMb(size: Long): String {
        return String.format("%.2f Mb", (size / 1024) / 1024.00f)
    }

    data class FileHolderPojo(
        val documentUri: Uri,
        val documentName: String = "",
        val documentSize: Int = 0,
        val lastModifiedTime: Long = -1L,
        val documentMimeType: String = "",
    )
}