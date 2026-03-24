package dev.flinku.sdk

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal object FlinkuHttp {
    fun match(config: FlinkuConfig): FlinkuLink {
        val body = JSONObject().apply {
            put("subdomain", config.subdomain)
            put("userAgent", System.getProperty("http.agent") ?: "Android")
        }

        for (attempt in 0..1) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL("${config.baseUrl.trimEnd('/')}/api/match")
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.connectTimeout = config.timeoutMs.toInt().coerceAtLeast(1)
                connection.readTimeout = config.timeoutMs.toInt().coerceAtLeast(1)
                connection.doOutput = true

                connection.outputStream.use { os ->
                    os.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }

                if (connection.responseCode == 200) {
                    val response = BufferedReader(
                        InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)
                    ).use { it.readText() }
                    return FlinkuLink.fromJson(JSONObject(response))
                }
                return FlinkuLink.notMatched
            } catch (e: Exception) {
                if (attempt == 1) return FlinkuLink.notMatched
            } finally {
                connection?.disconnect()
            }
        }
        return FlinkuLink.notMatched
    }
}
