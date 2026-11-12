package dev.rankis.openime.settings

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import dev.rankis.openime.R

@get:StringRes
val AfterInsertAction.titleRes: Int
    get() = when (this) {
        AfterInsertAction.SwitchKeyboard -> R.string.after_insert_switch
        AfterInsertAction.HidePanel -> R.string.after_insert_hide
        AfterInsertAction.KeepDictating -> R.string.after_insert_keep
    }

@get:StringRes
private val AfterInsertAction.summaryRes: Int
    get() = when (this) {
        AfterInsertAction.SwitchKeyboard -> R.string.after_insert_switch_summary
        AfterInsertAction.HidePanel -> R.string.after_insert_hide_summary
        AfterInsertAction.KeepDictating -> R.string.after_insert_keep_summary
    }

class BehaviorSettingsActivity : SettingsScreenActivity() {
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setScreenContent(R.layout.activity_behavior_settings, R.string.screen_behavior)
        store = SettingsStore(this)
        val settings = store.load()
        bindAfterInsertChoice(AfterInsertAction.of(settings))
        bindSwitches(settings)
    }

    private fun bindAfterInsertChoice(current: AfterInsertAction) {
        val group = findViewById<RadioGroup>(R.id.afterInsertGroup)
        AfterInsertAction.entries.forEach { action ->
            val option = LayoutInflater.from(this).inflate(R.layout.settings_radio_option, group, false) as RadioButton
            option.id = View.generateViewId()
            option.text = twoLineLabel(getString(action.titleRes), getString(action.summaryRes))
            option.tag = action
            group.addView(option)
            if (action == current) group.check(option.id)
        }
        group.setOnCheckedChangeListener { radioGroup, checkedId ->
            val action = radioGroup.findViewById<View>(checkedId)?.tag as? AfterInsertAction ?: return@setOnCheckedChangeListener
            store.update { it.withAfterInsertAction(action) }
        }
    }

    private fun bindSwitches(settings: AppSettings) {
        val insertion = findViewById<LinearLayout>(R.id.insertionRows)
        insertion.addSwitchRow(
            getString(R.string.settings_confirm_before_insert),
            getString(R.string.settings_confirm_before_insert_summary),
            settings.confirmBeforeInsert,
        ) { checked -> store.update { it.copy(confirmBeforeInsert = checked) } }
        insertion.addSwitchRow(
            getString(R.string.settings_select_inserted_text),
            getString(R.string.settings_select_inserted_text_summary),
            settings.selectInsertedText,
        ) { checked -> store.update { it.copy(selectInsertedText = checked) } }
        insertion.addSwitchRow(
            getString(R.string.settings_append_trailing_space),
            getString(R.string.settings_append_trailing_space_summary),
            settings.appendTrailingSpace,
        ) { checked -> store.update { it.copy(appendTrailingSpace = checked) } }
        findViewById<LinearLayout>(R.id.cancelRows).addSwitchRow(
            getString(R.string.settings_hide_after_cancel),
            getString(R.string.settings_hide_after_cancel_summary),
            settings.hideAfterCancel,
        ) { checked -> store.update { it.copy(hideAfterCancel = checked) } }
    }

    private fun twoLineLabel(title: String, summary: String): CharSequence {
        val text = SpannableStringBuilder(title).append('\n')
        val start = text.length
        text.append(summary)
        text.setSpan(RelativeSizeSpan(0.82f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(this, R.color.ime_muted)),
            start,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        return text
    }
}
