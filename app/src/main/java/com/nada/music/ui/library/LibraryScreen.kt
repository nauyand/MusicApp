package com.nada.music.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nada.music.ui.LocalRepository
import com.nada.music.ui.components.Artwork
import com.nada.music.ui.components.SectionTitle
import com.nada.music.ui.components.TextInputDialog
import kotlinx.coroutines.launch

/** id playlist khusus untuk "Lagu favorit". */
const val LIKED_PLAYLIST_ID = -1L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenPlaylist: (Long) -> Unit, contentPadding: PaddingValues) {
    val repository = LocalRepository.current
    val scope = rememberCoroutineScope()
    val liked by repository.likedSongs.collectAsState(initial = emptyList())
    val playlists by repository.playlists.collectAsState(initial = emptyList())
    var creating by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Pustaka", fontWeight = FontWeight.Bold) },
            actions = {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Playlist baru") }
            },
        )
        LazyColumn(contentPadding = contentPadding) {
            item {
                CollectionRow(
                    title = "Lagu favorit",
                    subtitle = "${liked.size} lagu",
                    icon = Icons.Rounded.Favorite,
                    onClick = { onOpenPlaylist(LIKED_PLAYLIST_ID) },
                )
            }
            item {
                SectionTitle("Playlist")
            }
            if (playlists.isEmpty()) {
                item {
                    Text(
                        "Belum ada playlist. Ketuk + untuk membuat, atau pilih \"Tambah ke playlist\" dari menu lagu.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPlaylist(playlist.id) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (playlist.thumbnailUrl != null) {
                        Artwork(playlist.thumbnailUrl, size = 56.dp)
                    } else {
                        IconTile(Icons.AutoMirrored.Rounded.QueueMusic)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${playlist.songCount} lagu",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        TextInputDialog(
            title = "Playlist baru",
            label = "Nama playlist",
            confirmText = "Buat",
            onDismiss = { creating = false },
            onConfirm = { name ->
                scope.launch { repository.createPlaylist(name) }
                creating = false
            },
        )
    }
}

@Composable
private fun CollectionRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, highlighted = true)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, highlighted: Boolean = false) {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
