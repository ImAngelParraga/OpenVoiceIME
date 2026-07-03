package dev.rankis.openime.settings

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

fun Context.withAppLocale(choice: AppLanguageChoice = SettingsStore(this).loadAppLanguageChoice()): Context {
    val tag = choice.languageTag ?: return this
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(resources.configuration)
    config.setLocales(LocaleList(locale))
    return createConfigurationContext(config)
}
