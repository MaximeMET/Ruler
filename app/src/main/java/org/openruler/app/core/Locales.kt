package org.openruler.app.core

import android.content.Context
import android.content.res.Configuration
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Language handling.
 *
 * The app ships one `res/values-*` folder per language and follows the system language by
 * default; the settings screen can pin one language instead. The pinned language is applied
 * by wrapping the activity context, which works on every API level the app supports, and is
 * mirrored into the platform per-app language settings from Android 13 on.
 */
object Locales {

    /** Stored value that means "follow the system language". */
    const val SYSTEM = ""

    /** A language the user can pick, labelled in that language. */
    data class Choice(val tag: String, val label: String)

    /** Everything `res/values-*` provides, in the order the dialog lists it. */
    val choices = listOf(
        Choice("en", "English"),
        Choice("zh-CN", "简体中文"),
        Choice("zh-TW", "繁體中文"),
        Choice("ja", "日本語"),
        Choice("ko", "한국어"),
        Choice("es", "Español"),
        Choice("pt-BR", "Português (Brasil)"),
        Choice("fr", "Français"),
        Choice("de", "Deutsch"),
        Choice("it", "Italiano"),
        Choice("ru", "Русский"),
        Choice("tr", "Türkçe"),
        Choice("ar", "العربية"),
        Choice("hi", "हिन्दी"),
        Choice("in", "Bahasa Indonesia"),
        Choice("vi", "Tiếng Việt"),
        Choice("th", "ไทย")
    )

    /** Name of a stored tag, or `null` when the system language is used. */
    fun labelOf(tag: String): String? = choices.firstOrNull { it.tag == tag }?.label

    private fun localeOf(tag: String): Locale? =
        if (tag.isEmpty()) null else Locale.forLanguageTag(tag)

    /** Wraps an activity base context so the pinned language is used for its resources. */
    fun wrap(base: Context, tag: String): Context {
        val locale = localeOf(tag) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        return base.createConfigurationContext(config)
    }

    /** Stores a choice and keeps the platform per-app language in sync on Android 13+. */
    fun apply(context: Context, tag: String) {
        Prefs.get(context).language = tag
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            manager.applicationLocales = if (tag.isEmpty()) {
                LocaleList.getEmptyLocaleList()
            } else {
                LocaleList.forLanguageTags(tag)
            }
        }
    }
}
