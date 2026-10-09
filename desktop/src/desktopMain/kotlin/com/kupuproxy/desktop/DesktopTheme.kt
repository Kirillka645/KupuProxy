package com.kupuproxy.desktop

import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.LightDefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Цвета статуса задержки. */
data class KupuStatusColors(
    val excellent: Color,
    val good: Color,
    val slow: Color,
    val offline: Color,
)

val LocalStatusColors: ProvidableCompositionLocal<KupuStatusColors> = staticCompositionLocalOf {
    KupuStatusColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified)
}

/**
 * Палитра Fluent 2 (Windows 11): слой Mica, карточки с тонкой обводкой, акцентная заливка,
 * «тихие» (subtle) заливки для наведения и цвета InfoBar.
 */
@Immutable
data class FluentColors(
    val isDark: Boolean,
    val accent: Color,
    val accentHover: Color,
    val accentPressed: Color,
    val onAccent: Color,
    /** Акцент для текста и иконок на обычном фоне. */
    val accentText: Color,
    val mica: Color,
    val layer: Color,
    val layerStroke: Color,
    val card: Color,
    val cardHover: Color,
    val cardStroke: Color,
    val divider: Color,
    val subtleHover: Color,
    val subtlePressed: Color,
    val control: Color,
    val controlHover: Color,
    val controlPressed: Color,
    val controlStroke: Color,
    val controlStrokeBottom: Color,
    val controlStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val success: Color,
    val caution: Color,
    val critical: Color,
    val info: Color,
    val successBg: Color,
    val cautionBg: Color,
    val criticalBg: Color,
    val infoBg: Color,
    val flyout: Color,
)

/** Типографика Fluent 2: Caption 12, Body 14, Body Strong, Subtitle 20, Title 28, Title Large 40. */
@Immutable
data class FluentType(
    val caption: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val bodyLarge: TextStyle,
    val subtitle: TextStyle,
    val title: TextStyle,
    val titleLarge: TextStyle,
)

val LocalFluentColors: ProvidableCompositionLocal<FluentColors> = staticCompositionLocalOf { fluentColors(false, AccentPair.Teal) }
val LocalFluentType: ProvidableCompositionLocal<FluentType> = staticCompositionLocalOf { fluentType() }

/** Доступ к токенам: `Fluent.colors.accent`, `Fluent.type.body`. */
object Fluent {
    val colors: FluentColors @Composable get() = LocalFluentColors.current
    val type: FluentType @Composable get() = LocalFluentType.current

    val controlRadius = 4.dp
    val overlayRadius = 8.dp
    val controlHeight = 32.dp
}

/** Акцент для светлой и тёмной темы (в Windows 11 это разные оттенки одной палитры). */
data class AccentPair(val light: Color, val dark: Color) {
    companion object {
        val Teal = AccentPair(light = Color(0xFF00796B), dark = Color(0xFF3DDFC8))
        val Blue = AccentPair(light = Color(0xFF005FB8), dark = Color(0xFF60CDFF))
    }
}

