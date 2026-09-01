package com.lazygeniouz.dfc.observer.internal.snapshot

import android.content.Context
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.file.internals.SingleDocumentFileCompat
import com.lazygeniouz.dfc.file.internals.TreeDocumentFileCompat
import com.lazygeniouz.dfc.observer.DirectoryObserver
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Local initial-snapshot allocation and steady-state diff comparison. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 23)
class SnapshotBenchmarkTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val parent = TreeDocumentFileCompat(
        context,
        Uri.parse("content://com.test.provider/tree/root/document/root"),
        "root",
        0,
        0,
        DocumentsContract.Document.MIME_TYPE_DIR,
        0,
    )
    private var retained: Any? = null

    @Test
    fun compareLegacyAndCompactSnapshotWork() {
        val ids = Array(CHILD_COUNT) { "root/file-$it" }
        val names = Array(CHILD_COUNT) { "file-$it.txt" }

        repeat(2) {
            retained = buildLegacySnapshot(ids, names)
            retained = buildCompactSnapshot(ids, names)
        }

        val legacyInitial = medianBuildSample { buildLegacySnapshot(ids, names) }
        val compactInitial = medianBuildSample { buildCompactSnapshot(ids, names) }

        val legacyOld = buildLegacySnapshot(ids, names)
        val legacyNew = LinkedHashMap(legacyOld)
        legacyNew[ids.last()] = legacyChild(ids.last(), names.last(), length = 2L)

        val compactOld = buildCompactSnapshot(ids, names)
        val compactNew = LinkedHashMap(compactOld)
        compactNew[ids.last()] = compactChild(ids.last(), names.last(), length = 2L)

        repeat(DIFF_WARMUP_ITERATIONS) {
            retained = legacyDiff(legacyOld, legacyNew)
            retained = SnapshotDiffer.diff(
                compactOld,
                compactNew,
                scannedCreations = emptyList(),
            )
        }

        val legacyDiffNanos = medianNanos {
            legacyDiff(legacyOld, legacyNew)
        }
        val compactDiffNanos = medianNanos {
            SnapshotDiffer.diff(
                compactOld,
                compactNew,
                scannedCreations = emptyList(),
            )
        }

        assertEquals(CHILD_COUNT, legacyOld.size)
        assertEquals(CHILD_COUNT, compactOld.size)
        println(
            "SNAPSHOT_BENCHMARK children=$CHILD_COUNT " +
                    "legacy_initial_allocated_bytes=${legacyInitial.first} " +
                    "compact_initial_allocated_bytes=${compactInitial.first} " +
                    "legacy_initial_ms=${legacyInitial.second / NANOS_PER_MILLISECOND.toDouble()} " +
                    "compact_initial_ms=${compactInitial.second / NANOS_PER_MILLISECOND.toDouble()} " +
                    "legacy_diff_ms=${legacyDiffNanos / NANOS_PER_MILLISECOND.toDouble()} " +
                    "compact_diff_ms=${compactDiffNanos / NANOS_PER_MILLISECOND.toDouble()}"
        )
    }

    private fun medianBuildSample(block: () -> Any): Pair<Long, Long> {
        val byteSamples = LongArray(BUILD_SAMPLE_COUNT)
        val timeSamples = LongArray(BUILD_SAMPLE_COUNT)
        repeat(BUILD_SAMPLE_COUNT) { index ->
            retained = null
            Runtime.getRuntime().gc()
            SystemClock.sleep(GC_SETTLE_MILLIS)

            val bytesBefore = allocatedBytes()
            val started = SystemClock.elapsedRealtimeNanos()
            retained = block()
            timeSamples[index] = SystemClock.elapsedRealtimeNanos() - started
            byteSamples[index] = allocatedBytes() - bytesBefore
        }
        byteSamples.sort()
        timeSamples.sort()
        return byteSamples[BUILD_SAMPLE_COUNT / 2] to timeSamples[BUILD_SAMPLE_COUNT / 2]
    }

    private fun medianNanos(block: () -> Any): Long {
        val samples = LongArray(DIFF_SAMPLE_COUNT)
        repeat(DIFF_SAMPLE_COUNT) { index ->
            val started = SystemClock.elapsedRealtimeNanos()
            retained = block()
            samples[index] = SystemClock.elapsedRealtimeNanos() - started
        }
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun allocatedBytes(): Long {
        return Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull() ?: 0L
    }

    private fun buildLegacySnapshot(
        ids: Array<String>,
        names: Array<String>,
    ): LinkedHashMap<String, DocumentFileCompat> {
        val snapshot = LinkedHashMap<String, DocumentFileCompat>(mapCapacity(ids.size))
        for (index in ids.indices) {
            snapshot[ids[index]] = legacyChild(ids[index], names[index])
        }
        return snapshot
    }

    private fun buildCompactSnapshot(
        ids: Array<String>,
        names: Array<String>,
    ): LinkedHashMap<String, ChildState> {
        val snapshot = LinkedHashMap<String, ChildState>(mapCapacity(ids.size))
        for (index in ids.indices) {
            snapshot[ids[index]] = compactChild(ids[index], names[index])
        }
        return snapshot
    }

    private fun legacyChild(
        id: String,
        name: String,
        length: Long = 1L,
    ): DocumentFileCompat {
        val child = SingleDocumentFileCompat(
            context,
            DocumentsContract.buildDocumentUriUsingTree(parent.uri, id),
            name,
            length,
            1L,
            MIME_TYPE,
            0,
        )
        child.parentFile = parent
        return child
    }

    private fun compactChild(
        id: String,
        name: String,
        length: Long = 1L,
    ): ChildState = ChildState(id, name, length, 1L, MIME_TYPE, 0)

    private fun legacyDiff(
        old: Map<String, DocumentFileCompat>,
        new: Map<String, DocumentFileCompat>,
    ): List<Pair<Int, DocumentFileCompat>> {
        val events = ArrayList<Pair<Int, DocumentFileCompat>>()
        for ((documentId, oldChild) in old) {
            if (documentId !in new) events.add(DirectoryObserver.DELETE to oldChild)
        }
        for ((documentId, oldChild) in old) {
            val newChild = new[documentId] ?: continue
            if (oldChild === newChild) continue
            if (oldChild.name != newChild.name) {
                events.add(DirectoryObserver.MOVED_FROM to oldChild)
                events.add(DirectoryObserver.MOVED_TO to newChild)
            } else if (
                oldChild.length != newChild.length ||
                oldChild.lastModified != newChild.lastModified ||
                oldChild.documentMimeType != newChild.documentMimeType
            ) {
                events.add(DirectoryObserver.MODIFY to newChild)
            }
        }
        for ((documentId, newChild) in new) {
            if (documentId !in old) events.add(DirectoryObserver.CREATE to newChild)
        }
        return events
    }

    private fun mapCapacity(expectedSize: Int): Int = when {
        expectedSize < 3 -> expectedSize + 1
        expectedSize < 1 shl 30 -> expectedSize + expectedSize / 3 + 1
        else -> Int.MAX_VALUE
    }

    private companion object {
        const val CHILD_COUNT = 10_000
        const val BUILD_SAMPLE_COUNT = 5
        const val DIFF_SAMPLE_COUNT = 21
        const val DIFF_WARMUP_ITERATIONS = 20
        const val GC_SETTLE_MILLIS = 50L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MIME_TYPE = "text/plain"
    }
}