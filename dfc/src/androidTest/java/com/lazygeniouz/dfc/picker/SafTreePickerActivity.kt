package com.lazygeniouz.dfc.picker

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class SafTreePickerActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialUri = initialUriExtra()
        val pickerIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
            if (initialUri != null) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
            }
        }
        startActivityForResult(pickerIntent, REQUEST_TREE)
    }

    private fun initialUriExtra(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_INITIAL_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_INITIAL_URI)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_TREE) {
            try {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    val flags = data.flags and (
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    runCatching {
                        contentResolver.takePersistableUriPermission(uri, flags)
                    }
                    resultUri.set(uri)
                }
            } finally {
                resultLatch.countDown()
                finish()
            }
            return
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    companion object {
        const val EXTRA_INITIAL_URI = "com.lazygeniouz.dfc.picker.EXTRA_INITIAL_URI"
        private const val REQUEST_TREE = 1
        private const val RESULT_TIMEOUT_SECONDS = 15L

        private val resultUri = AtomicReference<Uri?>()
        private var resultLatch = CountDownLatch(1)

        fun reset() {
            resultUri.set(null)
            resultLatch = CountDownLatch(1)
        }

        fun awaitResult(): Uri? {
            resultLatch.await(RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            return resultUri.get()
        }
    }
}
