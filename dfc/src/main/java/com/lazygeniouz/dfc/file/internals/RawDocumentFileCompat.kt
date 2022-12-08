package com.lazygeniouz.dfc.file.internals

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.lazygeniouz.dfc.extension.findFile
import com.lazygeniouz.dfc.extension.logError
import com.lazygeniouz.dfc.file.DocumentFileCompat
import java.io.File

/**
 * RawDocumentFileCompat serves as an alternative to the **RawDocumentFile**
 * which handles Documents using the native [File] api.
 */
internal class RawDocumentFileCompat constructor(context: Context, var file: File) :
    DocumentFileCompat(context, file) {

    /**
     * Returns a [Uri] via [Uri.fromFile] but
     * if you want to use the **FileProvider** api to get a Uri,
     * you should convert this Uri to a File, make your checks if necessary &
     * then use **FileProvider.getUriForFile**.
     */
    override val uri: Uri
        get() = Uri.fromFile(file)

    // Get file extension.
    override val extension: String
        get() = file.extension

    // Get file size.
    override val length: Long
        get() = file.length()

    /**
     * Delete the file & return the result.
     *
     * Note it will delete the everything **recursively** if this is a folder
     * because this uses Kotlin extension [File.deleteRecursively].
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

    // Create file & return a DocumentFileCompat (RawFileCompat),
    // can be null if there was an Exception.
    override fun createFile(mimeType: String, name: String): DocumentFileCompat? {
        var displayName = name
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
        if (extension != null) {
            displayName += ".$extension"
        }

        val target = File(file, displayName)
        return try {
            if (target.createNewFile()) fromFile(context, target)
            else null
        } catch (exception: Exception) {
            logError("Exception while creating a document: ${exception.message}")
            null
        }
    }

    // Create a Directory & return a DocumentFileCompat (RawFileCompat),
    // can be null if there was an Exception.
    override fun createDirectory(name: String): DocumentFileCompat? {
        val target = File(file, name)
        return if (target.isDirectory || target.mkdir()) fromFile(context, target)
        else null
    }

    // Performance of File api is pretty great as compared to others.
    override fun listFiles(): List<DocumentFileCompat> {
        val filesList = arrayListOf<DocumentFileCompat>()
        file.listFiles()?.onEach { child -> filesList.add(fromFile(context, child)) }
        return filesList
    }

    // Return a file if exists, else **null**
    override fun findFile(name: String): DocumentFileCompat? {
        return listFiles().findFile(name)
    }

    // Copies current file to the provided destination uri.
    override fun copyTo(destination: Uri) {
        val outPutStream = context.contentResolver.openOutputStream(destination)!!
        file.inputStream().use { inputStream -> inputStream.copyTo(outPutStream) }
    }

    // Copies current source file at current uri's location.
    override fun copyFrom(source: Uri) {
        val inputStream = context.contentResolver.openInputStream(source)!!
        inputStream.use { stream -> stream.copyTo(file.outputStream()) }
    }

    internal companion object {
        internal fun getMimeType(file: File): String {
            if (file.isDirectory) return ""

            val mimeTypeMap = MimeTypeMap.getSingleton()
            val mimeType = mimeTypeMap.getMimeTypeFromExtension(file.extension)
            if (mimeType != null) return mimeType

            return "application/octet-stream"
        }
    }
}