package dev.flinku.sdk

import android.content.Context
import android.os.Build
import org.json.JSONObject

object Flinku {
    private var config: FlinkuConfig? = null
    private var http: FlinkuHttp? = null
    private var initialized = false

    /**
     * Initialize the SDK. Call this in Application.onCreate() or MainActivity.onCreate()
     */
    fun configure(context: Context, config: FlinkuConfig) {
        this.config = config
        this.http = FlinkuHttp(config.baseUrl, config.matchTimeoutSeconds)
        FlinkuLogger.debugMode = config.debugMode
        FlinkuStorage.init(context.applicationContext)
        initialized = true
        FlinkuLogger.log("SDK configured with baseUrl: ${config.baseUrl}")
    }

    /**
     * Check for deferred deep link. Call once on app first launch.
     * Must be called from a coroutine scope.
     */
    suspend fun match(): FlinkuLink {
        if (!initialized || config == null || http == null) {
            FlinkuLogger.error("SDK not initialized. Call Flinku.configure() first.")
            return FlinkuLink.noMatch
        }

        if (FlinkuStorage.hasMatched) {
            FlinkuLogger.log("Already matched previously, skipping.")
            return FlinkuLink.noMatch
        }

        if (FlinkuStorage.hasLaunched) {
            FlinkuLogger.log("Not first launch, skipping match.")
            return FlinkuLink.noMatch
        }

        FlinkuLogger.log("Attempting deferred deep link match...")

        return try {
            val body = JSONObject().apply {
                put("apiKey", config!!.apiKey)
                put("deviceInfo", JSONObject().apply {
                    put("platform", "android")
                    put("userAgent", "FlinkuSDK/Android/${Build.VERSION.RELEASE}")
                    put("timestamp", System.currentTimeMillis())
                })
            }

            val response = http!!.post("/api/match", body)
            val link = FlinkuLink.fromJson(response)

            FlinkuStorage.hasLaunched = true
            if (link.matched) {
                FlinkuStorage.hasMatched = true
                FlinkuLogger.log("Match found: ${link.deepLink}")
            } else {
                FlinkuLogger.log("No match found.")
            }

            link
        } catch (e: Exception) {
            FlinkuLogger.error("Match failed", e)
            FlinkuStorage.hasLaunched = true
            FlinkuLink.noMatch
        }
    }

    /**
     * Debug only — reset match state to test again
     */
    fun resetForTesting() {
        FlinkuStorage.reset()
        FlinkuLogger.log("SDK state reset for testing.")
    }
}
