package dev.flinku.sdk

import android.content.Context
import android.content.SharedPreferences

internal object FlinkuStorage {
    private const val PREFS_NAME = "flinku_prefs"
    private const val KEY_MATCHED = "flinku_matched"
    private const val KEY_LAUNCHED = "flinku_first_launch_done"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var hasMatched: Boolean
        get() = prefs.getBoolean(KEY_MATCHED, false)
        set(value) = prefs.edit().putBoolean(KEY_MATCHED, value).apply()

    var hasLaunched: Boolean
        get() = prefs.getBoolean(KEY_LAUNCHED, false)
        set(value) = prefs.edit().putBoolean(KEY_LAUNCHED, value).apply()

    fun reset() {
        prefs.edit().remove(KEY_MATCHED).remove(KEY_LAUNCHED).apply()
    }
}
