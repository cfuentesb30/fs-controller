package com.fscontroller.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.darkColorScheme

/** Palette and type scale for a 10-foot UI. */
object Fs {
    val Background = Color(0xFF121212)
    val SurfaceRest = Color(0xFF2A2A2A)
    val SurfaceFocused = Color(0xFF3A3A3A)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB3B3B3)
    val Warning = Color(0xFFFFB74D)
    val WarningSurface = Color(0xFF2B2216)
    val Accent = Color(0xFF4F8CFF)
    val AccentFocused = Color(0xFF6FA2FF)
    val FocusBorder = Color.White

    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp
    val FocusBorderWidth = 3.dp
    const val FocusScale = 1.05f

    val Title = TextStyle(fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold)
    val RowLabel = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(fontSize = 20.sp, lineHeight = 26.sp)
    val Small = TextStyle(fontSize = 18.sp, lineHeight = 22.sp)
    val Mono = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontFamily = FontFamily.Monospace)
}

@Composable
fun FsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Fs.Background,
            surface = Fs.Background,
            onBackground = Fs.TextPrimary,
            onSurface = Fs.TextPrimary,
            primary = Fs.Accent,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            colors = SurfaceDefaults.colors(containerColor = Fs.Background, contentColor = Fs.TextPrimary),
        ) {
            ProvideTextStyle(Fs.Body) {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }
}
