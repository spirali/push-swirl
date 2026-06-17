package org.kreatrix.pushswirl

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    val colorScheme = if (dark) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
