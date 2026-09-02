package dev.flinku.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class CreateLinkInstantTests {
    private lateinit var context: Context
    private val apiKey = "flk_pk_test"
    private val baseUrl = "https://yourapp.flku.dev"

    @Before
    fun setUp() {
        Flinku.resetForTesting()
        context = ApplicationProvider.getApplicationContext()
        Flinku.configure(context, baseUrl, apiKey, debug = true)
    }

    @After
    fun tearDown() {
        Flinku.resetForTesting()
    }

    @Test
    fun retries500ThenSucceeds() = runBlocking {
        val calls = AtomicInteger(0)
        FlinkuHttp.postAuthorizedJsonInterceptor = { _, _, _, _, _ ->
            if (calls.incrementAndGet() == 1) {
                throw FlinkuHttpException("server error", 500)
            }
            JSONObject().put("id", "1")
        }

        Flinku.createLinkInstant(FlinkuLinkOptions(title = "Test", deepLink = "app://x"))
        delay(2500)
        assertEquals(2, calls.get())
    }

    @Test
    fun doesNotRetry403() = runBlocking {
        val calls = AtomicInteger(0)
        FlinkuHttp.postAuthorizedJsonInterceptor = { _, _, _, _, _ ->
            calls.incrementAndGet()
            throw FlinkuHttpException("Forbidden", 403)
        }

        Flinku.createLinkInstant(FlinkuLinkOptions(title = "Test", deepLink = "app://x"))
        delay(500)
        assertEquals(1, calls.get())
    }

    @Test
    fun doesNotRetry409() = runBlocking {
        val calls = AtomicInteger(0)
        FlinkuHttp.postAuthorizedJsonInterceptor = { _, _, _, _, _ ->
            calls.incrementAndGet()
            throw FlinkuHttpException("Slug already in use", 409)
        }

        Flinku.createLinkInstant(FlinkuLinkOptions(title = "Test", deepLink = "app://x"))
        delay(500)
        assertEquals(1, calls.get())
    }
}
