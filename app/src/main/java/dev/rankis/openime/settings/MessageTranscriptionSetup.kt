package dev.rankis.openime.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dev.rankis.openime.R
import dev.rankis.openime.sharing.MessageTranscriptionService

class MessageTranscriptionSetup(private val activity: AppCompatActivity) {
    init {
        activity.findViewById<Button>(R.id.messageSetupButton).setOnClickListener {
            val component = ComponentName(activity, MessageTranscriptionService::class.java)
            val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component)
            runCatching { activity.startActivity(intent) }.onFailure {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    fun refresh() {
        val manager = activity.getSystemService(AccessibilityManager::class.java)
        val enabled = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.name == MessageTranscriptionService::class.java.name &&
                it.resolveInfo.serviceInfo.packageName == activity.packageName }
        activity.findViewById<TextView>(R.id.messageSetupStatus).setText(
            if (enabled) R.string.message_setup_enabled else R.string.message_setup_disabled,
        )
    }
}
