package dev.flinku.testapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.flinku.sdk.Flinku
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.statusText)
        val resultText = findViewById<TextView>(R.id.resultText)
        val resetButton = findViewById<Button>(R.id.resetButton)
        val matchButton = findViewById<Button>(R.id.matchButton)

        Flinku.configure(
            this,
            baseUrl = BuildConfig.FLINKU_BASE_URL,
            apiKey = BuildConfig.FLINKU_API_KEY.takeIf { it.isNotEmpty() },
            debug = true
        )
        statusText.text = "SDK Status: Connected\nProject URL: ${BuildConfig.FLINKU_BASE_URL}"

        matchButton.setOnClickListener {
            lifecycleScope.launch {
                statusText.text = "Checking for deep link..."
                val link = Flinku.match(this@MainActivity)
                if (link.matched) {
                    resultText.text =
                        "✅ Deep Link Matched!\n\nDeep Link: ${link.deepLink}\nSlug: ${link.slug}\n" +
                        "Subdomain: ${link.subdomain}\nTitle: ${link.title}\nParams: ${link.params}\n" +
                        "Project: ${link.projectId}"
                } else {
                    resultText.text =
                        "❌ No match found.\n\nClick a Flinku link in your browser first, then tap Match again."
                }
                statusText.text = "SDK Status: Connected"
            }
        }

        resetButton.setOnClickListener {
            Flinku.reset(this)
            resultText.text = "SDK state reset. You can test again."
        }
    }
}
