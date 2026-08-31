package com.lazygeniouz.dfc.observer

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lazygeniouz.dfc.file.DocumentFileCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Failure, coalescing, cancellation, and resource-ownership regression tests. */
@RunWith(AndroidJUnit4::class)
class ObserverResilienceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val backingDir = File(context.filesDir, TestDocumentsProvider.ROOT_ID)
    private val treeUri: Uri = DocumentsContract.buildTreeDocumentUri(
        TestDocumentsProvider.AUTHORITY, TestDocumentsProvider.ROOT_ID
    )

    private val events = LinkedBlockingQueue<Pair<Int, String>>()
    private val errors = LinkedBlockingQueue<Throwable>()
    private var observer: DocumentFileCompat.Observer? = null
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

    private fun observe(): DocumentFileCompat.Observer {
        return directory.observe { event, document ->
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

    private fun requireEvent(timeoutMs: Long = 5000): Pair<Int, String> =
        events.poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: fail("Timed out waiting for an event").let { throw AssertionError() }

    private fun startAndAwaitWatching(started: DocumentFileCompat.Observer) {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        started.startWatching(
            onError = {
                errors.add(it)
                failure.set(it)
                completed.countDown()
            },
            onReady = completed::countDown,
        )
        assertTrue("Observer did not finish starting", completed.await(5, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("Observer failed to start", it) }
    }

    private fun startAndAwaitFailure(started: DocumentFileCompat.Observer): Throwable {
        val completed = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val becameReady = AtomicReference(false)
        started.startWatching(
            onError = {
                failure.set(it)
                completed.countDown()
            },
            onReady = {
                becameReady.set(true)
                completed.countDown()
            },
        )
        assertTrue("Observer did not report startup completion", completed.await(5, TimeUnit.SECONDS))
        assertFalse("Observer became ready despite terminal failure", becameReady.get())
        return failure.get() ?: throw AssertionError("Observer supplied no failure")
    }

    private fun observerThreadAlive(): Boolean =
        Thread.getAllStackTraces().keys.any { it.name == "dfc-observer" && it.isAlive }

    private fun awaitObserverThreadGone(timeoutMs: Long = 5000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (observerThreadAlive()) {
            if (SystemClock.elapsedRealtime() >= deadline) {
                fail("Observer worker thread was not released")
            }
            Thread.sleep(25)
        }
    }

    private fun awaitQueryCountAbove(baseline: Int, timeoutMs: Long = 5000) {
        awaitCounterAbove(TestDocumentsProvider.childQueryCount, baseline, timeoutMs)
    }

    private fun awaitCounterAbove(counter: AtomicInteger, baseline: Int, timeoutMs: Long = 5000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (counter.get() <= baseline) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Expected counter did not advance")
            Thread.sleep(10)
        }
    }

    private fun awaitQueryDelta(baseline: Int, expected: Int, timeoutMs: Long = 5000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (TestDocumentsProvider.childQueryCount.get() - baseline < expected) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Expected $expected refresh queries")
            Thread.sleep(10)
        }
    }

    private fun awaitAllChildCursorsClosed(timeoutMs: Long = 5000) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val opened = TestDocumentsProvider.openChildCursors.get() - openedCursorBaseline
            val closed = TestDocumentsProvider.closedChildCursors.get() - closedCursorBaseline
            if (opened == closed) return
            if (SystemClock.elapsedRealtime() >= deadline) {
                fail("Leaked child cursors: opened $opened, closed $closed")
            }
            Thread.sleep(25)
        }
    }

    @Test
    fun permissionRevocation_isTerminal_releasesSessionAndAllowsRestart() {
        startAndAwaitWatching(observe())

        TestDocumentsProvider.revokePermissions = true
        createFile("denied.txt")
        notifyChildren()

        assertTrue(errors.poll(5, TimeUnit.SECONDS) is SecurityException)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()

        val countAfterTermination = TestDocumentsProvider.childQueryCount.get()
        notifyChildren()
        Thread.sleep(250)
        assertEquals(countAfterTermination, TestDocumentsProvider.childQueryCount.get())

        TestDocumentsProvider.revokePermissions = false
        startAndAwaitWatching(observer!!)
        createFile("recovered.txt")
        notifyChildren()
        assertEquals(DocumentFileCompat.CREATE to "recovered.txt", requireEvent())
    }

    @Test
    fun startupQueryFailure_reportsErrorAndAllowsRestart() {
        TestDocumentsProvider.failChildQueries = true
        val failed = observe()

        assertTrue(startAndAwaitFailure(failed) is IllegalStateException)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()

        TestDocumentsProvider.failChildQueries = false
        startAndAwaitWatching(failed)
    }

    @Test
    fun registrationRevocation_reportsErrorAndReleasesSession() {
        TestDocumentsProvider.failObserverRegistration = true

        assertTrue(startAndAwaitFailure(observe()) is SecurityException)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
    }

    @Test
    fun transientQueryFailure_keepsWatchAliveAndReconcilesMissedChanges() {
        startAndAwaitWatching(observe())

        val failedBaseline = TestDocumentsProvider.failedChildQueries.get()
        TestDocumentsProvider.failChildQueries = true
        createFile("missed.txt")
        notifyChildren()
        awaitCounterAbove(TestDocumentsProvider.failedChildQueries, failedBaseline)
        assertNull(events.poll(250, TimeUnit.MILLISECONDS))

        TestDocumentsProvider.failChildQueries = false
        createFile("caught.txt")
        notifyChildren()

        assertEquals(
            setOf(
                DocumentFileCompat.CREATE to "missed.txt",
                DocumentFileCompat.CREATE to "caught.txt",
            ),
            setOf(requireEvent(), requireEvent())
        )
    }

    @Test
    fun notificationBurst_coalescesToExactlyTwoQueries() {
        startAndAwaitWatching(observe())

        val gate = CountDownLatch(1)
        TestDocumentsProvider.childQueryGate = gate
        val baseline = TestDocumentsProvider.childQueryCount.get()

        createFile("b1.txt")
        notifyChildren()
        awaitQueryCountAbove(baseline)

        createFile("b2.txt")
        createFile("b3.txt")
        repeat(9) { notifyChildren() }

        gate.countDown()
        TestDocumentsProvider.childQueryGate = null

        assertEquals(
            setOf(
                DocumentFileCompat.CREATE to "b1.txt",
                DocumentFileCompat.CREATE to "b2.txt",
                DocumentFileCompat.CREATE to "b3.txt",
            ),
            setOf(requireEvent(), requireEvent(), requireEvent())
        )
        awaitQueryDelta(baseline, 2)

        observer?.stopWatching()
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
        assertEquals(2, TestDocumentsProvider.childQueryCount.get() - baseline)
    }

    @Test
    fun mutationAfterRefreshSnapshot_triggersOneFollowUpQuery() {
        startAndAwaitWatching(observe())

        val snapshotCaptured = CountDownLatch(1)
        val returnGate = CountDownLatch(1)
        TestDocumentsProvider.childSnapshotCaptured = snapshotCaptured
        TestDocumentsProvider.childQueryReturnGate = returnGate
        val baseline = TestDocumentsProvider.childQueryCount.get()

        createFile("m1.txt")
        notifyChildren()
        assertTrue("Refresh snapshot was not captured", snapshotCaptured.await(5, TimeUnit.SECONDS))

        createFile("m2.txt")
        notifyChildren()

        returnGate.countDown()
        TestDocumentsProvider.childQueryReturnGate = null
        TestDocumentsProvider.childSnapshotCaptured = null

        assertEquals(
            setOf(
                DocumentFileCompat.CREATE to "m1.txt",
                DocumentFileCompat.CREATE to "m2.txt",
            ),
            setOf(requireEvent(), requireEvent())
        )
        awaitQueryDelta(baseline, 2)

        observer?.stopWatching()
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
        assertEquals(2, TestDocumentsProvider.childQueryCount.get() - baseline)
    }

    @Test
    fun permissionRevocationDuringCursorMaterialization_isTerminal() {
        startAndAwaitWatching(observe())

        TestDocumentsProvider.revokeDuringMaterialization = true
        createFile("denied-materialization.txt")
        notifyChildren()

        assertTrue(errors.poll(5, TimeUnit.SECONDS) is SecurityException)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()

        TestDocumentsProvider.revokeDuringMaterialization = false
        startAndAwaitWatching(observer!!)
        createFile("recovered-materialization.txt")
        notifyChildren()
        assertEquals(DocumentFileCompat.CREATE to "recovered-materialization.txt", requireEvent())
    }

    @Test
    fun stopDuringBlockedQuery_returnsImmediately_thenReleasesAfterQueryReturns() {
        startAndAwaitWatching(observe())

        val gate = CountDownLatch(1)
        TestDocumentsProvider.childQueryGate = gate
        val countBefore = TestDocumentsProvider.childQueryCount.get()

        createFile("blocked.txt")
        notifyChildren()
        awaitQueryCountAbove(countBefore)

        val stopStarted = SystemClock.elapsedRealtime()
        observer?.stopWatching()
        assertTrue(SystemClock.elapsedRealtime() - stopStarted < 500)

        gate.countDown()
        TestDocumentsProvider.childQueryGate = null

        assertNull(events.poll(500, TimeUnit.MILLISECONDS))
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
    }

    @Test
    fun workerThread_isReleasedAfterStop() {
        startAndAwaitWatching(observe())
        assertTrue(observerThreadAlive())

        observer?.stopWatching()
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
    }
}
