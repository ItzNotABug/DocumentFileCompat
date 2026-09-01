package com.lazygeniouz.dfc.observer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazygeniouz.dfc.file.DocumentFileCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end observer tests against [TestDocumentsProvider]: events are driven purely by
 * provider notifications (no polling). The backing directory is mutated with the [File] api,
 * mimicking external changes, followed by a `notifyChange` like a real provider would fire.
 */
@RunWith(AndroidJUnit4::class)
class DirectoryObserveTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val backingDir = File(context.filesDir, TestDocumentsProvider.ROOT_ID)
    private val treeUri: Uri = DocumentsContract.buildTreeDocumentUri(
        TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID
    )

    private val events = LinkedBlockingQueue<Pair<Int, String>>()
    private var observer: DirectoryObserver? = null
    private var openedCursorBaseline = 0
    private var closedCursorBaseline = 0

    private lateinit var directory: DocumentFileCompat

    @Before
    fun setUp() {
        TestDocumentsProvider.resetTestControls()
        openedCursorBaseline = TestDocumentsProvider.openChildCursors.get()
        closedCursorBaseline = TestDocumentsProvider.closedChildCursors.get()
        backingDir.deleteRecursively()
        backingDir.mkdirs()
        directory = DocumentFileCompat.fromTreeUri(context, treeUri)
            ?: fail("Could not build the observed directory").let { throw AssertionError() }
    }

    @After
    fun tearDown() {
        TestDocumentsProvider.resetTestControls()
        observer?.stopWatching()
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
        backingDir.deleteRecursively()
    }

    // region helpers

    private fun observe(
        @DirectoryEventMask mask: Int = DirectoryObserver.ALL_EVENTS,
    ): DirectoryObserver {
        return directory.observe(mask) { event, document ->
            events.add(event to document.name)
        }.also { observer = it }
    }

    private fun createFile(name: String, content: String = "content"): File =
        File(backingDir, name).apply { writeText(content) }

    private fun notifyChildren() {
        context.contentResolver.notifyChange(
            TestDocumentsProvider.childrenUriOf(TestDocumentsProvider.ROOT_ID), null
        )
    }

    private fun awaitEvent(timeoutMs: Long = 5000): Pair<Int, String>? =
        events.poll(timeoutMs, TimeUnit.MILLISECONDS)

    private fun awaitEventFor(name: String, event: Int? = null, timeoutMs: Long = 5000): Pair<Int, String> {
        val received = events.poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("Timed out waiting for an event for $name")
        if (received.second != name || (event != null && received.first != event)) {
            throw AssertionError("Expected ${event ?: "any"}/$name, received $received")
        }
        return received
    }

    private fun startAndAwaitWatching(started: DirectoryObserver) {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        started.startWatching(
            onError = {
                failure.set(it)
                completed.countDown()
            },
            onReady = completed::countDown,
        )
        assertTrue("Observer did not finish starting", completed.await(5, TimeUnit.SECONDS))
        failure.get()?.let {
            throw AssertionError("Observer failed to start", it)
        }
    }

    private fun observerThreadAlive(): Boolean =
        Thread.getAllStackTraces().keys.any { it.name == "dfc-observer" && it.isAlive }

    private fun awaitObserverThreadGone(timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (observerThreadAlive()) {
            if (System.currentTimeMillis() >= deadline) fail("Observer worker thread was not released")
            Thread.sleep(25)
        }
    }

    private fun awaitAllChildCursorsClosed(timeoutMs: Long = 5000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            val opened = TestDocumentsProvider.openChildCursors.get() - openedCursorBaseline
            val closed = TestDocumentsProvider.closedChildCursors.get() - closedCursorBaseline
            if (opened == closed) return
            if (System.currentTimeMillis() >= deadline) {
                fail("Leaked child cursors: opened $opened, closed $closed")
            }
            Thread.sleep(25)
        }
    }

    // endregion

    @Test
    fun existingChildren_emitNoEventsOnStart() {
        createFile("a.txt")
        createFile("b.txt")

        startAndAwaitWatching(observe())
        assertNull("Existing children emitted events", events.poll(300, TimeUnit.MILLISECONDS))

        createFile("c.txt")
        notifyChildren()

        // The very first event after activation must be the new file, not a baseline replay.
        assertEquals(DirectoryObserver.CREATE to "c.txt", awaitEvent())
    }

    @Test
    fun externalDelete_emitsDelete() {
        val victim = createFile("victim.txt")

        startAndAwaitWatching(observe())

        victim.delete()
        notifyChildren()

        awaitEventFor("victim.txt", DirectoryObserver.DELETE)
    }

    @Test
    fun externalModify_emitsModify() {
        val target = createFile("mod.txt", "12345")

        startAndAwaitWatching(observe())

        target.writeText("123456789") // size change: independent of mtime resolution
        notifyChildren()

        awaitEventFor("mod.txt", DirectoryObserver.MODIFY)
    }

    @Test
    fun pathIdRename_emitsDeleteThenCreate() {
        // Path based ids (like AOSP's local provider): a rename changes the id,
        // which must surface as DELETE + CREATE, never a guessed move.
        val target = createFile("old-name.txt")

        startAndAwaitWatching(observe())

        target.renameTo(File(backingDir, "new-name.txt"))
        notifyChildren()

        assertEquals(DirectoryObserver.DELETE to "old-name.txt", awaitEvent())
        assertEquals(DirectoryObserver.CREATE to "new-name.txt", awaitEvent())
    }

    @Test
    fun callbackRename_cannotMutateTheInternalSnapshotDocument() {
        val callbackEvents = LinkedBlockingQueue<Triple<Int, String, String>>()
        val renameFinished = CountDownLatch(1)
        val renameSucceeded = AtomicReference<Boolean?>()
        val callbackObserver = directory.observe { event, document ->
            callbackEvents.add(
                Triple(event, document.name, DocumentsContract.getDocumentId(document.uri))
            )
            if (event == DirectoryObserver.CREATE && document.name == "callback.txt") {
                renameSucceeded.set(document.renameTo("renamed.txt"))
                renameFinished.countDown()
            }
        }.also { observer = it }
        startAndAwaitWatching(callbackObserver)

        createFile("callback.txt")
        notifyChildren()

        assertTrue("Callback rename did not finish", renameFinished.await(5, TimeUnit.SECONDS))
        assertEquals(true, renameSucceeded.get())
        val first = callbackEvents.poll(5, TimeUnit.SECONDS)
            ?: throw AssertionError("Missing original CREATE event")
        val second = callbackEvents.poll(5, TimeUnit.SECONDS)
            ?: throw AssertionError("Missing DELETE event after rename")
        val third = callbackEvents.poll(5, TimeUnit.SECONDS)
            ?: throw AssertionError("Missing CREATE event after rename")

        assertEquals(
            Triple(DirectoryObserver.CREATE, "callback.txt", "${TestDocumentsProvider.ROOT_ID}/callback.txt"),
            first,
        )
        assertEquals(
            Triple(DirectoryObserver.DELETE, "callback.txt", "${TestDocumentsProvider.ROOT_ID}/callback.txt"),
            second,
        )
        assertEquals(
            Triple(DirectoryObserver.CREATE, "renamed.txt", "${TestDocumentsProvider.ROOT_ID}/renamed.txt"),
            third,
        )
    }

    @Test
    fun maskFiltering_suppressesUnrequestedEvents() {
        val victim = createFile("victim.txt")
        val deleteOnly = observe(DirectoryObserver.DELETE)
        startAndAwaitWatching(deleteOnly)

        createFile("noise.txt") // CREATE: must be filtered out
        victim.delete()
        notifyChildren()

        assertEquals(DirectoryObserver.DELETE to "victim.txt", awaitEvent())
    }

    @Test
    fun notificationBurst_coalesces_withoutDuplicateEvents() {
        startAndAwaitWatching(observe())

        createFile("b1.txt")
        createFile("b2.txt")
        createFile("b3.txt")
        repeat(5) { notifyChildren() }

        val received = mutableSetOf<String>()
        repeat(3) {
            val (event, name) = awaitEvent() ?: fail("Missing burst event").let { throw AssertionError() }
            assertEquals(DirectoryObserver.CREATE, event)
            received.add(name)
        }
        assertEquals(setOf("b1.txt", "b2.txt", "b3.txt"), received)

        // Sentinel proves the 5 notifications produced no duplicate events.
        createFile("sentinel.txt")
        notifyChildren()
        assertEquals(DirectoryObserver.CREATE to "sentinel.txt", awaitEvent())
    }

    @Test
    fun stopWatching_stopsEvents() {
        startAndAwaitWatching(observe())

        observer?.stopWatching()

        createFile("late.txt")
        notifyChildren()

        assertNull(events.poll(1500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun stopWatching_waitsForAdmittedCallback_andBlocksLaterCallbacks() {
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val callbacks = AtomicInteger(0)
        val blocking = directory.observe { _, _ ->
            callbacks.incrementAndGet()
            callbackEntered.countDown()
            releaseCallback.await(5, TimeUnit.SECONDS)
        }.also { observer = it }
        startAndAwaitWatching(blocking)

        createFile("blocking.txt")
        notifyChildren()
        assertTrue("Listener was not admitted", callbackEntered.await(5, TimeUnit.SECONDS))

        val stopper = Thread {
            blocking.stopWatching()
            stopReturned.countDown()
        }.apply { start() }
        assertFalse("Stop returned while a callback was running", stopReturned.await(200, TimeUnit.MILLISECONDS))

        releaseCallback.countDown()
        assertTrue("Stop did not return after the callback", stopReturned.await(5, TimeUnit.SECONDS))
        stopper.join(5000)

        createFile("after-stop.txt")
        notifyChildren()
        Thread.sleep(300)
        assertEquals(1, callbacks.get())
    }

    @Test
    fun restartAfterStop_deliversEventsAgain() {
        startAndAwaitWatching(observe())
        observer?.stopWatching()

        startAndAwaitWatching(observer!!)

        createFile("again.txt")
        notifyChildren()

        awaitEventFor("again.txt", DirectoryObserver.CREATE)
    }

    @Test
    fun startAndStop_areIdempotent() {
        val doubled = observe()
        val ready = CountDownLatch(1)
        val duplicateCallback = AtomicBoolean(false)
        doubled.startWatching(onReady = ready::countDown)
        doubled.startWatching(onReady = { duplicateCallback.set(true) })
        assertTrue("Observer did not become ready", ready.await(5, TimeUnit.SECONDS))
        assertTrue("Duplicate start callback ran", !duplicateCallback.get())

        doubled.stopWatching()
        doubled.stopWatching() // no-op

        createFile("late.txt")
        notifyChildren()
        assertNull(events.poll(1500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun stopFromInsideListener_doesNotDeadlock_orEmitFurther() {
        val callbacks = AtomicInteger(0)
        val firstEvent = CountDownLatch(1)
        lateinit var selfStopping: DirectoryObserver
        selfStopping = directory.observe { _, _ ->
            callbacks.incrementAndGet()
            selfStopping.stopWatching() // must not deadlock
            firstEvent.countDown()
        }
        observer = selfStopping
        startAndAwaitWatching(selfStopping)
        createFile("trigger.txt")
        notifyChildren()
        assertTrue("Listener was never invoked", firstEvent.await(5, TimeUnit.SECONDS))

        val countAtStop = callbacks.get()
        createFile("after-stop.txt")
        notifyChildren()
        Thread.sleep(1500)

        assertEquals(countAtStop, callbacks.get())
    }

    @Test
    fun observe_onNonDirectory_throws() {
        val plain = createFile("plain.txt")

        // A file child of the observed tree, exactly as listFiles() hands it out.
        val single = directory.listFiles().first { it.name == "plain.txt" }
        assertThrows(UnsupportedOperationException::class.java) {
            single.observe { _, _ -> }
        }

        val raw = DocumentFileCompat.fromFile(context, plain)
        assertThrows(UnsupportedOperationException::class.java) {
            raw.observe { _, _ -> }
        }
    }

    @Test
    @SuppressLint("WrongConstant")
    fun observe_withInvalidEventMask_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            directory.observe(0) { _, _ -> }
        }
        assertThrows(IllegalArgumentException::class.java) {
            directory.observe(0x20 /* OPEN */) { _, _ -> }
        }
        assertThrows(IllegalArgumentException::class.java) {
            directory.observe(DirectoryObserver.CREATE or 0x20 /* OPEN */) { _, _ -> }
        }
    }
}