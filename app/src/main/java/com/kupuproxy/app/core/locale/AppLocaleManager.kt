package com.kupuproxy.app.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

data class AppLocale(
    val languageTag: String,
    val nativeName: String
)

object AppLocaleManager {
    val supportedLocales: List<AppLocale> = listOf(
        AppLocale("ru", "Русский"),
        AppLocale("en", "English"),
        AppLocale("uk", "Українська"),
        AppLocale("de", "Deutsch"),
        AppLocale("fr", "Français"),
        AppLocale("es", "Español"),
        AppLocale("it", "Italiano"),
        AppLocale("pt-BR", "Português (Brasil)"),
        AppLocale("pl", "Polski"),
        AppLocale("tr", "Türkçe"),
        AppLocale("ar", "العربية"),
        AppLocale("fa", "فارسی"),
        AppLocale("hi", "हिन्दी"),
        AppLocale("id", "Bahasa Indonesia"),
        AppLocale("ja", "日本語"),
        AppLocale("ko", "한국어"),
        AppLocale("zh-CN", "简体中文"),
        AppLocale("zh-TW", "繁體中文"),
        AppLocale("vi", "Tiếng Việt"),
        AppLocale("kk", "Қазақша")
    )

    private val supportedTags = supportedLocales.mapTo(linkedSetOf()) { it.languageTag }

    fun currentTag(): String? {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .substringBefore(',')
            .takeIf(String::isNotBlank)
            ?: return null
        return normalizeTag(tag)
    }

    fun apply(languageTag: String?) {
        val normalized = languageTag?.let(::normalizeTag)
        val locales = if (normalized == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(normalized)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun normalizeTag(languageTag: String): String? = supportedTags.firstOrNull {
        it.equals(languageTag, ignoreCase = true)
    }
}
