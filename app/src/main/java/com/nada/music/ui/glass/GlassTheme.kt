package com.nada.music.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Tema gelap untuk layar di atas cover buram (pemutar & efek audio):
 * teks putih, aksen dari warna cover.
 */
@Composable
fun GlassDarkTheme(colors: ArtworkColors, content: @Composable () -> Unit) {
    val accent = lerp(colors.vibrant, Color.White, 0.25f)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            primaryContainer = accent.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondaryContainer = Color.White.copy(alpha = 0.18f),
            onSecondaryContainer = Color.White,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF1A1A1E),
            onSurface = Color.White,
            surfaceVariant = Color.White.copy(alpha = 0.12f),
            onSurfaceVariant = Color.White.copy(alpha = 0.7f),
            surfaceContainer = Color(0xFF222226),
            surfaceContainerHigh = Color(0xFF2A2A2F),
            outline = Color.White.copy(alpha = 0.3f),
            outlineVariant = Color.White.copy(alpha = 0.15f),
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}
