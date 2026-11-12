package com.nada.music.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class ArtworkColors(val vibrant: Color, val muted: Color, val dark: Color)

val DefaultArtworkColors = ArtworkColors(
    vibrant = Color(0xFFF472B6),
    muted = Color(0xFF6366F1),
    dark = Color(0xFF1E1B4B),
)

/** Mengambil warna dominan dari cover lagu (dianimasikan saat lagu berganti). */
@Composable
fun rememberArtworkColors(url: String?): ArtworkColors {
    val context = LocalContext.current
    val target by produceState(DefaultArtworkColors, url) {
        if (url == null) {
            value = DefaultArtworkColors
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val result = context.imageLoader.execute(
                    ImageRequest.Builder(context).data(url).size(128).allowHardware(false).build(),
                ) as? SuccessResult ?: return@runCatching DefaultArtworkColors
                val palette = Palette.from(result.drawable.toBitmap()).generate()
                fun c(rgb: Int?) = rgb?.let { Color(it) }
                ArtworkColors(
                    vibrant = c(palette.vibrantSwatch?.rgb) ?: c(palette.lightVibrantSwatch?.rgb)
                        ?: c(palette.dominantSwatch?.rgb) ?: DefaultArtworkColors.vibrant,
                    muted = c(palette.mutedSwatch?.rgb) ?: c(palette.dominantSwatch?.rgb)
                        ?: DefaultArtworkColors.muted,
                    dark = c(palette.darkMutedSwatch?.rgb) ?: c(palette.darkVibrantSwatch?.rgb)
                        ?: DefaultArtworkColors.dark,
                )
            }.getOrDefault(DefaultArtworkColors)
        }
    }
    val spec = tween<Color>(900)
    val vibrant by animateColorAsState(target.vibrant, spec, label = "vibrant")
    val muted by animateColorAsState(target.muted, spec, label = "muted")
    val dark by animateColorAsState(target.dark, spec, label = "dark")
    return ArtworkColors(vibrant, muted, dark)
}
