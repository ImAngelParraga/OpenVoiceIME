package dev.rankis.openime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValidationTest {
    @Test
    fun defaultBaseUrlUsesOpenAi() {
        assertEquals("https://api.openai.com", AppSettings().baseUrl)
    }

    @Test
    fun defaultModelUsesOpenAiTranscriptionModel() {
        assertEquals("gpt-4o-transcribe", AppSettings().model)
    }

    @Test
    fun defaultPresetUsesOpenAi() {
        assertEquals(BuiltInProviderPreset.OPENAI.id, AppSettings().selectedPresetId)
    }

    @Test
    fun selectInsertedTextDefaultsToCurrentBehavior() {
        assertTrue(AppSettings().selectInsertedText)
    }

    @Test
    fun openAiPresetUsesOpenAiBaseUrlAndTranscriptionModel() {
        assertEquals("OpenAI", BuiltInProviderPreset.OPENAI.displayName)
        assertEquals("https://api.openai.com", BuiltInProviderPreset.OPENAI.defaultBaseUrl)
        assertEquals("gpt-4o-transcribe", BuiltInProviderPreset.OPENAI.defaultModel)
        assertTrue(BuiltInProviderPreset.OPENAI.models.contains("whisper-1"))
    }

    @Test
    fun groqPresetUsesOpenAiCompatibleBaseUrlAndWhisperModel() {
        assertEquals("Groq", BuiltInProviderPreset.GROQ.displayName)
        assertEquals("https://api.groq.com/openai", BuiltInProviderPreset.GROQ.defaultBaseUrl)
        assertEquals("whisper-large-v3-turbo", BuiltInProviderPreset.GROQ.defaultModel)
    }

    @Test
    fun providerOptionsAppendSavedPresetsWithoutBuiltInPrivateServices() {
        val options = providerOptions(
            listOf(
                SavedProviderPreset(
                    id = "saved_private",
                    displayName = "My ASR",
                    baseUrl = "https://asr.example.test",
                    model = "whisper-1",
                    apiToken = "local-token",
                ),
            ),
        )

        assertEquals(listOf("OpenAI", "Groq", "Custom", "My ASR"), options.map { it.displayName })
        assertEquals("https://asr.example.test", options.last().baseUrl)
        assertEquals("whisper-1", options.last().model)
        assertEquals("local-token", options.last().apiToken)
    }

    @Test
    fun modelChoicesUseKnownListWithCustomFallback() {
        assertEquals(
            OPENAI_TRANSCRIPTION_MODELS + CUSTOM_MODEL_LABEL,
            modelChoicesFor(builtInProviderOptions().first { it.id == BuiltInProviderPreset.OPENAI.id }, "future-model"),
        )
    }

    @Test
    fun validSettingsPass() {
        val result = validateSettings(AppSettings(apiToken = "secret"))

        assertTrue(result.isValid)
        assertNull(result.message)
    }

    @Test
    fun invalidUrlFails() {
        val result = validateServerUrl("server:9000")

        assertFalse(result.isValid)
        assertEquals("Server URL must start with http:// or https://", result.message)
    }

    @Test
    fun testServerUrlDoesNotRequireToken() {
        val result = validateServerUrl("https://asr.example.test")

        assertTrue(result.isValid)
        assertNull(result.message)
    }

    @Test
    fun blankTokenFails() {
        val result = validateSettings(AppSettings(apiToken = ""))

        assertFalse(result.isValid)
        assertEquals("API token is required", result.message)
    }

    @Test
    fun blankModelFails() {
        val result = validateSettings(AppSettings(apiToken = "secret", model = " "))

        assertFalse(result.isValid)
        assertEquals("Model is required", result.message)
    }

    @Test
    fun customLanguageMustLookLikeIsoCode() {
        val result = validateSettings(
            AppSettings(
                apiToken = "secret",
                languageChoice = LanguageChoice.CUSTOM,
                customLanguage = "not a code",
            ),
        )

        assertFalse(result.isValid)
        assertEquals("Language must be an ISO code like es or en", result.message)
    }

    @Test
    fun commitTextFormattingAddsOptionalSpace() {
        assertEquals("hola", formatCommitText("hola", false))
        assertEquals("hola ", formatCommitText("hola", true))
    }
}
