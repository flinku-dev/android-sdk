package dev.flinku.sdk

import java.net.URI

data class FlinkuConfig(
    val baseUrl: String,
    val apiKey: String? = null,
    val debug: Boolean = false,
    val timeoutMs: Long = 5000L
) {
    /**
     * Extracts subdomain from baseUrl
     * e.g. https://yourapp.flku.dev → yourapp
     */
    val subdomain: String
        get() {
            return try {
                val host = URI(baseUrl).host ?: return ""
                val parts = host.split(".")
                if (parts.size >= 3) parts.first() else host
            } catch (e: Exception) {
                ""
            }
        }

    /**
     * API host URL with project subdomain stripped (for link creation APIs).
     * e.g. https://myapp.flku.dev → https://flku.dev
     */
    val apiBaseUrl: String
        get() {
            return try {
                val uri = URI(baseUrl.trim())
                val scheme = uri.scheme ?: "https"
                val host = uri.host ?: return baseUrl.trimEnd('/')
                val parts = host.split(".")
                val apiHost = if (parts.size >= 3) parts.drop(1).joinToString(".") else host
                "$scheme://$apiHost".trimEnd('/')
            } catch (e: Exception) {
                baseUrl.trimEnd('/')
            }
        }
}
