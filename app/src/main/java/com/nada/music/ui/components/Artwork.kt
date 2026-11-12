package com.nada.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage

@Composable
fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp? = 48.dp,
    corner: Dp = 8.dp,
) {
    val base = (if (size != null) modifier.size(size) else modifier)
        .clip(RoundedCornerShape(corner))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = base,
        error = { Placeholder() },
        loading = { Placeholder() },
    )
}

@Composable
private fun Placeholder() {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
