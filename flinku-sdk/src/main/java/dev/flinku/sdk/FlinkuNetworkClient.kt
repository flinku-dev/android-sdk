package dev.flinku.sdk

import org.json.JSONObject

/** HTTP transport used by Flinku (defaults to [FlinkuHttp] implementation). */
interface FlinkuNetworkClient {
    fun match(config: FlinkuConfig, body: JSONObject): FlinkuLink

    fun postReferral(
        apiBaseUrl: String,
        path: String,
        body: JSONObject,
        apiKey: String,
        timeoutMs: Long,
        onComplete: (success: Boolean) -> Unit,
    )
}

internal class DefaultFlinkuNetworkClient : FlinkuNetworkClient {
    override fun match(config: FlinkuConfig, body: JSONObject): FlinkuLink {
        return FlinkuHttp.matchWithBody(config, body)
    }

    override fun postReferral(
        apiBaseUrl: String,
        path: String,
        body: JSONObject,
        apiKey: String,
        timeoutMs: Long,
        onComplete: (success: Boolean) -> Unit,
    ) {
        try {
            FlinkuHttp.postAuthorizedJson(apiBaseUrl, path, body, apiKey, timeoutMs)
            onComplete(true)
        } catch (e: Exception) {
            onComplete(false)
        }
    }
}
