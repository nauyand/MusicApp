package com.nada.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.nada.music.data.Song
import com.nada.music.data.formatDuration
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.LocalRepository
import com.nada.music.ui.components.Artwork
import com.nada.music.ui.components.MenuAction
import com.nada.music.ui.components.SongRow
import kotlinx.coroutines.launch

private enum class PlayerPanel { Artwork, Lyrics, Queue }

@Composable
fun PlayerScreen(onClose: () -> Unit) {
    val player = LocalPlayer.current
    val song by player.currentSong.collectAsState()
    val current = song

    LaunchedEffect(current == null) {
        if (current == null) onClose()
    }
    if (current == null) return

    var panel by rememberSaveable { mutableStateOf(PlayerPanel.Artwork) }
    val (position, duration) = rememberPlaybackPosition()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Tutup") }
            Text(
                when (panel) {
                    PlayerPanel.Artwork -> "Sedang diputar"
                    PlayerPanel.Lyrics -> "Lirik"
                    PlayerPanel.Queue -> "Antrean"
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (panel) {
                PlayerPanel.Artwork -> Box(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Artwork(
                        current.thumbnailUrl,
                        Modifier.fillMaxWidth().aspectRatio(1f),
                        size = null,
                        corner = 16.dp,
                    )
                }
                PlayerPanel.Lyrics -> LyricsView(current, position)
                PlayerPanel.Queue -> QueueView()
            }
        }

        SongInfo(current)
        SeekBar(position, duration, onSeek = player::seekTo)
        Controls()

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PanelToggle(Icons.Rounded.Lyrics, "Lirik", panel == PlayerPanel.Lyrics) {
                panel = if (panel == PlayerPanel.Lyrics) PlayerPanel.Artwork else PlayerPanel.Lyrics
            }
            PanelToggle(Icons.AutoMirrored.Rounded.QueueMusic, "Antrean", panel == PlayerPanel.Queue) {
                panel = if (panel == PlayerPanel.Queue) PlayerPanel.Artwork else PlayerPanel.Queue
            }
        }
    }
}

@Composable
private fun SongInfo(song: Song) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val liked by remember(song.id) { repository.isLiked(song.id) }.collectAsState(initial = false)

    Row(
        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = { scope.launch { repository.setLiked(song, !liked) } }) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (liked) "Hapus dari favorit" else "Tambah ke favorit",
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SeekBar(position: Long, duration: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Column(Modifier.padding(horizontal = 24.dp)) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragging?.let { (it * duration).toLong() } ?: position
            Text(formatDuration(shown / 1000), style = MaterialTheme.typography.bodySmall)
            Text(
                if (duration > 0) formatDuration(duration / 1000) else "--:--",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Controls() {
    val player = LocalPlayer.current
    val isPlaying by player.isPlaying.collectAsState()
    val isBuffering by player.isBuffering.collectAsState()
    val shuffle by player.shuffle.collectAsState()
    val repeatMode by player.repeatMode.collectAsState()
    val error by player.error.collectAsState()

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = player::toggleShuffle) {
            Icon(
                Icons.Rounded.Shuffle,
                "Acak",
                tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = player::previous, Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Sebelumnya", Modifier.size(36.dp))
        }
        FilledIconButton(
            onClick = player::togglePlayPause,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            when {
                isBuffering && !isPlaying -> CircularProgressIndicator(
                    Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
                isPlaying -> Icon(Icons.Rounded.Pause, "Jeda", Modifier.size(40.dp))
                else -> Icon(Icons.Rounded.PlayArrow, "Putar", Modifier.size(40.dp))
            }
        }
        IconButton(onClick = player::next, Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Berikutnya", Modifier.size(36.dp))
        }
        IconButton(onClick = player::cycleRepeat) {
            Icon(
                if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Ulangi",
                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    error?.let {
        Text(
            "$it — ketuk tombol putar untuk mencoba lagi.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Composable
private fun PanelToggle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QueueView() {
    val player = LocalPlayer.current
    val queue by player.queue.collectAsState()
    val currentIndex by player.currentIndex.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (currentIndex > 0) listState.scrollToItem(currentIndex)
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        itemsIndexed(queue, key = { index, song -> "$index-${song.id}" }) { index, song ->
            SongRow(
                song = song,
                isCurrent = index == currentIndex,
                onClick = { player.skipTo(index) },
                extraMenu = listOf(
                    MenuAction("Hapus dari antrean", Icons.Rounded.Close) { player.removeFromQueue(index) },
                ),
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
