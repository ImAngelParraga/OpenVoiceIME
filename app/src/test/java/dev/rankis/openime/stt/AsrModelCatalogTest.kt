package dev.rankis.openime.stt

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrModelCatalogTest {
    @Test
    fun requestUsesSelectedEndpointAndBearerToken() {
        val request = buildAsrModelCatalogRequest("https://asr.example.test/", "test-token")

        assertEquals("GET", request.method)
        assertEquals("https://asr.example.test/v1/audio/models", request.url.toString())
        assertEquals("Bearer test-token", request.header("Authorization"))
    }

    @Test
    fun parserAcceptsFutureIdsAndRemovesDuplicates() {
        val ids = parseAsrModelIds(
            """{"object":"list","data":[{"id":"whisper-1"},{"id":"future-asr-4b"},{"id":"future-asr-4b"},{"id":" "}]}""",
        )

        assertEquals(listOf("whisper-1", "future-asr-4b"), ids)
    }

    @Test
    fun fetchUsesAdvertisedIdsWithoutClientSideModelNames() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"data":[{"id":"future-asr-4b"}]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()

        val result = AsrModelCatalog(client).fetch("https://asr.example.test", "test-token")

        assertEquals(listOf("future-asr-4b"), result.getOrThrow())
    }

    @Test
    fun fetchFailureDoesNotExposeServerBody() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(403)
                .message("Forbidden")
                .body("secret-token denied".toResponseBody())
                .build()
        }.build()

        val result = AsrModelCatalog(client).fetch("https://asr.example.test", "secret-token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("403"))
        assertFalse(result.exceptionOrNull()?.message.orEmpty().contains("secret-token"))
    }
}
