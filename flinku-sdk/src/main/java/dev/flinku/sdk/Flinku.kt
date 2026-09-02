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
    private var secretKeyWarningShown = false
    private var referralApiKeyWarningShown = false
    private const val KEY_MATCHED = "flinku_matched"
    private const val KEY_RESULT = "flinku_match_result"
    private const val KEY_USER_ID = "flinku_user_id"
    private const val KEY_REFERRAL_PROJECT_ID = "flinku_referral_project_id"
    private const val PENDING_REFERRAL_KEY_PREFIX = "flinku_pending_referral_"
    private const val KEY_PENDING_REFERRAL_INDEX = "flinku_pending_referral_index"
    private const val REFERRAL_TRACKED_KEY_PREFIX = "referral_tracked_"
    private const val PENDING_REFERRAL_TTL_MS = 30L * 24 * 60 * 60 * 1000

    /** Injectable persistence (defaults to SharedPreferences on [configure]). */
    @JvmField
    var store: FlinkuKeyValueStore? = null

    /** Injectable HTTP client (defaults to real network). */
    @JvmField
    var network: FlinkuNetworkClient? = null

    /** Optional sink for log lines (used by unit tests). */
    @JvmField
    var logSink: ((String) -> Unit)? = null

    private fun referralTrackedKey(projectId: String, userId: String) =
        "${REFERRAL_TRACKED_KEY_PREFIX}${projectId}_$userId"

    private fun pendingReferralKey(projectId: String) = "$PENDING_REFERRAL_KEY_PREFIX$projectId"

    private fun getStore(context: Context): FlinkuKeyValueStore {
        store?.let { return it }
        return SharedPreferencesKeyValueStore(context.applicationContext).also { store = it }
    }

    private fun getNetwork(): FlinkuNetworkClient {
        network?.let { return it }
        return DefaultFlinkuNetworkClient().also { network = it }
    }

    private fun log(message: String) {
        Log.w("Flinku", message)
        logSink?.invoke(message)
    }

    /** Resets injectable deps and static flags for unit tests. */
    @JvmStatic
    fun resetForTesting() {
        config = null
        secretKeyWarningShown = false
        referralApiKeyWarningShown = false
        store = null
        network = null
        logSink = null
        _playReferrer = null
        FlinkuHttp.postAuthorizedJsonInterceptor = null
    }

    fun configure(
        context: Context,
        baseUrl: String,
        apiKey: String? = null,
        debug: Boolean = false,
        timeoutMs: Long = 5000L,
        readClipboard: Boolean = true,
    ) {
        config = FlinkuConfig(baseUrl = baseUrl, apiKey = apiKey, debug = debug, timeoutMs = timeoutMs, readClipboard = readClipboard)
        val prefsStore = getStore(context)

        if (apiKey != null && apiKey.startsWith("flk_live_") && !secretKeyWarningShown) {
            secretKeyWarningShown = true
            if (BuildConfig.DEBUG) {
                log(
                    "FLINKU WARNING: You are embedding a secret key (flk_live_) in your app. " +
                        "Anyone can extract it and gain full access to your links. " +
                        "Use your publishable key (flk_pk_) instead — find it in your project settings at app.flinku.dev.",
                )
            }
        }

        // Retry a pending referral track if a userId was already stored (e.g. app relaunch).
        val userId = prefsStore.getString(KEY_USER_ID)?.trim().orEmpty()
        if (userId.isNotEmpty()) {
            trackReferralInBackground(prefsStore, userId)
        }

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
                    } catch (_: Exception) {
                        // ignore
                    }
                }
                referrerClient.endConnection()
            }

            override fun onInstallReferrerServiceDisconnected() {}
        })
    }

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
                cfg.timeoutMs,
            )
        }
        return parseCreatedLinkResponse(response)
    }

    fun createLinkInstant(options: FlinkuLinkOptions): FlinkuCreatedLink {
        val cfg = config ?: throw FlinkuException("Not configured. Call Flinku.configure() first.")
        val apiKey = cfg.apiKey ?: throw FlinkuException("apiKey is required to create links")
        val slug = generateInstantSlug(options.title)
        val shortUrl = "https://${cfg.subdomain}.flku.dev/$slug"
        val body = options.toJsonObject().apply { put("slug", slug) }
        // Retries run in the background; if the process dies mid-retry, attempts stop.
        CoroutineScope(Dispatchers.IO).launch {
            postCreateLinkInstantWithRetry(cfg, body, apiKey)
        }
        return FlinkuCreatedLink(
            id = "",
            slug = slug,
            shortUrl = shortUrl,
            deepLink = options.deepLink,
            params = options.params,
        )
    }

    private suspend fun postCreateLinkInstantWithRetry(
        cfg: FlinkuConfig,
        body: org.json.JSONObject,
        apiKey: String,
    ) {
        val backoffs = listOf(1000L, 2000L, 4000L)
        var lastMessage = "Failed to create link"
        repeat(3) { attempt ->
            try {
                FlinkuHttp.postAuthorizedJson(
                    cfg.apiBaseUrl,
                    "/api/links",
                    body,
                    apiKey,
                    cfg.timeoutMs,
                )
                return
            } catch (e: FlinkuHttpException) {
                lastMessage = e.message ?: lastMessage
                if (!isRetryableInstantLinkStatus(e.statusCode)) {
                    if (cfg.debug) {
                        Log.e("Flinku", "createLinkInstant background error: $lastMessage")
                    }
                    return
                }
            } catch (e: Exception) {
                lastMessage = e.message ?: lastMessage
            }
            if (attempt < 2) {
                kotlinx.coroutines.delay(backoffs[attempt])
            }
        }
        if (cfg.debug) {
            Log.e("Flinku", "createLinkInstant background error: $lastMessage")
        }
    }

    private fun isRetryableInstantLinkStatus(code: Int): Boolean =
        code == 429 || code >= 500

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
                cfg.timeoutMs,
            )
        }
        return parseCreatedLinksFromBody(responseText)
    }

    fun hasMatched(context: Context): Boolean {
        return getStore(context).getBoolean(KEY_MATCHED)
    }

    suspend fun match(context: Context): FlinkuLink {
        val cfg = config ?: run {
            log("[Flinku] Not configured. Call Flinku.configure() first.")
            return FlinkuLink.notMatched
        }

        val prefsStore = getStore(context)

        if (prefsStore.getBoolean(KEY_MATCHED)) {
            val stored = prefsStore.getString(KEY_RESULT)
            if (stored != null) {
                return try {
                    FlinkuLink.fromJson(JSONObject(stored))
                } catch (_: Exception) {
                    FlinkuLink.notMatched
                }
            }
            return FlinkuLink.notMatched
        }

        val subdomain = cfg.subdomain
        val baseUrl = cfg.baseUrl

        val result = withContext(Dispatchers.IO) {
            _playReferrer?.let { referrer ->
                val referrerResult = matchWithBody(
                    cfg,
                    JSONObject().apply {
                        put("subdomain", subdomain)
                        put("referrer", referrer)
                    },
                )
                if (referrerResult.matched) {
                    _playReferrer = null
                    return@withContext referrerResult
                }
            }

            if (cfg.readClipboard) {
                try {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipText = cm?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    if (clipText.isNotEmpty() && (clipText.contains(".flku.dev") || clipText.contains(baseUrl))) {
                        val clipManager =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipManager.setPrimaryClip(ClipData.newPlainText("", ""))
                        val clipResult = matchWithBody(
                            cfg,
                            JSONObject().apply {
                                put("subdomain", subdomain)
                                put("clipboardUrl", clipText)
                            },
                        )
                        if (clipResult.matched) return@withContext clipResult
                    }
                } catch (_: Exception) {
                    // ignore
                }
            }

            getNetwork().match(cfg, JSONObject().apply {
                put("subdomain", subdomain)
                put("userAgent", System.getProperty("http.agent") ?: "Android")
            })
        }

        if (result.matched) {
            persistMatchResult(prefsStore, result)
        }

        return result
    }

    fun setUserId(context: Context, userId: String) {
        val id = userId.trim()
        if (id.isEmpty()) return
        warnMissingReferralApiKeyOnce()
        val prefsStore = getStore(context)
        prefsStore.putString(KEY_USER_ID, id)
        trackReferralInBackground(prefsStore, id)
    }

    fun qualifyReferral(context: Context, event: String? = null) {
        warnMissingReferralApiKeyOnce()
        val prefsStore = getStore(context.applicationContext)
        qualifyReferralInBackground(prefsStore, event)
    }

    fun reset(context: Context) {
        val prefsStore = getStore(context)
        prefsStore.remove(KEY_MATCHED)
        prefsStore.remove(KEY_RESULT)
        _playReferrer = null
    }

    /**
     * Clears all Flinku local state, including referral attribution and stored user id.
     *
     * **Testing only — do not call in production.** Clearing attribution destroys
     * real referral data. [reset] was narrowed in 0.6.0 so production deep-link
     * handling does not wipe referrals; use [resetAll] only for a full wipe in
     * development or QA.
     */
    fun resetAll(context: Context) {
        reset(context)
        val prefsStore = getStore(context)
        prefsStore.remove(KEY_USER_ID)
        prefsStore.remove(KEY_REFERRAL_PROJECT_ID)
        prefsStore.remove(KEY_RESULT)

        for (projectId in getPendingReferralIndex(prefsStore)) {
            prefsStore.remove(pendingReferralKey(projectId))
        }
        prefsStore.remove(KEY_PENDING_REFERRAL_INDEX)

        for (key in prefsStore.allKeys()) {
            if (key.startsWith(PENDING_REFERRAL_KEY_PREFIX) ||
                key.startsWith(REFERRAL_TRACKED_KEY_PREFIX)
            ) {
                prefsStore.remove(key)
            }
        }
    }

    private fun hasReferralApiKey(): Boolean {
        val apiKey = config?.apiKey?.trim()
        return !apiKey.isNullOrEmpty()
    }

    private fun warnMissingReferralApiKeyOnce() {
        if (hasReferralApiKey() || referralApiKeyWarningShown) return
        referralApiKeyWarningShown = true
        log("[Flinku] Referral tracking skipped: no apiKey configured. Pass apiKey: 'flk_pk_...' to Flinku.configure().")
    }

    private fun matchWithBody(config: FlinkuConfig, body: JSONObject): FlinkuLink {
        return getNetwork().match(config, body)
    }

    private fun persistMatchResult(prefsStore: FlinkuKeyValueStore, result: FlinkuLink) {
        if (!result.matched) return
        prefsStore.putBoolean(KEY_MATCHED, true)
        val payload = JSONObject().apply {
            put("matched", true)
            put("deepLink", result.deepLink ?: "")
            put("slug", result.slug ?: "")
            put("subdomain", result.subdomain ?: "")
            put("title", result.title ?: "")
            put("params", JSONObject(result.params ?: emptyMap<String, Any>()))
            put("projectId", result.projectId ?: "")
            put("matchType", result.matchType ?: "")
            if (!result.linkId.isNullOrEmpty()) put("linkId", result.linkId)
        }
        prefsStore.putString(KEY_RESULT, payload.toString())
        persistPendingReferralIfNeeded(prefsStore, result)
    }

    private fun persistPendingReferralIfNeeded(prefsStore: FlinkuKeyValueStore, result: FlinkuLink) {
        if (!result.matched) return
        val projectId = result.projectId?.trim().orEmpty()
        if (projectId.isEmpty()) return

        val params = result.params ?: return
        val referrerRaw = params["referrerId"] ?: params["referrer_id"]
        val referrerId = referrerRaw?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return

        val labelRaw = params["referrerLabel"] ?: params["referrer_label"]
        val referrerLabel = labelRaw?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        val linkId = result.linkId?.trim()?.takeIf { it.isNotEmpty() }
            ?: result.slug?.trim()?.takeIf { it.isNotEmpty() }

        val value = JSONObject().apply {
            put("referrerId", referrerId)
            put("matchedAt", System.currentTimeMillis() / 1000.0)
            if (referrerLabel != null) put("referrerLabel", referrerLabel)
            if (linkId != null) put("linkId", linkId)
        }

        prefsStore.putString(pendingReferralKey(projectId), value.toString())
        prefsStore.putString(KEY_REFERRAL_PROJECT_ID, projectId)
        addPendingReferralIndex(prefsStore, projectId)
    }

    private fun getPendingReferralIndex(prefsStore: FlinkuKeyValueStore): List<String> {
        val raw = prefsStore.getString(KEY_PENDING_REFERRAL_INDEX)?.trim().orEmpty()
        if (raw.isNotEmpty()) {
            return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val pid = prefsStore.getString(KEY_REFERRAL_PROJECT_ID)?.trim().orEmpty()
        return if (pid.isNotEmpty()) listOf(pid) else emptyList()
    }

    private fun addPendingReferralIndex(prefsStore: FlinkuKeyValueStore, projectId: String) {
        val ids = getPendingReferralIndex(prefsStore).toMutableList()
        if (!ids.contains(projectId)) ids.add(projectId)
        prefsStore.putString(KEY_PENDING_REFERRAL_INDEX, ids.joinToString(","))
    }

    private fun removePendingReferralIndex(prefsStore: FlinkuKeyValueStore, projectId: String) {
        val ids = getPendingReferralIndex(prefsStore).toMutableList()
        ids.removeAll { it == projectId }
        if (ids.isEmpty()) {
            prefsStore.remove(KEY_PENDING_REFERRAL_INDEX)
        } else {
            prefsStore.putString(KEY_PENDING_REFERRAL_INDEX, ids.joinToString(","))
        }
    }

    private fun loadPendingReferral(prefsStore: FlinkuKeyValueStore): Pair<String, JSONObject>? {
        for (key in prefsStore.allKeys().toList()) {
            if (!key.startsWith(PENDING_REFERRAL_KEY_PREFIX)) continue
            val projectId = key.removePrefix(PENDING_REFERRAL_KEY_PREFIX)
            if (projectId.isEmpty()) continue

            val raw = prefsStore.getString(key) ?: continue
            val json = try {
                JSONObject(raw)
            } catch (_: Exception) {
                continue
            }

            val matchedAt = when (val v = json.opt("matchedAt")) {
                is Number -> v.toDouble()
                else -> {
                    prefsStore.remove(key)
                    continue
                }
            }

            val nowSeconds = System.currentTimeMillis() / 1000.0
            if (nowSeconds - matchedAt > PENDING_REFERRAL_TTL_MS / 1000.0) {
                prefsStore.remove(key)
                removePendingReferralIndex(prefsStore, projectId)
                continue
            }

            val referrerId = json.optString("referrerId", "").trim()
            if (referrerId.isEmpty()) {
                prefsStore.remove(key)
                continue
            }

            return projectId to json
        }
        return null
    }

    private fun clearPendingReferral(prefsStore: FlinkuKeyValueStore, projectId: String) {
        prefsStore.remove(pendingReferralKey(projectId))
        removePendingReferralIndex(prefsStore, projectId)
    }

    private fun trackReferralInBackground(prefsStore: FlinkuKeyValueStore, userId: String) {
        val cfg = config ?: return
        if (!hasReferralApiKey()) return
        val pending = loadPendingReferral(prefsStore) ?: return
        val projectId = pending.first
        val json = pending.second

        val trackedKey = referralTrackedKey(projectId, userId)
        if (prefsStore.getBoolean(trackedKey)) {
            clearPendingReferral(prefsStore, projectId)
            return
        }

        val referrerId = json.optString("referrerId", "").trim()
        if (referrerId.isEmpty()) {
            clearPendingReferral(prefsStore, projectId)
            return
        }

        val referrerLabel = json.optString("referrerLabel", "").trim().takeIf { it.isNotEmpty() }
        val linkId = json.optString("linkId", "").trim().takeIf { it.isNotEmpty() }

        val body = JSONObject().apply {
            put("projectId", projectId)
            put("referrerId", referrerId)
            put("newUserId", userId)
            if (referrerLabel != null) put("referrerLabel", referrerLabel)
            if (linkId != null) put("linkId", linkId)
        }

        prefsStore.putString(KEY_REFERRAL_PROJECT_ID, projectId)

        getNetwork().postReferral(
            cfg.apiBaseUrl,
            "/api/referrals/track",
            body,
            cfg.apiKey!!.trim(),
            cfg.timeoutMs,
        ) { success ->
            if (!success) return@postReferral
            prefsStore.putBoolean(trackedKey, true)
            clearPendingReferral(prefsStore, projectId)
        }
    }

    private fun qualifyReferralInBackground(prefsStore: FlinkuKeyValueStore, event: String?) {
        val cfg = config ?: return
        if (!hasReferralApiKey()) return
        val userId = prefsStore.getString(KEY_USER_ID)?.trim().orEmpty()
        if (userId.isEmpty()) return

        var projectId = prefsStore.getString(KEY_REFERRAL_PROJECT_ID)?.trim().orEmpty()
        if (projectId.isEmpty()) {
            projectId = loadPendingReferral(prefsStore)?.first.orEmpty()
        }
        if (projectId.isEmpty()) {
            val stored = prefsStore.getString(KEY_RESULT)
            if (stored != null) {
                try {
                    projectId = JSONObject(stored).optString("projectId", "").trim()
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
        if (projectId.isEmpty()) return

        val body = JSONObject().apply {
            put("projectId", projectId)
            put("newUserId", userId)
            val trimmed = event?.trim()
            if (!trimmed.isNullOrEmpty()) put("event", trimmed)
        }

        getNetwork().postReferral(
            cfg.apiBaseUrl,
            "/api/referrals/qualify",
            body,
            cfg.apiKey!!.trim(),
            cfg.timeoutMs,
        ) { }
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
