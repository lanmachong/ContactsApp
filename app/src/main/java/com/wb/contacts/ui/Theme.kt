package com.wb.contacts.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF0A2E6E),
    secondary = Color(0xFF5B6478),
    onSecondary = Color.White,
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF1B1F2A),
    surface = Color.White,
    onSurface = Color(0xFF1B1F2A),
    surfaceVariant = Color(0xFFEEF1F7),
    onSurfaceVariant = Color(0xFF4A5266),
    outline = Color(0xFFC9CFDB),
    error = Color(0xFFB3261E),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DBBFF),
    onPrimary = Color(0xFF0A2E6E),
    primaryContainer = Color(0xFF1B3C7A),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFFB9C2D6),
    onSecondary = Color(0xFF25304A),
    background = Color(0xFF12151C),
    onBackground = Color(0xFFE7EAF2),
    surface = Color(0xFF1B1F28),
    onSurface = Color(0xFFE7EAF2),
    surfaceVariant = Color(0xFF2A3040),
    onSurfaceVariant = Color(0xFFC4CBDA),
    outline = Color(0xFF565E70),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val AvatarPalette = listOf(
    Color(0xFF2563EB), Color(0xFF0D9488), Color(0xFF7C3AED),
    Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFF0891B2),
    Color(0xFF4F46E5), Color(0xFF16A34A)
)

fun avatarColor(seed: String): Color =
    AvatarPalette[(seed.hashCode() and Int.MAX_VALUE) % AvatarPalette.size]

@Composable
fun ContactsAppTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
