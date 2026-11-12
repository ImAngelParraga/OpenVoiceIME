package dev.rankis.openime.settings

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import dev.rankis.openime.R
import java.util.Locale

class DictationSettingsActivity : SettingsScreenActivity() {
    private lateinit var store: SettingsStore
    private lateinit var quickLanguagesList: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchResults: LinearLayout
    private val languageOptions = transcriptionLanguageOptions(Locale.getDefault())
    private var quickLanguages: List<String?> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_dictation_settings, R.string.screen_dictation)
        store = SettingsStore(this)
        quickLanguagesList = findViewById(R.id.quickLanguagesList)
        searchInput = findViewById(R.id.languageSearchInput)
        searchResults = findViewById(R.id.languageSearchResults)
        findViewById<TextView>(R.id.quickLanguagesHelp).text =
            getString(R.string.dictation_quick_languages_help, MAX_FAVORITE_TRANSCRIPTION_LANGUAGES)

        val settings = store.load()
        quickLanguages = normalizeFavoriteTranscriptionLanguageCodes(settings.favoriteTranscriptionLanguageCodes)
        renderQuickLanguages()
        bindSearch()
        bindPrompt(settings.transcriptionPrompt)
    }

    private fun bindSearch() {
        searchInput.doAfterTextChanged { renderSearchResults(it?.toString().orEmpty().trim()) }
        searchInput.setOnEditorActionListener { view, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            hideKeyboard(view)
            true
        }
    }

    private fun bindPrompt(prompt: String) {
        val input = findViewById<EditText>(R.id.transcriptionPromptInput)
        input.setText(prompt)
        input.doAfterTextChanged { text -> store.update { it.copy(transcriptionPrompt = text?.toString().orEmpty()) } }
    }

    private fun renderQuickLanguages() {
        quickLanguagesList.removeAllViews()
        quickLanguages.forEach { code ->
            val row = SettingsRow.addTo(quickLanguagesList).bind(
                title = code?.let(::languageLabel) ?: getString(R.string.language_auto),
                summary = if (code == null) getString(R.string.language_auto_summary) else null,
            )
            if (code != null) {
                row.trailingAction("×", getString(R.string.language_remove_format, languageLabel(code))) {
                    saveQuickLanguages(quickLanguages - code)
                }
            }
        }
        val full = quickLanguages.size >= MAX_FAVORITE_TRANSCRIPTION_LANGUAGES
        searchInput.isEnabled = !full
        searchInput.hint = getString(if (full) R.string.toast_language_favorite_limit else R.string.hint_search_language)
    }

    private fun renderSearchResults(query: String) {
        searchResults.removeAllViews()
        if (query.isBlank()) {
            searchResults.visibility = View.GONE
            return
        }
        searchResults.visibility = View.VISIBLE
        val matches = languageOptions.filter { option ->
            option.code != null && option.code !in quickLanguages &&
                (option.label.contains(query, ignoreCase = true) || option.code.contains(query, ignoreCase = true))
        }.take(MAX_SEARCH_RESULTS)
        if (matches.isEmpty()) {
            SettingsRow.addTo(searchResults).bind(title = getString(R.string.language_no_matches))
            return
        }
        matches.forEach { option ->
            SettingsRow.addTo(searchResults).bind(title = option.label) { addQuickLanguage(option.code) }
                .trailingAction("+", getString(R.string.language_add_format, option.label)) { addQuickLanguage(option.code) }
        }
    }

    private fun addQuickLanguage(code: String?) {
        saveQuickLanguages(quickLanguages + code)
        searchInput.setText("")
        hideKeyboard(searchInput)
        Toast.makeText(this, R.string.toast_language_favorite_added, Toast.LENGTH_SHORT).show()
    }

    private fun saveQuickLanguages(codes: List<String?>) {
        quickLanguages = normalizeFavoriteTranscriptionLanguageCodes(codes)
        store.update { it.copy(favoriteTranscriptionLanguageCodes = quickLanguages) }
        renderQuickLanguages()
    }

    private fun languageLabel(code: String): String =
        languageOptions.firstOrNull { it.code == code }?.label ?: code.uppercase(Locale.ROOT)

    private fun hideKeyboard(view: View) {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    private companion object {
        const val MAX_SEARCH_RESULTS = 6
    }
}
