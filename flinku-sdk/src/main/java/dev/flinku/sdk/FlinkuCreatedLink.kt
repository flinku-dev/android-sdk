package dev.flinku.sdk

data class FlinkuCreatedLink(
    val id: String,
    val slug: String,
    val shortUrl: String,
    val deepLink: String?,
    val params: Map<String, String>?,
)
