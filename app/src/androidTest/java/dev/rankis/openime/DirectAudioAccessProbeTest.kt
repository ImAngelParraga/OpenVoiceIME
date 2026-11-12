package dev.rankis.openime

import android.Manifest
import android.app.UiAutomation
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in experiment on an owned emulator; never enumerates personal media. */
@RunWith(AndroidJUnit4::class)
class DirectAudioAccessProbeTest {
    @Test fun compareRawMediaPermissionAndScopedFolderAccess() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runDirectAudioProbe") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= 33 && Build.HARDWARE.contains("ranchu"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val sourcePackage = instrumentation.context.packageName
        val original = context.assets.open("share-fixture.ogg").use { it.readBytes() }
        val provider = android.net.Uri.parse("content://$sourcePackage.media-fixture")
        val fixture = requireNotNull(context.contentResolver.call(provider, "create", null,
            Bundle().apply { putByteArray("audio", original) }))
        assertNotEquals(android.os.Process.myUid(), fixture.getInt("ownerUid"))
        val relative = requireNotNull(fixture.getString("relative"))
        val directory = "/sdcard/$relative"
        val file = File(directory, "voice.ogg")
        val permission = Manifest.permission.READ_MEDIA_AUDIO
        var chosenTree: android.net.Uri? = null
        try {
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(permission))
            val rawBefore = runCatching { file.readBytes().contentEquals(original) }.getOrDefault(false)
            automation.grantRuntimePermission(context.packageName, permission)
            val rawAfter = runCatching { file.readBytes().contentEquals(original) }.getOrDefault(false)
            val scanned = CountDownLatch(1)
            MediaScannerConnection.scanFile(context, arrayOf(file.path), arrayOf("audio/ogg")) { _, _ -> scanned.countDown() }
            assertTrue(scanned.await(10, TimeUnit.SECONDS))
            val rows = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Media._ID), "${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf("$relative/"), null)?.use { it.count } ?: 0
            ActivityScenario.launch(AudioFolderProbeActivity::class.java).use { scenario ->
                lateinit var host: AudioFolderProbeActivity
                scenario.onActivity {
                    host = it
                    it.chooseFolder(DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:$relative"))
                }
                val segments = java.util.ArrayDeque(listOf("Android", "media", sourcePackage, relative.substringAfterLast('/')))
                val deadline = android.os.SystemClock.uptimeMillis() + 25_000
                while (android.os.SystemClock.uptimeMillis() < deadline && chosenTree == null) {
                    val root = automation.rootInActiveWindow
                    if (root != null) try {
                        fun clickLabel(label: String): Boolean {
                            val matches = root.findAccessibilityNodeInfosByText(label)
                            try {
                                val node = matches.firstOrNull { it.text?.toString()?.equals(label, true) == true && it.isEnabled } ?: return false
                                var candidate = node
                                repeat(4) {
                                    if (candidate.isClickable) {
                                        val accepted = candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                        if (candidate !== node) { @Suppress("DEPRECATION") candidate.recycle() }
                                        return accepted
                                    }
                                    val parent = candidate.parent ?: return false
                                    if (candidate !== node) { @Suppress("DEPRECATION") candidate.recycle() }
                                    candidate = parent
                                }
                                if (candidate !== node) { @Suppress("DEPRECATION") candidate.recycle() }
                                return false
                            } finally { @Suppress("DEPRECATION") matches.forEach { it.recycle() } }
                        }
                        val files = root.findAccessibilityNodeInfosByText("voice.ogg")
                        val crumbs = root.findAccessibilityNodeInfosByText(relative.substringAfterLast('/'))
                        val inTarget = files.isNotEmpty() || crumbs.any {
                            val bounds = android.graphics.Rect().also { out -> it.getBoundsInScreen(out) }
                            bounds.top < 160 * context.resources.displayMetrics.density
                        }
                        @Suppress("DEPRECATION") crumbs.forEach { it.recycle() }
                        @Suppress("DEPRECATION") files.forEach { it.recycle() }
                        when {
                            inTarget -> clickLabel("Use this folder")
                            root.findAccessibilityNodeInfosByText("Allow").isNotEmpty() -> clickLabel("Allow")
                            segments.isNotEmpty() && clickLabel(segments.first) -> segments.removeFirst()
                        }
                    } finally { @Suppress("DEPRECATION") root.recycle() }
                    instrumentation.runOnMainSync { chosenTree = host.selectedTree }
                    Thread.sleep(100)
                }
                val tree = requireNotNull(chosenTree) { "Scoped folder approval did not complete" }
                assertEquals("primary:$relative", DocumentsContract.getTreeDocumentId(tree))
                val document = DocumentsContract.buildDocumentUriUsingTree(tree, "primary:$relative/voice.ogg")
                val scoped = context.contentResolver.openInputStream(document)!!.use { it.readBytes() }
                assertArrayEquals(original, scoped)
                instrumentation.sendStatus(2, Bundle().apply {
                    putString("probe_result", "rawWithoutPermission=$rawBefore rawWithReadMediaAudio=$rawAfter indexedRows=$rows scopedFolderRead=true")
                })
            }
        } finally {
            chosenTree?.let { context.contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            // Revoke the media permission from the host after the runner exits; revocation kills this process.
            context.contentResolver.call(provider, "cleanup", relative.substringAfterLast('/'), null)
        }
    }
}
