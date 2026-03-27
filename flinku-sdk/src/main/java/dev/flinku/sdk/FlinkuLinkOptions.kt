package dev.flinku.sdk

data class FlinkuLinkOptions(
    val title: String,
    val deepLink: String? = null,
    val params: Map<String, String>? = null,
    val slug: String? = null,
    val desktopUrl: String? = null,
    val utmSource: String? = null,
    val utmMedium: String? = null,
    val utmCampaign: String? = null,
    val utmContent: String? = null,
    val utmTerm: String? = null,
    val expiresAt: String? = null,
    val maxClicks: Int? = null,
    val password: String? = null,
    val ogTitle: String? = null,
    val ogDescription: String? = null,
    val ogImageUrl: String? = null,
)
