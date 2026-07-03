package dev.rankis.openime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RemoteSttInputMethodServiceSourceTest {
    @Test
    fun testV21OnCreateInputViewDoesNotStartRecorderInline() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "onCreateInputView")

        assertTrue(body.contains("scheduleStartRecordingOrShowSetupError()"))
        assertTrue(body.indexOf("showLanguageControls()") < body.indexOf("scheduleStartRecordingOrShowSetupError()"))
        assertFalse(
            Regex("""(?m)^\s*startRecordingOrShowSetupError\(\)""").containsMatchIn(body),
        )
    }

    @Test
    fun testV22AutoRecordStartsAfterGracePeriod() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "scheduleStartRecordingOrShowSetupError")

        assertTrue(source.contains("const val AUTO_RECORD_DELAY_MILLIS = 800L"))
        assertTrue(body.contains("handler.postDelayed(scheduledStartRecording, AUTO_RECORD_DELAY_MILLIS)"))
    }

    @Test
    fun testV22LanguageMenuPausesOnlyPendingRecordingStart() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "showLanguageMenu")

        assertTrue(body.contains("if (shouldStartFreshRecording())"))
        assertTrue(body.contains("cancelScheduledStartRecording()"))
        assertTrue(body.contains("menu.setOnDismissListener"))
        assertTrue(body.contains("scheduleStartRecordingIfFresh()"))
        assertFalse(body.contains("recorder.cancel()"))
    }

    private fun serviceSourcePath(): Path {
        val userDir = Paths.get(System.getProperty("user.dir"))
        val relativePath = Paths.get(
            "src",
            "main",
            "java",
            "dev",
            "rankis",
            "openime",
            "RemoteSttInputMethodService.kt",
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
