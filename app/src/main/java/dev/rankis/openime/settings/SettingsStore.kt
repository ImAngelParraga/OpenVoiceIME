@file:Suppress("DEPRECATION")

package dev.rankis.openime.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val securePrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun load(): AppSettings {
        return AppSettings(
            providerType = prefs.enumValue(KEY_PROVIDER, ProviderType.OPENAI_COMPATIBLE),
            selectedPresetId = prefs.getString(KEY_PROVIDER_PRESET, BuiltInProviderPreset.OPENAI.id)
                ?: BuiltInProviderPreset.OPENAI.id,
            baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
            model = prefs.getString(KEY_MODEL, DEFAULT_TRANSCRIPTION_MODEL) ?: DEFAULT_TRANSCRIPTION_MODEL,
            apiToken = securePrefs.getString(KEY_API_TOKEN, "") ?: "",
            appLanguageChoice = prefs.enumValue(KEY_APP_LANGUAGE_CHOICE, AppLanguageChoice.SYSTEM),
            languageChoice = prefs.enumValue(KEY_LANGUAGE_CHOICE, LanguageChoice.AUTO),
            customLanguage = prefs.getString(KEY_CUSTOM_LANGUAGE, "") ?: "",
            appendTrailingSpace = prefs.getBoolean(KEY_TRAILING_SPACE, false),
            hideAfterSuccess = prefs.getBoolean(KEY_HIDE_AFTER_SUCCESS, true),
            confirmBeforeInsert = prefs.getBoolean(KEY_CONFIRM_BEFORE_INSERT, false),
            selectInsertedText = prefs.getBoolean(KEY_SELECT_INSERTED_TEXT, true),
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit {
            putString(KEY_PROVIDER, settings.providerType.name)
                .putString(KEY_PROVIDER_PRESET, settings.selectedPresetId)
                .putString(KEY_BASE_URL, settings.normalizedBaseUrl())
                .putString(KEY_MODEL, settings.normalizedModel())
                .remove(KEY_API_TOKEN)
                .putString(KEY_APP_LANGUAGE_CHOICE, settings.appLanguageChoice.name)
                .putString(KEY_LANGUAGE_CHOICE, settings.languageChoice.name)
                .putString(KEY_CUSTOM_LANGUAGE, settings.customLanguage.trim())
                .putBoolean(KEY_TRAILING_SPACE, settings.appendTrailingSpace)
                .putBoolean(KEY_HIDE_AFTER_SUCCESS, settings.hideAfterSuccess)
                .putBoolean(KEY_CONFIRM_BEFORE_INSERT, settings.confirmBeforeInsert)
                .putBoolean(KEY_SELECT_INSERTED_TEXT, settings.selectInsertedText)
        }
        securePrefs.edit {
            putString(KEY_API_TOKEN, settings.apiToken)
        }
    }

    fun loadCustomPresets(): List<SavedProviderPreset> {
        val json = prefs.getString(KEY_CUSTOM_PRESETS, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    val name = item.optString("name").trim()
                    val baseUrl = item.optString("baseUrl").trim()
                    val model = item.optString("model").trim()
                    if (id.isNotBlank() && name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank()) {
                        add(
                            SavedProviderPreset(
                                id = id,
                                displayName = name,
                                baseUrl = baseUrl,
                                model = model,
                                apiToken = securePrefs.getString(customPresetTokenKey(id), "") ?: "",
                            ),
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveCustomPreset(name: String, baseUrl: String, model: String, apiToken: String): SavedProviderPreset {
        val normalizedName = name.trim()
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedModel = model.trim()
        val existing = loadCustomPresets().filterNot { it.displayName.equals(normalizedName, ignoreCase = true) }
        val preset = SavedProviderPreset(
            id = "saved_${System.currentTimeMillis()}",
            displayName = normalizedName,
            baseUrl = normalizedBaseUrl,
            model = normalizedModel,
            apiToken = apiToken,
        )
        saveCustomPresets(existing + preset)
        return preset
    }

    private fun saveCustomPresets(presets: List<SavedProviderPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.displayName)
                    .put("baseUrl", preset.baseUrl)
                    .put("model", preset.model),
            )
        }
        prefs.edit {
            putString(KEY_CUSTOM_PRESETS, array.toString())
        }
        securePrefs.edit {
            presets.forEach { preset ->
                putString(customPresetTokenKey(preset.id), preset.apiToken)
            }
        }
    }

    private fun customPresetTokenKey(id: String): String = "${KEY_CUSTOM_PRESET_TOKEN_PREFIX}$id"

    private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
        key: String,
        default: T,
    ): T {
        val stored = getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == stored } ?: default
    }

    private companion object {
        const val PREFS_NAME = "openime_settings"
        const val SECURE_PREFS_NAME = "openime_secure_settings"
        const val KEY_PROVIDER = "provider"
        const val KEY_PROVIDER_PRESET = "provider_preset"
        const val KEY_CUSTOM_PRESETS = "custom_presets"
        const val KEY_BASE_URL = "base_url"
        const val KEY_MODEL = "model"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_APP_LANGUAGE_CHOICE = "app_language_choice"
        const val KEY_LANGUAGE_CHOICE = "language_choice"
        const val KEY_CUSTOM_LANGUAGE = "custom_language"
        const val KEY_TRAILING_SPACE = "trailing_space"
        const val KEY_HIDE_AFTER_SUCCESS = "hide_after_success"
        const val KEY_CONFIRM_BEFORE_INSERT = "confirm_before_insert"
        const val KEY_SELECT_INSERTED_TEXT = "select_inserted_text"
        const val KEY_CUSTOM_PRESET_TOKEN_PREFIX = "custom_preset_token_"
    }
}
