package com.lazygeniouz.filecompat.example

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.storage.StorageManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
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

    private lateinit var textView: TextView
    private lateinit var progress: ProgressBar

    private val folderResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val documentUri = result.data?.data
                if (documentUri != null) {
                    textView.text = ""

                    lifecycleScope.launch {
                        progress.isVisible = true
                        buttonDir.isVisible = false
                        buttonFile.isVisible = false
                        val performanceResult = withContext(Dispatchers.IO) {
                            Performance.calculateDirectoryPerformance(
                                this@MainActivity, documentUri
                            )
                        }

                        progress.isVisible = false
                        buttonDir.isVisible = true
                        buttonFile.isVisible = true
                        textView.text = performanceResult
                    }
                }
            }
        }


    @SuppressLint("SetTextI18n")
    private val fileResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
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

    @Suppress("unchecked_cast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buttonDir = findViewById(R.id.buttonDir)
        buttonFile = findViewById(R.id.buttonFile)
        textView = findViewById(R.id.fileNames)
        progress = findViewById(R.id.progress)

        buttonDir.isVisible = true
        buttonFile.isVisible = true

        buttonDir.setOnClickListener {
            folderResultLauncher.launch(getStorageIntent())
        }

        buttonFile.setOnClickListener {
            fileResultLauncher.launch(getStorageIntent(true))
        }
    }

    private fun getStorageIntent(single: Boolean = false): Intent {
        return if (single) Intent(Intent.ACTION_GET_CONTENT).setType("*/*") else {
            if (SDK_INT >= 30) {
                val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
                storageManager.primaryStorageVolume.createOpenDocumentTreeIntent()
            } else Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        }
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}