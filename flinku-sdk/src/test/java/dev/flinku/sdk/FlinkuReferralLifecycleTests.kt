package dev.flinku.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URL

class MockKeyValueStore : FlinkuKeyValueStore {
    private val values = mutableMapOf<String, Any?>()

    override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun getString(key: String) = values[key] as? String

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun getBytes(key: String) = getString(key)?.toByteArray(Charsets.UTF_8)

    override fun putBytes(key: String, value: ByteArray?) {
        if (value == null) remove(key) else putString(key, String(value, Charsets.UTF_8))
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun allKeys(): Set<String> = values.keys

    fun pendingReferralJSON(projectId: String): JSONObject? {
        val raw = getString("flinku_pending_referral_$projectId") ?: return null
        return try {
            JSONObject(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun hasPendingReferral(projectId: String) =
        getString("flinku_pending_referral_$projectId") != null
}

class MockNetworkClient : FlinkuNetworkClient {
    data class RecordedRequest(
        val url: URL,
        val method: String,
        val headers: Map<String, String>,
        val body: JSONObject?,
    )

    val recorded = mutableListOf<RecordedRequest>()
    var asyncResponses = mutableMapOf<String, Pair<JSONObject, Int>>()
    var taskResponses = mutableMapOf<String, Triple<JSONObject?, Int, Exception?>>()
    var failAllTasks = false
    var taskError: Exception? = null

    fun reset() {
        recorded.clear()
        asyncResponses.clear()
        taskResponses.clear()
        failAllTasks = false
        taskError = null
    }

    override fun match(config: FlinkuConfig, body: JSONObject): FlinkuLink {
        record("POST", "${config.baseUrl.trimEnd('/')}/api/match", emptyMap(), body)
        val json = asyncResponses["/api/match"]?.first ?: return FlinkuLink.notMatched
        return FlinkuLink.fromJson(json)
    }

    override fun postReferral(
        apiBaseUrl: String,
        path: String,
        body: JSONObject,
        apiKey: String,
        timeoutMs: Long,
        onComplete: (success: Boolean) -> Unit,
    ) {
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $apiKey",
        )
        record("POST", "${apiBaseUrl.trimEnd('/')}$path", headers, body)

        if (failAllTasks) {
            onComplete(false)
            return
        }

        val entry = taskResponses.entries.firstOrNull { path.endsWith(it.key) || it.key == path }
        val error = entry?.value?.third
        if (error != null) {
            onComplete(false)
            return
        }
        val status = entry?.value?.second ?: 200
        onComplete(status in 200..299)
    }

    private fun record(method: String, urlString: String, headers: Map<String, String>, body: JSONObject?) {
        recorded.add(
            RecordedRequest(
                url = URL(urlString),
                method = method,
                headers = headers,
                body = body?.let { JSONObject(it.toString()) },
            ),
        )
    }

    fun requests(matchingPath: String) =
        recorded.filter { it.url.path.endsWith(matchingPath) || it.url.path == matchingPath }
}

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class FlinkuReferralLifecycleTests {
    private lateinit var context: Context
    private lateinit var store: MockKeyValueStore
    private lateinit var network: MockNetworkClient
    private val logs = mutableListOf<String>()

    private val projectId = "proj_test_1"
    private val apiKey = "flk_pk_test_key"
    private val baseUrl = "https://yourapp.flku.dev"

    @Before
    fun setUp() {
        Flinku.resetForTesting()
        context = ApplicationProvider.getApplicationContext()
        store = MockKeyValueStore()
        network = MockNetworkClient()
        logs.clear()
        Flinku.store = store
        Flinku.network = network
        Flinku.logSink = { logs.add(it) }
    }

    @After
    fun tearDown() {
        Flinku.resetForTesting()
    }

    @Test
    fun testMatchWithReferrerIdWritesPendingReferral() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)

        val link = Flinku.match(context)

        assertTrue(link.matched)
        val pending = store.pendingReferralJSON(projectId)
        assertNotNull("Expected pending referral record for project $projectId", pending)
        assertEquals("user_referrer_1", pending!!.getString("referrerId"))
        assertEquals("Alice", pending.getString("referrerLabel"))
        assertEquals("link_abc", pending.getString("linkId"))
        assertTrue(pending.has("matchedAt"))
        assertEquals(projectId, store.getString("flinku_referral_project_id"))
    }

    @Test
    fun testMatchWithoutReferrerIdDoesNotWritePending() = runBlocking {
        stubMatchJSON(
            JSONObject().apply {
                put("matched", true)
                put("deepLink", "myapp://home")
                put("slug", "plain-slug")
                put("linkId", "link_plain")
                put("projectId", projectId)
                put("params", JSONObject().put("campaign", "summer"))
            },
        )
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)

        val link = Flinku.match(context)

        assertTrue(link.matched)
        assertFalse(store.hasPendingReferral(projectId))
        assertNull(store.getString("flinku_referral_project_id"))
    }

