package dev.rankis.openime

import android.accessibilityservice.AccessibilityService.*
import org.junit.Assert.*
import org.junit.Test

class ShortcutKeyboardVisibilityTest {
    private var mode = SHOW_MODE_AUTO
    private var accepted = true
    private val requests = mutableListOf<Int>()
    private val visibility = ShortcutKeyboardVisibility({ mode }) {
        requests += it
        if (accepted) mode = it
        accepted
    }

    @Test fun ownedAllowanceIsRestoredOnce() {
        assertTrue(visibility.acquire())
        assertTrue(visibility.acquire())
        assertEquals(SHOW_MODE_IGNORE_HARD_KEYBOARD, mode)
        assertTrue(visibility.release())
        assertTrue(visibility.release())
        assertEquals(listOf(SHOW_MODE_IGNORE_HARD_KEYBOARD, SHOW_MODE_AUTO), requests)
    }

    @Test fun preExistingAllowanceRemainsOwnedByItsRequester() {
        mode = SHOW_MODE_IGNORE_HARD_KEYBOARD
        assertTrue(visibility.acquire())
        assertTrue(visibility.release())
        assertTrue(requests.isEmpty())
        assertEquals(SHOW_MODE_IGNORE_HARD_KEYBOARD, mode)
    }

    @Test fun hiddenModeIsRespected() {
        mode = SHOW_MODE_HIDDEN
        assertFalse(visibility.acquire())
        assertTrue(visibility.release())
        assertTrue(requests.isEmpty())
    }

    @Test fun rejectedAllowanceIsNotReleased() {
        accepted = false
        assertFalse(visibility.acquire())
        assertTrue(visibility.release())
        assertEquals(listOf(SHOW_MODE_IGNORE_HARD_KEYBOARD), requests)
    }

    @Test fun laterModeChangeIsRespected() {
        assertTrue(visibility.acquire())
        mode = SHOW_MODE_HIDDEN
        assertFalse(visibility.acquire())
        assertTrue(visibility.release())
        assertEquals(SHOW_MODE_HIDDEN, mode)
        assertEquals(listOf(SHOW_MODE_IGNORE_HARD_KEYBOARD), requests)
    }
}