fun fluentColors(dark: Boolean, accentPair: AccentPair): FluentColors {
    val accent = if (dark) accentPair.dark else accentPair.light
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF000000) else Color(0xFFFFFFFF)
    return if (dark) {
        val mica = Color(0xFF202020)
        FluentColors(
            isDark = true,
            accent = accent,
            accentHover = accent.copy(alpha = 0.9f).compositeOver(mica),
            accentPressed = accent.copy(alpha = 0.8f).compositeOver(mica),
            onAccent = onAccent,
            accentText = accent,
            mica = mica,
            layer = Color(0xFF272727),
            layerStroke = Color(0xFF1C1C1C),
            card = Color(0xFF2D2D2D),
            cardHover = Color(0xFF323232),
            cardStroke = Color(0xFF1F1F1F),
            divider = Color(0x15FFFFFF),
            subtleHover = Color(0x0FFFFFFF),
            subtlePressed = Color(0x0AFFFFFF),
            control = Color(0xFF2D2D2D),
            controlHover = Color(0xFF323232),
            controlPressed = Color(0xFF272727),
            controlStroke = Color(0x12FFFFFF),
            controlStrokeBottom = Color(0x8BFFFFFF),
            controlStrong = Color(0x8BFFFFFF),
            textPrimary = Color(0xFFFFFFFF),
            textSecondary = Color(0xC8FFFFFF),
            textTertiary = Color(0x8BFFFFFF),
            textDisabled = Color(0x5DFFFFFF),
            success = Color(0xFF6CCB5F),
            caution = Color(0xFFFCE100),
            critical = Color(0xFFFF99A4),
            info = accent,
            successBg = Color(0xFF393D1B),
            cautionBg = Color(0xFF433519),
            criticalBg = Color(0xFF442726),
            infoBg = Color(0xFF2F2F2F),
            flyout = Color(0xFF2C2C2C),
        )
    } else {
        val mica = Color(0xFFF3F3F3)
        FluentColors(
            isDark = false,
            accent = accent,
            accentHover = accent.copy(alpha = 0.9f).compositeOver(Color.White),
            accentPressed = accent.copy(alpha = 0.8f).compositeOver(Color.White),
            onAccent = onAccent,
            accentText = accent,
            mica = mica,
            layer = Color(0xFFF9F9F9),
            layerStroke = Color(0xFFE5E5E5),
            card = Color(0xFFFFFFFF),
            cardHover = Color(0xFFF9F9F9),
            cardStroke = Color(0xFFE5E5E5),
            divider = Color(0x14000000),
            subtleHover = Color(0x09000000),
            subtlePressed = Color(0x06000000),
            control = Color(0xFFFFFFFF),
            controlHover = Color(0xFFF9F9F9),
            controlPressed = Color(0xFFF5F5F5),
            controlStroke = Color(0x0F000000),
            controlStrokeBottom = Color(0x72000000),
            controlStrong = Color(0x72000000),
            textPrimary = Color(0xE4000000),
            textSecondary = Color(0x9E000000),
            textTertiary = Color(0x72000000),
            textDisabled = Color(0x5C000000),
            success = Color(0xFF0F7B0F),
            caution = Color(0xFF9D5D00),
            critical = Color(0xFFC42B1C),
            info = accent,
            successBg = Color(0xFFDFF6DD),
            cautionBg = Color(0xFFFFF4CE),
            criticalBg = Color(0xFFFDE7E9),
            infoBg = Color(0xFFF6F6F6),
            flyout = Color(0xFFFCFCFC),
        )
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

fun fluentType(): FluentType {
    val base = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    return FluentType(
        caption = base.copy(fontSize = 12.sp, lineHeight = 16.sp),
        body = base,
        bodyStrong = base.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.copy(fontSize = 18.sp, lineHeight = 24.sp),
        subtitle = base.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        title = base.copy(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = base.copy(fontSize = 40.sp, lineHeight = 52.sp, fontWeight = FontWeight.SemiBold),
    )
}

private fun materialScheme(c: FluentColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accent.copy(alpha = 0.16f).compositeOver(c.card),
        onPrimaryContainer = c.textPrimary,
        secondary = c.accent,
        onSecondary = c.onAccent,
        secondaryContainer = c.subtleHover.compositeOver(c.card),
        onSecondaryContainer = c.textPrimary,
        tertiary = c.caution,
        background = c.mica,
        onBackground = c.textPrimary,
        surface = c.card,
        onSurface = c.textPrimary,
        surfaceVariant = c.layer,
        onSurfaceVariant = c.textSecondary,
        surfaceContainer = c.flyout,
        surfaceContainerHigh = c.flyout,
        surfaceContainerLow = c.card,
        outline = c.controlStrong,
        outlineVariant = c.cardStroke,
        error = c.critical,
        onError = if (c.isDark) Color.Black else Color.White,
        errorContainer = c.criticalBg,
        onErrorContainer = c.textPrimary,
    )
}

private fun materialTypography(t: FluentType): Typography {
    val m = Typography()
    return m.copy(
        displaySmall = t.titleLarge,
        headlineLarge = t.titleLarge,
        headlineMedium = t.title,
        headlineSmall = t.title,
        titleLarge = t.subtitle,
        titleMedium = t.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.bodyStrong,
        bodyLarge = t.body,
        bodyMedium = t.body,
        bodySmall = t.caption,
        labelLarge = t.body,
        labelMedium = t.caption,
        labelSmall = t.caption,
    )
}

private fun statusColors(c: FluentColors): KupuStatusColors = KupuStatusColors(
    excellent = c.success,
    good = if (c.isDark) Color(0xFF9FD89F) else Color(0xFF498205),
    slow = if (c.isDark) Color(0xFFFCE100) else Color(0xFF9D5D00),
    offline = c.textTertiary,
)

/** Тема клиента: токены Fluent 2 плюс совместимая с ними тема Material для стандартных контролов. */
@Composable
fun KupuDesktopTheme(
    darkTheme: Boolean,
    accent: AccentMode = AccentMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val pair = remember(accent) {
        when (accent) {
            AccentMode.TEAL -> AccentPair.Teal
            AccentMode.BLUE -> AccentPair.Blue
            AccentMode.SYSTEM -> SystemAccent.pair ?: AccentPair.Teal
        }
    }
    val colors = remember(darkTheme, pair) { fluentColors(darkTheme, pair) }
    val type = remember { fluentType() }
    val scheme = remember(colors) { materialScheme(colors) }
    val shapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(Fluent.controlRadius),
            small = RoundedCornerShape(Fluent.controlRadius),
            medium = RoundedCornerShape(Fluent.overlayRadius),
            large = RoundedCornerShape(Fluent.overlayRadius),
            extraLarge = RoundedCornerShape(12.dp),
        )
    }
    CompositionLocalProvider(
        LocalFluentColors provides colors,
        LocalFluentType provides type,
        LocalStatusColors provides statusColors(colors),
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.35f)),
        LocalContextMenuRepresentation provides
            if (darkTheme) DarkDefaultContextMenuRepresentation else LightDefaultContextMenuRepresentation,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = materialTypography(type), content = content)
    }
}

/**
 * Системный акцентный цвет Windows 11 (Параметры → Персонализация → Цвета).
 * `AccentPalette` хранит 8 оттенков: для светлой темы Windows берёт «Dark 1», для тёмной — «Light 2».
 */
internal object SystemAccent {
    val pair: AccentPair? by lazy { runCatching { detect() }.getOrNull() }

    private fun detect(): AccentPair? {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (!os.contains("win")) return null
        val text = runCommand(
            "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Accent", "/v", "AccentPalette",
        ) ?: return null
        return parsePalette(text)
    }

    internal fun parsePalette(regOutput: String): AccentPair? {
        val hex = Regex("""AccentPalette\s+REG_BINARY\s+([0-9A-Fa-f]+)""").find(regOutput)?.groupValues?.get(1) ?: return null
        if (hex.length < 64) return null
        fun color(index: Int): Color {
            val o = index * 8
            val r = hex.substring(o, o + 2).toInt(16)
            val g = hex.substring(o + 2, o + 4).toInt(16)
            val b = hex.substring(o + 4, o + 6).toInt(16)
            return Color(r, g, b)
        }
        return AccentPair(light = color(4), dark = color(1))
    }

    private fun runCommand(vararg command: String): String? = runCatching {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0) output else null
    }.getOrNull()
}
