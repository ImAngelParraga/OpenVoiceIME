package dev.rankis.openime

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.KeyboardSetupGuide
import dev.rankis.openime.settings.SettingsActivity
import dev.rankis.openime.settings.SettingsStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class KeyboardSetupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("keyboard_setup_test", Context.MODE_PRIVATE)

    @Test
    fun testV48RoutesPersistAndOnlySelectedInstructionsAppear() {
        preferences.edit().clear().commit()
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.layoutInflater.inflate(R.layout.keyboard_setup_guide, null)
                activity.setContentView(root)
                fun create() = KeyboardSetupGuide(activity, root, preferences, {}, {}, {}, { null })
                val guide = create()
                guide.refresh(false, false)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.setupGuidePanel).visibility)
                val routes = root.findViewById<RadioGroup>(R.id.setupRouteGroup)
                routes.check(R.id.setupUseHeliboard)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.setupHeliboardPanel).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.setupCurrentKeyboardPanel).visibility)
                create().refresh(true, true)
                assertEquals(R.id.setupUseHeliboard, routes.checkedRadioButtonId)
                assertEquals(View.GONE, root.findViewById<View>(R.id.setupGuidePanel).visibility)
                routes.check(R.id.setupKeepKeyboard)
                create().refresh(false, true)
                assertEquals(R.id.setupKeepKeyboard, routes.checkedRadioButtonId)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.setupCurrentKeyboardPanel).visibility)
                assertEquals(View.GONE, root.findViewById<View>(R.id.setupHeliboardPanel).visibility)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.setupGuidePanel).visibility)
            }
        }
    }

    @Test
    fun testV49ButtonsRequestOnlyExplicitNavigationAndRecoverFromMissingHandlers() {
        preferences.edit().clear().commit()
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.layoutInflater.inflate(R.layout.keyboard_setup_guide, null)
                activity.setContentView(root)
                val requested = mutableListOf<Intent>()
                var pickerRequests = 0
                var serverRequests = 0
                var installed = false
                var failLaunch = false
                val heli = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("helium314.keyboard", "Settings"))
                val guide = KeyboardSetupGuide(activity, root, preferences, {
                    if (failLaunch) throw ActivityNotFoundException()
                    requested += it
                }, { pickerRequests++ }, { serverRequests++ }, { if (installed) heli else null })
                guide.refresh(false, false)
                root.findViewById<RadioGroup>(R.id.setupRouteGroup).check(R.id.setupUseHeliboard)
                val openHeli = root.findViewById<Button>(R.id.setupOpenHeliboard)
                assertFalse(openHeli.isEnabled)
                root.findViewById<Button>(R.id.setupDownloadHeliboard).performClick()
                assertEquals(Intent.ACTION_VIEW, requested.single().action)
                assertEquals(KeyboardSetupGuide.HELIBOARD_DOWNLOAD, requested.single().dataString)
                root.findViewById<Button>(R.id.enableImeButton).performClick()
                assertEquals(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS, requested.last().action)
                root.findViewById<Button>(R.id.setupShortcutButton).performClick()
                assertEquals(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, requested.last().action)
                assertTrue(root.findViewById<TextView>(R.id.setupShortcutStatus).text.isNotBlank())
                installed = true
                guide.refresh(false, false)
                assertTrue(openHeli.isEnabled)
                openHeli.performClick()
                assertEquals(heli.component, requested.last().component)
                root.findViewById<Button>(R.id.setupChooseKeyboard).performClick()
                assertEquals(1, pickerRequests)
                failLaunch = true
                root.findViewById<Button>(R.id.setupDownloadHeliboard).performClick()
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.setupActionStatus).visibility)
                assertEquals(activity.getString(R.string.setup_action_failed), root.findViewById<TextView>(R.id.setupActionStatus).text)
                root.findViewById<Button>(R.id.setupConfigureServer).performClick()
                assertEquals(1, serverRequests)
                assertEquals(View.GONE, root.findViewById<View>(R.id.setupGuidePanel).visibility)
            }
        }
    }

    @Test
    fun testV48SettingsRecreationPreservesRouteAndServerCredentials() {
        val store = SettingsStore(context)
        val original = store.load()
        val setup = context.getSharedPreferences(KeyboardSetupGuide.PREFERENCES, Context.MODE_PRIVATE)
        setup.edit().clear().commit()
        val fixture = AppSettings(baseUrl = "http://127.0.0.1:1", model = "fixture-model", apiToken = "fixture-token")
        store.save(fixture)
        try {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.findViewById<RadioGroup>(R.id.setupRouteGroup).check(R.id.setupKeepKeyboard)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    assertEquals(R.id.setupKeepKeyboard, activity.findViewById<RadioGroup>(R.id.setupRouteGroup).checkedRadioButtonId)
                    assertTrue(activity.findViewById<TextView>(R.id.setupGboardWarning).text.contains("Gboard"))
                    assertEquals(fixture.baseUrl, activity.findViewById<EditText>(R.id.baseUrlInput).text.toString())
                    assertEquals(fixture.model, store.load().model)
                    assertEquals(fixture.apiToken, store.load().apiToken)
                }
            }
        } finally {
            store.save(original)
            setup.edit().clear().commit()
        }
    }

    @Test
    fun testV49NarrowLayoutKeepsActionsReadableInEnglishAndSpanishAtLargeText() {
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
            for (language in listOf("en", "es")) {
                scenario.onActivity { activity ->
                    val config = Configuration(activity.resources.configuration).apply {
                        setLocale(Locale.forLanguageTag(language))
                        fontScale = 1.3f
                    }
                    val localized = activity.createConfigurationContext(config)
                    val root = LayoutInflater.from(localized).inflate(R.layout.keyboard_setup_guide, null)
                    val width = (320 * localized.resources.displayMetrics.density).toInt()
                    val scroll = ScrollView(activity).apply { addView(root) }
                    activity.setContentView(scroll)
                    preferences.edit().clear().commit()
                    KeyboardSetupGuide(localized, root, preferences, {}, {}, {}, { null }).refresh(false, false)
                    root.findViewById<RadioGroup>(R.id.setupRouteGroup).check(R.id.setupUseHeliboard)
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    root.layout(0, 0, width, root.measuredHeight)
                    for (id in listOf(R.id.setupDownloadHeliboard, R.id.setupOpenHeliboard, R.id.permissionButton, R.id.enableImeButton, R.id.setupChooseKeyboard, R.id.setupConfigureServer)) {
                        val button = root.findViewById<Button>(id)
                        assertTrue("$language action has no height", button.height > 0)
                        assertTrue("$language action outside layout", button.right <= width && button.left >= 0)
                        assertTrue("$language clipped action", button.layout.height <= button.height - button.compoundPaddingTop - button.compoundPaddingBottom)
                    }
                    assertTrue(root.measuredHeight > 0)
                }
            }
        }
    }
}
