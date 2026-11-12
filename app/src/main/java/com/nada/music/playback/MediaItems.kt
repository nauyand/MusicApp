package com.nada.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.nada.music.data.Song

/**
 * Setiap lagu diberi URI `nada://song/<videoId>`. URL audio sebenarnya baru dicari
 * saat ExoPlayer mau memutarnya (lihat [StreamResolver]), karena URL googlevideo
 * cepat kedaluwarsa.
 */
object SongUri {
    private const val SCHEME = "nada"
    private const val HOST = "song"

    fun of(videoId: String): Uri = "$SCHEME://$HOST/$videoId".toUri()

    fun videoId(uri: Uri): String? =
        if (uri.scheme == SCHEME && uri.host == HOST) uri.lastPathSegment else null
}

private const val EXTRA_DURATION = "nada.duration"

fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(SongUri.of(id))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setArtworkUri(thumbnailUrl?.toUri())
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply { putLong(EXTRA_DURATION, durationSec) })
                .build(),
        )
        .build()

fun MediaItem.toSong(): Song =
    Song(
        id = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString(),
        durationSec = mediaMetadata.extras?.getLong(EXTRA_DURATION, -1L) ?: -1L,
    )
