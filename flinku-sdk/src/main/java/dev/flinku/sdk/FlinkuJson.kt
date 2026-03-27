package dev.flinku.sdk

import org.json.JSONArray
import org.json.JSONObject

internal fun FlinkuLinkOptions.toJsonObject(): JSONObject {
    return JSONObject().apply {
        put("title", title)
        deepLink?.let { put("deepLink", it) }
        params?.let { p ->
            val o = JSONObject()
            p.forEach { (k, v) -> o.put(k, v) }
            put("params", o)
        }
        slug?.let { put("slug", it) }
        desktopUrl?.let { put("desktopUrl", it) }
        utmSource?.let { put("utmSource", it) }
        utmMedium?.let { put("utmMedium", it) }
        utmCampaign?.let { put("utmCampaign", it) }
        utmContent?.let { put("utmContent", it) }
        utmTerm?.let { put("utmTerm", it) }
        expiresAt?.let { put("expiresAt", it) }
        maxClicks?.let { put("maxClicks", it) }
        password?.let { put("password", it) }
        ogTitle?.let { put("ogTitle", it) }
        ogDescription?.let { put("ogDescription", it) }
        ogImageUrl?.let { put("ogImageUrl", it) }
    }
}

internal fun parseCreatedLinkResponse(json: JSONObject): FlinkuCreatedLink {
    val data = json.optJSONObject("link")
        ?: json.optJSONObject("data")
        ?: json.optJSONObject("created")
        ?: json
    return parseCreatedLink(data)
}

internal fun parseCreatedLink(json: JSONObject): FlinkuCreatedLink {
    val paramsObj = json.optJSONObject("params")
    val paramsMap = paramsObj?.let { obj ->
        val m = mutableMapOf<String, String>()
        obj.keys().forEach { key -> m[key] = obj.optString(key) }
        m.takeIf { it.isNotEmpty() }
    }
    val shortUrl = json.optString("shortUrl").takeIf { it.isNotEmpty() }
        ?: json.optString("short_url").takeIf { it.isNotEmpty() }
        ?: ""
    return FlinkuCreatedLink(
        id = json.getString("id"),
        slug = json.optString("slug").takeIf { it.isNotEmpty() } ?: "",
        shortUrl = shortUrl,
        deepLink = json.optString("deepLink").takeIf { it.isNotEmpty() }
            ?: json.optString("deep_link").takeIf { it.isNotEmpty() },
        params = paramsMap
    )
}

internal fun parseCreatedLinks(json: JSONObject): List<FlinkuCreatedLink> {
    val arr = json.optJSONArray("links")
        ?: json.optJSONArray("data")
        ?: json.optJSONArray("results")
    if (arr != null) {
        return (0 until arr.length()).map { parseCreatedLink(arr.getJSONObject(it)) }
    }
    return emptyList()
}

internal fun parseCreatedLinksFromBody(body: String): List<FlinkuCreatedLink> {
    val trimmed = body.trim()
    if (trimmed.startsWith("[")) {
        val arr = JSONArray(trimmed)
        return (0 until arr.length()).map { parseCreatedLink(arr.getJSONObject(it)) }
    }
    return parseCreatedLinks(JSONObject(trimmed))
}
