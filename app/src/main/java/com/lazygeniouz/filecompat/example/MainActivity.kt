package com.lazygeniouz.filecompat.example

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.storage.StorageManager
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.lazygeniouz.filecompat.example.performance.Performance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(R.layout.activity_main) {

    private lateinit var buttonDir: Button
    private lateinit var buttonFile: Button
    private lateinit var buttonProjections: Button

    private lateinit var textView: TextView
    private lateinit var progress: ProgressBar

    private var selectedFileCount = 250

    private val testFileGenerationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val documentUri = result.data?.data
                if (documentUri != null) {
                    textView.text = getString(R.string.creating_files)

                    lifecycleScope.launch {
                        progress.isVisible = true
                        buttonDir.isVisible = false
                        buttonFile.isVisible = false
                        buttonProjections.isVisible = false

                        val generationResult = TestFileGenerator.generateTestFiles(
                            this@MainActivity, documentUri, selectedFileCount
                        )

                        progress.isVisible = false
                        buttonDir.isVisible = true
                        buttonFile.isVisible = true
                        buttonProjections.isVisible = true
                        textView.text = generationResult
                    }
                }
            }
        }

    private val folderResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val documentUri = result.data?.data
                if (documentUri != null) {
                    textView.text = ""

                    lifecycleScope.launch {
                        progress.isVisible = true
                        buttonDir.isVisible = false
                        buttonFile.isVisible = false
                        buttonProjections.isVisible = false
                        val performanceResult = withContext(Dispatchers.IO) {
                            Performance.calculateDirectoryPerformance(
                                this@MainActivity, documentUri
                            )
                        }

                        progress.isVisible = false
                        buttonDir.isVisible = true
                        buttonFile.isVisible = true
                        buttonProjections.isVisible = true
                        textView.text = performanceResult
                    }
                }
            }
        }

    @SuppressLint("SetTextI18n")
    private val fileResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val documentUri = result.data?.data
                if (documentUri != null) {
                    textView.text = ""

                    lifecycleScope.launch {
                        val performance = withContext(Dispatchers.IO) {
                            Performance.calculateFilesPerformance(
                                this@MainActivity, documentUri
                            )
                        }

                        textView.text = performance
                    }
                }
            }
        }

    private val projectionResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val documentUri = result.data?.data
                if (documentUri != null) {
                    textView.text = ""

                    lifecycleScope.launch {
                        progress.isVisible = true
                        buttonDir.isVisible = false
                        buttonFile.isVisible = false
                        buttonProjections.isVisible = false

                        val performanceResult = withContext(Dispatchers.IO) {
                            Performance.calculateProjectionPerformance(
                                this@MainActivity, documentUri
                            )
                        }

                        progress.isVisible = false
                        buttonDir.isVisible = true
                        buttonFile.isVisible = true
                        buttonProjections.isVisible = true
                        textView.text = performanceResult
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buttonDir = findViewById(R.id.buttonDir)
        buttonFile = findViewById(R.id.buttonFile)
        buttonProjections = findViewById(R.id.buttonProjections)
        textView = findViewById(R.id.fileNames)
        progress = findViewById(R.id.progress)

        onBackPressedDispatcher.addCallback(this) { finishAffinity() }

        buttonDir.setOnClickListener {
            folderResultLauncher.launch(getStorageIntent())
        }

        buttonFile.setOnClickListener {
            fileResultLauncher.launch(getStorageIntent(true))
        }

        buttonProjections.setOnClickListener {
            projectionResultLauncher.launch(getStorageIntent())
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_add_test_files -> {
                showFileCountDialog()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFileCountDialog() {
        val options = arrayOf("250 files", "500 files", "1000 files")
        val counts = arrayOf(250, 500, 1000)

        AlertDialog.Builder(this)
            .setTitle(R.string.select_file_count)
            .setItems(options) { _, which ->
                selectedFileCount = counts[which]
                testFileGenerationLauncher.launch(getStorageIntent())
            }
            .show()
    }

    private fun getStorageIntent(single: Boolean = false): Intent {
        return if (single) Intent(Intent.ACTION_GET_CONTENT).setType("*/*") else {
            if (SDK_INT >= 30) {
                val storageManager = getSystemService(STORAGE_SERVICE) as StorageManager
                storageManager.primaryStorageVolume.createOpenDocumentTreeIntent()
            } else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }
    }
}