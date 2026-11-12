package dev.rankis.openime

import android.app.UiAutomation
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.rankis.openime.settings.AppSettings
import dev.rankis.openime.settings.SettingsStore
import dev.rankis.openime.sharing.SharedAudioException
import dev.rankis.openime.sharing.SharedAudioImporter
import dev.rankis.openime.sharing.SharedTranscriptionActivity
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SharedTranscriptionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun uri(name: String = "voice.wav") = Uri.parse("content://${context.packageName}.share-fixture/$name")
    private fun sharedIntent(name: String = "voice.wav") = Intent(context, SharedTranscriptionActivity::class.java)
        .setAction(Intent.ACTION_SEND).setType("audio/wav").putExtra(Intent.EXTRA_STREAM, uri(name))
        .apply { clipData = ClipData.newRawUri("audio", uri(name)) }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Test fun testV69ImportsWaveAndRejectsInvalidOversizedAndNonContentInput() = runBlocking {
        val importer = SharedAudioImporter(context)
        val opus = importer.import(uri("voice.ogg"))
        try {
            assertEquals("ogg", opus.file.extension)
            assertTrue(opus.durationMillis > 0)
        } finally { opus.file.delete() }
        val audio = importer.import(uri())
        try {
            assertEquals("wav", audio.file.extension)
            assertTrue(audio.durationMillis > 0)
            assertTrue(audio.file.length() > 44)
        } finally { audio.file.delete() }
        for ((source, reason) in listOf(uri("invalid") to SharedAudioException.Reason.INVALID,
            uri("oversized") to SharedAudioException.Reason.TOO_LARGE,
            Uri.parse("file:///not-allowed.wav") to SharedAudioException.Reason.INVALID)) {
            try { importer.import(source); fail("invalid input accepted") }
            catch (error: SharedAudioException) { assertEquals(reason, error.reason) }
        }
        assertTrue(sharedFiles().isEmpty())
    }

    @Test fun testV70ResultSurvivesRecreationWithoutUploadingAgainAndCloseDeletesAudio() = withServer { server ->
        server.enqueue(MockResponse().setBody("""{"text":"A synthetic shared transcript."}"""))
        ActivityScenario.launch<SharedTranscriptionActivity>(sharedIntent()).use { scenario ->
            await { scenarioText(scenario, R.id.shareTranscript) == "A synthetic shared transcript." }
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertTrue(request.body.readUtf8().contains("Content-Type: audio/wav"))
            assertEquals("Bearer shared-test", request.getHeader("Authorization"))
            scenario.recreate()
            assertEquals("A synthetic shared transcript.", scenarioText(scenario, R.id.shareTranscript))
            scenario.onActivity { it.findViewById<Button>(R.id.shareRetry).performClick() }
            assertEquals(1, server.requestCount)
            scenario.onActivity { assertEquals(View.VISIBLE, it.findViewById<View>(R.id.shareCopy).visibility) }
        }
        await { sharedFiles().isEmpty() }
    }

    @Test fun testV70CancelIgnoresLateResultAndRetryUsesRetainedAudio() = withServer { server ->
        server.enqueue(MockResponse().setBody("""{"text":"late response"}""").setBodyDelay(3, TimeUnit.SECONDS))
        server.enqueue(MockResponse().setBody("""{"text":"Recovered transcript"}"""))
        ActivityScenario.launch<SharedTranscriptionActivity>(sharedIntent()).use { scenario ->
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            scenario.onActivity { it.findViewById<Button>(R.id.shareCancel).performClick() }
            await { scenarioText(scenario, R.id.shareStatus).contains("cancel", true) }
            assertEquals("", scenarioText(scenario, R.id.shareTranscript))
            await {
                scenario.onActivity { it.findViewById<Button>(R.id.shareRetry).performClick() }
                server.requestCount == 2
            }
            try { await { scenarioText(scenario, R.id.shareTranscript) == "Recovered transcript" } }
            catch (error: AssertionError) {
                throw AssertionError("requests=${server.requestCount}; status=${scenarioText(scenario, R.id.shareStatus)}; synthetic result=${scenarioText(scenario, R.id.shareTranscript)}", error)
            }
        }
        await { sharedFiles().isEmpty() }
    }

    @Test fun testV69InvalidSharedDataNeverUploads() = withServer { server ->
        ActivityScenario.launch<SharedTranscriptionActivity>(sharedIntent("invalid")).use { scenario ->
            await {
                var failed = false
                scenario.onActivity { failed = it.findViewById<View>(R.id.shareSettings).visibility == View.VISIBLE }
                failed
            }
            assertEquals("", scenarioText(scenario, R.id.shareTranscript))
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun testV71FloatingActionNavigatesMoreShareAndRealResolverThenShowsTranscript() = withMessageServer { server ->
        server.enqueue(MockResponse().setBody("""{"text":"Meet at six. This is a synthetic voice message."}"""))
        var receiver: SharedTranscriptionActivity? = null
        try {
            ActivityScenario.launch(ShareFixtureActivity::class.java).use { fixture ->
                val audioBounds = android.graphics.Rect()
                fixture.onActivity {
                    val group = it.findViewById<android.view.ViewGroup>(android.R.id.content)
                    val layout = group.getChildAt(0) as android.view.ViewGroup
                    (layout.getChildAt(0) as Button).apply {
                        getGlobalVisibleRect(audioBounds)
                        performLongClick()
                    }
                }
                await {
                    var bounds: android.graphics.Rect? = null
                    main { bounds = MessageTestAccessibilityService.instance?.overlayBounds("messageTranscribeAction") }
                    bounds != null
                }
                main {
                    val bounds = MessageTestAccessibilityService.instance!!.overlayBounds("messageTranscribeAction")!!
                    assertTrue("Pill must retain a 48dp touch target", bounds.height() >= 48 * context.resources.displayMetrics.density)
                    assertTrue("Pill should sit below the selected message", bounds.top >= audioBounds.bottom)
                    assertTrue("Pill should stay near the selected message", bounds.top - audioBounds.bottom < 32 * context.resources.displayMetrics.density)
                }
                screenshot("shared-message-action.png")
                await {
                    var tapped = false
                    main { tapped = MessageTestAccessibilityService.instance?.tapFloatingAction() == true }
                    tapped
                }
                await {
                    main {
                        receiver = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                            .filterIsInstance<SharedTranscriptionActivity>().firstOrNull()
                    }
                    receiver != null
                }
                await {
                    var text = ""
                    main { text = receiver?.findViewById<TextView>(R.id.shareTranscript)?.text?.toString().orEmpty() }
                    text == "Meet at six. This is a synthetic voice message."
                }
                await {
                    var covered = false
                    main { covered = MessageTestAccessibilityService.instance?.overlayBounds("shareCover") != null }
                    !covered
                }
                assertEquals(1, server.requestCount)
                main {
                    val activity = receiver!!
                    assertEquals(android.view.Gravity.BOTTOM, activity.window.attributes.gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK)
                    val insets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)!!
                        .getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
                    val metrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getRealMetrics(metrics)
                    val bounds = android.graphics.Rect()
                    activity.findViewById<View>(R.id.shareCopy).getGlobalVisibleRect(bounds)
                    assertTrue("Copy must remain above system navigation", bounds.bottom <= metrics.heightPixels - insets.bottom)
                }
                screenshot("shared-transcript.png")
                main { receiver?.finish() }
            }
        } finally {
            main { receiver?.finish() }
        }
    }

    @Test fun testV72PanelWrapsShortResultsAndScrollsLongSpanishWithLargeText() {
        ActivityScenario.launch(IntegrationHostActivity::class.java).use { scenario ->
            scenario.onActivity { host ->
                val config = android.content.res.Configuration(host.resources.configuration).apply {
                    fontScale = 1.5f
                    setLocale(java.util.Locale.forLanguageTag("es"))
                }
                val themed = android.view.ContextThemeWrapper(host.createConfigurationContext(config), R.style.Theme_OpenVoiceIME_Transcript)
                val panel = android.view.LayoutInflater.from(themed).inflate(R.layout.activity_shared_transcription, null) as android.view.ViewGroup
                val transcript = panel.findViewById<TextView>(R.id.shareTranscript)
                transcript.text = "Nos vemos a las seis."
                panel.findViewById<View>(R.id.shareProgress).visibility = View.GONE
                panel.findViewById<View>(R.id.shareCancel).visibility = View.GONE
                panel.findViewById<View>(R.id.shareCopy).visibility = View.VISIBLE
                panel.findViewById<View>(R.id.shareText).visibility = View.VISIBLE
                val density = themed.resources.displayMetrics.density
                val width = (320 * density).toInt()
                val height = (480 * density).toInt()
                fun measure() {
                    panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST))
                    panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
                }
                measure()
                assertTrue("Short result should wrap", panel.height < height)
                transcript.text = "Este mensaje de prueba tiene una transcripción larga. ".repeat(250)
                measure()
                for (id in listOf(R.id.shareCopy, R.id.shareText, R.id.shareClose)) {
                    val action = panel.findViewById<View>(id)
                    val bounds = android.graphics.Rect(0, 0, action.width, action.height)
                    panel.offsetDescendantRectToMyCoords(action, bounds)
                    assertTrue("Action must remain inside panel", bounds.bottom <= panel.height)
                    assertTrue("Action must have a touch target", action.height >= (48 * density).toInt())
                }
                assertTrue("Long transcript must scroll", (transcript.parent.parent as android.widget.ScrollView).canScrollVertically(1))
            }
        }
    }

    @Test fun testV75CoverWaitsForDrawBlocksTouchesAndRemovesOnCancel() = withMessageService {
        var presentation: dev.rankis.openime.sharing.MessageShareOverlay? = null
        try {
            ActivityScenario.launch(ShareFixtureActivity::class.java).use { fixture ->
                val audioBounds = android.graphics.Rect()
                fixture.onActivity { activity ->
                    val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.view.ViewGroup
                    content.getChildAt(0).getGlobalVisibleRect(audioBounds)
                }
                var drawn = false
                main {
                    val service = MessageTestAccessibilityService.instance!!
                    val themed = android.view.ContextThemeWrapper(service, R.style.Theme_OpenVoiceIME)
                    presentation = dev.rankis.openime.sharing.MessageShareOverlay(themed, service.getSystemService(android.view.WindowManager::class.java))
                    assertTrue(presentation!!.showCover(onCancel = { presentation!!.clear() }, onDrawn = { drawn = true }))
                    assertFalse(drawn)
                }
                await {
                    var visible = false
                    main { visible = drawn && MessageTestAccessibilityService.instance?.overlayBounds("shareCover") != null }
                    visible
                }
                screenshot("shared-message-progress.png")
                val now = android.os.SystemClock.uptimeMillis()
                for (action in listOf(android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_UP)) {
                    val event = android.view.MotionEvent.obtain(now, now + action * 20L, action,
                        audioBounds.exactCenterX(), audioBounds.exactCenterY(), 0)
                    try { automation.injectInputEvent(event, true) } finally { event.recycle() }
                }
                instrumentation.waitForIdleSync()
                fixture.onActivity { assertEquals("Cover must block the underlying message", 0, it.messageClicks) }
                main { assertTrue(MessageTestAccessibilityService.instance!!.tapOverlayAction("shareCoverCancel")) }
                await {
                    var visible = false
                    main { visible = MessageTestAccessibilityService.instance?.overlayBounds("shareCover") != null }
                    !visible
                }
            }
        } finally {
            main { presentation?.clear() }
        }
    }

    @Test fun testV75TimeoutAndInterruptAlwaysRemoveCover() = withMessageService {
        for (interrupt in listOf(true, false)) {
            val intent = Intent(context, ShareFixtureActivity::class.java).putExtra("no_share", true)
            ActivityScenario.launch<ShareFixtureActivity>(intent).use { fixture ->
                fixture.onActivity { activity ->
                    val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) as android.view.ViewGroup
                    content.getChildAt(0).performLongClick()
                }
                await {
                    var tapped = false
                    main { tapped = MessageTestAccessibilityService.instance?.tapFloatingAction() == true }
                    tapped
                }
                await {
                    var visible = false
                    main { visible = MessageTestAccessibilityService.instance?.overlayBounds("shareCover") != null }
                    visible
                }
                if (interrupt) main { MessageTestAccessibilityService.instance!!.onInterrupt() }
                await {
                    var visible = false
                    main { visible = MessageTestAccessibilityService.instance?.overlayBounds("shareCover") != null }
                    !visible
                }
            }
        }
    }

    private fun withMessageServer(test: (MockWebServer) -> Unit) = withServer { server ->
        withMessageService { test(server) }
    }

    private fun withMessageService(test: () -> Unit) {
        val previousServices = setting("enabled_accessibility_services")
        val previousEnabled = setting("accessibility_enabled")
        val listener = "${context.packageName}/dev.rankis.openime.MessageTestAccessibilityService"
        try {
            val services = previousServices.takeUnless { it == "null" }.orEmpty()
            shell("settings put secure enabled_accessibility_services ${listOf(services, listener).filter { it.isNotBlank() }.joinToString(":")}")
            shell("settings put secure accessibility_enabled 1")
            await { MessageTestAccessibilityService.instance != null }
            test()
        } finally {
            restore("enabled_accessibility_services", previousServices)
            restore("accessibility_enabled", previousEnabled)
        }
    }

    private fun screenshot(name: String) {
        // Let the bounded sheet entrance animation finish before capture.
        android.os.SystemClock.sleep(220)
        automation.takeScreenshot()?.let { bitmap ->
            File(context.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    private fun sharedFiles() = File(context.cacheDir, "shared-transcription").listFiles()?.toList().orEmpty()
    private fun scenarioText(scenario: ActivityScenario<SharedTranscriptionActivity>, id: Int): String {
        var text = ""
        scenario.onActivity { text = it.findViewById<TextView>(id).text.toString() }
        return text
    }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(condition: () -> Boolean) {
        val end = android.os.SystemClock.uptimeMillis() + 12_000
        while (android.os.SystemClock.uptimeMillis() < end) {
            if (condition()) return
            Thread.sleep(80)
        }
        assertTrue("Expected state did not arrive", condition())
    }
    private fun shell(command: String): String = automation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private fun setting(name: String) = shell("settings get secure $name")
    private fun restore(name: String, value: String) = shell(if (value == "null") "settings delete secure $name" else "settings put secure $name $value")
    private fun withServer(test: (MockWebServer) -> Unit) {
        val store = SettingsStore(context)
        val saved = store.load()
        val server = MockWebServer()
        try {
            server.start()
            store.save(AppSettings(baseUrl = server.url("/").toString(), apiToken = "shared-test"))
            test(server)
        } finally { store.save(saved); server.shutdown() }
    }
}
