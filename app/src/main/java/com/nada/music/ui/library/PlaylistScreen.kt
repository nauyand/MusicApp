package com.nada.music.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nada.music.data.Song
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.LocalRepository
import com.nada.music.ui.components.EmptyState
import com.nada.music.ui.components.MenuAction
import com.nada.music.ui.components.SongRow
import com.nada.music.ui.components.TextInputDialog
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(playlistId: Long, onBack: () -> Unit, contentPadding: PaddingValues) {
    val repository = LocalRepository.current
    val player = LocalPlayer.current
    val scope = rememberCoroutineScope()
    val isLiked = playlistId == LIKED_PLAYLIST_ID
    val isLocal = playlistId == LOCAL_PLAYLIST_ID
    val isSpecial = isLiked || isLocal
    val specialTitle = if (isLiked) "Lagu favorit" else "Musik lokal"

    val title by remember(playlistId) {
        if (isSpecial) flowOf(specialTitle) else repository.playlist(playlistId).map { it?.name }
    }.collectAsState(initial = if (isSpecial) specialTitle else "")
    val songs: List<Song> by remember(playlistId) {
        when {
            isLiked -> repository.likedSongs
            isLocal -> repository.localSongs
            else -> repository.playlistSongs(playlistId)
        }
    }.collectAsState(initial = emptyList())
    val localImport = if (isLocal) rememberLocalImport() else null
    val currentSong by player.currentSong.collectAsState()

    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            colors = com.nada.music.ui.glass.transparentTopBarColors(),
            title = { Text(title.orEmpty()) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
            },
            actions = {
                if (localImport != null) {
                    IconButton(onClick = localImport.scan) { Icon(Icons.Rounded.Refresh, "Pindai musik di HP") }
                    IconButton(onClick = localImport.pickFiles) { Icon(Icons.Rounded.Add, "Tambah file") }
                }
                if (!isSpecial) {
                    IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Ganti nama") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Hapus playlist") }
                }
            },
        )

        if (localImport?.busy == true) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        if (songs.isEmpty() && localImport != null) {
            EmptyState(
                icon = Icons.Rounded.PhoneAndroid,
                title = "Belum ada musik lokal",
                message = "Pindai HP untuk menemukan semua lagu yang tersimpan, atau pilih file audio sendiri (MP3, M4A, FLAC, OGG, WAV).",
                modifier = Modifier.padding(top = 48.dp),
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = localImport.scan) { Text("Pindai HP") }
                        FilledTonalButton(onClick = localImport.pickFiles) { Text("Pilih file") }
                    }
                },
            )
            return@Column
        }

        if (songs.isEmpty()) {
            EmptyState(
                icon = if (isLiked) Icons.Rounded.FavoriteBorder else Icons.AutoMirrored.Rounded.QueueMusic,
                title = "Masih kosong",
                message = if (isLiked) {
                    "Ketuk ikon hati di pemutar atau di menu lagu untuk menambahkan favorit."
                } else {
                    "Tambahkan lagu lewat menu ⋮ > \"Tambah ke playlist\"."
                },
                modifier = Modifier.padding(top = 48.dp),
            )
            return@Column
        }

        LazyColumn(contentPadding = contentPadding) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${songs.size} lagu",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(onClick = { player.playSongs(songs) }, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Putar")
                        }
                        FilledTonalButton(onClick = { player.playSongs(songs, shuffle = true) }, Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Shuffle, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Acak")
                        }
                    }
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == currentSong?.id,
                    onClick = { player.playSongs(songs, index) },
                    extraMenu = when {
                        isLiked -> emptyList()
                        isLocal -> listOf(
                            MenuAction("Hapus dari musik lokal", Icons.Rounded.RemoveCircleOutline) {
                                scope.launch { repository.removeLocalSong(song.id) }
                            },
                        )
                        else -> listOf(
                            MenuAction("Hapus dari playlist", Icons.Rounded.RemoveCircleOutline) {
                                scope.launch { repository.removeFromPlaylist(playlistId, song.id) }
                            },
                        )
                    },
                )
            }
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Ganti nama playlist",
            label = "Nama playlist",
            confirmText = "Simpan",
            initial = title.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = { name ->
                scope.launch { repository.renamePlaylist(playlistId, name) }
                renaming = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Hapus playlist?") },
            text = { Text("Playlist \"${title.orEmpty()}\" akan dihapus. Lagu-lagunya tetap ada di riwayat dan favorit.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repository.deletePlaylist(playlistId)
                        onBack()
                    }
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Batal") } },
        )
    }
}
