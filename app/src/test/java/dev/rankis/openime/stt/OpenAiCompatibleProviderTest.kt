package dev.rankis.openime.stt

import dev.rankis.openime.settings.AppSettings
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class OpenAiCompatibleProviderTest {
    @Test
    fun parsesTranscriptText() {
        assertEquals("hello", parseTranscriptText("""{"text":"hello"}"""))
    }

    @Test
    fun missingTranscriptTextReturnsNull() {
        assertNull(parseTranscriptText("""{"value":"hello"}"""))
    }

    @Test
    fun malformedTranscriptJsonReturnsNull() {
        assertNull(parseTranscriptText("not json"))
    }

    @Test
    fun missingTranscriptTextMapsToParseError() {
        assertEquals(
            TranscriptionResult.Failure(TranscriptionError.Parse("missing text field")),
            parseTranscriptionResponse("""{"value":"hello"}"""),
        )
    }

    @Test
    fun blankTranscriptTextMapsToEmptyTranscript() {
        assertEquals(
            TranscriptionResult.Failure(TranscriptionError.EmptyTranscript),
            parseTranscriptionResponse("""{"text":"  "}"""),
        )
    }

    @Test
    fun successTranscriptTextMapsToSuccess() {
        assertEquals(
            TranscriptionResult.Success("hello"),
            parseTranscriptionResponse("""{"text":"hello"}"""),
        )
    }

    @Test
    fun transcriptionRequestUsesConfiguredOpenAiCompatibleFields() {
        val file = tempAudioFile()
        val settings = AppSettings(
            baseUrl = "https://api.example.test/",
            model = "gpt-4o-transcribe",
            apiToken = "secret-token",
            transcriptionLanguageCode = "pt",
        )

        val request = buildOpenAiTranscriptionRequest(file, "audio/mp4", settings)

        assertEquals("https://api.example.test/v1/audio/transcriptions", request.url.toString())
        assertEquals("Bearer secret-token", request.header("Authorization"))
        request.multipartText().let { body ->
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("gpt-4o-transcribe"))
            assertTrue(body.contains("name=\"language\""))
            assertTrue(body.contains("pt"))
            assertTrue(body.contains("name=\"temperature\""))
            assertTrue(body.contains("0.0"))
            assertTrue(body.contains("name=\"file\"; filename=\"${file.name}\""))
        }
    }

    @Test
    fun diagnosticRequestUsesConfiguredModelAndEndpoint() {
        val request = buildOpenAiDiagnosticRequest(
            AppSettings(
                baseUrl = "https://api.groq.com/openai",
                model = "whisper-large-v3-turbo",
                apiToken = "groq-token",
            ),
        )

        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", request.url.toString())
        assertEquals("Bearer groq-token", request.header("Authorization"))
        request.multipartText().let { body ->
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("whisper-large-v3-turbo"))
            assertTrue(body.contains("name=\"file\"; filename=\"openime-probe.wav\""))
        }
    }

    @Test
    fun mapsAuthErrors() {
        assertEquals(TranscriptionError.Unauthorized, mapHttpCode(401))
        assertEquals(TranscriptionError.Unauthorized, mapHttpCode(403))
    }

    @Test
    fun mapsBusyError() {
        assertEquals(TranscriptionError.ServerBusy, mapHttpCode(503))
    }

    @Test
    fun mapsGenericHttpError() {
        assertEquals(TranscriptionError.Http(500), mapHttpCode(500))
    }

    @Test
    fun mapsGenericHttpErrorWithServerBody() {
        assertEquals(
            "Server returned HTTP 500: decoder failed",
            mapHttpResponse(500, "  decoder\nfailed  ").userMessage,
        )
    }

    @Test
    fun testV1MapsUnknownHostToActionableError() {
        assertEquals(
            "Cannot resolve server hostname",
            UnknownHostException("asr.local").toTranscriptionError().userMessage,
        )
    }

    @Test
    fun testV1MapsConnectionFailuresToActionableErrors() {
        assertEquals(
            "Cannot connect to server",
            ConnectException("failed to connect").toTranscriptionError().userMessage,
        )
        assertEquals(
            "Server did not respond",
            SocketTimeoutException("timeout").toTranscriptionError().userMessage,
        )
    }

    private fun tempAudioFile(): File {
        return File.createTempFile("openime-test", ".m4a").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
    }

    private fun Request.multipartText(): String {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return buffer.readUtf8()
    }
}
