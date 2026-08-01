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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Teal = Color(0xFF006C61)
private val TealLight = Color(0xFF6FDBC9)
private val Navy = Color(0xFF17324D)
private val Amber = Color(0xFF8A5100)

private val BaseLightColors =
    lightColorScheme(
        primary = Teal,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA9F2E4),
        onPrimaryContainer = Color(0xFF00201B),
        secondary = Navy,
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFD3E4FF),
        onSecondaryContainer = Color(0xFF001C38),
        tertiary = Amber,
        surface = Color(0xFFF8FAFA),
        surfaceVariant = Color(0xFFDAE5E1),
        onSurface = Color(0xFF171D1B),
        onSurfaceVariant = Color(0xFF3F4946),
        outline = Color(0xFF6F7976),
    )

private val BaseDarkColors =
    darkColorScheme(
        primary = TealLight,
        onPrimary = Color(0xFF003730),
        primaryContainer = Color(0xFF005047),
        onPrimaryContainer = Color(0xFFA9F2E4),
        secondary = Color(0xFFA1C9F7),
        onSecondary = Color(0xFF00315C),
        secondaryContainer = Color(0xFF194872),
        onSecondaryContainer = Color(0xFFD3E4FF),
        tertiary = Color(0xFFFFB95F),
        surface = Color(0xFF0F1513),
        surfaceVariant = Color(0xFF3F4946),
        onSurface = Color(0xFFE0E3E1),
        onSurfaceVariant = Color(0xFFBEC9C5),
        outline = Color(0xFF89938F),
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
    CompositionLocalProvider(
        LocalDensity provides
            Density(density.density, density.fontScale * settings.fontScale.multiplier)
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
