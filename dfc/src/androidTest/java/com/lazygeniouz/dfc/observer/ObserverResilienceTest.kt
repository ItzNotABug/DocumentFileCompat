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
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
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

    private fun observe(): DirectoryObserver {
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

    private fun startAndAwaitWatching(started: DirectoryObserver) {
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

    private fun startAndAwaitFailure(started: DirectoryObserver): Throwable {
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
        assertEquals(DirectoryObserver.CREATE to "recovered.txt", requireEvent())
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
    fun startupReconciliationFailure_neverReportsReady() {
        val snapshotCaptured = CountDownLatch(1)
        val returnGate = CountDownLatch(1)
        TestDocumentsProvider.childSnapshotCaptured = snapshotCaptured
        TestDocumentsProvider.childQueryReturnGate = returnGate

        val started = observe()
        val completed = CountDownLatch(1)
        val becameReady = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()
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

        assertTrue("Initial snapshot was not captured", snapshotCaptured.await(5, TimeUnit.SECONDS))
        createFile("blind-window.txt")
        notifyChildren() // No observer is registered yet; this notification is deliberately lost.
        // The next query installs the lightweight cursor; fail the full reconciliation after it.
        TestDocumentsProvider.failChildQueryAt = TestDocumentsProvider.childQueryCount.get() + 2

        returnGate.countDown()
        TestDocumentsProvider.childQueryReturnGate = null
        TestDocumentsProvider.childSnapshotCaptured = null

        assertTrue("Startup did not terminate", completed.await(5, TimeUnit.SECONDS))
        assertFalse("A stale baseline was reported ready", becameReady.get())
        assertTrue(failure.get() is IllegalStateException)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
    }

    @Test
    fun loadingStartup_waitsForACompleteBaseline() {
        createFile("cached.txt")
        createFile("remote.txt")
        TestDocumentsProvider.returnLoadingChildren = true
        TestDocumentsProvider.loadingChildLimit = 1

        val started = observe()
        val ready = CountDownLatch(1)
        val queryBaseline = TestDocumentsProvider.childQueryCount.get()
        started.startWatching(onError = errors::add, onReady = ready::countDown)

        awaitQueryCountAbove(queryBaseline)
        assertFalse("Observer became ready from a partial cursor", ready.await(250, TimeUnit.MILLISECONDS))
        assertTrue(events.isEmpty())

        TestDocumentsProvider.returnLoadingChildren = false
        notifyChildren()

        assertTrue("Observer did not become ready after loading completed", ready.await(5, TimeUnit.SECONDS))
        assertTrue(events.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun loadingCompletionBeforeRegistration_stillBecomesReady() {
        createFile("cached.txt")
        createFile("remote.txt")
        TestDocumentsProvider.returnLoadingChildren = true
        TestDocumentsProvider.loadingChildLimit = 1

        val snapshotCaptured = CountDownLatch(1)
        val returnGate = CountDownLatch(1)
        TestDocumentsProvider.childSnapshotCaptured = snapshotCaptured
        TestDocumentsProvider.childQueryReturnGate = returnGate

        val started = observe()
        val ready = CountDownLatch(1)
        started.startWatching(onError = errors::add, onReady = ready::countDown)

        assertTrue("Partial snapshot was not captured", snapshotCaptured.await(5, TimeUnit.SECONDS))
        TestDocumentsProvider.returnLoadingChildren = false
        notifyChildren()
        TestDocumentsProvider.childSnapshotCaptured = null
        TestDocumentsProvider.childQueryReturnGate = null
        returnGate.countDown()

        assertTrue("Observer remained stuck on the partial cursor", ready.await(5, TimeUnit.SECONDS))
        assertTrue(events.isEmpty())
        assertTrue(errors.isEmpty())
    }

    @Test
    fun loadingRefresh_neverTurnsOmittedRowsIntoDeletions() {
        createFile("a.txt")
        createFile("b.txt")
        startAndAwaitWatching(observe())

        TestDocumentsProvider.returnLoadingChildren = true
        TestDocumentsProvider.loadingChildLimit = 1
        val queryBaseline = TestDocumentsProvider.childQueryCount.get()
        notifyChildren()
        awaitQueryCountAbove(queryBaseline)

        assertNull(events.poll(250, TimeUnit.MILLISECONDS))

        assertTrue(File(backingDir, "b.txt").delete())
        TestDocumentsProvider.returnLoadingChildren = false
        notifyChildren()

        assertEquals(DirectoryObserver.DELETE to "b.txt", requireEvent())
        assertNull(events.poll(250, TimeUnit.MILLISECONDS))
    }

    @Test
    fun readySession_retainsOnlyTheLightweightNotificationCursor() {
        createFile("existing.txt")
        startAndAwaitWatching(observe())

        assertEquals(1, TestDocumentsProvider.activeNotificationCursors.get())
        assertEquals(0, TestDocumentsProvider.activeSnapshotCursors.get())

        observer?.stopWatching()
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
        assertEquals(0, TestDocumentsProvider.activeNotificationCursors.get())
        assertEquals(0, TestDocumentsProvider.activeSnapshotCursors.get())
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
                DirectoryObserver.CREATE to "missed.txt",
                DirectoryObserver.CREATE to "caught.txt",
            ),
            setOf(requireEvent(), requireEvent())
        )
    }

    @Test
    fun oneShotRefreshFailure_retriesWithoutAnotherNotification() {
        startAndAwaitWatching(observe())

        val failedBaseline = TestDocumentsProvider.failedChildQueries.get()
        val queryBaseline = TestDocumentsProvider.childQueryCount.get()
        TestDocumentsProvider.failNextChildQueries.set(1)
        createFile("retried.txt")
        notifyChildren()

        awaitCounterAbove(TestDocumentsProvider.failedChildQueries, failedBaseline)
        assertEquals(DirectoryObserver.CREATE to "retried.txt", requireEvent())
        assertTrue(TestDocumentsProvider.childQueryCount.get() - queryBaseline >= 2)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun nullRefreshCursor_isRecoverableAndReconcilesMissedChanges() {
        startAndAwaitWatching(observe())

        val queryBaseline = TestDocumentsProvider.childQueryCount.get()
        TestDocumentsProvider.returnNullChildQueries = true
        createFile("missed-null-cursor.txt")
        notifyChildren()

        awaitQueryDelta(queryBaseline, 2)
        assertTrue(errors.isEmpty())
        assertTrue(observerThreadAlive())
        assertNull(events.poll(250, TimeUnit.MILLISECONDS))

        TestDocumentsProvider.returnNullChildQueries = false
        notifyChildren()

        assertEquals(
            DirectoryObserver.CREATE to "missed-null-cursor.txt",
            requireEvent(),
        )
    }

    @Test
    fun nullDirectoryCheckCursor_isRecoverable() {
        startAndAwaitWatching(observe())

        val queryBaseline = TestDocumentsProvider.childQueryCount.get()
        TestDocumentsProvider.returnNullDocumentQueries = true
        notifyChildren()

        awaitQueryDelta(queryBaseline, 2)
        assertTrue(errors.isEmpty())
        assertTrue(observerThreadAlive())

        TestDocumentsProvider.returnNullDocumentQueries = false
        createFile("after-null-directory-check.txt")
        notifyChildren()

        assertEquals(
            DirectoryObserver.CREATE to "after-null-directory-check.txt",
            requireEvent(),
        )
    }

    @Test
    fun watchedDirectoryDeletion_isTerminalAndAllowsRestart() {
        createFile("existing.txt")
        startAndAwaitWatching(observe())

        assertTrue(backingDir.deleteRecursively())
        notifyChildren()

        assertTrue(errors.poll(5, TimeUnit.SECONDS) is FileNotFoundException)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()

        assertTrue(backingDir.mkdirs())
        startAndAwaitWatching(observer!!)
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
                DirectoryObserver.CREATE to "b1.txt",
                DirectoryObserver.CREATE to "b2.txt",
                DirectoryObserver.CREATE to "b3.txt",
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
                DirectoryObserver.CREATE to "m1.txt",
                DirectoryObserver.CREATE to "m2.txt",
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
        assertEquals(DirectoryObserver.CREATE to "recovered-materialization.txt", requireEvent())
    }

    @Test
    fun stopDuringTerminalCallback_waitsUntilTheCallbackFinishes() {
        val callbackEntered = CountDownLatch(1)
        val releaseCallback = CountDownLatch(1)
        val stopReturned = CountDownLatch(1)
        val terminal = directory.observe { _, _ -> }.also { observer = it }
        val ready = CountDownLatch(1)
        terminal.startWatching(
            onError = {
                callbackEntered.countDown()
                releaseCallback.await(5, TimeUnit.SECONDS)
            },
            onReady = ready::countDown,
        )
        assertTrue("Observer did not become ready", ready.await(5, TimeUnit.SECONDS))

        TestDocumentsProvider.revokePermissions = true
        notifyChildren()
        assertTrue("Terminal callback was not admitted", callbackEntered.await(5, TimeUnit.SECONDS))

        val stopper = Thread {
            terminal.stopWatching()
            stopReturned.countDown()
        }.apply { start() }
        assertFalse("Stop returned while onError was running", stopReturned.await(200, TimeUnit.MILLISECONDS))

        releaseCallback.countDown()
        assertTrue("Stop did not return after onError", stopReturned.await(5, TimeUnit.SECONDS))
        stopper.join(5000)
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
    }

    @Test
    fun stopBeforeTerminalCallback_suppressesTheCallback() {
        startAndAwaitWatching(observe())

        val closeStarted = CountDownLatch(1)
        val closeGate = CountDownLatch(1)
        TestDocumentsProvider.childCursorCloseStarted = closeStarted
        TestDocumentsProvider.childCursorCloseGate = closeGate

        TestDocumentsProvider.revokePermissions = true
        notifyChildren()
        assertTrue("Terminal cleanup did not begin", closeStarted.await(5, TimeUnit.SECONDS))

        observer?.stopWatching()
        assertTrue("onError ran before stop returned", errors.isEmpty())

        closeGate.countDown()
        TestDocumentsProvider.childCursorCloseGate = null
        TestDocumentsProvider.childCursorCloseStarted = null
        awaitObserverThreadGone()
        awaitAllChildCursorsClosed()
        assertNull("onError ran after stop returned", errors.poll(250, TimeUnit.MILLISECONDS))
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