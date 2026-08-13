package dev.rankis.openime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RemoteSttInputMethodServiceSourceTest {
    @Test
    fun testV25ConfigurationRebuildCleansBeforeRecreatingView() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val configurationBody = functionBody(source, "onConfigurationChanged")
        val inputViewBody = functionBody(source, "onCreateInputView")

        assertTrue(configurationBody.indexOf("cleanupForInputViewRecreation()") < configurationBody.indexOf("super.onConfigurationChanged"))
        assertTrue(inputViewBody.indexOf("cleanupForInputViewRecreation()") < inputViewBody.indexOf("LayoutInflater"))
    }

    @Test
    fun testV25CleanupInvalidatesTransientWorkRegardlessOfLogicalState() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val cleanupBody = functionBody(source, "cleanupForInputViewRecreation")
        val cancelBody = functionBody(source, "cancelCurrentWork")

        assertTrue(cleanupBody.contains("operationId += 1"))
        assertTrue(cleanupBody.contains("cancelScheduledStartRecording()"))
        assertTrue(cleanupBody.contains("uploadJob?.cancel()"))
        assertTrue(cleanupBody.contains("activeEditorGesture = null"))
        assertTrue(cleanupBody.contains("activeGestureMode = null"))
        assertTrue(cleanupBody.contains("recorder.cancel()"))
        assertTrue(cleanupBody.contains("recordingAudioFocus.abandon()"))
        assertTrue(cleanupBody.contains("state = ImeState.Idle"))
        assertFalse(cleanupBody.contains("state == ImeState.Recording"))
        assertTrue(cancelBody.contains("recorder.cancel()"))
        assertTrue(cancelBody.contains("recordingAudioFocus.abandon()"))
        assertFalse(cancelBody.contains("if (state == ImeState.Recording)"))
    }

    @Test
    fun testV25RecorderStartFailureCleansRecorderAndFileState() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "startRecordingOrShowSetupError")

        assertTrue(body.contains("recorder.cancel()"))
        assertTrue(body.contains("recordingAudioFocus.abandon()"))
        assertTrue(body.contains("audioFile = null"))
        assertTrue(body.indexOf("recorder.cancel()") < body.lastIndexOf("showError"))
    }

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
    fun testV22AutoRecordStartsAfterViewReturns() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "scheduleStartRecordingOrShowSetupError")

        assertTrue(body.contains("handler.post(scheduledStartRecording)"))
        assertFalse(body.contains("postDelayed"))
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

    @Test
    fun recordingRequestsAudioFocusBeforeStartingRecorder() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "startRecordingOrShowSetupError")

        assertTrue(body.contains("recordingAudioFocus.request()"))
        assertTrue(body.indexOf("recordingAudioFocus.request()") < body.indexOf("recorder.start()"))
        assertFalse(body.contains("AUDIOFOCUS_REQUEST_GRANTED"))
    }

    @Test
    fun recordingCleanupAbandonsAudioFocus() {
        val source = String(Files.readAllBytes(serviceSourcePath()))

        assertTrue(functionBody(source, "stopAndUpload").contains("recordingAudioFocus.abandon()"))
        assertTrue(functionBody(source, "cancelCurrentWork").contains("recordingAudioFocus.abandon()"))
        assertTrue(functionBody(source, "onFinishInputView").contains("recordingAudioFocus.abandon()"))
        assertTrue(functionBody(source, "onDestroy").contains("recordingAudioFocus.abandon()"))
        assertTrue(functionBody(source, "validateRecordingSettingsAsync").contains("recordingAudioFocus.abandon()"))
    }

    @Test
    fun cancelCanKeepPanelOpenAndStartFreshRecording() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "cancelCurrentWork")

        assertTrue(body.contains("settingsStore.load().hideAfterCancel"))
        assertTrue(body.contains("requestHideSelf(0)"))
        assertTrue(body.contains("resetControls()"))
        assertTrue(body.contains("scheduleStartRecordingOrShowSetupError()"))
    }

    @Test
    fun postInsertKeyboardReturnCanKeepPanelForFreshRecording() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "commitPendingText")

        assertTrue(body.contains("if (pendingReturnToKeyboardAfterInsert)"))
        assertTrue(body.contains("switchToNextKeyboard()"))
        assertTrue(body.contains("requestHideSelf(0)"))
        assertTrue(body.contains("resetControls()"))
        assertTrue(body.contains("scheduleStartRecordingOrShowSetupError()"))
    }

    @Test
    fun hideAfterInsertWinsWhenKeyboardReturnDisabled() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "commitPendingText")

        assertTrue(body.contains("} else if (pendingHideAfterSuccess) {"))
        assertTrue(body.contains("requestHideSelf(0)"))
    }

    @Test
    fun languageSelectionUsesCurrentEditorPackage() {
        val source = String(Files.readAllBytes(serviceSourcePath()))

        assertTrue(source.contains("override fun onStartInput("))
        assertTrue(source.contains("updateCurrentEditorPackageName(info)"))
        assertTrue(functionBody(source, "showLanguageControls").contains("currentEditorPackageName"))
        assertTrue(functionBody(source, "saveLanguageSettings").contains("currentEditorPackageName"))
        assertTrue(functionBody(source, "loadCurrentEditorSettings").contains("currentEditorPackageName"))
        assertTrue(functionBody(source, "upload").contains("loadCurrentEditorSettings()"))
    }

    @Test
    fun editControlsUseSnapshotGuardedGestures() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        val body = functionBody(source, "onCreateInputView")

        assertTrue(body.contains("cursorButton.setOnTouchListener"))
        assertTrue(body.contains("deleteButton.setOnTouchListener"))
        assertTrue(source.contains("currentEditorSnapshot()"))
        assertTrue(source.contains("EditorGestureController.deleteTap"))
        assertTrue(source.contains("inputConnection.commitText(\"\", 1)"))
        assertTrue(source.contains("applyGesturePreview"))
        assertTrue(source.contains("restoreActiveGesture"))
        assertTrue(source.contains("deleteButton.performClick()"))
        assertTrue(source.contains("current.text != active.expectedText"))
        assertTrue(source.contains("current.selection != active.previewSelection"))
    }

    @Test
    fun terminalEditorsUseKeyEventFallback() {
        val source = String(Files.readAllBytes(serviceSourcePath()))
        assertTrue(source.contains("usesTerminalFallback"))
        assertTrue(source.contains("InputType.TYPE_NULL"))
        assertTrue(source.contains("KEYCODE_DPAD_LEFT"))
        assertTrue(source.contains("KEYCODE_DPAD_RIGHT"))
        assertTrue(source.contains("KEYCODE_DEL"))
        assertTrue(source.contains("sendDownUpKeyEvents"))
        assertTrue(source.contains("extracted.startOffset"))
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
