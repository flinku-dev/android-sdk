package dev.flinku.sdk

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Flinku {
    private var _playReferrer: String? = null

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
    fun configure(
        context: Context,
        baseUrl: String,
        apiKey: String? = null,
        debug: Boolean = false,
        timeoutMs: Long = 5000L
    ) {
        config = FlinkuConfig(baseUrl = baseUrl, apiKey = apiKey, debug = debug, timeoutMs = timeoutMs)

        // Read Play Install Referrer for deterministic deferred deep linking
        val referrerClient = InstallReferrerClient.newBuilder(context.applicationContext).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                    try {
                        val referrer = referrerClient.installReferrer.installReferrer
                        if (referrer.contains("flinku_click=")) {
                            _playReferrer = referrer
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                referrerClient.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() {}
        })
    }

    /**
     * Create a single short link. Requires [FlinkuConfig.apiKey] (set via [configure]).
     */
    suspend fun createLink(options: FlinkuLinkOptions): FlinkuCreatedLink {
        val cfg = config ?: throw FlinkuException("Not configured. Call Flinku.configure() first.")
        val apiKey = cfg.apiKey ?: throw FlinkuException("apiKey is required to create links")
        val body = options.toJsonObject()
        val response = withContext(Dispatchers.IO) {
            FlinkuHttp.postAuthorizedJson(
                cfg.apiBaseUrl,
                "/api/links",
                body,
                apiKey,
                cfg.timeoutMs
            )
        }
        return parseCreatedLinkResponse(response)
    }

    /**
     * Create a short link optimistically: returns immediately with a locally generated
     * slug and short URL, then registers the link on the server in the background.
     * Requires [FlinkuConfig.apiKey] (set via [configure]).
     */
    fun createLinkInstant(options: FlinkuLinkOptions): FlinkuCreatedLink {
        val cfg = config ?: throw FlinkuException("Not configured. Call Flinku.configure() first.")
        val apiKey = cfg.apiKey ?: throw FlinkuException("apiKey is required to create links")
        val slug = generateInstantSlug(options.title)
        val shortUrl = "https://${cfg.subdomain}.flku.dev/$slug"
        val body = options.toJsonObject().apply { put("slug", slug) }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                FlinkuHttp.postAuthorizedJson(
                    cfg.apiBaseUrl,
                    "/api/links",
                    body,
                    apiKey,
                    cfg.timeoutMs
                )
            } catch (e: Exception) {
                Log.e("Flinku", "createLinkInstant background error: ${e.message}")
            }
        }
        return FlinkuCreatedLink(
            id = "",
            slug = slug,
            shortUrl = shortUrl,
            deepLink = options.deepLink,
            params = options.params,
        )
    }

    /**
     * Create multiple short links in one request. Requires [FlinkuConfig.apiKey] (set via [configure]).
     */
    suspend fun createLinks(links: List<FlinkuLinkOptions>): List<FlinkuCreatedLink> {
        val cfg = config ?: throw FlinkuException("Not configured. Call Flinku.configure() first.")
        val apiKey = cfg.apiKey ?: throw FlinkuException("apiKey is required to create links")
        val linksArray = JSONArray()
        links.forEach { linksArray.put(it.toJsonObject()) }
        val body = JSONObject().put("links", linksArray)
        val responseText = withContext(Dispatchers.IO) {
            FlinkuHttp.postAuthorizedText(
                cfg.apiBaseUrl,
                "/api/links/bulk",
                body,
                apiKey,
                cfg.timeoutMs
            )
        }
        return parseCreatedLinksFromBody(responseText)
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

        val subdomain = cfg.subdomain
        val baseUrl = cfg.baseUrl

        val result = withContext(Dispatchers.IO) {
            // 1. Play Install Referrer — deterministic match
            _playReferrer?.let { referrer ->
                val referrerResult = matchWithBody(
                    cfg,
                    mapOf("subdomain" to subdomain, "referrer" to referrer)
                )
                if (referrerResult.matched) {
                    _playReferrer = null
                    return@withContext referrerResult
                }
            }

            // 2. Clipboard — deterministic match
            try {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipText = cm?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                if (clipText.isNotEmpty() && (clipText.contains(".flku.dev") || clipText.contains(baseUrl))) {
                    val clipManager =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipManager.setPrimaryClip(ClipData.newPlainText("", "")) // clear
                    val clipResult = matchWithBody(
                        cfg,
                        mapOf("subdomain" to subdomain, "clipboardUrl" to clipText)
                    )
                    if (clipResult.matched) return@withContext clipResult
                }
            } catch (e: Exception) {
                // ignore
            }

            // 3. Fingerprint match
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
                        put("matchType", result.matchType ?: "")
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
        _playReferrer = null
    }

    private fun matchWithBody(config: FlinkuConfig, body: Map<String, String>): FlinkuLink {
        val json = JSONObject()
        body.forEach { (key, value) -> json.put(key, value) }
        return FlinkuHttp.matchWithBody(config, json)
    }

    private fun generateInstantSlug(title: String): String {
        var base = title.lowercase().trim()
        base = base.replace(Regex("[^a-z0-9\\s-]"), "")
        base = base.replace(Regex("\\s+"), "-")
        base = base.replace(Regex("-+"), "-").trim('-')
        if (base.isEmpty()) {
            base = "link"
        }
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        val suffix = (1..4).map { chars.random() }.joinToString("")
        return "$base-$suffix"
    }
}
