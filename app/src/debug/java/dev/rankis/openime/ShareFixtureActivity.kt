package dev.rankis.openime

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu

/** Isolated chat surface for exercising the real Accessibility-to-Sharesheet handoff. */
class ShareFixtureActivity : Activity() {
    var shareMenusOpened = 0
        private set
    var messageClicks = 0
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("secure", false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, (96 * resources.displayMetrics.density).toInt(), 16, 16)
        }
        fun add(label: String, action: (Button) -> Unit = {}) = Button(this).apply {
            text = label
            setOnClickListener { action(this) }
            layout.addView(this)
        }
        val message = add("Voice message (test)") { messageClicks++ }
        message.setOnLongClickListener {
            message.isSelected = true
            add("Forward")
            add("Delete")
            add("More options") { anchor ->
                shareMenusOpened++
                PopupMenu(this, anchor).apply {
                    menu.add(if (intent.getBooleanExtra("no_share", false)) "Unavailable" else "Share").setOnMenuItemClickListener {
                        val uri = Uri.parse("content://$packageName.share-fixture/voice.wav")
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/wav")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .apply { clipData = ClipData.newRawUri("audio", uri) }
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share test audio"))
                        true
                    }
                    show()
                }
            }
            true
        }
        setContentView(layout)
    }
}
