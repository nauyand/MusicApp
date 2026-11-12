package com.nada.music.data.local

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.nada.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lagu lokal memakai id `local:<content uri>`, jadi seluruh aplikasi (antrean, playlist,
 * favorit, riwayat) bisa memperlakukannya sama seperti lagu YouTube.
 */
object LocalMusic {
    const val ID_PREFIX = "local:"

    fun isLocal(songId: String) = songId.startsWith(ID_PREFIX)

    fun uriOf(songId: String): Uri = Uri.parse(songId.removePrefix(ID_PREFIX))

    private fun idOf(uri: Uri) = ID_PREFIX + uri

    /** Semua musik yang terdaftar di MediaStore (butuh izin baca audio). */
    suspend fun scanDevice(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val songs = mutableListOf<Song>()
        context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                val albumArt = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    c.getLong(albumCol),
                )
                songs += Song(
                    id = idOf(uri),
                    title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Tanpa judul",
                    artist = c.getString(artistCol)?.takeUnless { it.isBlank() || it == "<unknown>" }
                        ?: "Artis tidak diketahui",
                    thumbnailUrl = albumArt.toString(),
                    durationSec = c.getLong(durationCol) / 1000,
                )
            }
        }
        songs
    }

    /**
     * File yang dipilih lewat pemilih file. Izin akses disimpan permanen supaya
     * tetap bisa diputar setelah HP dinyalakan ulang.
     */
    suspend fun fromPickedFiles(context: Context, uris: List<Uri>): List<Song> = withContext(Dispatchers.IO) {
        uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { readMetadata(context, uri) }.getOrNull()
        }
    }

    private fun readMetadata(context: Context, uri: Uri): Song {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: displayName(context, uri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Artis tidak diketahui"
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val id = idOf(uri)
            val art = retriever.embeddedPicture?.let { bytes ->
                val file = File(context.filesDir, "artwork/${id.hashCode().toUInt()}.jpg")
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                Uri.fromFile(file).toString()
            }
            return Song(id, title, artist, art, durationMs?.div(1000) ?: -1)
        } finally {
            retriever.release()
        }
    }

    private fun displayName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        } ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Tanpa judul"
}
