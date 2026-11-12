package com.nada.music.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** HazeState untuk konten utama; permukaan kaca di atasnya memburamkan konten ini. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

val GlassShape = RoundedCornerShape(24.dp)

/**
 * Permukaan kaca ala iOS:
 * - latar belakang buram (blur nyata di Android 12+, kaca transparan di versi lama),
 * - kilau pantulan di bagian atas (specular highlight),
 * - tepi tipis yang berkilau dari kiri atas ke kanan bawah.
 *
 * Kalau [hazeState] null (misalnya di atas latar yang sudah buram seperti layar pemutar),
 * dipakai lapisan kaca transparan biasa.
 */
@Composable
fun Modifier.glass(
    shape: Shape = GlassShape,
    hazeState: HazeState? = null,
    tint: Color = glassTint(),
    blurRadius: Dp = 30.dp,
    borderWidth: Dp = 1.dp,
): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val base = this.clip(shape)
    val surface = if (hazeState != null) {
        base.hazeChild(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = MaterialTheme.colorScheme.background,
                tint = HazeTint(tint),
                blurRadius = blurRadius,
                noiseFactor = 0.06f,
                fallbackTint = HazeTint(tint.copy(alpha = (tint.alpha + 0.45f).coerceAtMost(0.95f))),
            ),
        )
    } else {
        base.background(tint)
    }
    val sheenTop = if (dark) 0.16f else 0.55f
    val edgeHi = if (dark) 0.38f else 0.9f
    val edgeLo = if (dark) 0.06f else 0.25f
    return surface
        .drawWithContent {
            // Kilau pantulan: gradasi putih dari tepi atas memudar ke tengah.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = sheenTop),
                    0.45f to Color.White.copy(alpha = 0f),
                ),
            )
            drawRect(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = sheenTop * 0.5f),
                    0.35f to Color.Transparent,
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                ),
            )
            drawContent()
        }
        .border(
            borderWidth,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = edgeHi),
                0.5f to Color.White.copy(alpha = edgeLo),
                1f to Color.White.copy(alpha = edgeHi * 0.5f),
            ),
            shape,
        )
}

@Composable
fun glassTint(): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.45f)
}
