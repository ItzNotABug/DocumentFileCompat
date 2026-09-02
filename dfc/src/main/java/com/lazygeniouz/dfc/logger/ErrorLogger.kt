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
        if (throwable == null) {
            Log.e(TAG, message)
        } else {
            Log.e(TAG, message, throwable)
        }
    }

    private const val TAG = "DocumentFileCompat"
}