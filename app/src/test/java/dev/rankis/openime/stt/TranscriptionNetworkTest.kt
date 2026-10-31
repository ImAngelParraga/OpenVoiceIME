package dev.rankis.openime.stt

import dev.rankis.openime.settings.AppSettings
import kotlinx.coroutines.CancellationException
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
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TranscriptionNetworkTest {
    @Test
    fun testV30StalledServerTimesOutAndSameRecordingCanBeRetried() = runBlocking {
        withServer { server, file, provider ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(
                TranscriptionResult.Failure(TranscriptionError.Timeout),
                withTimeout(5_000L) { provider.transcribe(file, settings(server), 300L) },
            )
            assertTrue(file.exists())
            server.enqueue(MockResponse().setBody("""{"text":"recovered"}"""))
            assertEquals(
                TranscriptionResult.Success("recovered"),
                provider.transcribe(file, settings(server), 2_000L),
            )
        }
    }

    @Test
    fun testV30DeadlineIncludesSlowResponseBody() = runBlocking {
        withServer { server, file, provider ->
            server.enqueue(MockResponse().setBody("""{"text":"hello"}""").setBodyDelay(2L, TimeUnit.SECONDS))
            assertEquals(
                TranscriptionResult.Failure(TranscriptionError.Timeout),
                withTimeout(5_000L) { provider.transcribe(file, settings(server), 350L) },
            )
        }
    }

    @Test
    fun testV30CancelStopsHttpCallWithoutReturningNetworkFailure() = runBlocking {
        val cancelled = AtomicBoolean(false)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.set(true) }
        }).build()
        withServer(client) { server, file, provider ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job = async { provider.transcribe(file, settings(server), 10_000L) }
            val request = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                server.takeRequest(2L, TimeUnit.SECONDS)
            }
            assertTrue(request != null)
            withTimeout(2_000L) { job.cancelAndJoin() }
            assertTrue(cancelled.get())
            try {
                job.await()
                error("Cancellation must propagate")
            } catch (_: CancellationException) { }
        }
    }

    @Test
    fun testV30CancelDuringBodyReadClosesHttpCall() = runBlocking {
        val headersReceived = CompletableDeferred<Unit>()
        val cancelled = AtomicBoolean(false)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun responseHeadersEnd(call: Call, response: Response) { headersReceived.complete(Unit) }
            override fun canceled(call: Call) { cancelled.set(true) }
        }).build()
        withServer(client) { server, file, provider ->
            server.enqueue(MockResponse().setBody("""{"text":"hello"}""").setBodyDelay(2L, TimeUnit.SECONDS))
            val job = async { provider.transcribe(file, settings(server), 10_000L) }
            withTimeout(2_000L) { headersReceived.await() }
            withTimeout(2_000L) { job.cancelAndJoin() }
            assertTrue(cancelled.get())
            assertTrue(file.exists())
        }
    }

    @Test
    fun testV30SettingsNetworkRequestsAlsoCancelTheirCalls() = runBlocking {
        val cancelled = AtomicBoolean(false)
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.set(true) }
        }).build()
        withServer(client) { server, _, _ ->
            val requests: List<suspend () -> Any> = listOf(
                { NetworkDiagnostics(client).test(settings(server)) },
                { AsrModelCatalog(client).fetch(server.url("/").toString(), "test-token") },
            )
            requests.forEach { request ->
                cancelled.set(false)
                server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
                val job = async { request() }
                val received = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    server.takeRequest(2L, TimeUnit.SECONDS)
                }
                assertTrue(received != null)
                withTimeout(2_000L) { job.cancelAndJoin() }
                assertTrue(cancelled.get())
            }
        }
    }

    @Test
    fun testV30SettingsRequestsConsumeBodiesAndKeepExpectedResults() = runBlocking {
        val client = OkHttpClient()
        withServer(client) { server, _, _ ->
            server.enqueue(MockResponse().setBody("""{"text":"probe"}"""))
            assertEquals(DiagnosticResult.Success("Transcription endpoint reachable"), NetworkDiagnostics(client).test(settings(server)))
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"model-a"}]}"""))
            assertEquals(listOf("model-a"), AsrModelCatalog(client).fetch(server.url("/").toString(), "test-token").getOrThrow())
        }
    }

    @Test
    fun testV33ProviderRejectsMalformedBodyAndPreservesHttpErrors() = runBlocking {
        withServer { server, file, provider ->
            server.enqueue(MockResponse().setBody("""{"text":null}"""))
            assertEquals(
                TranscriptionResult.Failure(TranscriptionError.Parse("missing text field")),
                provider.transcribe(file, settings(server), 2_000L),
            )
            server.enqueue(MockResponse().setResponseCode(503).setBody("busy"))
            assertEquals(
                TranscriptionResult.Failure(TranscriptionError.ServerBusy),
                provider.transcribe(file, settings(server), 2_000L),
            )
        }
    }

    private fun settings(server: MockWebServer) = AppSettings(baseUrl = server.url("/").toString(), apiToken = "test-token")

    private suspend fun withServer(
        client: OkHttpClient = OkHttpClient(),
        test: suspend (MockWebServer, File, OpenAiCompatibleProvider) -> Unit,
    ) {
        val server = MockWebServer()
        val file = File.createTempFile("transcription", ".m4a").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            server.start()
            test(server, file, OpenAiCompatibleProvider(client))
        } finally {
            file.delete()
            server.shutdown()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
}
