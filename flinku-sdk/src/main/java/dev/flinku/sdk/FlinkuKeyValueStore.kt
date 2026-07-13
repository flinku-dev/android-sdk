package dev.flinku.sdk

import android.content.Context

/** Key-value persistence used by Flinku (defaults to SharedPreferences). */
interface FlinkuKeyValueStore {
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
    fun getBytes(key: String): ByteArray?
    fun putBytes(key: String, value: ByteArray?)
    fun remove(key: String)
    fun allKeys(): Set<String>
}

class SharedPreferencesKeyValueStore(context: Context) : FlinkuKeyValueStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBytes(key: String): ByteArray? = prefs.getString(key, null)?.toByteArray(Charsets.UTF_8)

    override fun putBytes(key: String, value: ByteArray?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, String(value, Charsets.UTF_8)).apply()
        }
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun allKeys(): Set<String> = prefs.all.keys

    companion object {
        const val PREFS_NAME = "flinku_prefs"
    }
}
