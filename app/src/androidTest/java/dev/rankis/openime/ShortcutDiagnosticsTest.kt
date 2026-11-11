package dev.rankis.openime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.rankis.openime.settings.KeyboardSetupGuide
import dev.rankis.openime.settings.SettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShortcutDiagnosticsTest {
    @Test
    fun testV63DiagnosticsAreBoundedAndCopiedOnlyExplicitlyWithoutEditorOrCredentials() {
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
            scenario.onActivity { host ->
                val store = SettingsStore(host)
                val original = store.load()
                val token = "secret-token-must-not-be-copied"
                val address = "https://private-server-must-not-be-copied.invalid"
                val typedText = "private editor text must not be copied"
                val clipboard = host.getSystemService(ClipboardManager::class.java)
                try {
                    store.save(original.copy(apiToken = token, baseUrl = address))
                    host.editor.setText(typedText)
                    clipboard.setPrimaryClip(ClipData.newPlainText("test", "unchanged clipboard"))
                    ShortcutDiagnostics.record(host, "oldest-diagnostic-marker")
                    repeat(100) { ShortcutDiagnostics.record(host, "diagnostic-test-$it") }
                    val root = host.layoutInflater.inflate(R.layout.keyboard_setup_guide, null)
                    host.setContentView(root)
                    val guide = KeyboardSetupGuide(host, root,
                        host.getSharedPreferences("diagnostic_guide_test", Context.MODE_PRIVATE), {}, {}, {})
                    guide.refresh(true, true)
                    assertEquals("unchanged clipboard", clipboard.primaryClip!!.getItemAt(0).text.toString())
                    root.findViewById<Button>(R.id.setupCopyShortcutDiagnostics).performClick()
                    val report = clipboard.primaryClip!!.getItemAt(0).text.toString()
                    assertTrue(report.contains("OpenVoiceIME"))
                    assertTrue(report.contains("diagnostic-test-99"))
                    assertFalse(report.contains("oldest-diagnostic-marker"))
                    assertTrue(report.lineSequence().count { it.contains(" diagnostic-test-") } <= 80)
                    assertFalse(report.contains(token))
                    assertFalse(report.contains(address))
                    assertFalse(report.contains(typedText))
                } finally {
                    store.save(original)
                }
            }
        }
    }
}
