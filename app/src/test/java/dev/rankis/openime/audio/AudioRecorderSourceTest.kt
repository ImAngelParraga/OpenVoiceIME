package dev.rankis.openime.audio

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class AudioRecorderSourceTest {
    @Test
    fun testV34FailedStopDiscardsIncompleteFileInsteadOfUploadingIt() {
        val body = functionBody(String(Files.readAllBytes(sourcePath())), "stop")
        assertTrue(body.contains("val stopped = runCatching { mediaRecorder.stop() }.isSuccess"))
        assertTrue(body.indexOf("releaseRecorder(mediaRecorder)") < body.indexOf("if (!stopped)"))
        assertTrue(body.substringAfter("if (!stopped)").contains("file?.delete()"))
        assertTrue(body.substringAfter("if (!stopped)").contains("return null"))
    }

    @Test
    fun teardownDetachesRecorderStateBeforeMediaRecorderCalls() {
        val source = String(Files.readAllBytes(sourcePath()))

        val stop = functionBody(source, "stop")
        assertTrue(stop.indexOf("recorder = null") < stop.indexOf("mediaRecorder.stop()"))
        assertTrue(stop.indexOf("currentFile = null") < stop.indexOf("mediaRecorder.stop()"))

        val discard = functionBody(source, "discard")
        assertTrue(discard.indexOf("recorder = null") < discard.indexOf("stopAndRelease"))
        assertTrue(discard.indexOf("currentFile = null") < discard.indexOf("stopAndRelease"))
    }

    @Test
    fun keyboardReturnReleasesRecorderOffMainAndRestartWaitsForRelease() {
        val source = String(Files.readAllBytes(sourcePath()))
        val discard = functionBody(source, "discard")
        val start = functionBody(source, "start")
        val waitForRelease = start.indexOf("pendingKeyboardReturnRelease?.get()")

        assertTrue(source.contains("fun cancelForKeyboardReturn() = discard(releaseInBackground = true)"))
        assertTrue(discard.contains("pendingKeyboardReturnRelease = recorderReleaseExecutor.submit"))
        assertTrue(waitForRelease >= 0 && waitForRelease < start.indexOf("File.createTempFile"))
    }

    @Test
    fun startReleasesConstructedRecorderAndTemporaryFileOnFailure() {
        val source = String(Files.readAllBytes(sourcePath()))
        val body = functionBody(source, "start")

        assertTrue(body.contains("var mediaRecorder: MediaRecorder? = null"))
        assertTrue(body.contains("mediaRecorder = if"))
        assertTrue(body.contains("mediaRecorder?.let(::releaseRecorder)"))
        assertTrue(body.contains("runCatching { file.delete() }"))
    }

    private fun sourcePath(): Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val relativePath = Paths.get(
            "src",
            "main",
            "java",
            "dev",
            "rankis",
            "openime",
            "audio",
            "AudioRecorder.kt",
        )
        val modulePath = userDir.resolve(relativePath)
        if (Files.exists(modulePath)) {
            return modulePath
        }
        return userDir.resolve("app").resolve(relativePath)
    }

    private fun functionBody(source: String, functionName: String): String {
        val signatureStart = source.indexOf("fun $functionName(")
        require(signatureStart >= 0) { "Missing function $functionName" }
        val bodyStart = source.indexOf('{', signatureStart)
        require(bodyStart >= 0) { "Missing body for $functionName" }

        var depth = 0
        for (index in bodyStart until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(bodyStart + 1, index)
                    }
                }
            }
        }
        error("Unclosed body for $functionName")
    }
}
