package com.lazygeniouz.dfc.observer

import android.database.ContentObserver
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal SAF provider backed by `filesDir/root`, reimplementing the notification contract of
 * AOSP's `FileSystemProvider`: child cursors carry a notification uri, tests mutate the backing
 * directory & call `notifyChange` on it. Document ids are path based (`root/name`), so renames
 * are id-changing — same as the real local provider.
 */
class TestDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    private val baseDir: File
        get() = File(context!!.filesDir, ROOT_ID)

    private fun fileFor(documentId: String): File {
        if (documentId == ROOT_ID) return baseDir
        return File(baseDir, documentId.removePrefix("$ROOT_ID/"))
    }

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        return MatrixCursor(resolve(projection)).also { cursor ->
            include(cursor, documentId, fileFor(documentId))
        }
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val queryNumber = childQueryCount.incrementAndGet()
        childQueryGate?.await(10, TimeUnit.SECONDS)
        if (revokePermissions) throw SecurityException("Permission revoked (test)")
        if (failChildQueriesWithFileNotFound) {
            failedChildQueries.incrementAndGet()
            throw FileNotFoundException("Directory unavailable (test)")
        }
        if (failChildQueryAt == queryNumber || consumeNextChildQueryFailure() || failChildQueries) {
            if (failChildQueryAt == queryNumber) failChildQueryAt = null
            failedChildQueries.incrementAndGet()
            throw IllegalStateException("Transient failure (test)")
        }

        val cursor = TrackingCursor(resolve(projection))
        openChildCursors.incrementAndGet()
        fileFor(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { child ->
            include(cursor, "$parentDocumentId/${child.name}", child)
        }
        cursor.setNotificationUri(
            context!!.contentResolver, childrenUriOf(parentDocumentId)
        )
        childSnapshotCaptured?.countDown()
        childQueryReturnGate?.await(10, TimeUnit.SECONDS)
        return cursor
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val source = fileFor(documentId)
        val parent = source.parentFile
            ?: throw FileNotFoundException("Document has no parent: $documentId")
        val target = File(parent, displayName)
        if (!source.renameTo(target)) {
            throw FileNotFoundException("Could not rename $documentId")
        }

        val parentDocumentId = documentId.substringBeforeLast('/', ROOT_ID)
        context!!.contentResolver.notifyChange(childrenUriOf(parentDocumentId), null)
        return "$parentDocumentId/${target.name}"
    }

    /** Child cursor with failure injection and close accounting. */
    private class TrackingCursor(columns: Array<out String>) : MatrixCursor(columns) {

        private val closedOnce = java.util.concurrent.atomic.AtomicBoolean(false)

        override fun getCount(): Int {
            if (revokeDuringMaterialization) {
                throw SecurityException("Permission revoked while materializing rows (test)")
            }
            return super.getCount()
        }

        override fun registerContentObserver(observer: ContentObserver) {
            if (failObserverRegistration) {
                throw SecurityException("Permission revoked during observer registration (test)")
            }
            super.registerContentObserver(observer)
        }

        override fun close() {
            childCursorCloseStarted?.countDown()
            childCursorCloseGate?.await(10, TimeUnit.SECONDS)
            if (closedOnce.compareAndSet(false, true)) closedChildCursors.incrementAndGet()
            super.close()
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.startsWith("$parentDocumentId/")

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?,
    ): ParcelFileDescriptor = ParcelFileDescriptor.open(
        fileFor(documentId), ParcelFileDescriptor.parseMode(mode)
    )

    private fun resolve(projection: Array<out String>?): Array<out String> =
        projection ?: arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
        )

    private fun include(cursor: MatrixCursor, documentId: String, file: File) {
        val row = cursor.newRow()
        cursor.columnNames.forEach { column ->
            when (column) {
                Document.COLUMN_DOCUMENT_ID -> row.add(column, documentId)
                Document.COLUMN_DISPLAY_NAME -> row.add(column, file.name)
                Document.COLUMN_SIZE -> row.add(column, file.length())
                Document.COLUMN_LAST_MODIFIED -> row.add(column, file.lastModified())
                Document.COLUMN_FLAGS -> row.add(column, 0)
                Document.COLUMN_MIME_TYPE -> row.add(
                    column,
                    if (file.isDirectory) Document.MIME_TYPE_DIR else "application/octet-stream"
                )
            }
        }
    }

    companion object {
        const val AUTHORITY = "com.lazygeniouz.dfc.test.documents"
        const val ROOT_ID = "root"

        // Test controls for failure / blocking / measurement scenarios.
        @Volatile
        var failChildQueries = false

        @Volatile
        var failChildQueriesWithFileNotFound = false

        @Volatile
        var failChildQueryAt: Int? = null

        @Volatile
        var revokePermissions = false

        @Volatile
        var revokeDuringMaterialization = false

        @Volatile
        var failObserverRegistration = false

        @Volatile
        var childQueryGate: CountDownLatch? = null

        @Volatile
        var childSnapshotCaptured: CountDownLatch? = null

        @Volatile
        var childQueryReturnGate: CountDownLatch? = null

        @Volatile
        var childCursorCloseStarted: CountDownLatch? = null

        @Volatile
        var childCursorCloseGate: CountDownLatch? = null

        val childQueryCount = AtomicInteger(0)
        val failedChildQueries = AtomicInteger(0)
        val failNextChildQueries = AtomicInteger(0)
        val openChildCursors = AtomicInteger(0)
        val closedChildCursors = AtomicInteger(0)

        fun resetTestControls() {
            failChildQueries = false
            failChildQueriesWithFileNotFound = false
            failChildQueryAt = null
            failNextChildQueries.set(0)
            revokePermissions = false
            revokeDuringMaterialization = false
            failObserverRegistration = false
            childQueryGate?.countDown()
            childQueryGate = null
            childQueryReturnGate?.countDown()
            childQueryReturnGate = null
            childCursorCloseGate?.countDown()
            childCursorCloseGate = null
            childCursorCloseStarted = null
            childSnapshotCaptured = null
        }

        private fun consumeNextChildQueryFailure(): Boolean {
            while (true) {
                val remaining = failNextChildQueries.get()
                if (remaining <= 0) return false
                if (failNextChildQueries.compareAndSet(remaining, remaining - 1)) return true
            }
        }

        fun childrenUriOf(parentDocumentId: String) =
            DocumentsContract.buildChildDocumentsUri(AUTHORITY, parentDocumentId)!!
    }
}