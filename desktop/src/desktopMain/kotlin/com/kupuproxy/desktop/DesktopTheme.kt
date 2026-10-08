package com.kupuproxy.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kupuproxy.shared.design.KupuColors
import com.kupuproxy.shared.design.KupuRadius

/** Цвета статуса задержки. Раньше были объявлены в colors.xml, но не использовались. */
data class KupuStatusColors(
    val excellent: Color,
    val good: Color,
    val slow: Color,
    val offline: Color,
)

val LocalStatusColors: ProvidableCompositionLocal<KupuStatusColors> = staticCompositionLocalOf {
    KupuStatusColors(
        excellent = Color.Unspecified,
        good = Color.Unspecified,
        slow = Color.Unspecified,
        offline = Color.Unspecified,
    )
}

private fun lightScheme(): ColorScheme = lightColorScheme(
    primary = Color(KupuColors.Teal),
    onPrimary = Color(KupuColors.OnPrimaryLight),
    primaryContainer = Color(KupuColors.PrimaryContainerLight),
    onPrimaryContainer = Color(KupuColors.OnPrimaryContainerLight),
    secondary = Color(KupuColors.Indigo),
    onSecondary = Color(KupuColors.OnSecondaryLight),
    secondaryContainer = Color(KupuColors.SecondaryContainerLight),
    onSecondaryContainer = Color(KupuColors.OnSecondaryContainerLight),
    tertiary = Color(KupuColors.Coral),
    background = Color(KupuColors.BackgroundLight),
    onBackground = Color(KupuColors.OnSurfaceLight),
    surface = Color(KupuColors.SurfaceLight),
    onSurface = Color(KupuColors.OnSurfaceLight),
    surfaceVariant = Color(KupuColors.SurfaceVariantLight),
    onSurfaceVariant = Color(KupuColors.OnSurfaceVariantLight),
    outline = Color(KupuColors.OutlineLight),
    outlineVariant = Color(KupuColors.OutlineLight),
    error = Color(KupuColors.ErrorLight),
    onError = Color(KupuColors.OnErrorLight),
)

private fun darkScheme(): ColorScheme = darkColorScheme(
    primary = Color(KupuColors.TealLight),
    onPrimary = Color(KupuColors.OnPrimaryDark),
    primaryContainer = Color(KupuColors.PrimaryContainerDark),
    onPrimaryContainer = Color(KupuColors.OnPrimaryContainerDark),
    secondary = Color(KupuColors.IndigoLight),
    onSecondary = Color(KupuColors.OnSecondaryDark),
    secondaryContainer = Color(KupuColors.SecondaryContainerDark),
    onSecondaryContainer = Color(KupuColors.OnSecondaryContainerDark),
    tertiary = Color(KupuColors.CoralLight),
    background = Color(KupuColors.BackgroundDark),
    onBackground = Color(KupuColors.OnSurfaceDark),
    surface = Color(KupuColors.SurfaceDark),
    onSurface = Color(KupuColors.OnSurfaceDark),
    surfaceVariant = Color(KupuColors.SurfaceVariantDark),
    onSurfaceVariant = Color(KupuColors.OnSurfaceVariantDark),
    outline = Color(KupuColors.OutlineDark),
    outlineVariant = Color(KupuColors.OutlineDark),
    error = Color(KupuColors.ErrorDark),
    onError = Color(KupuColors.OnErrorDark),
)

private fun statusColors(dark: Boolean): KupuStatusColors = KupuStatusColors(
    excellent = Color(if (dark) KupuColors.PingExcellentDark else KupuColors.PingExcellentLight),
    good = Color(if (dark) KupuColors.PingGoodDark else KupuColors.PingGoodLight),
    slow = Color(if (dark) KupuColors.PingSlowDark else KupuColors.PingSlowLight),
    offline = Color(KupuColors.OnSurfaceVariantDark),
)

/**
 * Та же типографика, что и на Android: те же размеры и начертания, тот же радиус 18.dp у карточек.
 */
private fun typography(): Typography {
    val base = Typography()
    return base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun KupuDesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = remember(darkTheme) { if (darkTheme) darkScheme() else lightScheme() }
    val shapes = remember {
        Shapes(
            extraSmall = RoundedCornerShape(KupuRadius.small),
            small = RoundedCornerShape(KupuRadius.small),
            medium = RoundedCornerShape(KupuRadius.medium),
            large = RoundedCornerShape(KupuRadius.large),
            extraLarge = RoundedCornerShape(KupuRadius.xl),
        )
    }
    CompositionLocalProvider(LocalStatusColors provides statusColors(darkTheme)) {
        MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography(), content = content)
    }
}