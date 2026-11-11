package dev.rankis.openime

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.SettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareDictationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test
    fun testV60PendingHandoffConsumesEnterDownRepeatsAndUpWithoutForwarding() = main {
        val listener = ShortcutTestAccessibilityService().apply { attachForTest(instrumentation.targetContext) }
        var selected: String? = "typing/ime"
        try {
            HardwareDictation.request("voice/ime", selected, { selected }, {}) { selected = it; true }
            val modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
            for (code in listOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                assertFalse(listener.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0)))
                assertTrue(listener.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, modifiers)))
                assertTrue(listener.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 1)))
                assertTrue(listener.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0)))
                assertFalse(listener.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0)))
            }
            HardwareDictation.finish()
            assertFalse(listener.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER, 0, modifiers)))
        } finally {
            HardwareDictation.finish()
        }
    }

    @Test
    fun testV58MissingEditorExpiresAndRestoresPriorKeyboard() {
        var selected: String? = "typing/ime"
        var failures = 0
        val switches = mutableListOf<String>()
        try {
            main {
                HardwareDictation.request("voice/ime", selected, { selected }, { failures++ }) {
                    selected = it
                    switches += it
                    true
                }
            }
            await { failures == 1 }
            main {
                assertEquals("typing/ime", selected)
                assertEquals(listOf("voice/ime", "typing/ime"), switches)
            }
        } finally {
            main { HardwareDictation.finish() }
        }
    }

    @Test
    fun testV58ManualKeyboardChangeIsNotOverridden() {
        main {
            var selected: String? = "typing/ime"
            val switches = mutableListOf<String>()
            try {
                HardwareDictation.request("voice/ime", selected, { selected }, {}) {
                    selected = it
                    switches += it
                    true
                }
                selected = "other/ime"
                assertFalse(HardwareDictation.finish())
                assertEquals(listOf("voice/ime"), switches)
                assertEquals("other/ime", selected)
            } finally {
                HardwareDictation.finish()
            }
        }
    }

    @Test
    fun testV57V59V60HardwareShortcutsRespectReturnAndHideActions() = exerciseShortcuts()

    @Test
    fun testV62LandscapeNormalEditorKeepsDictationPanelBoundedAndVisible() =
        exerciseShortcuts(normalEditor = true, landscape = true)

    @Test
    fun testSettingsReturnAndAccessibilityRestartCanOpenRecordingAgain() =
        withShortcutKeyboard(normalEditor = true, landscape = true) { scenario, _, _ ->
            val context = instrumentation.targetContext
            val store = SettingsStore(context)
            val settings = store.load().copy(baseUrl = "http://127.0.0.1:1")
            store.save(settings)
            focusHiddenEditor(scenario)
            shortcut()
            await("visible connection-test error") {
                SystemImeTestService.instance?.let { service ->
                    service.isInputViewShown && service.window!!.window!!.decorView
                        .findViewById<android.widget.EditText>(R.id.errorDetailsInput).text.toString() ==
                        context.getString(R.string.validation_connection_test_required)
                } == true
            }
            main {
                SystemImeTestService.instance!!.window!!.window!!.decorView
                    .findViewById<android.widget.ImageButton>(R.id.settingsButton).performClick()
            }
            var settingsActivity: dev.rankis.openime.settings.SettingsActivity? = null
            await("settings activity") {
                settingsActivity = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                    .filterIsInstance<dev.rankis.openime.settings.SettingsActivity>().firstOrNull()
                settingsActivity != null
            }
            main {
                SystemImeTestService.instance?.let {
                    assertFalse(HardwareDictation.owns(it))
                    assertFalse(it.testRecorder.active)
                }
                store.markConnectionTestSucceeded(settings)
                settingsActivity!!.finish()
            }
            await("landscape editor after settings closes") {
                var ready = false
                scenario.onActivity { host ->
                    host.editor.requestFocus()
                    ready = settingsActivity!!.isDestroyed &&
                        host.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
                        host.window.decorView.width > host.window.decorView.height && host.editor.hasWindowFocus()
                }
                ready
            }
            val animations = focusHiddenEditor(scenario)
            shortcut()
            await("recording after connection check") {
                SystemImeTestService.instance?.let { it.testRecorder.active && it.isInputViewShown } == true &&
                    hostImeVisible(scenario, true) && animations.isEmpty()
            }
            val enabledServices = getSetting("enabled_accessibility_services")
            val listener = "${context.packageName}/dev.rankis.openime.ShortcutTestAccessibilityService"
            val others = enabledServices.split(":").filter { it != listener }.joinToString(":")
            shell(if (others.isEmpty()) "settings delete secure enabled_accessibility_services"
                else "settings put secure enabled_accessibility_services $others")
            await("shortcut service stopped") {
                ShortcutTestAccessibilityService.instance == null && SystemImeTestService.instance == null &&
                    hostImeVisible(scenario, false) && animations.isEmpty()
            }
            assertEquals("0", getSetting("show_ime_with_hard_keyboard"))
            shell("settings put secure enabled_accessibility_services $enabledServices")
            await("shortcut service reconnected") { ShortcutTestAccessibilityService.instance != null }
            focusHiddenEditor(scenario)
            shortcut()
            await("recording after shortcut service restart") {
                SystemImeTestService.instance?.let { it.testRecorder.active && it.isInputViewShown } == true
            }
            main {
                assertEquals(1, SystemImeTestService.instance!!.testRecorder.starts)
                assertEquals(0, SystemImeTestService.instance!!.testRecorder.stops)
            }
        }

    @Test
    fun testV64AlreadySelectedVoiceKeyboardReopensAndRestoresHiddenPreference() = exerciseRepeatedFinishAndHide()

    @Test
    fun testV65DroppedShowCallbackRecoversVisiblePanelAndCanFinishWithoutEnter() =
        exerciseRepeatedFinishAndHide(dropRepeatedShow = true)

    private fun exerciseRepeatedFinishAndHide(dropRepeatedShow: Boolean = false) =
        withShortcutKeyboard(normalEditor = true, landscape = true) { scenario, ime, _ ->
            focusHiddenEditor(scenario)
            org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
            val animations = trackImeAnimations(scenario)
            repeat(2) { round ->
                if (round == 1 && dropRepeatedShow) main {
                    SystemImeTestService.instance!!.dropShowCallbacks = true
                }
                shortcut()
                await("visible recording with hardware visibility override") {
                    SystemImeTestService.instance?.let { it.testRecorder.active && it.isInputViewShown } == true
                }
                await("settled recording panel") {
                    hostImeVisible(scenario, true) && animations.isEmpty()
                }
                main {
                    val service = SystemImeTestService.instance!!
                    val panel = service.window!!.window!!.decorView.findViewById<android.view.View>(R.id.imePanel)
                    assertFalse(service.isFullscreenMode)
                    assertTrue(panel.isShown)
                    assertEquals(Math.round(260 * service.resources.displayMetrics.density), panel.height)
                    assertEquals(round + 1, service.testRecorder.starts)
                }
                assertEquals("1", getSetting("show_ime_with_hard_keyboard"))
                shortcut(code = KeyEvent.KEYCODE_ENTER)
                await("panel closed and hidden keyboard preference restored", {
                    "animations=${animations.size}"
                }) {
                    SystemImeTestService.instance?.isInputViewShown == false &&
                        getSetting("show_ime_with_hard_keyboard") == "0" && animations.isEmpty() &&
                        hostImeVisible(scenario, false)
                }
                assertEquals(ime, getSetting("default_input_method"))
            }
            if (dropRepeatedShow) {
                assertTrue(ShortcutDiagnostics.report(instrumentation.targetContext).contains("direct-window fallback"))
            }
            shortcut(code = KeyEvent.KEYCODE_ENTER)
            shortcut(code = KeyEvent.KEYCODE_NUMPAD_ENTER)
            scenario.onActivity { assertEquals("shortcut dictationshortcut dictation", it.editor.text.toString()) }
        }

    @Test
    fun testV64ShowTimeoutReleasesTheHardwareVisibilityAllowance() =
        withShortcutKeyboard { scenario, ime, _ ->
            org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
            focusHiddenEditor(scenario)
            shell("ime set $ime")
            await("voice keyboard bound") { SystemImeTestService.instance != null }
            main {
                SystemImeTestService.instance!!.apply {
                    dropShowCallbacks = true
                    blockWindow = true
                }
            }
            shortcut()
            await("temporary hardware visibility allowance") { getSetting("show_ime_with_hard_keyboard") == "1" }
            await("timeout restores hardware visibility preference") { getSetting("show_ime_with_hard_keyboard") == "0" }
            main {
                val service = SystemImeTestService.instance!!
                assertFalse(HardwareDictation.owns(service))
                assertFalse(service.testRecorder.active)
                assertEquals(1, service.windowShowCalls)
            }
        }

    @Test
    fun testV65ManualKeyboardSwitchCancelsPendingWindowFallback() = exerciseCancelledFallback()

    @Test
    fun testV65NewEditorCancelsPendingWindowFallback() = exerciseCancelledFallback(replaceEditor = true)

    private fun exerciseCancelledFallback(replaceEditor: Boolean = false) =
        withShortcutKeyboard { scenario, ime, previous ->
            org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
            focusHiddenEditor(scenario)
            shell("ime set $ime")
            await("voice editor bound") {
                SystemImeTestService.instance?.let { it.currentInputStarted && it.currentInputConnection != null } == true
            }
            val service = SystemImeTestService.instance!!
            main { service.dropShowCallbacks = true }
            shortcut()
            val afterDeadline = SystemClock.elapsedRealtime() + 3_500
            await("pending show allowance") { getSetting("show_ime_with_hard_keyboard") == "1" }
            if (replaceEditor) {
                ActivityScenario.launch(IntegrationHostActivity::class.java).use {
                    await("replacement editor cancels opening") {
                        !HardwareDictation.owns(service) && getSetting("show_ime_with_hard_keyboard") == "0"
                    }
                    await("cancelled opening deadline passed") { SystemClock.elapsedRealtime() >= afterDeadline }
                }
            } else {
                shell("ime set $previous")
                await("manual keyboard restored") {
                    SystemImeTestService.instance == null && getSetting("show_ime_with_hard_keyboard") == "0"
                }
                await("cancelled opening deadline passed") { SystemClock.elapsedRealtime() >= afterDeadline }
            }
            main {
                assertEquals(0, service.windowShowCalls)
                assertFalse(service.testRecorder.active)
                assertFalse(HardwareDictation.owns(service))
            }
            assertEquals(if (replaceEditor) ime else previous, getSetting("default_input_method"))
        }

    @Test
    fun testV64ExistingVisibleKeyboardPreferenceSurvivesSession() =
        withShortcutKeyboard { scenario, _, _ ->
            org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 36)
            shell("settings put secure show_ime_with_hard_keyboard 1")
            focusHiddenEditor(scenario)
            shortcut()
            await("recording") { SystemImeTestService.instance?.testRecorder?.active == true }
            main {
                SystemImeTestService.instance!!.window!!.window!!.decorView
                    .findViewById<Button>(R.id.cancelButton).performClick()
            }
            await("voice keyboard released") { SystemImeTestService.instance == null }
            assertEquals("1", getSetting("show_ime_with_hard_keyboard"))
        }

    private fun exerciseShortcuts(normalEditor: Boolean = false, landscape: Boolean = false) =
        withShortcutKeyboard(normalEditor, landscape) { scenario, ime, previous ->
            main {
                val service = ShortcutTestAccessibilityService.instance!!
                val info = service.serviceInfo
                assertEquals(0, info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT)
                assertTrue(info.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0)
                fun key(code: Int, modifiers: Int, repeat: Int = 0) = KeyEvent(
                    0, 0, KeyEvent.ACTION_DOWN, code, repeat, modifiers,
                )
                val modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_A, modifiers)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_SPACE, KeyEvent.META_CTRL_ON)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_SPACE, modifiers or KeyEvent.META_ALT_ON)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_SPACE, modifiers or KeyEvent.META_META_ON)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_SPACE, modifiers, 1)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_ENTER, modifiers or KeyEvent.META_ALT_ON)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_ENTER, modifiers or KeyEvent.META_META_ON)))
                assertFalse(service.dispatch(key(KeyEvent.KEYCODE_ENTER, modifiers, 1)))
            }
            var previousService: SystemImeTestService? = null
            repeat(4) { round ->
                if (round == 3) scenario.onActivity { it.editor.setText("") }
                val animations = focusHiddenEditor(scenario)
                shortcut()
                await("visible recording round $round") {
                    SystemImeTestService.instance?.let {
                        it.testRecorder.active && it.isInputViewShown && HardwareDictation.owns(it)
                    } == true && hostImeVisible(scenario, true) && animations.isEmpty()
                }
                val service = SystemImeTestService.instance!!
                val recorder = service.testRecorder
                val starts = recorder.starts
                val file = recorder.file!!
                shortcut(repeatCount = 2)
                main {
                    previousService?.let { HardwareDictation.hidden(it) }
                    assertFalse("Dictation must stay out of fullscreen", service.isFullscreenMode)
                    assertTrue(ShortcutDiagnostics.report(instrumentation.targetContext), HardwareDictation.owns(service))
                    assertTrue(service.isInputViewShown)
                    assertTrue(service.onEvaluateInputViewShown())
                    assertEquals(starts, recorder.starts)
                }
                assertEquals(if (android.os.Build.VERSION.SDK_INT >= 36) "1" else "0",
                    getSetting("show_ime_with_hard_keyboard"))
                scenario.onActivity { assertEquals("", it.editor.text.toString()) }
                main {
                    val view = service.window!!.window!!.decorView
                    when (round) {
                        0 -> view.findViewById<Button>(R.id.cancelButton).performClick()
                        1 -> view.findViewById<android.widget.ImageButton>(R.id.keyboardButton).performClick()
                        2 -> view.findViewById<Button>(R.id.stopButton).performClick()
                        else -> Unit
                    }
                }
                if (round == 3) shortcut(repeatCount = 2, code = KeyEvent.KEYCODE_ENTER)
                val expectedIme = if (round == 3) ime else previous
                await { !recorder.active && getSetting("default_input_method") == expectedIme }
                assertFalse(file.exists())
                if (round == 3) {
                    await { !service.isInputViewShown }
                    assertSame(service, SystemImeTestService.instance)
                    main { assertFalse(HardwareDictation.owns(service)) }
                } else {
                    await { SystemImeTestService.instance == null }
                }
                assertEquals("0", getSetting("show_ime_with_hard_keyboard"))
                previousService = service
            }
            scenario.onActivity {
                assertEquals("shortcut dictation", it.editor.text.toString())
            }
            shell("input text hardware")
            var actualText = ""
            await("resumed typing", { "editor text: $actualText" }) {
                scenario.onActivity { actualText = it.editor.text.toString() }
                actualText == "shortcut dictationhardware"
            }
        }

    private fun withShortcutKeyboard(
        normalEditor: Boolean = false,
        landscape: Boolean = false,
        block: (ActivityScenario<IntegrationHostActivity>, String, String) -> Unit,
    ) {
        val context = instrumentation.targetContext
        automation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val settings = AppSettings(apiToken = "integration-test", selectInsertedText = false)
        SettingsStore(context).apply { save(settings); markConnectionTestSucceeded(settings) }
        val saved = listOf("default_input_method", "enabled_input_methods", "enabled_accessibility_services",
            "accessibility_enabled", "show_ime_with_hard_keyboard").associateWith { getSetting(it) }
        val previous = saved.getValue("default_input_method")
        val ime = "${context.packageName}/dev.rankis.openime.SystemImeTestService"
        val listener = "${context.packageName}/dev.rankis.openime.ShortcutTestAccessibilityService"
        try {
            shell("ime enable $ime")
            shell("settings put secure show_ime_with_hard_keyboard 0")
            val services = saved.getValue("enabled_accessibility_services").takeUnless { it == "null" }.orEmpty()
            shell("settings put secure enabled_accessibility_services ${listOf(services, listener).filter { it.isNotBlank() }.joinToString(":")}")
            shell("settings put secure accessibility_enabled 1")
            await { ShortcutTestAccessibilityService.instance != null }
            val intent = android.content.Intent(context, IntegrationHostActivity::class.java)
                .putExtra("normal_editor", normalEditor)
            ActivityScenario.launch<IntegrationHostActivity>(intent).use { scenario ->
                if (landscape) {
                    scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    await("landscape editor") {
                        var ready = false
                        scenario.onActivity { ready = it.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
                        ready
                    }
                }
                block(scenario, ime, previous)
            }
        } finally {
            main { HardwareDictation.finish() }
            shell("ime set $previous")
            saved.forEach { (key, value) ->
                shell(if (value == "null") "settings delete secure $key" else "settings put secure $key $value")
            }
        }
    }

    private fun trackImeAnimations(scenario: ActivityScenario<IntegrationHostActivity>): Set<WindowInsetsAnimationCompat> {
        val animations = mutableSetOf<WindowInsetsAnimationCompat>()
        scenario.onActivity { host ->
            ViewCompat.setWindowInsetsAnimationCallback(host.window.decorView,
                object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                        if (animation.typeMask and WindowInsetsCompat.Type.ime() != 0) {
                            animations += animation
                        }
                    }
                    override fun onProgress(
                        insets: WindowInsetsCompat,
                        runningAnimations: MutableList<WindowInsetsAnimationCompat>,
                    ) = insets
                    override fun onEnd(animation: WindowInsetsAnimationCompat) {
                        animations -= animation
                    }
                })
        }
        return animations
    }

    private fun hostImeVisible(scenario: ActivityScenario<IntegrationHostActivity>, visible: Boolean): Boolean {
        var matches = false
        scenario.onActivity {
            matches = ViewCompat.getRootWindowInsets(it.editor)?.isVisible(WindowInsetsCompat.Type.ime()) == visible
        }
        return matches
    }

    private fun focusHiddenEditor(scenario: ActivityScenario<IntegrationHostActivity>): Set<WindowInsetsAnimationCompat> {
        val animations = trackImeAnimations(scenario)
        scenario.onActivity { it.editor.requestFocus() }
        await("focused editor served by the typing keyboard") {
            var ready = false
            scenario.onActivity { host ->
                ready = host.editor.hasWindowFocus() &&
                    host.getSystemService(InputMethodManager::class.java).isActive(host.editor)
            }
            ready
        }
        scenario.onActivity { host ->
            host.getSystemService(InputMethodManager::class.java)
                .showSoftInput(host.editor, InputMethodManager.SHOW_IMPLICIT)
        }
        fun awaitVisibility(visible: Boolean) = await("settled keyboard visibility $visible") {
            var settled = false
            scenario.onActivity {
                settled = ViewCompat.getRootWindowInsets(it.editor)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == visible && animations.isEmpty()
            }
            settled
        }
        awaitVisibility(true)
        scenario.onActivity { host -> host.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(host.editor.windowToken, 0) }
        awaitVisibility(false)
        instrumentation.waitForIdleSync()
        automation.waitForIdle(1_000, 5_000)
        return animations
    }

    private fun shortcut(repeatCount: Int = 0, code: Int = KeyEvent.KEYCODE_SPACE) = main {
        val listener = ShortcutTestAccessibilityService.instance!!
        val now = SystemClock.uptimeMillis()
        val modifiers = KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON
        assertTrue(listener.dispatch(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, modifiers)))
        repeat(repeatCount) { index ->
            assertTrue(listener.dispatch(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, index + 1, modifiers)))
        }
        assertTrue(listener.dispatch(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0)))
        assertFalse(listener.dispatch(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0)))
    }

    private fun getSetting(key: String) = shell("settings get secure $key").trim()

    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(message: String = "expected state", diagnostic: () -> String = { "" }, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 8_000
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            main { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        fail("Hardware dictation did not reach $message ${diagnostic()}\n${ShortcutDiagnostics.report(instrumentation.targetContext)}")
    }
}
