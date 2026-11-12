package dev.rankis.openime

import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rankis.openime.metrics.TranscriptionMetricsStore
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.ServerSettingsActivity
import dev.rankis.openime.settings.SettingsStore
import dev.rankis.openime.settings.builtInProviderOptions
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SettingsAndMetricsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun testV46ExplicitAutoLanguageOverridesLegacyLanguage() {
        val prefs = context.getSharedPreferences("openime_settings", android.content.Context.MODE_PRIVATE)
        val store = SettingsStore(context)
        prefs.edit().putString("language_choice", "SPANISH").remove("transcription_language_code").commit()
        try {
            assertEquals("es", store.loadTranscriptionLanguage().languageCode)
            store.save(store.load().copy(transcriptionLanguageCode = null))
            assertNull(store.loadTranscriptionLanguage().languageCode)
            store.saveTranscriptionLanguage("en")
            store.saveTranscriptionLanguage(null)
            assertNull(store.loadTranscriptionLanguage("new.editor").languageCode)
        } finally {
            prefs.edit().remove("language_choice").remove("transcription_language_code").commit()
        }
    }

    @Test
    fun testV47BuiltinProviderSwitchDoesNotReuseAnotherProvidersToken() = withServer { server, _ ->
        SettingsStore(context).save(AppSettings(baseUrl = server.url("/").toString(), apiToken = "not-a-real-credential"))
        ActivityScenario.launch(ServerSettingsActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> activity.applyPreset(builtInProviderOptions()[1]) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity -> assertEquals("", activity.findViewById<EditText>(R.id.tokenInput).text.toString()) }
        }
    }

    @Test
    fun testV40OpeningSettingsPreservesSavedUrlTokenAndCustomModel() = withServer { server, _ ->
        val saved = AppSettings(baseUrl = server.url("/").toString().trimEnd('/'), apiToken = "local-test-token", model = "my-custom-model")
        val store = SettingsStore(context)
        store.save(saved)
        ActivityScenario.launch(ServerSettingsActivity::class.java).use {
            instrumentation.waitForIdleSync()
            val loaded = store.load()
            assertEquals(saved.baseUrl, loaded.baseUrl)
            assertEquals(saved.model, loaded.model)
            assertEquals(saved.apiToken, loaded.apiToken)
        }
    }

    @Test
    fun testV40ConnectionChangesIgnoreAnOlderSuccessfulProbe() = withServer { server, received ->
        val store = SettingsStore(context)
        store.save(AppSettings(baseUrl = server.url("/").toString(), apiToken = "before"))
        ActivityScenario.launch(ServerSettingsActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                activity.findViewById<EditText>(R.id.baseUrlInput).setText(server.url("/").toString())
                activity.findViewById<EditText>(R.id.tokenInput).setText("before")
                activity.findViewById<Button>(R.id.testServerButton).performClick()
            }
            assertTrue(received.await(3, TimeUnit.SECONDS))
            scenario.onActivity { activity -> activity.findViewById<EditText>(R.id.tokenInput).setText("after") }
            // The server deliberately completes its old request after the form changes.
            Thread.sleep(1_300L)
            instrumentation.waitForIdleSync()
            assertFalse(store.hasCurrentConnectionTest(store.load()))
            scenario.onActivity { activity ->
                val status = activity.findViewById<android.view.View>(R.id.connectionStatusRow).findViewById<TextView>(R.id.rowTitle)
                assertEquals(activity.getString(R.string.status_not_tested), status.text.toString())
            }
        }
    }

    @Test
    fun testV41ConcurrentMetricWritersDoNotLoseCounts() {
        TranscriptionMetricsStore(context).clear()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        try {
            val writes = (0 until 100).map { index -> executor.submit {
                start.await()
                TranscriptionMetricsStore(context).record(16L, 10L, index % 2 == 0)
            } }
            start.countDown()
            writes.forEach { it.get(5, TimeUnit.SECONDS) }
            val metrics = TranscriptionMetricsStore(context).load()
            assertEquals(100L, metrics.totalCount)
            assertEquals(50L, metrics.successCount)
            assertEquals(50L, metrics.failureCount)
            assertEquals(1_600L, metrics.totalBytes)
            assertEquals(1_000L, metrics.totalDurationMillis)
            assertEquals(100L, metrics.buckets.sumOf { it.count })
        } finally { executor.shutdownNow() }
    }

    @Test
    fun testV42ReplacingNamedPresetRemovesItsOldEncryptedToken() {
        val store = SettingsStore(context)
        store.saveCustomPreset("replace-me", "https://example.test", "model", "old-secret")
        val replacement = store.saveCustomPreset("replace-me", "https://example.test", "model", "new-secret")
        val presets = store.loadCustomPresets().filter { it.displayName == "replace-me" }
        assertEquals(1, presets.size)
        assertEquals(replacement.id, presets.single().id)
        assertEquals("new-secret", presets.single().apiToken)
        @Suppress("DEPRECATION")
        val secure = androidx.security.crypto.EncryptedSharedPreferences.create(
            context, "openime_secure_settings",
            androidx.security.crypto.MasterKey.Builder(context).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build(),
            androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        assertFalse(secure.all.values.contains("old-secret"))
    }

    private fun withServer(test: (MockWebServer, CountDownLatch) -> Unit) {
        val received = CountDownLatch(1)
        val server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/v1/audio/models") return MockResponse().setBody("""{"data":[{"id":"my-custom-model"}]}""")
                    received.countDown()
                    return MockResponse().setBody("""{"text":"probe"}""").setBodyDelay(1L, TimeUnit.SECONDS)
                }
            }
            start()
        }
        try { test(server, received) } finally { server.shutdown() }
    }
}
