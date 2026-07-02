package dev.rankis.openime.settings

import java.net.URI

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

enum class LanguageChoice(val wireValue: String?) {
    AUTO(null),
    SPANISH("es"),
    ENGLISH("en"),
    CUSTOM(null),
}

enum class AppLanguageChoice(val languageTag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es"),
}

data class AppSettings(
    val providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
    val selectedPresetId: String = BuiltInProviderPreset.OPENAI.id,
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = DEFAULT_TRANSCRIPTION_MODEL,
    val apiToken: String = "",
    val appLanguageChoice: AppLanguageChoice = AppLanguageChoice.SYSTEM,
    val languageChoice: LanguageChoice = LanguageChoice.AUTO,
    val customLanguage: String = "",
    val appendTrailingSpace: Boolean = false,
    val hideAfterSuccess: Boolean = true,
    val confirmBeforeInsert: Boolean = false,
    val selectInsertedText: Boolean = true,
) {
    val languageCode: String?
        get() = when (languageChoice) {
            LanguageChoice.AUTO -> null
            LanguageChoice.CUSTOM -> customLanguage.trim().ifBlank { null }
            else -> languageChoice.wireValue
        }

    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')

    fun normalizedModel(): String = model.trim()
}

const val DEFAULT_BASE_URL = "https://api.openai.com"
const val DEFAULT_TRANSCRIPTION_MODEL = "gpt-4o-transcribe"
const val CUSTOM_MODEL_LABEL = "Custom..."

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
    LanguageIsoCodeRequired,
    ServerUrlRequired,
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
    val language = settings.languageCode
    if (language != null && !Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{2,8})?$").matches(language)) {
        return SettingsValidation(false, "Language must be an ISO code like es or en", SettingsValidationError.LanguageIsoCodeRequired)
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

fun formatCommitText(text: String, appendTrailingSpace: Boolean): String {
    return if (appendTrailingSpace) "$text " else text
}
