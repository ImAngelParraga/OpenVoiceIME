package dev.rankis.openime

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ShareFixtureProvider : ContentProvider() {
    override fun onCreate() = true
    override fun getType(uri: Uri) = if (uri.lastPathSegment?.endsWith("ogg") == true) "audio/ogg" else "audio/wav"
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = File(requireNotNull(context).cacheDir, "share-fixture-${uri.lastPathSegment}")
        when (uri.lastPathSegment) {
            "voice.ogg" -> requireNotNull(context).assets.open("share-fixture.ogg").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            "invalid" -> file.writeText("not an audio file")
            "oversized" -> java.io.RandomAccessFile(file, "rw").use { it.setLength(26L * 1024 * 1024) }
            else -> {
                val sampleBytes = 16000 * 2
                val data = ByteBuffer.allocate(44 + sampleBytes).order(ByteOrder.LITTLE_ENDIAN)
                    .put("RIFF".toByteArray()).putInt(36 + sampleBytes).put("WAVEfmt ".toByteArray())
                    .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
                    .putShort(2).putShort(16).put("data".toByteArray()).putInt(sampleBytes).array()
                file.writeBytes(data)
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).also { file.delete() }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
