package dev.flinku.sdk

data class FlinkuConfig(
    val apiKey: String,
    val baseUrl: String = "http://159.65.159.159:3001",
    val debugMode: Boolean = false,
    val matchTimeoutSeconds: Long = 10L
)
