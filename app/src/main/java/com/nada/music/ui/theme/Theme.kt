package com.nada.music.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF472B6),
    onPrimary = Color(0xFF3B0A24),
    primaryContainer = Color(0xFF5B1A3D),
    onPrimaryContainer = Color(0xFFFFD8E9),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    background = Color(0xFF0E0E10),
    onBackground = Color.White,
    surface = Color(0xFF0E0E10),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2629),
    onSurfaceVariant = Color.White.copy(alpha = 0.72f),
    surfaceContainer = Color(0xFF1C1A1C),
    surfaceContainerHigh = Color(0xFF262326),
)

/**
 * Aplikasi selalu memakai tema gelap supaya teks putih tetap terbaca di atas
 * latar ambient dan permukaan kaca.
 */
@Composable
fun NadaTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(LocalContext.current).copy(
            background = DarkColors.background,
            surface = DarkColors.surface,
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color.White.copy(alpha = 0.72f),
        )
    } else {
        DarkColors
    }
    MaterialTheme(colorScheme = colors) {
        // Warna default untuk Text & Icon yang tidak diberi warna eksplisit.
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, content = content)
    }
}
