package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.filecompat.example.performance.Performance.measureTimeSeconds
import java.io.File

object DirectoryPerformance {

    fun calculateDirectorySidePerformance(context: Context, uri: Uri): String {
        var results = ""
        results += calculateNativeFilePerformance(uri) + "\n\n"
        results += calculateRawFileCompatPerformance(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += calculateDocumentFileCompatPerformance(context, uri) + "\n"
        results += calculateDocumentFilePerformance(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += "Fetching only Uris will always be faster (after File)" + "\nBut try fetching the Documents' Names.\n\n"
        results += calculateDocumentFileCompatPerformanceWithName(context, uri) + "\n"
        results += calculateDocumentFilePerformanceOnlyUri(context, uri) + "\n"
        results += calculateDocumentFilePerformanceWithName(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")
        results += calculateCountVsListSize(context, uri)
        return results
    }


    // Nothing beats this.
    // This test also requires the File Access Permission
    private fun calculateNativeFilePerformance(uri: Uri): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                runFilesPerformance(uri)
            } else {
                "Android R+ requires All Files Access for this test to run."
            }
        } else {
            runFilesPerformance(uri)
        }
    }

    // This test also requires the File Access Permission.
    // Comes Second to the Native File API due to building internal objects.
    private fun calculateRawFileCompatPerformance(context: Context, uri: Uri): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                runFilesCompatPerformance(context, uri)
            } else {
                "Android R+ requires All Files Access for this test to run."
            }
        } else {
            runFilesCompatPerformance(context, uri)
        }
    }

    private fun runFilesPerformance(uri: Uri): String {
        var size: Int = -1
        measureTimeSeconds {
            // If the paths file for tests, just hardcode with a fixed directory.
            val formattedPath = Performance.getUsablePath(uri)
            val files = File(Environment.getExternalStorageDirectory(), formattedPath).listFiles()
            size = files?.size ?: -1
        }.also { time ->
            val message = "Folder has $size items.\nNative File Performance = ${time}s"
            return (message)
        }
    }

    private fun runFilesCompatPerformance(context: Context, uri: Uri): String {
        var size: Int = -1
        measureTimeSeconds {
            // If the paths file for tests, just hardcode with a fixed directory.
            val formattedPath = Performance.getUsablePath(uri)
            val file = File(Environment.getExternalStorageDirectory(), formattedPath)
            val files = DocumentFileCompat.fromFile(context, file).listFiles()
            size = files.size
        }.also { time ->
            // building a DFC model with File will take a few ms against the native perf.
            val message = "Folder has $size items.\nDFC (File API) Performance = ${time}s"
            return (message)
        }
    }

    private fun calculateDocumentFileCompatPerformance(context: Context, uri: Uri): String {
        measureTimeSeconds {
            val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            documentFile?.listFiles()
        }.also { time ->
            // This files have Uri, Name, Last Modified time for easy accessibility
            return ("DFC Performance = ${time}s")
        }
    }

    private fun calculateDocumentFilePerformance(context: Context, uri: Uri): String {
        measureTimeSeconds {
            val listOfUsableElements = arrayListOf<Performance.FileHolderPojo>()
            DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
                listOfUsableElements.add(
                    Performance.FileHolderPojo(
                        documentFile.uri,
                        // Each of this will call ContentResolver
                        documentFile.name.orEmpty(),
                        documentFile.length().toInt(),
                        documentFile.lastModified(),
                        documentFile.type.orEmpty(),
                    )
                )
            }
        }.also { time -> return ("DocumentFile Performance = ${time}s") }
    }

    private fun calculateDocumentFilePerformanceOnlyUri(context: Context, uri: Uri): String {
        measureTimeSeconds {
            DocumentFile.fromTreeUri(context, uri)?.listFiles()
        }.also { time ->
            // These files only have a Uri, so this operation is kinda faster
            return ("DocumentFile Performance (Uri Only) = ${time}s")
        }
    }

    private fun calculateDocumentFilePerformanceWithName(context: Context, uri: Uri): String {
        measureTimeSeconds {
            val listOfUsableElements = arrayListOf<String>()
            DocumentFile.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
                listOfUsableElements.add(documentFile.name.orEmpty())
            }
        }.also { time -> return ("DocumentFile Performance (With Names) = ${time}s") }
    }

    private fun calculateDocumentFileCompatPerformanceWithName(context: Context, uri: Uri): String {
        measureTimeSeconds {
            val listOfUsableElements = arrayListOf<String>()
            DocumentFileCompat.fromTreeUri(context, uri)?.listFiles()?.forEach { documentFile ->
                listOfUsableElements.add(documentFile.name)
            }
        }.also { time ->
            return ("DFC Performance (With Names) = ${time}s")
        }
    }

    private fun calculateCountVsListSize(context: Context, uri: Uri): String {
        val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            ?: return "Failed to access directory"

        val dfListSizeTime = measureTimeSeconds {
            DocumentFile.fromTreeUri(context, uri)?.listFiles()?.size
        }

        val dfcCountTime = measureTimeSeconds {
            documentFile.count()
        }

        val dfcListSizeTime = measureTimeSeconds {
            documentFile.listFiles().size
        }

        return "DocumentFile.listFiles().size = ${dfListSizeTime}s\n" +
                "DFC count() Performance = ${dfcCountTime}s\n" +
                "DFC listFiles().size Performance = ${dfcListSizeTime}s"
    }
}