package dev.rankis.openime

import android.accessibilityservice.AccessibilityService

/** Releases only the hardware-keyboard allowance acquired for this dictation. */
internal class ShortcutKeyboardVisibility(
    private val getMode: () -> Int,
    private val setMode: (Int) -> Boolean,
) {
    private var acquired = false

    fun acquire(): Boolean {
        if (acquired) return getMode() == AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD
        return when (getMode()) {
            AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD -> true
            AccessibilityService.SHOW_MODE_AUTO -> setMode(AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD)
                .also { acquired = it }
            else -> false
        }
    }

    fun release(): Boolean {
        if (!acquired) return true
        acquired = false
        return getMode() != AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD ||
            setMode(AccessibilityService.SHOW_MODE_AUTO)
    }
}
