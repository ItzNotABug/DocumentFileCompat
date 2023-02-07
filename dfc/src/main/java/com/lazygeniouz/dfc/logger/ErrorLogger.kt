package com.lazygeniouz.dfc.logger

import android.util.Log

/**
 * A logging utility class.
 */
object ErrorLogger {

    /**
     * Log error to the logcat to let the developer know if something went wrong.
     */
    internal fun logError(message: String, throwable: Throwable?) {
        Log.e("DocumentFileCompat", "$message: ${throwable?.message}")
    }
}