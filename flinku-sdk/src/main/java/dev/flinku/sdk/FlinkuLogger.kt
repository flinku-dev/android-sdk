package dev.flinku.sdk

import android.util.Log

internal object FlinkuLogger {
    private const val TAG = "FlinkuSDK"
    var debugMode: Boolean = false

    fun log(message: String) {
        if (debugMode) Log.d(TAG, message)
    }

    fun error(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
