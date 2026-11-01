package dev.rankis.openime

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.stt.OpenAiCompatibleProvider
import dev.rankis.openime.stt.TranscriptionError
import dev.rankis.openime.stt.TranscriptionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class TranscriptionAndroidTest {
    @Test
    fun testV30AndroidBodyTimeoutRetainsRecordingAndAllowsRetry() = runBlocking {
        withServer { server, file, client ->
            val provider = OpenAiCompatibleProvider(client)
            val settings = AppSettings(baseUrl = server.url("/").toString(), apiToken = "local-test")
            server.enqueue(MockResponse().setBody("""{"text":"late"}""").setBodyDelay(1, TimeUnit.SECONDS))
            assertEquals(TranscriptionResult.Failure(TranscriptionError.Timeout), withTimeout(3_000L) { provider.transcribe(file, settings, 500L) })
            assertTrue(file.exists())
            server.enqueue(MockResponse().setBody("""{"text":"recovered"}"""))
            assertEquals(TranscriptionResult.Success("recovered"), provider.transcribe(file, settings, 2_000L))
        }
    }

    @Test
    fun testV30AndroidCancellationDuringBodyReadCancelsHttp() = runBlocking {
        val headers = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean(false)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun responseHeadersEnd(call: Call, response: Response) { headers.complete(Unit) }
            override fun canceled(call: Call) { cancelled.set(true) }
        }).build()
        withServer(client) { server, file, http ->
            server.enqueue(MockResponse().setBody("""{"text":"late"}""").setBodyDelay(1, TimeUnit.SECONDS))
            val provider = OpenAiCompatibleProvider(http)
            val settings = AppSettings(baseUrl = server.url("/").toString(), apiToken = "local-test")
            val job = async { provider.transcribe(file, settings, 10_000L) }
            withTimeout(3_000L) { headers.await() }
            withTimeout(2_000L) { job.cancelAndJoin() }
            assertTrue(cancelled.get())
            assertTrue(file.exists())
        }
    }

    private suspend fun withServer(
        client: OkHttpClient = OkHttpClient(),
        test: suspend (MockWebServer, File, OkHttpClient) -> Unit,
    ) {
        val file = File.createTempFile("network-test-", ".m4a", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        file.writeBytes(byteArrayOf(1, 2, 3))
        val server = MockWebServer()
        try { server.start(); test(server, file, client) } finally {
            file.delete(); server.shutdown(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
    }
}
