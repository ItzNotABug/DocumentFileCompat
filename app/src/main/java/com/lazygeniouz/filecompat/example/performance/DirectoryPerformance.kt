package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.filecompat.file.DocumentFileCompat
import java.io.File
import java.util.*

object DirectoryPerformance {

    fun calculateDirectorySidePerformance(context: Context, uri: Uri): String {
        var results = ""
        results += calculateNativeFilePerformance(uri) + "\n\n"
        results += calculateRawFileCompatPerformance(uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += "Fetch Uri & build a custom model,\nFileCompat Vs. DocumentFile\n\n"
        results += calculateFileCompatPerformance(context, uri) + "\n"
        results += calculateDocumentFilePerformance(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += "Fetching only Uris will always be faster (after File)" +
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

    // This test also requires the File Access Permission.
    // Comes Second to the Native File API due to building internal objects.
    private fun calculateRawFileCompatPerformance(uri: Uri): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager())
                runFilesCompatPerformance(uri)
            else "Android R+ requires All Files Access for this test to run."
        } else {
            runFilesCompatPerformance(uri)
        }
    }

    private fun runFilesPerformance(uri: Uri): String {
        // If the paths file for tests, just hardcode with a fixed directory.
        val formattedPath = Performance.getUsablePath(uri)

        val startingTime = Date().time
        val files = File(
            Environment.getExternalStorageDirectory(),
            formattedPath
        ).listFiles()

        val endCount = Performance.getDifference(startingTime)
        val message = "Folder has ${files?.size} items.\nNative File Performance = ${endCount}s"
        return (message)
    }

    private fun runFilesCompatPerformance(uri: Uri): String {
        // If the paths file for tests, just hardcode with a fixed directory.
        val formattedPath = Performance.getUsablePath(uri)
        val file = File(Environment.getExternalStorageDirectory(), formattedPath)

        val startingTime = Date().time
        val files = DocumentFileCompat.fromFile(file).listFiles()

        val endCount = Performance.getDifference(startingTime)
        val message = "Folder has ${files.size} items.\nRawFileCompat Performance = ${endCount}s"
        return (message)
    }

    private fun calculateFileCompatPerformance(context: Context, uri: Uri): String {
        val startingTime = Date().time
        DocumentFileCompat.fromTreeUri(context, uri)?.listFiles()

        // This files have following data for easy accessibility
        // Uri, Name, Last Modified time
        return ("FileCompat Performance = ${Performance.getDifference(startingTime)}s")
    }

    private fun calculateDocumentFilePerformance(context: Context, uri: Uri): String {
        val startingTime = Date().time
        val listOfUsableElements = arrayListOf<Performance.FileHolderPojo>()
        DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
            listOfUsableElements.add(
                Performance.FileHolderPojo(
                    documentFile.uri,
                    // Each of this will call ContentResolver
                    documentFile.name ?: "",
                    documentFile.length().toInt(),
                    documentFile.lastModified(),
                    documentFile.type ?: "",
                )
            )
        }

        return ("DocumentFile Performance = ${Performance.getDifference(startingTime)}s")
    }

    private fun calculateDocumentFilePerformanceOnlyUri(context: Context, uri: Uri): String {
        val startingTime = Date().time
        DocumentFile.fromTreeUri(context, uri)?.listFiles()

        // This files have only a Uri, so this operation would be faster
        return ("DocumentFile Performance (Uri Only) = ${Performance.getDifference(startingTime)}s")
    }

    private fun calculateDocumentFilePerformanceWithName(context: Context, uri: Uri): String {
        val startingTime = Date().time
        val listOfUsableElements = arrayListOf<String>()
        DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
            listOfUsableElements.add(documentFile.name ?: "")
        }

        return ("DocumentFile Performance (With Names) = ${Performance.getDifference(startingTime)}s")
    }

}