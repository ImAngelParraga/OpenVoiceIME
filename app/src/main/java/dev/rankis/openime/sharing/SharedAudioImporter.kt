package dev.rankis.openime.sharing

import dev.rankis.openime.audio.AudioFormat
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class SharedAudioException(val reason: Reason) : Exception() {
    enum class Reason { INVALID, TOO_LARGE, UNREADABLE }
}

data class SharedAudio(val file: File, val durationMillis: Long)

class SharedAudioImporter(private val context: Context) {
    companion object { const val MAX_BYTES = 25L * 1024 * 1024 }

    suspend fun import(uri: Uri): SharedAudio {
        var pendingFile: File? = null
        return try {
            withContext(Dispatchers.IO) {
                if (uri.scheme != "content") throw SharedAudioException(SharedAudioException.Reason.INVALID)
                val directory = File(context.cacheDir, "shared-transcription").apply { mkdirs() }
                directory.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 86_400_000L }
                    ?.forEach { it.delete() }
                var owned = File.createTempFile("audio-", ".part", directory)
                pendingFile = owned
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw SharedAudioException(SharedAudioException.Reason.UNREADABLE)
                stream.use { input ->
                    owned.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_BYTES) throw SharedAudioException(SharedAudioException.Reason.TOO_LARGE)
                            output.write(buffer, 0, count)
                        }
                    }
                }
                val header = owned.inputStream().use { input ->
                    val bytes = ByteArray(16)
                    bytes.copyOf(input.read(bytes).coerceAtLeast(0))
                }
                val format = AudioFormat.detect(header)
                    ?: throw SharedAudioException(SharedAudioException.Reason.INVALID)
                val target = File(directory, "${owned.nameWithoutExtension}.${format.extension}")
                check(owned.renameTo(target))
                owned = target
                pendingFile = target
                val retriever = MediaMetadataRetriever()
                val duration = try {
                    retriever.setDataSource(owned.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                        ?.takeIf { it > 0 } ?: throw SharedAudioException(SharedAudioException.Reason.INVALID)
                } finally { retriever.release() }
                coroutineContext.ensureActive()
                SharedAudio(owned, duration)
            }
        } catch (error: Throwable) {
            pendingFile?.delete()
            throw error
        }
    }
}
