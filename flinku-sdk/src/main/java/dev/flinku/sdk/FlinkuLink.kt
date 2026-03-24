package dev.flinku.sdk

import org.json.JSONObject

data class FlinkuLink(
    val matched: Boolean,
    val deepLink: String? = null,
    val slug: String? = null,
    val subdomain: String? = null,
    val title: String? = null,
    val params: Map<String, Any>? = null,
    val clickedAt: String? = null,
    val projectId: String? = null
) {
    companion object {
        val notMatched = FlinkuLink(matched = false)

        fun fromJson(json: JSONObject): FlinkuLink {
            val params = if (json.has("params") && !json.isNull("params")) {
                val paramsObj = json.getJSONObject("params")
                val map = mutableMapOf<String, Any>()
                paramsObj.keys().forEach { key ->
                    map[key] = paramsObj.get(key)
                }
                map
            } else null

            return FlinkuLink(
                matched = json.optBoolean("matched", false),
                deepLink = json.optString("deepLink").takeIf { it.isNotEmpty() },
                slug = json.optString("slug").takeIf { it.isNotEmpty() },
                subdomain = json.optString("subdomain").takeIf { it.isNotEmpty() },
                title = json.optString("title").takeIf { it.isNotEmpty() },
                params = params,
                clickedAt = json.optString("clickedAt").takeIf { it.isNotEmpty() },
                projectId = json.optString("projectId").takeIf { it.isNotEmpty() }
            )
        }
    }
}
