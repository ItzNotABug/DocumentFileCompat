package com.lazygeniouz.filecompat.example

import android.content.Context
import android.net.Uri
import com.lazygeniouz.dfc.file.DocumentFileCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

object TestFileGenerator {

    private val extensions = listOf("txt", "pdf", "jpg", "png", "mp4", "mp3", "doc", "zip", "apk")
    private val random = Random()

    suspend fun generateTestFiles(context: Context, directoryUri: Uri, fileCount: Int): String {
        return try {
            val directory = DocumentFileCompat.fromTreeUri(context, directoryUri)
                ?: return "Failed to access directory"

            val startTime = System.currentTimeMillis()
            val successCount = AtomicInteger(0)
            val failCount = AtomicInteger(0)

            val semaphore = Semaphore(10)

            coroutineScope {
                val jobs = (1..fileCount).map { index ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val fileName = "test_file_$index"
                            val extension = extensions.random()
                            val sizeInKb = random.nextInt(100) + 1 // 1KB to 100KB

                            val file = directory.createFile(
                                "application/octet-stream",
                                "$fileName.$extension"
                            )
                            if (file != null) {
                                val success = writeRandomData(context, file.uri, sizeInKb)
                                if (success) successCount.incrementAndGet() else failCount.incrementAndGet()
                            } else {
                                failCount.incrementAndGet()
                            }
                        }
                    }
                }

                jobs.awaitAll()
            }

            val elapsedTime = (System.currentTimeMillis() - startTime) / 1000.0

            buildString {
                append("Test Files Generation Complete!\n\n")
                append("Total: $fileCount files\n")
                append("Success: ${successCount.get()}\n")
                append("Failed: ${failCount.get()}\n")
                append("Time: ${elapsedTime}s\n\n")
                append("Files have random sizes between 1KB-100KB\n")
                append("Extensions: ${extensions.joinToString(", ")}")
            }
        } catch (e: Exception) {
            "Error generating files: ${e.message}"
        }
    }

    /**
     * Write random data to a file to achieve desired size.
     */
    private fun writeRandomData(context: Context, fileUri: Uri, sizeInKb: Int): Boolean {
        return try {
            context.contentResolver.openOutputStream(fileUri)?.use { output ->
                val bufferSize = 8192 // 8KB buffer
                val buffer = ByteArray(bufferSize)
                val totalBytes = sizeInKb * 1024
                var written = 0

                while (written < totalBytes) {
                    random.nextBytes(buffer)
                    val toWrite = minOf(bufferSize, totalBytes - written)
                    output.write(buffer, 0, toWrite)
                    written += toWrite
                }
                output.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
