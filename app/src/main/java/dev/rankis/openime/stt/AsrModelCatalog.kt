package dev.rankis.openime.stt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AsrModelCatalog(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(baseUrl: String, token: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = buildAsrModelCatalogRequest(baseUrl, token)
            val models = withTimeout(10_000L) {
                val response = client.newCall(request).awaitBody()
                if (!response.isSuccessful) {
                    throw IOException("Model list returned HTTP ${response.code}")
                }
                parseAsrModelIds(response.text).also {
                    if (it.isEmpty()) throw IOException("Server returned no transcription models")
                }
            }
            Result.success(models)
        } catch (e: TimeoutCancellationException) {
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun buildAsrModelCatalogRequest(baseUrl: String, token: String): Request {
    return Request.Builder()
        .url("${baseUrl.trim().trimEnd('/')}/v1/audio/models")
        .header("Authorization", "Bearer $token")
        .get()
        .build()
}

fun parseAsrModelIds(json: String): List<String> {
    val data = JSONObject(json).getJSONArray("data")
    return (0 until data.length())
        .map { index -> data.getJSONObject(index).getString("id").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}
