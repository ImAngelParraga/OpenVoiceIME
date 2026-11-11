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
        val listener = ShortcutTestAccessibilityService()
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
    fun testV57V59V60HardwareShortcutsRespectReturnAndHideActions() {
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
            ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
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
                    focusHiddenEditor(scenario)
                    shortcut()
                    await("visible recording round $round") {
                        SystemImeTestService.instance?.let { it.testRecorder.active && it.isInputViewShown } == true
                    }
                    val service = SystemImeTestService.instance!!
                    val recorder = service.testRecorder
                    val starts = recorder.starts
                    val file = recorder.file!!
                    shortcut(repeatCount = 2)
                    main {
                        previousService?.let { HardwareDictation.hidden(it) }
                        assertTrue(HardwareDictation.owns(service))
                        assertTrue(service.isInputViewShown)
                        assertTrue(service.onEvaluateInputViewShown())
                        assertEquals(starts, recorder.starts)
                    }
                    assertEquals("0", getSetting("show_ime_with_hard_keyboard"))
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
                    assertEquals("0", getSetting("show_ime_with_hard_keyboard"))
                    if (round == 3) {
                        await { !service.isInputViewShown }
                        assertSame(service, SystemImeTestService.instance)
                        main { assertFalse(HardwareDictation.owns(service)) }
                    } else {
                        await { SystemImeTestService.instance == null }
                    }
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
        } finally {
            main { HardwareDictation.finish() }
            shell("ime set $previous")
            saved.forEach { (key, value) ->
                shell(if (value == "null") "settings delete secure $key" else "settings put secure $key $value")
            }
        }
    }

    private fun focusHiddenEditor(scenario: ActivityScenario<IntegrationHostActivity>) {
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
            host.editor.requestFocus()
        }
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
        fail("Hardware dictation did not reach $message ${diagnostic()}")
    }
}
