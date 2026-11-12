package com.nada.music.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nada.music.data.Song
import com.nada.music.data.lyrics.Lyrics
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.LocalRepository

private sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
    data object Failed : LyricsState
}

@Composable
fun LyricsView(song: Song, positionMs: Long, modifier: Modifier = Modifier) {
    val repository = LocalRepository.current
    val state by produceState<LyricsState>(LyricsState.Loading, song.id) {
        value = LyricsState.Loading
        value = runCatching { repository.lyrics(song) }
            .fold({ LyricsState.Loaded(it) }, { LyricsState.Failed })
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (val s = state) {
            LyricsState.Loading -> CircularProgressIndicator()
            LyricsState.Failed -> Message("Gagal memuat lirik. Periksa koneksi internet.")
            is LyricsState.Loaded -> when (val lyrics = s.lyrics) {
                Lyrics.NotFound -> Message("Lirik tidak ditemukan untuk lagu ini.")
                is Lyrics.Plain -> Text(
                    lyrics.text,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                )
                is Lyrics.Synced -> SyncedLyrics(lyrics, positionMs)
            }
        }
    }
}

@Composable
private fun SyncedLyrics(lyrics: Lyrics.Synced, positionMs: Long) {
    val player = LocalPlayer.current
    val lines = lyrics.lines
    val activeIndex = remember(positionMs, lines) {
        lines.indexOfLast { it.timeMs <= positionMs + 300 }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(activeIndex) {
        // Baris aktif dijaga di sekitar sepertiga atas layar.
        listState.animateScrollToItem((activeIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            val active = index == activeIndex
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                label = "lyricColor",
            )
            Text(
                text = line.text.ifBlank { "♪" },
                color = color,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { player.seekTo(line.timeMs) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(32.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