    @Test
    fun testResetKeepsPendingReferral() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)
        assertTrue(store.hasPendingReferral(projectId))
        assertTrue(store.getBoolean("flinku_matched"))

        Flinku.reset(context)

        assertFalse(store.getBoolean("flinku_matched"))
        assertNull(store.getString("flinku_match_result"))
        val pending = store.pendingReferralJSON(projectId)
        assertNotNull("Pending referral must survive reset()", pending)
        assertEquals("user_referrer_1", pending!!.getString("referrerId"))
        assertEquals("Alice", pending.getString("referrerLabel"))
        assertEquals("link_abc", pending.getString("linkId"))
    }

    @Test
    fun testSetUserIdPostsTrackWithCorrectBodyAndBearer() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        network.taskResponses["/api/referrals/track"] = Triple(null, 200, null)
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)

        Flinku.setUserId(context, "new_user_42")

        val tracks = network.requests("/api/referrals/track")
        assertEquals(1, tracks.size)
        val req = tracks[0]
        assertEquals("POST", req.method)
        assertEquals("flku.dev", req.url.host)
        assertEquals("Bearer $apiKey", req.headers["Authorization"])
        assertEquals(projectId, req.body!!.getString("projectId"))
        assertEquals("user_referrer_1", req.body.getString("referrerId"))
        assertEquals("new_user_42", req.body.getString("newUserId"))
        assertEquals("Alice", req.body.getString("referrerLabel"))
        assertEquals("link_abc", req.body.getString("linkId"))
    }

    @Test
    fun testSetUserIdSuccessSetsTrackedFlagAndClearsPending() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        network.taskResponses["/api/referrals/track"] = Triple(null, 201, null)
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)
        assertTrue(store.hasPendingReferral(projectId))

        Flinku.setUserId(context, "new_user_42")

        assertTrue(store.getBoolean("referral_tracked_${projectId}_new_user_42"))
        assertFalse(store.hasPendingReferral(projectId))
    }

    @Test
    fun testSetUserIdNetworkFailureKeepsPending() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        network.failAllTasks = true
        network.taskError = java.net.SocketTimeoutException("timeout")
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)

        Flinku.setUserId(context, "new_user_42")

        assertEquals(1, network.requests("/api/referrals/track").size)
        assertTrue(store.hasPendingReferral(projectId))
        assertFalse(store.getBoolean("referral_tracked_${projectId}_new_user_42"))
    }

    @Test
    fun testSetUserIdTwiceAfterSuccessPostsOnlyOnce() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        network.taskResponses["/api/referrals/track"] = Triple(null, 200, null)
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)

        Flinku.setUserId(context, "new_user_42")
        assertEquals(1, network.requests("/api/referrals/track").size)
        assertFalse(store.hasPendingReferral(projectId))

        Flinku.setUserId(context, "new_user_42")
        assertEquals(1, network.requests("/api/referrals/track").size)
    }

    @Test
    fun testSetUserIdWithoutApiKeySkipsNetworkAndWarnsOnce() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        Flinku.configure(context, baseUrl, apiKey = null, readClipboard = false)
        Flinku.match(context)
        assertTrue(store.hasPendingReferral(projectId))

        Flinku.setUserId(context, "new_user_42")
        Flinku.setUserId(context, "new_user_42")

        assertTrue(network.requests("/api/referrals/track").isEmpty())
        assertTrue(store.hasPendingReferral(projectId))
        assertEquals(1, logs.count { it.contains("no apiKey configured") })
        assertEquals("new_user_42", store.getString("flinku_user_id"))
    }

    @Test
    fun testConfigureWithStoredUserIdRetriesTrack() {
        writePending(
            projectId,
            "user_referrer_1",
            "Alice",
            "link_abc",
            System.currentTimeMillis() / 1000.0,
        )
        store.putString("flinku_user_id", "returning_user")
        network.taskResponses["/api/referrals/track"] = Triple(null, 200, null)

        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)

        val tracks = network.requests("/api/referrals/track")
        assertEquals(1, tracks.size)
        assertEquals("returning_user", tracks[0].body!!.getString("newUserId"))
        assertEquals("user_referrer_1", tracks[0].body!!.getString("referrerId"))
        assertEquals("Bearer $apiKey", tracks[0].headers["Authorization"])
        assertTrue(store.getBoolean("referral_tracked_${projectId}_returning_user"))
        assertFalse(store.hasPendingReferral(projectId))
    }

    @Test
    fun testStalePendingReferralIsDroppedWithoutPost() {
        val thirtyOneDaysAgo = System.currentTimeMillis() / 1000.0 - (31 * 24 * 60 * 60)
        writePending(projectId, "user_referrer_1", "Alice", "link_abc", thirtyOneDaysAgo)
        store.putString("flinku_user_id", "returning_user")
        network.taskResponses["/api/referrals/track"] = Triple(null, 200, null)

        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)

        assertTrue(network.requests("/api/referrals/track").isEmpty())
        assertFalse(store.hasPendingReferral(projectId))
        assertFalse(store.getBoolean("referral_tracked_${projectId}_returning_user"))
    }

    @Test
    fun testQualifyReferralPostsCorrectBody() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        network.taskResponses["/api/referrals/track"] = Triple(null, 200, null)
        network.taskResponses["/api/referrals/qualify"] = Triple(null, 200, null)
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)
        Flinku.setUserId(context, "new_user_42")

        Flinku.qualifyReferral(context, "first_meal_logged")

        val qualifies = network.requests("/api/referrals/qualify")
        assertEquals(1, qualifies.size)
        val req = qualifies[0]
        assertEquals("POST", req.method)
        assertEquals("/api/referrals/qualify", req.url.path)
        assertEquals("Bearer $apiKey", req.headers["Authorization"])
        assertEquals(projectId, req.body!!.getString("projectId"))
        assertEquals("new_user_42", req.body.getString("newUserId"))
        assertEquals("first_meal_logged", req.body.getString("event"))
    }

    @Test
    fun testQualifyReferralWithoutUserIdDoesNotCallNetwork() = runBlocking {
        stubMatchJSON(matchedWithReferrerJSON())
        network.taskResponses["/api/referrals/qualify"] = Triple(null, 200, null)
        Flinku.configure(context, baseUrl, apiKey, readClipboard = false)
        Flinku.match(context)
        assertNull(store.getString("flinku_user_id"))

        Flinku.qualifyReferral(context, "first_meal_logged")

        assertTrue(network.requests("/api/referrals/qualify").isEmpty())
    }

    private fun stubMatchJSON(json: JSONObject) {
        network.asyncResponses["/api/match"] = json to 200
    }

    private fun matchedWithReferrerJSON(): JSONObject {
        return JSONObject().apply {
            put("matched", true)
            put("deepLink", "myapp://home")
            put("slug", "ref-slug")
            put("linkId", "link_abc")
            put("subdomain", "yourapp")
            put("title", "Invite")
            put("projectId", projectId)
            put("matchType", "fingerprint")
            put(
                "params",
                JSONObject().apply {
                    put("referrerId", "user_referrer_1")
                    put("referrerLabel", "Alice")
                },
            )
        }
    }

    private fun writePending(
        projectId: String,
        referrerId: String,
        referrerLabel: String,
        linkId: String,
        matchedAt: Double,
    ) {
        val value = JSONObject().apply {
            put("referrerId", referrerId)
            put("referrerLabel", referrerLabel)
            put("linkId", linkId)
            put("matchedAt", matchedAt)
        }
        store.putString("flinku_pending_referral_$projectId", value.toString())
        store.putString("flinku_referral_project_id", projectId)
    }
}
