package com.lazygeniouz.filecompat.example.performance

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract.Document
import androidx.documentfile.provider.DocumentFile
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.Query
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

        // Test 4: count() vs listFiles().size
        results += testCountVsListSize(context, uri) + "\n\n"

        results += "=".repeat(48).plus("\n\n")

        // Test 5: Projection overload parity vs Query.select
        results += testSelectQueryParity(context, uri) + "\n\n"

        // Test 6: Provider query correctness for filesOnly
        results += testFilesOnlyQuery(context, uri) + "\n\n"

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

    private fun testCountVsListSize(context: Context, uri: Uri): String {
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

        return "count() vs listFiles().size:\n\n" +
                "DocumentFile.listFiles().size = ${dfListSizeTime}s\n" +
                "DFC.count() = ${dfcCountTime}s\n" +
                "DFC.listFiles().size = ${dfcListSizeTime}s"
    }

    private fun testSelectQueryParity(context: Context, uri: Uri): String {
        val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            ?: return "Query.select parity:\nFailed to access directory"

        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
        )

        var projectionCount = 0
        val projectionTime = measureTimeSeconds {
            projectionCount = documentFile.listFiles(projection).size
        }

        var queryCount = 0
        val queryTime = measureTimeSeconds {
            queryCount = documentFile.listFiles(
                Query.select(
                    Document.COLUMN_DOCUMENT_ID,
                    Document.COLUMN_DISPLAY_NAME,
                    Document.COLUMN_SIZE,
                )
            ).size
        }

        return "Projection overload vs Query.select:\n" +
                "Projection count = $projectionCount (${projectionTime}s)\n" +
                "Query.select count = $queryCount (${queryTime}s)\n" +
                "Match = ${projectionCount == queryCount}"
    }

    private fun testFilesOnlyQuery(context: Context, uri: Uri): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return "Query.filesOnly correctness:\nSkipped on API < 26 (query filters are ignored)."
        }

        val documentFile = DocumentFileCompat.fromTreeUri(context, uri)
            ?: return "Query.filesOnly correctness:\nFailed to access directory"

        var providerResult = emptyList<com.lazygeniouz.dfc.file.DocumentFileCompat>()
        val providerTime = measureTimeSeconds {
            providerResult = documentFile.listFiles(Query.filesOnly())
        }

        var clientSideResult = emptyList<com.lazygeniouz.dfc.file.DocumentFileCompat>()
        val clientSideTime = measureTimeSeconds {
            clientSideResult = documentFile.listFiles().filter { !it.isDirectory() }
        }

        val providerUris = providerResult.map { it.uri }.toSet()
        val clientSideUris = clientSideResult.map { it.uri }.toSet()

        return "Query.filesOnly correctness:\n" +
                "Provider query count = ${providerResult.size} (${providerTime}s)\n" +
                "Client-side filter count = ${clientSideResult.size} (${clientSideTime}s)\n" +
                "Match = ${providerUris == clientSideUris}"
    }
}
