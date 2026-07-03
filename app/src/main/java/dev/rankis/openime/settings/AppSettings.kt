package dev.rankis.openime.settings

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

enum class ProviderType {
    OPENAI_COMPATIBLE,
}

val OPENAI_TRANSCRIPTION_MODELS = listOf(
    "gpt-4o-transcribe",
    "gpt-4o-mini-transcribe",
    "gpt-4o-mini-transcribe-2025-12-15",
    "whisper-1",
    "gpt-4o-transcribe-diarize",
)

val GROQ_TRANSCRIPTION_MODELS = listOf(
    "whisper-large-v3-turbo",
    "whisper-large-v3",
    "distil-whisper-large-v3-en",
)

enum class BuiltInProviderPreset(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val models: List<String>,
) {
    OPENAI(
        id = "builtin_openai",
        displayName = "OpenAI",
        defaultBaseUrl = "https://api.openai.com",
        defaultModel = OPENAI_TRANSCRIPTION_MODELS.first(),
        models = OPENAI_TRANSCRIPTION_MODELS,
    ),
    GROQ(
        id = "builtin_groq",
        displayName = "Groq",
        defaultBaseUrl = "https://api.groq.com/openai",
        defaultModel = GROQ_TRANSCRIPTION_MODELS.first(),
        models = GROQ_TRANSCRIPTION_MODELS,
    ),
    CUSTOM(
        id = "custom",
        displayName = "Custom",
        defaultBaseUrl = "",
        defaultModel = OPENAI_TRANSCRIPTION_MODELS.first(),
        models = emptyList(),
    ),
}

data class SavedProviderPreset(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val apiToken: String,
)

data class ProviderPresetOption(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val model: String,
    val apiToken: String,
    val models: List<String>,
    val isSaved: Boolean = false,
)

enum class AppLanguageChoice(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es"),
}

data class TranscriptionLanguageSettings(
    val languageCode: String? = null,
    val favoriteLanguageCodes: List<String?> = defaultFavoriteTranscriptionLanguageCodes(),
)

data class AppSettings(
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val selectedPresetId: String = BuiltInProviderPreset.OPENAI.id,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_TRANSCRIPTION_MODEL,
    val apiToken: String = "",
    val appLanguageChoice: AppLanguageChoice = AppLanguageChoice.SYSTEM,
    val transcriptionLanguageCode: String? = null,
    val favoriteTranscriptionLanguageCodes: List<String?> = defaultFavoriteTranscriptionLanguageCodes(),
    val appendTrailingSpace: Boolean = false,
    val hideAfterSuccess: Boolean = true,
    val confirmBeforeInsert: Boolean = false,
    val selectInsertedText: Boolean = true,
) {
    val languageCode: String?
        get() = transcriptionLanguageCode?.trim()?.ifBlank { null }

    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')

    fun normalizedModel(): String = model.trim()
}

const val DEFAULT_BASE_URL = "https://api.openai.com"
const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-transcribe"
const val CUSTOM_MODEL_LABEL = "Custom..."
const val MAX_FAVORITE_TRANSCRIPTION_LANGUAGES = 6

fun builtInProviderOptions(): List<ProviderPresetOption> {
    return BuiltInProviderPreset.entries.map { preset ->
        ProviderPresetOption(
            id = preset.id,
            displayName = preset.displayName,
            baseUrl = preset.defaultBaseUrl,
            model = preset.defaultModel,
            apiToken = "",
            models = preset.models,
        )
    }
}

fun providerOptions(savedPresets: List<SavedProviderPreset>): List<ProviderPresetOption> {
    return builtInProviderOptions() + savedPresets.map { preset ->
        ProviderPresetOption(
            id = preset.id,
            displayName = preset.displayName,
            baseUrl = preset.baseUrl,
            model = preset.model,
            apiToken = preset.apiToken,
            models = listOf(preset.model).filter { it.isNotBlank() },
            isSaved = true,
        )
    }
}

fun modelChoicesFor(option: ProviderPresetOption, currentModel: String): List<String> {
    if (option.id == BuiltInProviderPreset.CUSTOM.id) {
        return listOf(CUSTOM_MODEL_LABEL)
    }
    val knownModels = (option.models.ifEmpty { listOf(option.model) })
        .filter { it.isNotBlank() }
        .distinct()
    return knownModels + CUSTOM_MODEL_LABEL
}

data class SettingsValidation(
    val isValid: Boolean,
    val message: String? = null,
    val error: SettingsValidationError? = null,
)

enum class SettingsValidationError {
    ApiTokenRequired,
    ModelRequired,
    ServerUrlRequired,
}

data class TranscriptionLanguageOption(
    val code: String?,
    val label: String,
)

fun transcriptionLanguageOptions(locale: Locale = Locale.getDefault()): List<TranscriptionLanguageOption> {
    val languages = Locale.getISOLanguages()
        .mapNotNull { code ->
            val displayName = Locale.forLanguageTag(code).getDisplayLanguage(locale)
                .replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(locale) else char.toString()
                }
            displayName.takeIf { it.isNotBlank() }?.let { name ->
                TranscriptionLanguageOption(code = code, label = "$name ($code)")
            }
        }
        .distinctBy { it.code }
        .sortedBy { it.label.lowercase(locale) }
    return listOf(TranscriptionLanguageOption(code = null, label = "Auto")) + languages
}

fun defaultFavoriteTranscriptionLanguageCodes(locale: Locale = Locale.getDefault()): List<String?> {
    val language = locale.language.takeIf { it.isNotBlank() }
    return normalizeFavoriteTranscriptionLanguageCodes(listOf(null, language))
}

fun normalizeFavoriteTranscriptionLanguageCodes(codes: List<String?>): List<String?> {
    return (listOf<String?>(null) + codes)
        .map { code -> code?.trim()?.lowercase(Locale.ROOT)?.ifBlank { null } }
        .distinct()
        .take(MAX_FAVORITE_TRANSCRIPTION_LANGUAGES)
}

fun validateSettings(settings: AppSettings): SettingsValidation {
    validateServerUrl(settings.normalizedBaseUrl()).let { validation ->
        if (!validation.isValid) {
            return validation
        }
    }
    if (settings.apiToken.isBlank()) {
        return SettingsValidation(false, "API token is required", SettingsValidationError.ApiTokenRequired)
    }
    if (settings.normalizedModel().isBlank()) {
        return SettingsValidation(false, "Model is required", SettingsValidationError.ModelRequired)
    }
    return SettingsValidation(true)
}

fun validateServerUrl(baseUrl: String): SettingsValidation {
    val normalized = baseUrl.trim().trimEnd('/')
    val uri = runCatching { URI(normalized) }.getOrNull()
    if (normalized.isBlank() || uri?.scheme !in setOf("http", "https") || uri?.host.isNullOrBlank()) {
        return SettingsValidation(false, "Server URL must start with http:// or https://", SettingsValidationError.ServerUrlRequired)
    }
    return SettingsValidation(true)
}

fun connectionTestFingerprint(settings: AppSettings): String {
    val value = listOf(
        settings.normalizedBaseUrl(),
        settings.normalizedModel(),
        settings.apiToken,
    ).joinToString(separator = "\u0000")
    return MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

fun formatCommitText(text: String, appendTrailingSpace: Boolean): String {
    return if (appendTrailingSpace) "$text " else text
}
