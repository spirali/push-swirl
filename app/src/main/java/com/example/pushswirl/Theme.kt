package com.example.pushswirl

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PinkPrimary = Color(0xFFFF94C2)
private val PinkSecondary = Color(0xFFFFB3D9)
private val LavenderPrimary = Color(0xFFDDB3FF)
/*private val PeachAccent = Color(0xFFFFDAB9)
private val MintAccent = Color(0xFFB5EAD7)*/

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

@Composable
fun PushSwirlTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}