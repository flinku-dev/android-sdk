package dev.flinku.sdk

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object Flinku {
    private var config: FlinkuConfig? = null
    private const val PREFS_NAME = "flinku_prefs"
    private const val KEY_MATCHED = "flinku_matched"
    private const val KEY_RESULT = "flinku_match_result"

    /**
     * Configure Flinku with your project subdomain URL.
     * Call once in Application.onCreate() before any match() call.
     *
     * Example:
     * ```kotlin
     * Flinku.configure(context, baseUrl = "https://yourapp.flku.dev")
     * ```
     */
    fun configure(context: Context, baseUrl: String, debug: Boolean = false, timeoutMs: Long = 5000L) {
        config = FlinkuConfig(baseUrl = baseUrl, debug = debug, timeoutMs = timeoutMs)
    }

    /**
     * Returns true if match() has already found a match.
     * Prevents double-matching across app launches.
     */
    fun hasMatched(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_MATCHED, false)
    }

    /**
     * Match the current device to a previously clicked Flinku link.
     * Call once on app launch — runs on a background thread automatically.
     * Must be called from a coroutine or background thread.
     */
    suspend fun match(context: Context): FlinkuLink {
        val cfg = config ?: run {
            Log.e("Flinku", "Not configured. Call Flinku.configure() first.")
            return FlinkuLink.notMatched
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Prevent double matching
        if (prefs.getBoolean(KEY_MATCHED, false)) {
            val stored = prefs.getString(KEY_RESULT, null)
            if (stored != null) {
                return try {
                    FlinkuLink.fromJson(JSONObject(stored))
                } catch (e: Exception) {
                    FlinkuLink.notMatched
                }
            }
            return FlinkuLink.notMatched
        }

        val result = withContext(Dispatchers.IO) {
            FlinkuHttp.match(cfg)
        }

        if (result.matched) {
            prefs.edit()
                .putBoolean(KEY_MATCHED, true)
                .putString(
                    KEY_RESULT,
                    JSONObject().apply {
                        put("matched", true)
                        put("deepLink", result.deepLink ?: "")
                        put("slug", result.slug ?: "")
                        put("subdomain", result.subdomain ?: "")
                        put("title", result.title ?: "")
                        put("projectId", result.projectId ?: "")
                    }.toString()
                )
                .apply()
        }

        return result
    }

    /**
     * Reset stored match result. Use only during development/testing.
     */
    fun reset(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_MATCHED)
            .remove(KEY_RESULT)
            .apply()
    }
}
