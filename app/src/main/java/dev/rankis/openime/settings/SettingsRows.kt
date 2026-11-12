package dev.rankis.openime.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import dev.rankis.openime.R

enum class StatusTone(val symbol: String, @ColorRes val color: Int) {
    Ok("✓", R.color.ime_accent),
    Attention("!", R.color.ime_danger),
    Pending("•", R.color.settings_warning),
    Neutral("•", R.color.ime_muted),
}

/** One tappable or informational line: optional status badge, title, summary and trailing affordance. */
class SettingsRow private constructor(val view: View) {
    private val badge: TextView = view.findViewById(R.id.rowBadge)
    private val title: TextView = view.findViewById(R.id.rowTitle)
    private val summary: TextView = view.findViewById(R.id.rowSummary)
    private val action: Button = view.findViewById(R.id.rowAction)
    private val chevron: View = view.findViewById(R.id.rowChevron)

    fun bind(
        title: CharSequence,
        summary: CharSequence? = null,
        tone: StatusTone? = null,
        showChevron: Boolean = false,
        onClick: (() -> Unit)? = null,
    ): SettingsRow {
        this.title.text = title
        this.summary.text = summary
        this.summary.visibility = if (summary.isNullOrBlank()) View.GONE else View.VISIBLE
        showTone(tone)
        chevron.visibility = if (showChevron) View.VISIBLE else View.GONE
        view.isClickable = onClick != null
        view.isFocusable = onClick != null
        view.setOnClickListener(onClick?.let { click -> View.OnClickListener { click() } })
        return this
    }

    private fun showTone(tone: StatusTone?) {
        badge.visibility = if (tone == null) View.GONE else View.VISIBLE
        tone ?: return
        badge.text = tone.symbol
        badge.setTextColor(ContextCompat.getColor(view.context, tone.color))
    }

    fun trailingAction(symbol: String, description: String, onClick: () -> Unit): SettingsRow {
        action.text = symbol
        action.contentDescription = description
        action.visibility = View.VISIBLE
        action.setOnClickListener { onClick() }
        return this
    }

    companion object {
        fun addTo(parent: ViewGroup): SettingsRow {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.settings_row, parent, false)
            parent.addView(view)
            return SettingsRow(view)
        }

        fun attach(view: View): SettingsRow = SettingsRow(view)
    }
}

fun ViewGroup.addSwitchRow(
    title: CharSequence,
    summary: CharSequence,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
): SwitchCompat {
    val row = LayoutInflater.from(context).inflate(R.layout.settings_switch_row, this, false)
    row.findViewById<TextView>(R.id.rowTitle).text = title
    row.findViewById<TextView>(R.id.rowSummary).text = summary
    val switch = row.findViewById<SwitchCompat>(R.id.rowSwitch)
    switch.isChecked = checked
    switch.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
    row.setOnClickListener { switch.toggle() }
    addView(row)
    return switch
}
