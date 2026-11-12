package dev.rankis.openime.audio

/** Determine upload metadata from the bytes rather than a sender-supplied filename. */
enum class AudioFormat(val extension: String, val mimeType: String) {
    OGG("ogg", "audio/ogg"), MP4("m4a", "audio/mp4"), WAV("wav", "audio/wav"),
    MP3("mp3", "audio/mpeg"), FLAC("flac", "audio/flac"), WEBM("webm", "audio/webm"),
    AAC("aac", "audio/aac"), AMR("amr", "audio/amr");

    companion object {
        fun detect(bytes: ByteArray): AudioFormat? {
            fun matches(offset: Int, text: String) = bytes.size >= offset + text.length &&
                text.indices.all { bytes[offset + it].toInt() and 255 == text[it].code }
            return when {
                matches(0, "OggS") -> OGG
                matches(4, "ftyp") -> MP4
                matches(0, "RIFF") && matches(8, "WAVE") -> WAV
                matches(0, "fLaC") -> FLAC
                matches(0, "#!AMR") -> AMR
                matches(0, "ID3") -> MP3
                bytes.size >= 4 && bytes.take(4).map { it.toInt() and 255 } == listOf(0x1a, 0x45, 0xdf, 0xa3) -> WEBM
                bytes.size >= 2 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 0xf6 == 0xf0 -> AAC
                bytes.size >= 2 && bytes[0].toInt() and 255 == 255 && bytes[1].toInt() and 0xe0 == 0xe0 -> MP3
                else -> null
            }
        }

        fun mimeTypeFor(filename: String): String = entries.firstOrNull {
            filename.substringAfterLast('.', "").lowercase() == it.extension
        }?.mimeType ?: "audio/mp4"
    }
}
