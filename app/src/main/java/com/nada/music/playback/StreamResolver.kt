package com.nada.music.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.nada.music.data.youtube.YouTubeMusic
import java.io.IOException

/**
 * Mengubah `nada://song/<id>` menjadi URL googlevideo yang asli, tepat sebelum
 * ExoPlayer mulai mengunduh. Dipanggil di thread loader milik ExoPlayer.
 */
@UnstableApi
class StreamResolver : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val videoId = SongUri.videoId(dataSpec.uri) ?: return dataSpec
        val stream = try {
            YouTubeMusic.resolveStreamBlocking(videoId)
        } catch (e: Exception) {
            throw IOException("Gagal mengambil stream untuk $videoId: ${e.message}", e)
        }
        return dataSpec.buildUpon()
            .setUri(stream.url)
            // Kunci cache per lagu + format, supaya cache tetap terpakai walau URL berganti.
            .setKey("$videoId:${stream.itag}")
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + stream.headers)
            .build()
    }
}
