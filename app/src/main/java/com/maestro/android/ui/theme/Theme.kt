package com.maestro.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF1A1A2E)
val Surface = Color(0xFF16213E)
val SurfaceHover = Color(0xFF1A2745)
val Primary = Color(0xFFE94560)
val PrimaryHover = Color(0xFFC73652)
val TextColor = Color(0xFFEEEEEE)
val TextMuted = Color(0xFF999999)
val Border = Color(0xFF2A2A4A)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = TextColor,
    secondary = PrimaryHover,
    background = Bg,
    surface = Surface,
    surfaceVariant = SurfaceHover,
    onBackground = TextColor,
    onSurface = TextColor,
    onSurfaceVariant = TextMuted,
    outline = Border,
)

@Composable
fun MaestroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
