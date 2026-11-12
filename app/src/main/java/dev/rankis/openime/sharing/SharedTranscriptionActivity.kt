package dev.rankis.openime.sharing

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.Gravity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.view.doOnPreDraw
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dev.rankis.openime.R
import dev.rankis.openime.settings.SettingsActivity
import dev.rankis.openime.settings.withAppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SharedTranscriptionActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var model: SharedTranscriptionModel
    private var transcript: String? = null

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(newBase.withAppLocale()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_transcription)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        val display = resources.displayMetrics
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setDimAmount(0.24f)
        window.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        window.setLayout(minOf(display.widthPixels, (640 * display.density).toInt()),
            WindowManager.LayoutParams.WRAP_CONTENT)
        window.decorView.doOnPreDraw {
            window.decorView.postOnAnimation { ShareHandoff.receiverReady?.invoke() }
        }
        val sheet = findViewById<View>(R.id.shareSheet)
        val bottomPadding = sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(sheet) { view, insets ->
            view.updatePadding(bottom = bottomPadding + insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or WindowInsetsCompat.Type.displayCutout(),
            ).bottom)
            insets
        }
        ViewCompat.requestApplyInsets(sheet)
        setFinishOnTouchOutside(false)
        model = ViewModelProvider(this)[SharedTranscriptionModel::class.java]
        findViewById<Button>(R.id.shareClose).setOnClickListener { finish() }
        findViewById<Button>(R.id.shareRetry).setOnClickListener { model.retry() }
        findViewById<Button>(R.id.shareCancel).setOnClickListener { model.cancel() }
        findViewById<Button>(R.id.shareSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<Button>(R.id.shareCopy).setOnClickListener {
            transcript?.let {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.share_title), it))
                Toast.makeText(this, R.string.share_copied, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.shareText).setOnClickListener {
            transcript?.let {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, it), getString(R.string.share_text)))
            }
        }
        scope.launch { model.state.collect { render(it) } }
        model.start(intent)
    }

    private fun render(state: SharedTranscriptState) {
        transcript = (state as? SharedTranscriptState.Ready)?.text
        val working = state is SharedTranscriptState.Working
        findViewById<ProgressBar>(R.id.shareProgress).visibility = if (working) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.shareStatus).text = when (state) {
            is SharedTranscriptState.Working -> getString(if (state.importing) R.string.share_importing else R.string.share_working)
            is SharedTranscriptState.Ready -> getString(R.string.share_done)
            is SharedTranscriptState.Failed -> state.message
        }
        findViewById<TextView>(R.id.shareTranscript).apply {
            text = transcript.orEmpty()
            visibility = if (transcript != null) View.VISIBLE else View.GONE
        }
        findViewById<Button>(R.id.shareCancel).visibility = if (working) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.shareRetry).visibility = if (state is SharedTranscriptState.Failed && state.canRetry) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.shareSettings).visibility = if (state is SharedTranscriptState.Failed) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.shareCopy).visibility = if (transcript != null) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.shareText).visibility = if (transcript != null) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
