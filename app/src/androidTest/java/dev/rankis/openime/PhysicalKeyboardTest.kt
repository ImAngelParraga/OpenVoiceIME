package dev.rankis.openime

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in check using the installed keyboard, its real microphone and physical shortcuts. */
@RunWith(AndroidJUnit4::class)
class PhysicalKeyboardTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val rounds = JSONArray()
    private var currentRound = JSONObject()
    private val report by lazy { File(context.filesDir, "physical-keyboard-check.json") }

    @Test
    fun installedKeyboardRecordsInsertsAndHidesWithoutEnter() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("physical_keyboard") == "true")
        instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val keyboard = ComponentName("dev.rankis.openime", "dev.rankis.openime.RemoteSttInputMethodService")
        val enabled = context.getSystemService(InputMethodManager::class.java).enabledInputMethodList
        assertTrue("Release voice keyboard must be enabled", enabled.any { it.component == keyboard })
        val services = context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        assertTrue("Release shortcut service must be enabled", services.any { it.resolveInfo.serviceInfo.packageName == keyboard.packageName })
        var enterEvents = 0
        var editorActions = 0
        var instructions: TextView? = null
        try {
            ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                await("landscape test editor", 10_000) {
                    var ready = false
                    scenario.onActivity {
                        ready = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                            it.window.decorView.width > it.window.decorView.height && it.editor.hasWindowFocus()
                    }
                    ready
                }
                scenario.onActivity { host ->
                    (host.editor.parent as ViewGroup).removeView(host.editor)
                    val layout = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
                    instructions = TextView(host).apply { textSize = 20f; setPadding(24, 16, 24, 16) }
                    layout.addView(instructions)
                    layout.addView(host.editor, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                    ViewCompat.setOnApplyWindowInsetsListener(layout) { view, insets ->
                        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                        insets
                    }
                    host.setContentView(layout)
                    ViewCompat.requestApplyInsets(layout)
                    host.editor.setOnKeyListener { _, code, _ ->
                        if (code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) enterEvents++
                        false
                    }
                    host.editor.setOnEditorActionListener { _, _, _ -> editorActions++; false }
                }
                val firstRound = InstrumentationRegistry.getArguments().getString("start_round")?.toIntOrNull() ?: 1
                require(firstRound in 1..3)
                for (round in firstRound..3) {
                    currentRound = JSONObject().put("round", round)
                    var beforeLength = 0
                    scenario.onActivity { host ->
                        beforeLength = host.editor.length()
                        host.editor.setSelection(beforeLength)
                        val phrase = listOf("prueba uno", "prueba dos", "prueba tres")[round - 1]
                        instructions!!.text = "Test $round/3: Ctrl+Shift+Space → say ‘$phrase’ → Ctrl+Shift+Enter."
                        host.editor.hint = "Your test transcription appears here"
                        host.editor.requestFocus()
                    }
                    await("served editor", 10_000) {
                        var ready = false
                        scenario.onActivity {
                            ready = it.editor.hasWindowFocus() && it.getSystemService(InputMethodManager::class.java).isActive(it.editor)
                        }
                        ready
                    }
                    assertFalse("Panel must be hidden before the open chord", visible(scenario))
                    publish("ready", round)
                    await("physical open shortcut", 600_000) { visible(scenario) }
                    val visibleAt = SystemClock.uptimeMillis()
                    val visibleElapsed = SystemClock.elapsedRealtime()
                    val visibleWall = System.currentTimeMillis()
                    currentRound.put("visible_uptime_ms", visibleAt).put("visible_elapsed_ms", visibleElapsed)
                        .put("visible_wall_ms", visibleWall)
                    publish("recording", round)
                    var insertedAt = 0L
                    var afterLength = beforeLength
                    await("transcript insertion and physical finish", 120_000) {
                        scenario.onActivity { afterLength = it.editor.length() }
                        if (afterLength > beforeLength && insertedAt == 0L) insertedAt = SystemClock.uptimeMillis()
                        afterLength > beforeLength && !visible(scenario)
                    }
                    val hiddenAt = SystemClock.uptimeMillis()
                    currentRound.put("inserted_uptime_ms", insertedAt).put("hidden_uptime_ms", hiddenAt)
                        .put("inserted_characters", afterLength - beforeLength)
                    publish("closing", round)
                    await("closing callback restores keyboard preference", 5_000) {
                        Settings.Secure.getInt(context.contentResolver, "show_ime_with_hard_keyboard", 0) == 0 &&
                            !visible(scenario)
                    }
                    assertEquals("Finish must keep the release voice keyboard selected", keyboard,
                        ComponentName.unflattenFromString(Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)))
                    assertEquals("Hidden-keyboard preference must be restored", 0,
                        Settings.Secure.getInt(context.contentResolver, "show_ime_with_hard_keyboard", 0))
                    assertEquals("Enter must never reach the editor", 0, enterEvents)
                    assertEquals("No editor action may be submitted", 0, editorActions)
                    currentRound.put("restored_uptime_ms", SystemClock.uptimeMillis())
                    rounds.put(currentRound)
                    publish("round_complete", round)
                }
            }
            publish("complete", 3)
        } catch (failure: Throwable) {
            publish("failed", currentRound.optInt("round", 1))
            throw failure
        }
    }

    private fun visible(scenario: ActivityScenario<IntegrationHostActivity>): Boolean {
        var shown = false
        scenario.onActivity {
            shown = ViewCompat.getRootWindowInsets(it.editor)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return shown
    }

    private fun publish(phase: String, round: Int) {
        report.writeText(JSONObject().put("phase", phase).put("round", round)
            .put("uptime_ms", SystemClock.uptimeMillis()).put("current", currentRound).put("rounds", rounds).toString())
    }

    private fun await(message: String, timeout: Long, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(50)
        }
        fail("Timed out waiting for $message")
    }
}
