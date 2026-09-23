package dev.rankis.openime.stt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AsrModelCatalog(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
) {
    suspend fun fetch(baseUrl: String, token: String): Result<List<String>> = withContext(Dispatchers.IO) {
        runCatching {
            val request = buildAsrModelCatalogRequest(baseUrl, token)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Model list returned HTTP ${response.code}")
                }
                val models = parseAsrModelIds(response.body?.string().orEmpty())
                if (models.isEmpty()) throw IOException("Server returned no transcription models")
                models
            }
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
