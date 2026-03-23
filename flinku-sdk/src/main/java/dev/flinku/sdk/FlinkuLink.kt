package dev.flinku.sdk

import org.json.JSONObject

data class FlinkuLink(
    val matched: Boolean,
    val deepLink: String? = null,
    val params: Map<String, Any>? = null,
    val slug: String? = null
) {
    companion object {
        val noMatch = FlinkuLink(matched = false)

        fun fromJson(json: JSONObject): FlinkuLink {
            val matched = json.optBoolean("matched", false)
            val deepLink = json.optString("deepLink").takeIf { it.isNotEmpty() }
            val slug = json.optString("slug").takeIf { it.isNotEmpty() }
            val paramsJson = json.optJSONObject("params")
            val params = paramsJson?.let {
                val map = mutableMapOf<String, Any>()
                it.keys().forEach { key -> map[key] = it.get(key) }
                map
            }
            return FlinkuLink(matched = matched, deepLink = deepLink, params = params, slug = slug)
        }
    }
}
