package com.kupuproxy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF00C2A8)
private val TealLight = Color(0xFF3DDFC8)
private val Indigo = Color(0xFF5B6CFF)
private val IndigoLight = Color(0xFFA8B2FF)
private val Coral = Color(0xFFFF7A59)
private val CoralLight = Color(0xFFFF9B82)

/**
 * Цвета индикаторов задержки. Раньше они были объявлены в `res/values/colors.xml`
 * (`ping_excellent/good/slow`), но нигде не использовались: статус прокси передавался
 * только порядком в списке.
 */
data class KupuStatusColors(
    val excellent: Color,
    val good: Color,
    val fair: Color,
    val poor: Color,
    val offline: Color,
)

val LocalStatusColors: ProvidableCompositionLocal<KupuStatusColors> = staticCompositionLocalOf {
    KupuStatusColors(
        excellent = Color(0xFF12B76A),
        good = Color(0xFFF79009),
        fair = Color(0xFFF04438),
        poor = Color(0xFFBA1A1A),
        offline = Color(0xFF6F7976),
    )
}

private val BaseLightColors =
    lightColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD4F7F1),
        onPrimaryContainer = Color(0xFF003730),
        secondary = Indigo,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFE0E4FF),
        onSecondaryContainer = Color(0xFF121848),
        tertiary = Coral,
        onTertiary = Color.White,
        background = Color(0xFFF4F7FB),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFE8EEF5),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF5B6B7C),
        outline = Color(0xFFC9D4E0),
        outlineVariant = Color(0xFFC9D4E0),
    )

private val BaseDarkColors =
    darkColorScheme(
        primary = TealLight,
        onPrimary = Color(0xFF003730),
        primaryContainer = Color(0xFF0A3D36),
        onPrimaryContainer = Color(0xFFD4F7F1),
        secondary = IndigoLight,
        onSecondary = Color(0xFF121848),
        secondaryContainer = Color(0xFF2A3370),
        onSecondaryContainer = Color(0xFFE0E4FF),
        tertiary = CoralLight,
        onTertiary = Color(0xFF5A1500),
        background = Color(0xFF0B1220),
        onBackground = Color(0xFFE8EEF7),
        surface = Color(0xFF121A2B),
        surfaceVariant = Color(0xFF1A2438),
        onSurface = Color(0xFFE8EEF7),
        onSurfaceVariant = Color(0xFF9AABC0),
        outline = Color(0xFF2C3A52),
        outlineVariant = Color(0xFF2C3A52),
    )

private val KupuTypography =
    Typography(
        headlineSmall =
            Typography()
                .headlineSmall
                .copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )

@Composable
fun KupuProxyTheme(
    settingsOverride: AppearanceSettings? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { AppearancePreferences.preferences(context) }
    var storedSettings by remember(context) { mutableStateOf(AppearancePreferences.load(context)) }

    DisposableEffect(prefs) {
        val listener =
            android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                storedSettings = AppearancePreferences.load(context)
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val settings = settingsOverride ?: storedSettings
    val systemDark = isSystemInDarkTheme()
    val darkTheme =
        when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val colors =
        when {
            settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> personalizedScheme(settings, darkTheme)
        }
    val density = LocalDensity.current
    val statusColors =
        remember(colors, darkTheme) {
            KupuStatusColors(
                excellent = if (darkTheme) Color(0xFF32D583) else Color(0xFF12B76A),
                good = if (darkTheme) Color(0xFFFDB022) else Color(0xFFF79009),
                fair = if (darkTheme) Color(0xFFF97066) else Color(0xFFF04438),
                poor = colors.error,
                offline = colors.onSurfaceVariant,
            )
        }
    CompositionLocalProvider(
        LocalDensity provides
            Density(density.density, density.fontScale * settings.fontScale.multiplier),
        LocalStatusColors provides statusColors,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = KupuTypography,
            shapes = shapesFor(settings.cornerStyle),
            content = content,
        )
    }
}

private fun personalizedScheme(settings: AppearanceSettings, dark: Boolean): ColorScheme {
    val primaryRaw =
        if (settings.palette == ColorPalette.CUSTOM) {
            colorFromHex(settings.customPrimary) ?: Color(ColorPalette.KUPU.primary)
        } else Color(settings.palette.primary)
    val accentRaw =
        if (settings.palette == ColorPalette.CUSTOM) {
            colorFromHex(settings.customAccent) ?: Color(ColorPalette.KUPU.accent)
        } else Color(settings.palette.accent)

    val primary = if (dark) blend(primaryRaw, Color.White, 0.35f) else primaryRaw
    val secondary = if (dark) blend(accentRaw, Color.White, 0.35f) else accentRaw
    val base = if (dark) BaseDarkColors else BaseLightColors
    val primaryContainer =
        if (dark) blend(primaryRaw, Color.Black, 0.35f) else blend(primaryRaw, Color.White, 0.78f)
    val secondaryContainer =
        if (dark) blend(accentRaw, Color.Black, 0.30f) else blend(accentRaw, Color.White, 0.80f)

    return base.copy(
        primary = primary,
        onPrimary = bestOnColor(primary),
        primaryContainer = primaryContainer,
        onPrimaryContainer = bestOnColor(primaryContainer),
        secondary = secondary,
        onSecondary = bestOnColor(secondary),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = bestOnColor(secondaryContainer),
        tertiary = blend(primary, secondary, 0.5f),
    )
}

private fun shapesFor(style: CornerStyle): Shapes =
    when (style) {
        CornerStyle.COMPACT ->
            Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(12.dp),
                extraLarge = RoundedCornerShape(16.dp),
            )
        CornerStyle.BALANCED ->
            Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp),
                extraLarge = RoundedCornerShape(30.dp),
            )
        CornerStyle.ROUND ->
            Shapes(
                extraSmall = RoundedCornerShape(12.dp),
                small = RoundedCornerShape(18.dp),
                medium = RoundedCornerShape(24.dp),
                large = RoundedCornerShape(30.dp),
                extraLarge = RoundedCornerShape(36.dp),
            )
    }

private fun colorFromHex(value: String): Color? {
    val normalized = normalizeHexColor(value) ?: return null
    return normalized.removePrefix("#").toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

private fun blend(start: Color, end: Color, amount: Float): Color =
    Color(
        red = start.red + (end.red - start.red) * amount,
        green = start.green + (end.green - start.green) * amount,
        blue = start.blue + (end.blue - start.blue) * amount,
        alpha = 1f,
    )

private fun bestOnColor(background: Color): Color =
    if (background.luminance() > 0.42f) Color.Black else Color.White
