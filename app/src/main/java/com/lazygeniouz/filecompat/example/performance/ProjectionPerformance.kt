package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract.Document
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.filecompat.example.performance.Performance.measureTimeSeconds

object ProjectionPerformance {

    fun calculateProjectionPerformance(context: Context, uri: Uri): String {
        var results = ""

        results += "Testing Custom Projections Performance\n\n"
        results += "=".repeat(48).plus("\n\n")

        // Test 1: Full projection (default)
        results += testFullProjection(context, uri) + "\n\n"

        // Test 2: Minimal projection (ID + Name only)
        results += testMinimalProjection(context, uri) + "\n\n"

        // Test 3: ID + Name + Size
        results += testPartialProjection(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")

        return results
    }

    private fun testFullProjection(context: Context, uri: Uri): String {
        var fileCount = 0
        measureTimeSeconds {
            val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            // Uses default fullProjection
            val files = documentFile?.listFiles()
            fileCount = files?.size ?: 0
        }.also { time ->
            return "Full Projection:\n" +
                    "Files: $fileCount\n" +
                    "Time: ${time}s"
        }
    }

    private fun testMinimalProjection(context: Context, uri: Uri): String {
        var fileCount = 0
        measureTimeSeconds {
            val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            // Only fetch ID and Name
            val minimalProjection = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME
            )
            val files = documentFile?.listFiles(minimalProjection)
            fileCount = files?.size ?: 0

            // Verify we can access the names
            files?.forEach { file ->
                val name = file.name // Should work
            }
        }.also { time ->
            return "Minimal Projection (ID + Name):\n" +
                    "Files: $fileCount\n" +
                    "Time: ${time}s"
        }
    }

    private fun testPartialProjection(context: Context, uri: Uri): String {
        var fileCount = 0
        var totalSize = 0L
        measureTimeSeconds {
            val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            // Fetch ID, Name, and Size
            val partialProjection = arrayOf(
                Document.COLUMN_DOCUMENT_ID,
                Document.COLUMN_DISPLAY_NAME,
                Document.COLUMN_SIZE
            )
            val files = documentFile?.listFiles(partialProjection)
            fileCount = files?.size ?: 0

            // Calculate total size
            files?.forEach { file ->
                totalSize += file.length
            }
        }.also { time ->
            val sizeMb = Performance.getSizeInMb(totalSize)
            return "Partial Projection (ID + Name + Size):\n" +
                    "Files: $fileCount\n" +
                    "Total Size: $sizeMb\n" +
                    "Time: ${time}s"
        }
    }
}
