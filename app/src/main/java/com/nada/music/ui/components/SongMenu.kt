package com.nada.music.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.nada.music.data.Song
import com.nada.music.ui.LocalPlayer
import com.nada.music.ui.LocalRepository
import kotlinx.coroutines.launch

data class MenuAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extraActions: List<MenuAction> = emptyList(),
) {
    val player = LocalPlayer.current
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val liked by remember(song.id) { repository.isLiked(song.id) }.collectAsState(initial = false)
    var showPlaylistPicker by remember { mutableStateOf(false) }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Putar berikutnya") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null) },
            onClick = {
                player.playNext(song)
                Toast.makeText(context, "Diputar berikutnya", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Tambah ke antrean") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
            onClick = {
                player.addToQueue(song)
                Toast.makeText(context, "Ditambahkan ke antrean", Toast.LENGTH_SHORT).show()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text("Putar radio dari lagu ini") },
            leadingIcon = { Icon(Icons.Rounded.Radio, null) },
            onClick = {
                onDismiss()
                scope.launch {
                    val related = runCatching { repository.related(song.id) }.getOrDefault(emptyList())
                    player.playSongs(listOf(song) + related)
                }
            },
        )
        DropdownMenuItem(
            text = { Text("Tambah ke playlist") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
            onClick = {
                showPlaylistPicker = true
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(if (liked) "Hapus dari favorit" else "Tambah ke favorit") },
            leadingIcon = { Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
            onClick = {
                scope.launch { repository.setLiked(song, !liked) }
                onDismiss()
            },
        )
        extraActions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                leadingIcon = { Icon(action.icon, null) },
                onClick = {
                    action.onClick()
                    onDismiss()
                },
            )
        }
    }

    if (showPlaylistPicker) {
        AddToPlaylistDialog(song = song, onDismiss = { showPlaylistPicker = false })
    }
}

@Composable
fun AddToPlaylistDialog(song: Song, onDismiss: () -> Unit) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playlists by repository.playlists.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        TextInputDialog(
            title = "Playlist baru",
            label = "Nama playlist",
            confirmText = "Buat",
            onDismiss = { creating = false },
            onConfirm = { name ->
                scope.launch {
                    val id = repository.createPlaylist(name)
                    repository.addToPlaylist(id, song)
                    Toast.makeText(context, "Ditambahkan ke \"$name\"", Toast.LENGTH_SHORT).show()
                }
                creating = false
                onDismiss()
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah ke playlist") },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("Playlist baru", color = MaterialTheme.colorScheme.primary)
                }
                if (playlists.isNotEmpty()) HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        repository.addToPlaylist(playlist.id, song)
                                        Toast.makeText(
                                            context,
                                            "Ditambahkan ke \"${playlist.name}\"",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        ) {
                            Text(playlist.name, Modifier.weight(1f))
                            Text(
                                "${playlist.songCount} lagu",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
