package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import java.util.*

@Suppress("deprecation")
object Performance {

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

    fun getSizeInMb(size: Long): String {
        return "${(size / 1024) / 1024} Mb"
    }

    fun getDifference(startingTime: Long, divisible: Double = 1000.0): Double {
        return (Date().time - startingTime) / divisible
    }

    data class FileHolderPojo(
        val documentUri: String,
        val documentName: String = "",
        val documentSize: Int = 0,
        val lastModifiedTime: Long = -1L,
        val documentMimeType: String = "",
    )
}