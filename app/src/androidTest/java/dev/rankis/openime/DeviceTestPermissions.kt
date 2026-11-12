package dev.rankis.openime

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.content.pm.PackageManager

internal object DeviceTestPermissions {
    fun microphone(automation: UiAutomation, context: Context) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            try {
                automation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
            } catch (failure: SecurityException) {
                throw AssertionError("Grant Microphone to the isolated test app in Android settings, then rerun device tests", failure)
            }
        }
    }
}
