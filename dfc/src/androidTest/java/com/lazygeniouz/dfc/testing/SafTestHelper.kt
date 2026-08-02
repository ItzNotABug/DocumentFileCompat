package com.lazygeniouz.dfc.testing

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.lazygeniouz.dfc.picker.SafTreePickerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

internal class SafTestHelper {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.context
    private val device: UiDevice = UiDevice.getInstance(instrumentation)

    fun hasExternalStorageProvider(): Boolean {
        return context.packageManager.resolveContentProvider(EXTERNAL_STORAGE_AUTHORITY, 0) != null
    }

    fun grantTree(documentId: String): Uri {
        SafTreePickerActivity.reset()
        val initialUri = DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, documentId)
        context.startActivity(
            Intent(context, SafTreePickerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(SafTreePickerActivity.EXTRA_INITIAL_URI, initialUri)
            }
        )

        clickUseThisFolder()
        clickAllowIfShown()

        val treeUri = SafTreePickerActivity.awaitResult()
        assertNotNull(treeUri)
        val grantedTreeUri = treeUri!!
        assertEquals(EXTERNAL_STORAGE_AUTHORITY, grantedTreeUri.authority)
        assertEquals(documentId, DocumentsContract.getTreeDocumentId(grantedTreeUri))
        return grantedTreeUri
    }

    fun releaseTree(treeUri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    fun shell(command: String): String {
        return device.executeShellCommand(command)
    }

    fun honoredQueryArgs(treeUri: Uri): Set<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "(${Document.COLUMN_MIME_TYPE} != ?) AND " +
                    "(${Document.COLUMN_DISPLAY_NAME} LIKE ? ESCAPE '\\')",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(Document.MIME_TYPE_DIR, "%report%"),
            )
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${Document.COLUMN_DISPLAY_NAME} ASC",
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, 100)
        }

        return runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(Document.COLUMN_DISPLAY_NAME),
                queryArgs,
                null,
            )?.use { cursor ->
                cursor.extras.getStringArray(ContentResolver.EXTRA_HONORED_ARGS)
                    ?.toSet()
                    .orEmpty()
            }.orEmpty()
        }.getOrDefault(emptySet())
    }

    private fun clickUseThisFolder() {
        waitForDocumentsUi()
        val selectButton = device.wait(
            Until.findObject(By.res("android", "button1")),
            PICKER_TIMEOUT_MS,
        ) ?: device.wait(Until.findObject(By.textContains("USE THIS FOLDER")), SHORT_TIMEOUT_MS)
            ?: findDocumentsUiObject("action_menu_select", SHORT_TIMEOUT_MS)
            ?: throw AssertionError("DocumentsUI select-folder button was not found")
        selectButton.click()
    }

    private fun waitForDocumentsUi() {
        val opened = device.wait(
            Until.hasObject(By.pkg(GOOGLE_DOCUMENTS_UI_PACKAGE)),
            PICKER_TIMEOUT_MS,
        ) || device.wait(
            Until.hasObject(By.pkg(AOSP_DOCUMENTS_UI_PACKAGE)),
            SHORT_TIMEOUT_MS,
        )
        assertTrue("DocumentsUI did not open", opened)
    }

    private fun clickAllowIfShown() {
        val allowButton = device.wait(Until.findObject(By.textContains("ALLOW")), SHORT_TIMEOUT_MS)
            ?: device.wait(Until.findObject(By.textContains("Allow")), SHORT_TIMEOUT_MS)
        runCatching { allowButton?.click() }
    }

    private fun findDocumentsUiObject(resourceName: String, timeout: Long): UiObject2? {
        return device.wait(
            Until.findObject(By.res(GOOGLE_DOCUMENTS_UI_PACKAGE, resourceName)),
            timeout,
        ) ?: device.wait(
            Until.findObject(By.res(AOSP_DOCUMENTS_UI_PACKAGE, resourceName)),
            SHORT_TIMEOUT_MS,
        )
    }

    companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val PRIMARY_ROOT_ID = "primary"

        private const val GOOGLE_DOCUMENTS_UI_PACKAGE = "com.google.android.documentsui"
        private const val AOSP_DOCUMENTS_UI_PACKAGE = "com.android.documentsui"
        private const val PICKER_TIMEOUT_MS = 10_000L
        private const val SHORT_TIMEOUT_MS = 2_000L
    }
}
