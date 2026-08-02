package com.lazygeniouz.dfc.query

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.Query
import com.lazygeniouz.dfc.testing.SafTestHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class SafQueryLoadAndroidTest {

    private val arguments = InstrumentationRegistry.getArguments()
    private val saf = SafTestHelper()
    private val context = saf.context

    private lateinit var treeUri: Uri
    private lateinit var root: DocumentFileCompat

    @Before
    fun setUp() {
        assumeTrue(
            "Set instrumentation arg $ARG_ENABLE_LOAD_TEST=true to run the SAF load test.",
            arguments.getString(ARG_ENABLE_LOAD_TEST).toBoolean(),
        )
        assumeTrue("ExternalStorageProvider is not available.", saf.hasExternalStorageProvider())

        resetFixtureDirectory()

        treeUri = saf.grantTree(FIXTURE_DOCUMENT_ID)
        root = requireRoot()

        seedFixture()
        val createdCount = fileCount(FIXTURE_PATH)
        assertEquals(
            "Expected seeded fixture file count",
            TOTAL_FILE_COUNT,
            createdCount,
        )
    }

    @After
    fun tearDown() {
        if (::treeUri.isInitialized) {
            saf.releaseTree(treeUri)
        }
        if (arguments.getString(ARG_ENABLE_LOAD_TEST).toBoolean()) {
            deleteFixtureDirectory()
        }
    }

    @Test
    fun listAndQueryLargeSafFixture() {
        timed("count") {
            val count = root.count()
            assertEquals("Expected root child count", ROOT_CHILD_COUNT, count)
        }

        val children = timed("listFiles(select + sort)") {
            root.listFiles(
                Query.select(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE),
                Query.orderByAsc(Document.COLUMN_DISPLAY_NAME),
            )
        }

        val names = children.map { file -> file.name }
        val nameSet = names.toSet()
        assertEquals("Expected root child count", ROOT_CHILD_COUNT, children.size)
        assertTrue("Expected docs directory in root", nameSet.contains("docs"))
        assertTrue("Expected media directory in root", nameSet.contains("media"))
        assertTrue("Expected report files in root", names.any { name -> name.startsWith("report_") })
        assertTrue("Expected image files in root", names.any { name -> name.startsWith("image_") })
        assertFalse(
            "Root query should stay immediate, actual nested name present",
            names.any { name -> name.startsWith("nested_") },
        )

        val filtered = timed("query clauses") {
            root.listFiles(
                Query.filesOnly(),
                Query.nameContains("report"),
                Query.limit(100),
                Query.orderByAsc(Document.COLUMN_DISPLAY_NAME),
            )
        }
        val filteredNames = filtered.map { file -> file.name }
        val honoredArgs = saf.honoredQueryArgs(treeUri)
        assertTrue("Expected provider-backed query to return rows", filteredNames.isNotEmpty())
        assertFalse(
            "Query should stay immediate, actual nested name present",
            filteredNames.any { name -> name.startsWith("nested_") },
        )

        if (honoredArgs.contains(ContentResolver.QUERY_ARG_SQL_SELECTION)) {
            assertTrue(
                "Expected all filtered rows to be files, actual=$filteredNames",
                filtered.all { file -> file.isFile() },
            )
            assertTrue(
                "Expected all filtered names to contain report, actual=$filteredNames",
                filteredNames.all { name -> name.contains("report") },
            )
        }
        if (honoredArgs.contains(ContentResolver.QUERY_ARG_LIMIT)) {
            assertTrue("Expected limit(100), actual=${filtered.size}", filtered.size <= 100)
        }
        if (honoredArgs.contains(ContentResolver.QUERY_ARG_SQL_SORT_ORDER)) {
            assertEquals(
                "Expected provider-backed ascending sort, actual=$filteredNames",
                filteredNames.sorted(),
                filteredNames,
            )
        }

        Log.i(TAG, "SAF load test completed")
    }

    private fun seedFixture() {
        val workerCount = seedWorkerCount()
        Log.i(TAG, "Seeding $TOTAL_FILE_COUNT SAF files into $FIXTURE_PATH with $workerCount workers")
        val directories = createDirectoryMap()
        val createdCount = AtomicInteger()
        val executor = Executors.newFixedThreadPool(workerCount)

        val tasks = List(workerCount) { worker ->
            Callable {
                var index = worker
                while (index < TOTAL_FILE_COUNT) {
                    createLoadFile(index, directories)

                    val created = createdCount.incrementAndGet()
                    if (created % 500 == 0) {
                        Log.i(TAG, "Seeded $created/$TOTAL_FILE_COUNT files")
                    }

                    index += workerCount
                }
            }
        }

        try {
            timed("seed fixture") {
                executor.invokeAll(tasks).forEach { future -> future.get() }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun createLoadFile(
        index: Int,
        directories: Map<String, DocumentFileCompat>,
    ) {
        val spec = fileSpec(index)
        val parent = directories.getValue(spec.directory)
        val file = parent.createFile(spec.mimeType, spec.name)
            ?: throw AssertionError("Failed to create ${spec.directory}/${spec.name}")

        context.contentResolver.openOutputStream(file.uri)?.use { stream ->
            stream.write(bytesFor(index, spec.size))
        } ?: throw AssertionError("Failed to open output stream for ${spec.name}")
    }

    private fun seedWorkerCount(): Int {
        val requested = arguments.getString(ARG_SEED_WORKERS)
            ?.toIntOrNull()
            ?.takeIf { count -> count > 0 }
            ?: DEFAULT_SEED_WORKERS

        return requested.coerceAtMost(MAX_SEED_WORKERS)
    }

    private fun createDirectoryMap(): Map<String, DocumentFileCompat> {
        val docs = root.requireDirectory("docs")
        val media = root.requireDirectory("media")
        val logs = root.requireDirectory("logs")
        val data = root.requireDirectory("data")
        val misc = root.requireDirectory("misc")

        return mapOf(
            ROOT_DIR to root,
            "docs" to docs,
            "media" to media,
            "logs" to logs,
            "data" to data,
            "misc" to misc,
            "docs/archive" to docs.requireDirectory("archive"),
            "media/camera" to media.requireDirectory("camera"),
            "logs/old" to logs.requireDirectory("old"),
            "data/exports" to data.requireDirectory("exports"),
            "misc/names" to misc.requireDirectory("names"),
        )
    }

    private fun DocumentFileCompat.requireDirectory(name: String): DocumentFileCompat {
        return createDirectory(name) ?: throw AssertionError("Failed to create $name directory")
    }

    private fun fileSpec(index: Int): LoadFileSpec {
        val directory = when {
            index < ROOT_FILE_COUNT -> ROOT_DIR
            index < ROOT_FILE_COUNT + CATEGORY_FILE_COUNT -> CATEGORY_DIRS[
                (index - ROOT_FILE_COUNT) % CATEGORY_DIRS.size
            ]
            else -> NESTED_DIRS[
                (index - ROOT_FILE_COUNT - CATEGORY_FILE_COUNT) % NESTED_DIRS.size
            ]
        }
        val type = FILE_TYPES[index % FILE_TYPES.size]
        val stem = NAME_STEMS[index % NAME_STEMS.size]
        val name = "${stem}_${index.toString().padStart(5, '0')}.${type.extension}"

        return LoadFileSpec(directory, name, type.mimeType, sizeFor(index))
    }

    private fun sizeFor(index: Int): Int {
        return when (index % 100) {
            in 0..9 -> index % 129
            in 10..54 -> 1024 + (index % 4) * 1024
            in 55..84 -> 8192 + (index % 4) * 8192
            in 85..94 -> 65536 + (index % 4) * 32768
            in 95..98 -> 262_144
            else -> 1_048_576
        }
    }

    private fun bytesFor(seed: Int, size: Int): ByteArray {
        return ByteArray(size) { offset -> ((seed + offset) and 0xff).toByte() }
    }

    private fun resetFixtureDirectory() {
        saf.shell("rm -rf $FIXTURE_PATH")
        saf.shell("mkdir -p $FIXTURE_PATH")
        val created = waitForDirectory(FIXTURE_PATH)
        assertTrue("Failed to create $FIXTURE_PATH", created)
    }

    private fun deleteFixtureDirectory() {
        saf.shell("rm -rf $FIXTURE_PATH")
    }

    private fun directoryExists(path: String): Boolean {
        return saf.shell("ls -d $path").lineSequence().any { line ->
            line.trim() == path
        }
    }

    private fun waitForDirectory(path: String): Boolean {
        repeat(10) {
            if (directoryExists(path)) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun fileCount(path: String): Int {
        return saf.shell("find $path -type f")
            .lineSequence()
            .count { line -> line.trim().startsWith(path) }
    }

    private fun requireRoot(): DocumentFileCompat {
        val file = DocumentFileCompat.fromTreeUri(context, treeUri)
        assertNotNull(file)
        return file!!
    }

    private inline fun <T> timed(label: String, block: () -> T): T {
        val start = SystemClock.elapsedRealtime()
        return block().also {
            Log.i(TAG, "$label took ${SystemClock.elapsedRealtime() - start}ms")
        }
    }

    private data class FileType(
        val extension: String,
        val mimeType: String,
    )

    private data class LoadFileSpec(
        val directory: String,
        val name: String,
        val mimeType: String,
        val size: Int,
    )

    private companion object {
        const val TAG = "DFCLoadTest"
        const val ARG_ENABLE_LOAD_TEST = "dfcSafLoad"
        const val ARG_SEED_WORKERS = "dfcSafLoadWorkers"
        const val FIXTURE_NAME = "DFCQueryPerfFixture"
        const val FIXTURE_PATH = "/sdcard/Download/$FIXTURE_NAME"
        const val FIXTURE_DOCUMENT_ID = "${SafTestHelper.PRIMARY_ROOT_ID}:Download/$FIXTURE_NAME"
        const val ROOT_DIR = ""
        const val TOTAL_FILE_COUNT = 5_000
        const val ROOT_FILE_COUNT = 2_000
        const val CATEGORY_FILE_COUNT = 2_000
        const val ROOT_CHILD_COUNT = ROOT_FILE_COUNT + 5
        const val DEFAULT_SEED_WORKERS = 4
        const val MAX_SEED_WORKERS = 8

        val CATEGORY_DIRS = listOf("docs", "media", "logs", "data", "misc")
        val NESTED_DIRS = listOf(
            "docs/archive",
            "media/camera",
            "logs/old",
            "data/exports",
            "misc/names",
        )
        val NAME_STEMS = listOf(
            "report",
            "invoice",
            "notes",
            "image",
            "clip",
            "audio",
            "export",
            "payload",
            "app-log",
            "crash-trace",
            "spaced name",
            "UPPERCASE_NAME",
        )
        val FILE_TYPES = listOf(
            FileType("txt", "text/plain"),
            FileType("log", "text/plain"),
            FileType("md", "text/markdown"),
            FileType("pdf", "application/pdf"),
            FileType("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            FileType("json", "application/json"),
            FileType("csv", "text/csv"),
            FileType("zip", "application/zip"),
            FileType("png", "image/png"),
            FileType("jpg", "image/jpeg"),
            FileType("mp3", "audio/mpeg"),
            FileType("mp4", "video/mp4"),
            FileType("bin", "application/octet-stream"),
        )
    }
}
