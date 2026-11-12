package dev.rankis.openime.settings

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

object SetupStatus {
    fun microphoneGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun keyboardEnabled(context: Context): Boolean =
        context.getSystemService(InputMethodManager::class.java).enabledInputMethodList
            .any { it.packageName == context.packageName }

    fun accessibilityServiceEnabled(context: Context, service: Class<*>): Boolean =
        context.getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == context.packageName && info.name == service.name
            }
}
