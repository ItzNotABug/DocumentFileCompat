package com.lazygeniouz.dfc.extension

import android.util.Log

// Print error logs to logcat.
internal fun logError(message: String?) {
    if (message == null) return
    Log.e("DocumentFileCompat", message)
}