package com.iris.design.system

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.iris.design.system.colors.IrisColors

@Composable
fun IrisMaterial3Theme(
    isTrueBlack: Boolean,
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (dark) irisDarkColorScheme(isTrueBlack) else irisLightColorScheme(),
        content = content,
    )
}

private fun irisLightColorScheme(): ColorScheme = ColorScheme(
    primary = IrisColors.Purple.primary,
    onPrimary = IrisColors.White,
    primaryContainer = IrisColors.Purple.light,
    onPrimaryContainer = IrisColors.White,
    inversePrimary = IrisColors.Purple.dark,
    secondary = IrisColors.Green.primary,
    onSecondary = IrisColors.White,
    secondaryContainer = IrisColors.Green.light,
    onSecondaryContainer = IrisColors.White,
    tertiary = IrisColors.Green.primary,
    onTertiary = IrisColors.White,
    tertiaryContainer = IrisColors.Green.light,
    onTertiaryContainer = IrisColors.White,

    error = IrisColors.Red.primary,
    onError = IrisColors.White,
    errorContainer = IrisColors.Red.light,
    onErrorContainer = IrisColors.White,

    background = IrisColors.White,
    onBackground = IrisColors.Black,
    surface = IrisColors.White,
    onSurface = IrisColors.Black,
    surfaceVariant = IrisColors.ExtraLightGray,
    onSurfaceVariant = IrisColors.Black,
    surfaceTint = IrisColors.Black,
    inverseSurface = IrisColors.DarkGray,
    inverseOnSurface = IrisColors.White,

    outline = IrisColors.Gray,
    outlineVariant = IrisColors.DarkGray,
    scrim = IrisColors.ExtraDarkGray.copy(alpha = 0.8f)
)

private fun irisDarkColorScheme(isTrueBlack: Boolean): ColorScheme = ColorScheme(
    primary = IrisColors.Purple.primary,
    onPrimary = IrisColors.White,
    primaryContainer = IrisColors.Purple.light,
    onPrimaryContainer = IrisColors.White,
    inversePrimary = IrisColors.Purple.dark,
    secondary = IrisColors.Green.primary,
    onSecondary = IrisColors.White,
    secondaryContainer = IrisColors.Green.light,
    onSecondaryContainer = IrisColors.White,
    tertiary = IrisColors.Green.primary,
    onTertiary = IrisColors.White,
    tertiaryContainer = IrisColors.Green.light,
    onTertiaryContainer = IrisColors.White,

    error = IrisColors.Red.primary,
    onError = IrisColors.White,
    errorContainer = IrisColors.Red.light,
    onErrorContainer = IrisColors.White,

    background = if (isTrueBlack) IrisColors.TrueBlack else IrisColors.Black,
    onBackground = IrisColors.White,
    surface = if (isTrueBlack) IrisColors.TrueBlack else IrisColors.Black,
    onSurface = IrisColors.White,
    surfaceVariant = IrisColors.ExtraDarkGray,
    onSurfaceVariant = IrisColors.White,
    surfaceTint = IrisColors.White,
    inverseSurface = IrisColors.LightGray,
    inverseOnSurface = if (isTrueBlack) IrisColors.TrueBlack else IrisColors.Black,

    outline = IrisColors.Gray,
    outlineVariant = IrisColors.LightGray,
    scrim = IrisColors.ExtraLightGray.copy(alpha = 0.8f)
)
