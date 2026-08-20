package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MtkBluePrimaryDarkTheme,
    onPrimary = Color(0xFF003354),
    primaryContainer = Color(0xFF004B76),
    onPrimaryContainer = Color(0xFFCCE5FF),
    secondary = MtkBlueSecondaryDarkTheme,
    onSecondary = Color(0xFF00325B),
    secondaryContainer = Color(0xFF00497E),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = MtkBlueTertiaryDarkTheme,
    background = MtkBackgroundDark,
    surface = MtkSurfaceDark,
    surfaceVariant = MtkCardDark,
    onBackground = MtkTextPrimaryDark,
    onSurface = MtkTextPrimaryDark,
    onSurfaceVariant = MtkTextSecondaryDark,
    outline = MtkBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = MtkBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF), // Light Blue 50
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = MtkBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F9FF),
    onSecondaryContainer = Color(0xFF0C4A6E),
    tertiary = MtkBlueTertiary,
    background = MtkBackgroundLight,
    surface = MtkSurfaceLight,
    surfaceVariant = MtkSurfaceVariantLight,
    onBackground = MtkTextPrimaryLight,
    onSurface = MtkTextPrimaryLight,
    onSurfaceVariant = MtkTextSecondaryLight,
    outline = MtkBorderLight,
    outlineVariant = Color(0xFFF1F5F9)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
