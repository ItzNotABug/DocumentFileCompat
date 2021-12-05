package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.filecompat.example.performance.Performance.getSizeInMb
import com.lazygeniouz.filecompat.file.DocumentFileCompat
import java.io.File
import java.util.*

@Suppress("deprecation")
object FilesPerformance {

    fun calculateFileSidePerformance(context: Context, uri: Uri): String {
        var results = ""
        results += calculateFilePerformance(uri) + "\n\n\n"
        results += calculateFileCompatPerformance(uri) + "\n\n\n"
        results += calculateDocumentFilePerformance(context, uri) + "\n"
        return results
    }

    // Native File
    private fun calculateFilePerformance(uri: Uri): String {
        val file = File(Environment.getExternalStorageDirectory(), Performance.getUsablePath(uri))
        val startingTime = Date().time

        var message = buildString(file.name, file.extension, file.lastModified(), file.length())
        val endCount = Performance.getDifference(startingTime, 1000.0000)

        message = "$message\nNative File Performance = ${endCount}s"
        return (message)
    }

    // FileCompat
    private fun calculateFileCompatPerformance(uri: Uri): String {
        val file = File(Environment.getExternalStorageDirectory(), Performance.getUsablePath(uri))
        val fileCompat = DocumentFileCompat.fromFile(file)
        val startingTime = Date().time
        var message = buildString(
            fileCompat.name,
            fileCompat.extension,
            fileCompat.lastModified,
            fileCompat.length
        )
        val endCount = Performance.getDifference(startingTime, 1000.0000)

        message = "$message\nFileCompat Performance = ${endCount}s"
        return (message)
    }

    // DocumentFile
    private fun calculateDocumentFilePerformance(context: Context, uri: Uri): String {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        val startingTime = Date().time

        var message = buildString(
            documentFile?.name!!,
            documentFile.name!!.substringAfterLast("."),
            documentFile.lastModified(),
            documentFile.length()
        )
        val endCount = Performance.getDifference(startingTime, 1000.0000)

        message = "$message\nDocumentFile File Performance = ${endCount}s"
        return (message)
    }

    private fun buildString(
        name: String, extension: String,
        lastModified: Long, fileSize: Long
    ): String {
        return "File Name: ${name}," +
                "\nFile Extension: ${extension}," +
                "\nFile Size: ${getSizeInMb(fileSize)}," +
                "\nFile last modified: ${Date(lastModified)}"
    }
}