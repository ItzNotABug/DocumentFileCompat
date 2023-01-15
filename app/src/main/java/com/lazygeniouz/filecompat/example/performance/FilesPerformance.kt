package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.filecompat.example.performance.Performance.getSizeInMb
import com.lazygeniouz.filecompat.example.performance.Performance.measureTimeSeconds
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object FilesPerformance {

    fun calculateFileSidePerformance(context: Context, uri: Uri): String {
        var results = ""
        results += calculateFilePerformance(uri) + "\n\n\n"
        results += calculateFileCompatPerformance(context, uri) + "\n\n\n"
        results += calculateDocumentFilePerformance(context, uri) + "\n"
        return results
    }

    // Native File
    private fun calculateFilePerformance(uri: Uri): String {
        var message = ""
        measureTimeSeconds {
            val usableUri = Performance.getUsablePath(uri)
            val file = File(Environment.getExternalStorageDirectory(), usableUri)
            message = buildString(file.name, file.extension, file.lastModified(), file.length())
        }.also { time ->
            message = "$message\nNative File Performance = ${time}s"
            return (message)
        }
    }

    // FileCompat
    private fun calculateFileCompatPerformance(context: Context, uri: Uri): String {
        var message = ""
        measureTimeSeconds {
            val file = DocumentFileCompat.fromSingleUri(context, uri) ?: return@measureTimeSeconds
            message = buildString(
                file.name, file.extension,
                file.lastModified, file.length
            )
        }.also { time ->
            message = "$message\nDFC Performance = ${time}s"
            return (message)
        }
    }

    // DocumentFile
    private fun calculateDocumentFilePerformance(context: Context, uri: Uri): String {
        var message = ""
        measureTimeSeconds {
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            message = buildString(
                documentFile?.name!!,
                documentFile.name!!.substringAfterLast("."),
                documentFile.lastModified(),
                documentFile.length()
            )
        }.also { time ->
            message = "$message\nDocumentFile Performance = ${time}s"
            return (message)
        }
    }

    private fun buildString(
        name: String, extension: String,
        lastModified: Long, fileSize: Long,
    ): String {
        return "Name: ${name}," +
                "\nSize: ${getSizeInMb(fileSize)}, " +
                "Extension: ${extension}, " +
                "\nLast modified: ${
                    SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
                        .format(Date(lastModified))
                }"
    }
}