package org.kreatrix.pushswirl

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PinkPrimary = Color(0xFFFF94C2)
private val PinkSecondary = Color(0xFFFFB3D9)
private val LavenderPrimary = Color(0xFFDDB3FF)

private val LightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    secondary = PinkSecondary,
    tertiary = LavenderPrimary,
    background = Color(0xFFFFF5F7),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF4A4A4A),
    onSurface = Color(0xFF4A4A4A)
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    secondary = PinkSecondary,
    tertiary = LavenderPrimary,
    background = Color(0xFF1A1218),
    surface = Color(0xFF2A1F26),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFEDE0E7),
    onSurface = Color(0xFFEDE0E7)
)

@Composable
fun PushSwirlTheme(themeMode: ThemeMode = ThemeMode.AUTO, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content
    )
}
