package dev.rankis.openime.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import dev.rankis.openime.R
import dev.rankis.openime.metrics.TranscriptionMetricsSnapshot
import dev.rankis.openime.metrics.TranscriptionMetricsStore
import dev.rankis.openime.metrics.formatBytes
import dev.rankis.openime.metrics.formatMillis
import dev.rankis.openime.metrics.formatTranscriptionMetrics
import dev.rankis.openime.metrics.successRatePercent

class MetricsActivity : SettingsScreenActivity() {
    private lateinit var metricsStore: TranscriptionMetricsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_metrics, R.string.screen_metrics)
        metricsStore = TranscriptionMetricsStore(this)
        findViewById<Button>(R.id.copyMetricsButton).setOnClickListener { copyMetrics() }
        findViewById<Button>(R.id.shareMetricsButton).setOnClickListener { shareMetrics() }
        findViewById<Button>(R.id.resetMetricsButton).setOnClickListener { confirmReset() }
    }

    override fun onResume() {
        super.onResume()
        render(metricsStore.load())
    }

    private fun render(snapshot: TranscriptionMetricsSnapshot) {
        val empty = snapshot.totalCount == 0L
        findViewById<View>(R.id.metricsEmpty).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.metricsContent).visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) return

        val tiles = findViewById<LinearLayout>(R.id.metricsTiles)
        tiles.removeAllViews()
        addTile(tiles, snapshot.totalCount.toString(), getString(R.string.metrics_uploads))
        addTile(tiles, "${successRatePercent(snapshot)} %", getString(R.string.metrics_success))
        addTile(tiles, formatMillis(snapshot.averageDurationMillis), getString(R.string.metrics_average))

        val buckets = findViewById<LinearLayout>(R.id.metricsBuckets)
        buckets.removeAllViews()
        snapshot.buckets.filter { it.count > 0L }.forEach { bucket ->
            SettingsRow.addTo(buckets).bind(
                title = getString(R.string.metrics_bucket_title_format, bucket.label, formatMillis(bucket.averageDurationMillis)),
                summary = getString(
                    R.string.metrics_bucket_summary_format,
                    bucket.count,
                    bucket.successCount,
                    formatBytes(bucket.averageBytes),
                ),
            )
        }
    }

    private fun addTile(parent: LinearLayout, value: String, label: String) {
        val tile = LayoutInflater.from(this).inflate(R.layout.settings_metric_tile, parent, false)
        tile.findViewById<TextView>(R.id.tileValue).text = value
        tile.findViewById<TextView>(R.id.tileLabel).text = label
        if (parent.childCount > 0) {
            (tile.layoutParams as LinearLayout.LayoutParams).marginStart = resources.getDimensionPixelSize(R.dimen.settings_tile_gap)
        }
        parent.addView(tile)
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle(R.string.metrics_reset_title)
            .setMessage(R.string.metrics_reset_message)
            .setPositiveButton(R.string.button_reset_metrics) { _, _ ->
                metricsStore.clear()
                render(metricsStore.load())
                Toast.makeText(this, R.string.toast_metrics_reset, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun metricsText(): String = formatTranscriptionMetrics(metricsStore.load())

    private fun copyMetrics() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.metrics_clip_label), metricsText()))
        Toast.makeText(this, R.string.toast_metrics_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareMetrics() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.metrics_clip_label))
            .putExtra(Intent.EXTRA_TEXT, metricsText())
        startActivity(Intent.createChooser(intent, getString(R.string.metrics_share_chooser)))
    }
}
