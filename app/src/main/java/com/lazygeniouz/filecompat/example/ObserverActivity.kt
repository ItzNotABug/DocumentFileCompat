package com.lazygeniouz.filecompat.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.lazygeniouz.dfc.file.DocumentFileCompat
import com.lazygeniouz.dfc.observer.DirectoryObserver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

/** Interactive, real-provider sample for [DocumentFileCompat.observe]. */
class ObserverActivity : AppCompatActivity(R.layout.activity_observer) {

    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private lateinit var selectedDirectory: TextView
    private lateinit var eventLog: TextView
    private lateinit var selectButton: MaterialButton
    private lateinit var toggleButton: MaterialButton
    private lateinit var demoButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var progress: ProgressBar

    private val logLines = ArrayDeque<String>()
    private val timestampFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    private var directoryUri: Uri? = null
    private var directory: DocumentFileCompat? = null
    private var observer: DirectoryObserver? = null
    private var openingJob: Job? = null
    private var demoJob: Job? = null
    private var openingDirectory = false
    private var shouldObserve = false
    private var observerReady = false
    private var demoRunning = false
    private var openingGeneration = 0L
    private var observerGeneration = 0L

    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            persistPermission(uri)
            openDirectory(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        scroll = findViewById(R.id.observerScroll)
        status = findViewById(R.id.observerStatus)
        selectedDirectory = findViewById(R.id.observerDirectory)
        eventLog = findViewById(R.id.observerEventLog)
        selectButton = findViewById(R.id.selectObserverDirectory)
        toggleButton = findViewById(R.id.toggleObserver)
        demoButton = findViewById(R.id.runObserverDemo)
        clearButton = findViewById(R.id.clearObserverLog)
        progress = findViewById(R.id.observerProgress)

        applyEdgeToEdgeInsets()

        renderLog()
        updateControls()

        selectButton.setOnClickListener {
            directoryPicker.launch(directoryUri)
        }
        toggleButton.setOnClickListener {
            if (observer == null) startObserver() else stopObserver()
        }
        demoButton.setOnClickListener { runDemo() }
        clearButton.setOnClickListener {
            logLines.clear()
            renderLog()
        }

        savedInstanceState?.getString(STATE_DIRECTORY_URI)?.let { savedUri ->
            openDirectory(
                savedUri.toUri(),
                savedInstanceState.getBoolean(STATE_SHOULD_OBSERVE)
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DIRECTORY_URI, directoryUri?.toString())
        outState.putBoolean(STATE_SHOULD_OBSERVE, shouldObserve)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        openingGeneration++
        openingJob?.cancel()
        demoJob?.cancel()
        closeObserver()
        super.onDestroy()
    }

    private fun persistPermission(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            appendSystemLine(getString(R.string.observer_permission_not_persisted))
        }
    }

    private fun openDirectory(uri: Uri, startWatching: Boolean = true) {
        val generation = ++openingGeneration
        openingJob?.cancel()
        demoJob?.cancel()
        closeObserver()

        directoryUri = uri
        directory = null
        openingDirectory = true
        shouldObserve = startWatching
        updateControls()

        openingJob = lifecycleScope.launch {
            try {
                val opened = withContext(Dispatchers.IO) {
                    DocumentFileCompat.fromTreeUri(this@ObserverActivity, uri)
                }
                if (opened == null || !opened.isDirectory()) {
                    throw IOException("The selected document is not an accessible directory")
                }
                if (generation != openingGeneration) return@launch

                directory = opened
                if (startWatching) startObserver()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                if (generation != openingGeneration) return@launch
                shouldObserve = false
                appendSystemLine(
                    getString(
                        R.string.observer_open_failed,
                        exception.message ?: exception.javaClass.simpleName
                    )
                )
            } finally {
                if (generation == openingGeneration) {
                    openingDirectory = false
                    openingJob = null
                    if (!isDestroyed) updateControls()
                }
            }
        }
    }

    private fun startObserver() {
        val observedDirectory = directory ?: return
        closeObserver()
        shouldObserve = true
        val generation = observerGeneration

        try {
            val startedObserver = observedDirectory.observe { event, document ->
                runOnUiThread {
                    if (
                        !isDestroyed &&
                        generation == observerGeneration
                    ) {
                        appendEvent(event, document)
                    }
                }
            }
            observer = startedObserver
            startedObserver.startWatching(
                onError = { exception ->
                    runOnUiThread {
                        if (
                            !isDestroyed &&
                            generation == observerGeneration &&
                            observer === startedObserver
                        ) {
                            observer = null
                            observerReady = false
                            shouldObserve = false
                            observerGeneration++
                            appendSystemLine(
                                getString(
                                    R.string.observer_failed,
                                    exception.message ?: exception.javaClass.simpleName
                                )
                            )
                            updateControls()
                        }
                    }
                },
                onReady = {
                    runOnUiThread {
                        if (
                            !isDestroyed &&
                            generation == observerGeneration &&
                            observer === startedObserver
                        ) {
                            observerReady = true
                            appendSystemLine(getString(R.string.observer_started))
                            updateControls()
                        }
                    }
                },
            )
        } catch (exception: Exception) {
            closeObserver()
            shouldObserve = false
            appendSystemLine(
                getString(
                    R.string.observer_failed,
                    exception.message ?: exception.javaClass.simpleName
                )
            )
        }
        updateControls()
    }

