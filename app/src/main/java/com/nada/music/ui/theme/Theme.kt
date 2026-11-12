package com.nada.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
    background = Color(0xFF121212),
    onBackground = Color(0xFFECE6EA),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFECE6EA),
    surfaceVariant = Color(0xFF2A2629),
    onSurfaceVariant = Color(0xFFCFC4C9),
    surfaceContainer = Color(0xFF1C1A1C),
    surfaceContainerHigh = Color(0xFF262326),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFBE185D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8E9),
    onPrimaryContainer = Color(0xFF3B0A24),
    secondary = Color(0xFF4F46E5),
    onSecondary = Color.White,
)

@Composable
fun NadaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
