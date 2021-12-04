package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.filecompat.file.BaseFileCompat.FileCompat
import java.io.File
import java.util.*

@Suppress("deprecation")
object Performance {

    fun calculatePerformance(context: Context, uri: Uri): String {
        var results = ""
        results += calculateNativeFilePerformance(uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += "Fetch Uri & build a custom model,\nFileCompat Vs. DocumentFile\n\n"
        results += calculateFileCompatPerformance(context, uri) + "\n"
        results += calculateDocumentFilePerformance(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += "Fetching Uri only will always be faster (after File)" +
                "\nBut try fetching the Documents' Names.\n\n"
        results += calculateDocumentFilePerformanceOnlyUri(context, uri) + "\n"
        results += calculateDocumentFilePerformanceWithName(context, uri)
        return results
    }


    // Nothing beats this.
    // This test also requires the File Access Permission
    private fun calculateNativeFilePerformance(uri: Uri): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager())
                runFilesPerformance(uri)
            else "Android R+ requires All Files Access for this test to run."
        } else {
            runFilesPerformance(uri)
        }
    }

    private fun runFilesPerformance(uri: Uri): String {
        val path = uri.path!!
        // If the paths file for tests, just hardcode with a fixed directory.
        val formattedPath = when {
            path.contains(":") -> path.split(":")[1]
            path.contains("tree") -> path.substringAfter("tree/")
            else -> path
        }

        val startingTime = Date().time
        val files = File(
            Environment.getExternalStorageDirectory(),
            formattedPath
        ).listFiles()

        val endCount = getDifference(startingTime)
        val message = "Folder has ${files?.size} items.\nNative File Performance = ${endCount}s"
        return (message)
    }

    private fun calculateFileCompatPerformance(context: Context, uri: Uri): String {
        val startingTime = Date().time
        FileCompat.initialTreeUri(context, uri.toString()).listFiles()

        // This files have following data for easy accessibility
        // Uri, Name, Last Modified time
        return ("FileCompat Performance = ${getDifference(startingTime)}s")
    }

    private fun calculateDocumentFilePerformance(context: Context, uri: Uri): String {
        val startingTime = Date().time
        val listOfUsableElements = arrayListOf<FileCompat>()
        DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
            listOfUsableElements.add(
                FileCompat(
                    context, documentFile.uri.toString(),
                    documentFile.name ?: UUID.randomUUID().toString(),
                    documentFile.lastModified()
                )
            )
        }

        return ("DocumentFile Performance = ${getDifference(startingTime)}s")
    }

    private fun calculateDocumentFilePerformanceOnlyUri(context: Context, uri: Uri): String {
        val startingTime = Date().time
        DocumentFile.fromTreeUri(context, uri)?.listFiles()

        // This files have only a Uri, so this operation would be faster
        return ("DocumentFile Performance (Uri Only) = ${getDifference(startingTime)}s")
    }

    private fun calculateDocumentFilePerformanceWithName(context: Context, uri: Uri): String {
        val startingTime = Date().time
        val listOfUsableElements = arrayListOf<String>()
        DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
            listOfUsableElements.add(documentFile.name ?: "")
        }

        return ("DocumentFile Performance (With Names) = ${getDifference(startingTime)}s")
    }

    private fun getDifference(startingTime: Long): Double {
        return (Date().time - startingTime) / 1000.0
    }
}