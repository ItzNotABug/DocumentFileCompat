package com.lazygeniouz.dfc.query

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract.Document
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.Query
import com.lazygeniouz.dfc.testing.SafTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class SafQueryAndroidTest {

    private val saf = SafTestHelper()
    private val context = saf.context

    private lateinit var fixtureName: String
    private lateinit var fixturePath: String
    private lateinit var fixtureDocumentId: String
    private lateinit var fixtureTreeUri: Uri
    private lateinit var root: DocumentFileCompat

    @Before
    fun setUp() {
        assumeTrue("ExternalStorageProvider is not available.", saf.hasExternalStorageProvider())

        saf.shell("rm -rf /sdcard/Download/${FIXTURE_PREFIX}*")

        fixtureName = "$FIXTURE_PREFIX${System.currentTimeMillis()}"
        fixturePath = "/sdcard/Download/$fixtureName"
        fixtureDocumentId = "${SafTestHelper.PRIMARY_ROOT_ID}:Download/$fixtureName"

        saf.shell("mkdir -p $fixturePath")

        fixtureTreeUri = saf.grantTree(fixtureDocumentId)
        root = requireFixtureRoot()
        seedFixture()
    }

    @After
    fun tearDown() {
        if (::fixtureTreeUri.isInitialized) {
            saf.releaseTree(fixtureTreeUri)
        }
        if (::fixturePath.isInitialized) {
            saf.shell("rm -rf $fixturePath")
        }
    }

    @Test
    fun listFilesReadsSafFixture() {
        val children = root.listFiles()
        val names = children.map { file -> file.name }.toSet()

        assertEquals(
            "Expected all seeded immediate children, actual=$names",
            EXPECTED_IMMEDIATE_CHILDREN.toSet(),
            names,
        )
        assertFalse(names.contains("nested-report.txt"))

        val report = children.first { file -> file.name == "report-alpha.txt" }
        assertTrue(report.isFile())
        assertFalse(report.isDirectory())
        assertEquals("text/plain", report.getType())
        assertTrue(report.length > 0L)

        val photos = children.first { file -> file.name == "photos" }
        assertTrue(photos.isDirectory())
        assertFalse(photos.isFile())
        assertNull(photos.getType())
    }

    @Test
    fun selectKeepsMetadataUsable() {
        val children = root.listFiles(
            Query.select(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
        )
        val report = children.first { file -> file.name == "report-alpha.txt" }
        val photos = children.first { file -> file.name == "photos" }

        assertEquals("text/plain", report.getType())
        assertTrue(report.isFile())
        assertTrue(report.length > 0L)
        assertTrue(report.canWrite())

        assertNull(photos.getType())
        assertTrue(photos.isDirectory())
        assertTrue(photos.canWrite())
    }

    @Test
    fun queryClausesStayProviderBackedAndImmediate() {
        val children = root.listFiles(
            Query.filesOnly(),
            Query.nameContains("report"),
            Query.orderByAsc(Document.COLUMN_DISPLAY_NAME),
        )
        val names = children.map { file -> file.name }
        val honoredArgs = saf.honoredQueryArgs(fixtureTreeUri)

        if (honoredArgs.contains(ContentResolver.QUERY_ARG_SQL_SELECTION)) {
            assertEquals(
                "Expected provider-backed filter, actual=$names",
                setOf("report-alpha.txt", "report-beta.txt"),
                names.toSet(),
            )
            assertTrue(children.all { file -> file.isFile() })
        } else {
            assertEquals(
                "Expected immediate children when provider ignores query args, actual=$names",
                EXPECTED_IMMEDIATE_CHILDREN.toSet(),
                names.toSet(),
            )
            assertFalse(names.contains("nested-report.txt"))
        }

        if (honoredArgs.contains(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)) {
            assertEquals(
                "Expected provider-backed sort, actual=$names",
                names.sorted(),
                names,
            )
        }
    }

    private fun requireFixtureRoot(): DocumentFileCompat {
        val root = DocumentFileCompat.fromTreeUri(context, fixtureTreeUri)
        assertNotNull(root)
        return root!!
    }

    private fun seedFixture() {
        val photos = requireCreated(
            root.createDirectory("photos"),
            "Failed to create photos directory via SAF",
        )
        requireCreated(
            root.createDirectory("empty"),
            "Failed to create empty directory via SAF",
        )
        requireCreated(
            root.createDirectory("report-folder"),
            "Failed to create report-folder directory via SAF",
        )

        writeFile(
            root.createFile("text/plain", "report-alpha.txt"),
            "alpha-report".toByteArray(),
        )
        writeFile(
            root.createFile("text/plain", "report-beta.txt"),
            "beta-report".toByteArray(),
        )
        writeFile(
            root.createFile("text/plain", "notes.txt"),
            "plain-notes".toByteArray(),
        )
        writeFile(
            root.createFile("image/png", "cover.png"),
            byteArrayOf(-119, 80, 78, 71),
        )
        writeFile(
            root.createFile("application/octet-stream", "big.bin"),
            ByteArray(4096) { 1 },
        )
        writeFile(
            photos.createFile("text/plain", "nested-report.txt"),
            "nested-report".toByteArray(),
        )
    }

    private fun writeFile(file: DocumentFileCompat?, bytes: ByteArray) {
        val document = requireCreated(file, "Failed to create file via SAF")
        context.contentResolver.openOutputStream(document.uri)?.use { stream ->
            stream.write(bytes)
        } ?: throw AssertionError("Failed to open output stream for ${document.name}")
    }

    private fun requireCreated(
        file: DocumentFileCompat?,
        message: String,
    ): DocumentFileCompat {
        return file ?: throw AssertionError(message)
    }

    private companion object {
        val EXPECTED_IMMEDIATE_CHILDREN = listOf(
            "report-alpha.txt",
            "report-beta.txt",
            "notes.txt",
            "cover.png",
            "big.bin",
            "photos",
            "empty",
            "report-folder",
        )
        const val FIXTURE_PREFIX = "DFCQueryAndroidTest_"
    }
}
