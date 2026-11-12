package com.nada.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.glass.LocalHazeState
import com.nada.music.ui.glass.glass
import com.nada.music.ui.player.rememberPlaybackPosition

@Composable
fun MiniPlayer(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val player = LocalPlayer.current
    val song by player.currentSong.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val current = song ?: return
    val (position, duration) = rememberPlaybackPosition()
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(22.dp)

    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .glass(shape = shape, hazeState = LocalHazeState.current)
            .clip(shape)
            .clickable(onClick = onOpen)
            .drawBehind {
                // Garis progres tipis di bagian bawah kapsul.
                val y = size.height - 1.5.dp.toPx()
                drawLine(
                    accent,
                    start = Offset(16.dp.toPx(), y),
                    end = Offset(16.dp.toPx() + (size.width - 32.dp.toPx()) * progress, y),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            },
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(current.thumbnailUrl, size = 44.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    current.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = player::togglePlayPause) {
                when {
                    isBuffering && !isPlaying -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    isPlaying -> Icon(Icons.Rounded.Pause, "Jeda")
                    else -> Icon(Icons.Rounded.PlayArrow, "Putar")
                }
            }
            IconButton(onClick = player::next) {
                Icon(Icons.Rounded.SkipNext, "Berikutnya")
            }
        }
    }
}
