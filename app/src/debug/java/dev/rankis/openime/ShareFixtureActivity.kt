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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun add(label: String, action: (Button) -> Unit = {}) = Button(this).apply {
            text = label
            setOnClickListener { action(this) }
            layout.addView(this)
        }
        val message = add("Voice message (test)")
        message.setOnLongClickListener {
            add("Forward")
            add("Delete")
            add("More options") { anchor ->
                PopupMenu(this, anchor).apply {
                    menu.add("Share").setOnMenuItemClickListener {
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
