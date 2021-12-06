package com.lazygeniouz.filecompat.file.internals

import android.webkit.MimeTypeMap
import com.lazygeniouz.filecompat.extension.toFile
import com.lazygeniouz.filecompat.file.DocumentFileCompat
import java.io.File

/**
 * RawFileCompat serves as an alternative to the **RawDocumentFile**
 * which handles Documents using the native [File] api.
 *
 * Other params same as [DocumentFileCompat] except there's no Context here.
 */
internal class RawDocumentFileCompat constructor(
    documentUri: String, documentName: String = "", documentSize: Long = 0,
    lastModifiedTime: Long = -1L, documentMimeType: String = "", documentFlags: Int = -1,
) : DocumentFileCompat(
    null, documentUri, documentName, documentSize,
    lastModifiedTime, documentFlags, documentMimeType
) {

    var file: File = documentUri.toFile()

    // Get file extension.
    override val extension: String
        get() = file.extension

    // Get file size.
    override val length: Long
        get() = file.length()

    /**
     * Delete the file & return the result.
     *
     * Note it will delete the everything if this is a folder as this uses `deleteRecursively`.
     *
     */
    override fun delete(): Boolean {
        return file.deleteRecursively()
    }

    // Check if the file exists.
    override fun exists(): Boolean {
        return file.exists()
    }

    // File is not virtual & is an openable.
    override fun isVirtual(): Boolean {
        return false
    }

    // Check if the file can be read
    override fun canRead(): Boolean {
        return file.canRead()
    }

    // Check if the file can be modified.
    override fun canWrite(): Boolean {
        return file.canWrite()
    }

    // Check if the file is a File & not a Directory
    override fun isFile(): Boolean {
        return file.isFile
    }

    // Check if the file is a Directory
    override fun isDirectory(): Boolean {
        return file.isDirectory
    }

    // Rename a file & return the result & assign the new file to the current one.
    override fun renameTo(name: String): Boolean {
        val target = File(file.parentFile, name)
        val renameResult = file.renameTo(target)
        return if (renameResult) {
            file = target
            true
        } else false
    }

    // Create file & return a FileCompat (RawFileCompat),
    // can be null if there was an Exception.
    override fun createFile(mimeType: String, name: String): DocumentFileCompat? {
        var displayName = name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) {
            displayName += ".$extension"
        }

        val target = File(file, displayName)
        return try {
            if (target.createNewFile()) fromFile(target)
            else null

        } catch (exception: Exception) {
            println("FileCompat: Exception while creating a document = ${exception.message}")
            null
        }
    }

    // Create a Directory & return a FileCompat (RawFileCompat),
    // can be null if there was an Exception.
    override fun createDirectory(name: String): DocumentFileCompat? {
        val target = File(file, name)
        return if (target.isDirectory || target.mkdir()) fromFile(target)
        else null
    }

    // Performance of Files api is pretty great as compared to others.
    override fun listFiles(): List<DocumentFileCompat> {
        val filesList = arrayListOf<DocumentFileCompat>()
        file.listFiles()?.forEach { child -> filesList.add(fromFile(child)) }
        return filesList
    }

    companion object {
        fun getMimeType(file: File): String {
            if (file.isDirectory) return ""

            val mimeType = MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(file.extension)

            if (mimeType != null) return mimeType

            return "application/octet-stream"
        }
    }
}