    private fun stopObserver() {
        if (observer == null) return
        shouldObserve = false
        closeObserver()
        appendSystemLine(getString(R.string.observer_stopped))
        updateControls()
    }

    private fun closeObserver() {
        observerGeneration++
        observerReady = false
        val stopped = observer
        observer = null
        stopped?.close()
    }

    private fun runDemo() {
        val targetDirectory = directory ?: return
        if (demoRunning) return

        demoJob = lifecycleScope.launch {
            var demoDocument: DocumentFileCompat? = null
            var demoDeleted = false
            demoRunning = true
            updateControls()
            appendSystemLine(getString(R.string.observer_demo_started))

            try {
                val suffix = System.currentTimeMillis()
                val originalName = "dfc_observer_$suffix.txt"
                val renamedName = "dfc_observer_${suffix}_renamed.txt"

                val document = withContext(Dispatchers.IO) {
                    targetDirectory.createFile("text/plain", originalName).also {
                        demoDocument = it
                    }
                } ?: throw IOException("The provider did not create the demo file")
                appendSystemLine(getString(R.string.observer_demo_created, originalName))

                delay(DEMO_STEP_DELAY_MS.milliseconds)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(document.uri, "wt")?.use { stream ->
                        stream.write("Modified by the FileCompat observer demo\n".toByteArray())
                    } ?: throw IOException("The provider did not open the demo file")
                }
                appendSystemLine(getString(R.string.observer_demo_modified, originalName))

                delay(DEMO_STEP_DELAY_MS.milliseconds)
                val renamed = withContext(Dispatchers.IO) {
                    document.renameTo(renamedName)
                }
                appendSystemLine(
                    if (renamed) getString(R.string.observer_demo_renamed, renamedName)
                    else getString(R.string.observer_demo_rename_unsupported)
                )

                delay(DEMO_STEP_DELAY_MS.milliseconds)
                val deleted = withContext(Dispatchers.IO) { document.delete() }
                if (!deleted) throw IOException("The provider did not delete the demo file")
                demoDeleted = true
                appendSystemLine(
                    getString(
                        R.string.observer_demo_deleted,
                        if (renamed) renamedName else originalName
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                appendSystemLine(
                    getString(
                        R.string.observer_demo_failed,
                        exception.message ?: exception.javaClass.simpleName
                    )
                )
            } finally {
                val leftover = demoDocument
                if (leftover != null && !demoDeleted) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { leftover.delete() }
                    }
                }
                demoRunning = false
                demoJob = null
                if (!isDestroyed) updateControls()
            }
        }
    }

    private fun appendEvent(event: Int, document: DocumentFileCompat) {
        appendLine(
            getString(
                R.string.observer_event_entry,
                timestamp(),
                eventName(event),
                document.name.ifEmpty { document.uri.lastPathSegment.orEmpty() }
            )
        )
    }

    private fun appendSystemLine(message: String) {
        appendLine(getString(R.string.observer_system_entry, timestamp(), message))
    }

    private fun appendLine(line: String) {
        while (logLines.size >= MAX_LOG_LINES) logLines.removeFirst()
        logLines.addLast(line)
        renderLog()
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun renderLog() {
        eventLog.text = if (logLines.isEmpty()) {
            getString(R.string.observer_event_log_empty)
        } else {
            logLines.joinToString(separator = "\n")
        }
    }

    private fun applyEdgeToEdgeInsets() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )
            view.updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
            windowInsets
        }
        ViewCompat.requestApplyInsets(scroll)
    }

    private fun updateControls() {
        status.text = when {
            openingDirectory -> getString(R.string.observer_status_opening)
            observer != null && !observerReady -> getString(R.string.observer_status_starting)
            observerReady -> getString(R.string.observer_status_observing)
            directory != null -> getString(R.string.observer_status_stopped)
            else -> getString(R.string.observer_status_idle)
        }
        selectedDirectory.text = directoryUri?.toString()
            ?: getString(R.string.observer_no_directory)

        selectButton.isEnabled = !openingDirectory && !demoRunning
        toggleButton.isEnabled = directory != null && !openingDirectory && !demoRunning
        toggleButton.text = getString(
            if (observer == null) R.string.observer_start else R.string.observer_stop
        )
        demoButton.isEnabled = observerReady && !openingDirectory && !demoRunning
        clearButton.isEnabled = logLines.isNotEmpty()
        progress.isVisible = openingDirectory || (observer != null && !observerReady) || demoRunning
    }

    private fun timestamp(): String = timestampFormatter.format(Date())

    private fun eventName(event: Int): String = when (event) {
        DirectoryObserver.CREATE -> "CREATE"
        DirectoryObserver.DELETE -> "DELETE"
        DirectoryObserver.MODIFY -> "MODIFY"
        DirectoryObserver.MOVED_FROM -> "MOVED_FROM"
        DirectoryObserver.MOVED_TO -> "MOVED_TO"
        else -> "0x${event.toString(16)}"
    }

    private companion object {
        const val STATE_DIRECTORY_URI = "observer_directory_uri"
        const val STATE_SHOULD_OBSERVE = "observer_should_observe"
        const val MAX_LOG_LINES = 200
        const val DEMO_STEP_DELAY_MS = 1_200L
    }
}