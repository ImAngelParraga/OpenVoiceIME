package dev.rankis.openime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSummariesTest {
    @Test
    fun defaultSettingsReturnToTypingKeyboard() {
        assertEquals(AfterInsertAction.SwitchKeyboard, AfterInsertAction.of(AppSettings()))
    }

    @Test
    fun afterInsertActionRoundTripsThroughSettings() {
        AfterInsertAction.entries.forEach { action ->
            assertEquals(action, AfterInsertAction.of(AppSettings().withAfterInsertAction(action)))
        }
    }

    @Test
    fun keepDictatingClearsHideAndReturn() {
        val settings = AppSettings().withAfterInsertAction(AfterInsertAction.KeepDictating)

        assertEquals(false, settings.hideAfterSuccess)
        assertEquals(false, settings.returnToKeyboardAfterInsert)
    }

    @Test
    fun returnWithoutHideStillReadsAsSwitchKeyboard() {
        val legacy = AppSettings(returnToKeyboardAfterInsert = true, hideAfterSuccess = false)

        assertEquals(AfterInsertAction.SwitchKeyboard, AfterInsertAction.of(legacy))
    }

    @Test
    fun serverWithoutTokenIsNotConfigured() {
        assertEquals(ServerReadiness.NotConfigured, serverReadiness(AppSettings(apiToken = ""), connectionTested = true))
    }

    @Test
    fun validServerNeedsSuccessfulTest() {
        val settings = AppSettings(apiToken = "token")

        assertEquals(ServerReadiness.NotTested, serverReadiness(settings, connectionTested = false))
        assertEquals(ServerReadiness.Ready, serverReadiness(settings, connectionTested = true))
    }
}
