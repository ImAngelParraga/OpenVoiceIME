package dev.rankis.openime.sharing

import dev.rankis.openime.audio.AudioFormat
import org.junit.Assert.*
import org.junit.Test

class AudioFormatTest {
    @Test fun detectsCommonMessengerContainersAndUploadMimeTypes() {
        assertEquals(AudioFormat.OGG, AudioFormat.detect("OggSmore bytes".toByteArray()))
        assertEquals(AudioFormat.MP4, AudioFormat.detect(byteArrayOf(0,0,0,24) + "ftypM4A ".toByteArray()))
        assertEquals(AudioFormat.WAV, AudioFormat.detect("RIFF0000WAVE".toByteArray()))
        assertEquals(AudioFormat.MP3, AudioFormat.detect("ID3".toByteArray()))
        assertEquals("audio/ogg", AudioFormat.mimeTypeFor("import.ogg"))
        assertEquals("audio/mp4", AudioFormat.mimeTypeFor("recording.m4a"))
        assertEquals("audio/wav", AudioFormat.mimeTypeFor("import.WAV"))
    }
    @Test fun rejectsEmptyTextAndOtherRiffData() {
        assertNull(AudioFormat.detect(byteArrayOf()))
        assertNull(AudioFormat.detect("not audio".toByteArray()))
        assertNull(AudioFormat.detect("RIFF0000AVI ".toByteArray()))
    }
}
