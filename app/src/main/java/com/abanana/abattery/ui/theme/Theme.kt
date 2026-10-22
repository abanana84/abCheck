package com.abanana.abattery.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class AbatteryColors(
    val background: Color,
    val surfaceLow: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val primaryGreen: Color,
    val primaryContainer: Color,
    val secondary: Color,
    val onSurface: Color,
    val onSurfaceVar: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
)

private val DarkAbatteryColors =
    AbatteryColors(
        background = BgDark,
        surfaceLow = SurfaceLow,
        surfaceHigh = SurfaceHigh,
        surfaceHighest = SurfaceHighest,
        primaryGreen = PrimaryGreen,
        primaryContainer = PrimaryContainer,
        secondary = SecondaryColor,
        onSurface = OnSurface,
        onSurfaceVar = OnSurfaceVar,
        outline = OutlineColor,
        outlineVariant = OutlineVariant,
        error = ErrorColor,
    )

private val LightAbatteryColors =
    AbatteryColors(
        background = LightBg,
        surfaceLow = LightSurfaceLow,
        surfaceHigh = LightSurfaceHigh,
        surfaceHighest = LightSurfaceHighest,
        primaryGreen = LightPrimaryGreen,
        primaryContainer = LightPrimaryContainer,
        secondary = LightSecondary,
        onSurface = LightOnSurface,
        onSurfaceVar = LightOnSurfaceVar,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = LightError,
    )

private val AppDarkMaterial =
    darkColorScheme(
        background = BgDark,
        surface = SurfaceLow,
        surfaceVariant = SurfaceHigh,
        surfaceContainerLow = SurfaceLow,
        surfaceContainerHigh = SurfaceHigh,
        surfaceContainer = SurfaceLow,
        surfaceContainerHighest = SurfaceHighest,
        surfaceContainerLowest = BgDark,
        primary = PrimaryGreen,
        onPrimary = BgDark,
        primaryContainer = PrimaryContainer,
        onPrimaryContainer = OnSurface,
        secondary = SecondaryColor,
        secondaryContainer = PrimaryContainer,
        onSecondaryContainer = OnSurface,
        onBackground = OnSurface,
        onSurface = OnSurface,
        onSurfaceVariant = OnSurfaceVar,
        outline = OutlineColor,
        outlineVariant = OutlineVariant,
        error = ErrorColor,
    )

private val AppLightMaterial =
    lightColorScheme(
        background = LightBg,
        surface = LightSurfaceLow,
        surfaceVariant = LightSurfaceHigh,
        surfaceContainerLow = LightSurfaceLow,
        surfaceContainerHigh = LightSurfaceHigh,
        surfaceContainer = LightSurfaceLow,
        surfaceContainerHighest = LightSurfaceHigh,
        surfaceContainerLowest = LightBg,
        primary = LightPrimaryGreen,
        onPrimary = LightSurfaceLow,
        primaryContainer = LightPrimaryContainer,
        onPrimaryContainer = LightOnSurface,
        secondary = LightSecondary,
        secondaryContainer = LightPrimaryContainer,
        onSecondaryContainer = LightOnSurface,
        onBackground = LightOnSurface,
        onSurface = LightOnSurface,
        onSurfaceVariant = LightOnSurfaceVar,
        outline = LightOutline,
        outlineVariant = LightOutlineVariant,
        error = LightError,
    )

private val LocalAbatteryColors = staticCompositionLocalOf { DarkAbatteryColors }

object AppTheme {
    val colors: AbatteryColors
        @Composable @ReadOnlyComposable get() = LocalAbatteryColors.current
}

@Composable
fun ABatteryTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val extended = if (darkTheme) DarkAbatteryColors else LightAbatteryColors
    val material = if (darkTheme) AppDarkMaterial else AppLightMaterial
    CompositionLocalProvider(LocalAbatteryColors provides extended) {
        MaterialTheme(
            colorScheme = material,
            typography = CheckTypography,
            shapes =
                Shapes(
                    extraSmall = RoundedCornerShape(4.dp),
                    small = RoundedCornerShape(8.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(18.dp),
                    extraLarge = RoundedCornerShape(22.dp),
                ),
            content = content,
        )
    }
}

@Composable
fun rememberThemeDark(themeMode: ThemeMode): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}
