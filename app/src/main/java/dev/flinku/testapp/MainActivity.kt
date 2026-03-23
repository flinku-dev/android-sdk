package dev.flinku.testapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.flinku.sdk.Flinku
import dev.flinku.sdk.FlinkuConfig
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val resultText = findViewById<TextView>(R.id.resultText)
        val resetButton = findViewById<Button>(R.id.resetButton)
        val matchButton = findViewById<Button>(R.id.matchButton)

        if (BuildConfig.FLINKU_API_KEY.isEmpty()) {
            statusText.text =
                "SDK Status: Missing API key\nAdd flinku.api.key to local.properties (see local.properties.example)"
            resultText.text = "Configure local.properties before testing."
        } else {
            Flinku.configure(
                this,
                FlinkuConfig(
                    apiKey = BuildConfig.FLINKU_API_KEY,
                    baseUrl = BuildConfig.FLINKU_BASE_URL,
                    debugMode = true
                )
            )
            statusText.text = "SDK Status: Connected\nServer: ${BuildConfig.FLINKU_BASE_URL}"
        }

        matchButton.setOnClickListener {
            if (BuildConfig.FLINKU_API_KEY.isEmpty()) {
                resultText.text = "Set flinku.api.key in local.properties first."
                return@setOnClickListener
            }
            lifecycleScope.launch {
                statusText.text = "Checking for deep link..."
                val link = Flinku.match()
                if (link.matched) {
                    resultText.text =
                        "✅ Deep Link Matched!\n\nDeep Link: ${link.deepLink}\nSlug: ${link.slug}\nParams: ${link.params}"
                } else {
                    resultText.text =
                        "❌ No match found.\n\nClick a Flinku link in your browser first, then tap Match again."
                }
                statusText.text = "SDK Status: Connected"
            }
        }

        resetButton.setOnClickListener {
            Flinku.resetForTesting()
            resultText.text = "SDK state reset. You can test again."
        }
    }
}
