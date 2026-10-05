package com.bastionzero.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** OLED survival palette: unlit pixels are truly off; red preserves night vision. */
object BastionColors {
    val Black = Color(0xFF000000)
    val Red = Color(0xFFFF3B30)
    val DimRed = Color(0xFFB02A22)
    val Ember = Color(0xFF2A0705)
    val Outline = Color(0xFF7A1D18)
}

private val BastionScheme = darkColorScheme(
    primary = BastionColors.Red,
    onPrimary = BastionColors.Black,
    secondary = BastionColors.DimRed,
    onSecondary = BastionColors.Black,
    background = BastionColors.Black,
    onBackground = BastionColors.Red,
    surface = BastionColors.Black,
    onSurface = BastionColors.Red,
    surfaceVariant = BastionColors.Black,
    onSurfaceVariant = BastionColors.DimRed,
    surfaceContainer = BastionColors.Black,
    surfaceContainerHigh = BastionColors.Ember,
    secondaryContainer = BastionColors.Ember,
    onSecondaryContainer = BastionColors.Red,
    outline = BastionColors.Outline,
    outlineVariant = BastionColors.Outline,
    error = BastionColors.Red,
    onError = BastionColors.Black,
)

@Composable
fun BastionTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BastionScheme, typography = Typography(), content = content)
}
