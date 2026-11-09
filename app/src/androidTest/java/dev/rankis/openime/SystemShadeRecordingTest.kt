package dev.rankis.openime

import android.Manifest
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.SettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemShadeRecordingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun testV55QuickSettingsKeepsSameRecordingUntilKeyboardIsDismissed() {
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        val settings = AppSettings(apiToken = "integration-test")
        SettingsStore(context).apply { save(settings); markConnectionTestSucceeded(settings) }
        val previous = shell("settings get secure default_input_method").trim()
        val enabled = shell("settings get secure enabled_input_methods").trim()
        val component = "${context.packageName}/dev.rankis.openime.SystemImeTestService"
        try {
            ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
                shell("ime enable $component")
                shell("ime set $component")
                scenario.onActivity { host ->
                    host.editor.requestFocus()
                    host.getSystemService(InputMethodManager::class.java)
                        .showSoftInput(host.editor, InputMethodManager.SHOW_IMPLICIT)
                }
                await { SystemImeTestService.instance?.testRecorder?.active == true }
                val service = SystemImeTestService.instance!!
                val recorder = service.testRecorder
                val file = recorder.file!!
                repeat(2) {
                    var previousTimer = ""
                    main { previousTimer = service.window!!.window!!.decorView.findViewById<TextView>(R.id.timerText).text.toString() }
                    shell("cmd statusbar expand-settings")
                    SystemClock.sleep(700)
                    main { assertTrue(recorder.active); assertTrue(file.exists()) }
                    shell("cmd statusbar collapse")
                    SystemClock.sleep(700)
                    main {
                        assertTrue(recorder.active)
                        assertEquals(1, recorder.starts)
                        assertSame(file, recorder.file)
                        assertTrue(file.exists())
                        val timer = service.window!!.window!!.decorView.findViewById<TextView>(R.id.timerText).text.toString()
                        assertTrue("Recording timer must continue through Quick Settings", timer > previousTimer)
                    }
                }
                main { service.requestHideSelf(0) }
                await { !recorder.active }
                assertFalse(file.exists())
            }
        } finally {
            shell("cmd statusbar collapse")
            shell("ime set $previous")
            shell("settings put secure enabled_input_methods $enabled")
        }
    }

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
        }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            main { satisfied = condition() }
            if (satisfied) return
            SystemClock.sleep(50)
        }
        fail("IME did not reach the expected recorder state")
    }
}
