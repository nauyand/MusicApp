package com.nada.music.ui.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Latar "ambient" di belakang seluruh aplikasi: gradasi dan cahaya lembut
 * dari warna cover lagu yang sedang diputar. Permukaan kaca jadi terlihat hidup.
 */
@Composable
fun AmbientBackground(colors: ArtworkColors, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val dark = bg.luminance() < 0.5f
    val strength = if (dark) 0.55f else 0.35f
    Box(
        modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        0f to lerp(bg, colors.dark, strength + 0.15f),
                        0.6f to lerp(bg, colors.dark, strength * 0.4f),
                        1f to bg,
                    ),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.vibrant.copy(alpha = if (dark) 0.35f else 0.28f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.1f),
                        radius = size.width * 0.9f,
                    ),
                    radius = size.width * 0.9f,
                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(colors.muted.copy(alpha = if (dark) 0.3f else 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.45f),
                        radius = size.width * 0.8f,
                    ),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.95f, size.height * 0.45f),
                )
            },
    )
}

/**
 * Latar layar pemutar: cover lagu diperbesar dan dibuat sangat buram, lalu digelapkan.
 * Gambar dimuat dalam resolusi sangat kecil, jadi tetap terlihat buram walau
 * Android < 12 tidak mendukung Modifier.blur.
 */
@Composable
fun ArtworkBackdrop(url: String?, colors: ArtworkColors) {
    Box(Modifier.fillMaxSize().background(lerp(Color.Black, colors.dark, 0.6f))) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).size(24).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(40.dp) else Modifier),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.25f),
                        0.5f to Color.Black.copy(alpha = 0.45f),
                        1f to Color.Black.copy(alpha = 0.8f),
                    ),
                ),
        )
    }
}
