package com.kupuproxy.app.ui.theme

import android.content.Context
import android.content.SharedPreferences

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ColorPalette(val primary: Long, val accent: Long) {
    KUPU(0xFF006C61, 0xFF17324D),
    OCEAN(0xFF00639A, 0xFF4C5D92),
    VIOLET(0xFF6750A4, 0xFF7D5260),
    SUNSET(0xFF9C423D, 0xFF76546F),
    FOREST(0xFF386A20, 0xFF55624C),
    MONO(0xFF444746, 0xFF5F6368),
    CUSTOM(0xFF006C61, 0xFF17324D),
}

enum class CornerStyle {
    COMPACT,
    BALANCED,
    ROUND,
}

enum class FontScale(val multiplier: Float) {
    SMALL(0.90f),
    NORMAL(1.00f),
    LARGE(1.15f),
    EXTRA_LARGE(1.30f),
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val palette: ColorPalette = ColorPalette.KUPU,
    val customPrimary: String = "#006C61",
    val customAccent: String = "#17324D",
    val cornerStyle: CornerStyle = CornerStyle.BALANCED,
    val fontScale: FontScale = FontScale.NORMAL,
)

object AppearancePreferences {
    const val PREFS_NAME = "kupu_settings"
    private const val KEY_THEME = "theme"
    private const val KEY_DYNAMIC = "appearance_dynamic"
    private const val KEY_PALETTE = "appearance_palette"
    private const val KEY_PRIMARY = "appearance_primary"
    private const val KEY_ACCENT = "appearance_accent"
    private const val KEY_CORNERS = "appearance_corners"
    private const val KEY_FONT_SCALE = "appearance_font_scale"

    internal fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(context: Context): AppearanceSettings {
        val prefs = preferences(context)
        return AppearanceSettings(
            themeMode =
                when (prefs.getInt(KEY_THEME, 0)) {
                    1 -> ThemeMode.LIGHT
                    2 -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                },
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC, false),
            palette = enumValueOrDefault(prefs.getString(KEY_PALETTE, null), ColorPalette.KUPU),
            customPrimary = normalizeHexColor(prefs.getString(KEY_PRIMARY, null)) ?: "#006C61",
            customAccent = normalizeHexColor(prefs.getString(KEY_ACCENT, null)) ?: "#17324D",
            cornerStyle =
                enumValueOrDefault(prefs.getString(KEY_CORNERS, null), CornerStyle.BALANCED),
            fontScale = enumValueOrDefault(prefs.getString(KEY_FONT_SCALE, null), FontScale.NORMAL),
        )
    }

    fun save(context: Context, settings: AppearanceSettings) {
        preferences(context)
            .edit()
            .putInt(
                KEY_THEME,
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> 0
                    ThemeMode.LIGHT -> 1
                    ThemeMode.DARK -> 2
                },
            )
            .putBoolean(KEY_DYNAMIC, settings.dynamicColor)
            .putString(KEY_PALETTE, settings.palette.name)
            .putString(KEY_PRIMARY, normalizeHexColor(settings.customPrimary) ?: "#006C61")
            .putString(KEY_ACCENT, normalizeHexColor(settings.customAccent) ?: "#17324D")
            .putString(KEY_CORNERS, settings.cornerStyle.name)
            .putString(KEY_FONT_SCALE, settings.fontScale.name)
            .apply()
    }

    fun reset(context: Context) {
        preferences(context)
            .edit()
            .remove(KEY_THEME)
            .remove(KEY_DYNAMIC)
            .remove(KEY_PALETTE)
            .remove(KEY_PRIMARY)
            .remove(KEY_ACCENT)
            .remove(KEY_CORNERS)
            .remove(KEY_FONT_SCALE)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}

fun normalizeHexColor(input: String?): String? {
    val raw = input?.trim()?.removePrefix("#") ?: return null
    if (raw.length != 6 || raw.any { it !in "0123456789abcdefABCDEF" }) return null
    return "#${raw.uppercase()}"
}
