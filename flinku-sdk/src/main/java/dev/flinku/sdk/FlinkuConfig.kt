package dev.flinku.sdk

data class FlinkuConfig(
    val baseUrl: String,
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
                val host = java.net.URI(baseUrl).host ?: return ""
                val parts = host.split(".")
                if (parts.size >= 3) parts.first() else host
            } catch (e: Exception) {
                ""
            }
        }
}
