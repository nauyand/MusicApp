package com.nada.music.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nada.music.MainActivity
import com.nada.music.NadaApp
import com.nada.music.R
import com.nada.music.data.youtube.YouTubeMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service pemutar musik. Berjalan di background, menampilkan notifikasi media,
 * dan bisa dikontrol dari lockscreen, headset Bluetooth, atau Android Auto.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    /** Lagu yang sudah dicoba ulang setelah error, supaya tidak berulang tanpa henti. */
    private var retriedMediaId: String? = null
    private var radioLoadingFor: String? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as NadaApp

        val httpFactory = OkHttpDataSource.Factory(app.okHttpClient)
        val cacheFactory = CacheDataSource.Factory()
            .setCache(PlayerCache.get(this))
            .setUpstreamDataSourceFactory(httpFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val dataSourceFactory = ResolvingDataSource.Factory(cacheFactory, StreamResolver())

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        player.addListener(PlayerListener())

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        /**
         * MediaController tidak mengirim URI ke session, jadi URI dibangun ulang
         * dari mediaId di sini.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { item ->
                    item.buildUpon()
                        .setUri(SongUri.of(item.mediaId))
                        .build()
                }.toMutableList(),
            )
    }

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem ?: return
            val song = mediaItem.toSong()
            scope.launch { (application as NadaApp).repository.recordPlay(song) }
            maybeExtendWithRadio()
        }

        override fun onPlayerError(error: PlaybackException) {
            val current = player.currentMediaItem ?: return
            val httpCode = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode
            val isNetworkish = error.errorCode in setOf(
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )

            // URL kedaluwarsa (403) atau gangguan jaringan: ambil URL baru lalu coba sekali lagi.
            if ((httpCode == 403 || isNetworkish) && retriedMediaId != current.mediaId) {
                retriedMediaId = current.mediaId
                YouTubeMusic.invalidate(current.mediaId)
                val position = player.currentPosition
                player.prepare()
                player.seekTo(position)
                player.play()
                return
            }

            // Masih gagal: lewati ke lagu berikutnya.
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) retriedMediaId = null
        }
    }

    /**
     * Radio otomatis: saat lagu terakhir di antrean diputar, tambahkan lagu-lagu
     * terkait supaya musik tidak berhenti.
     */
    private fun maybeExtendWithRadio() {
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        if (player.currentMediaItemIndex < player.mediaItemCount - 1) return
        val currentId = player.currentMediaItem?.mediaId ?: return
        if (radioLoadingFor == currentId) return
        radioLoadingFor = currentId

        scope.launch {
            val related = runCatching {
                (application as NadaApp).repository.related(currentId)
            }.getOrDefault(emptyList())
            val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
            val toAdd = related.filter { it.id !in existing }.take(10)
            if (toAdd.isNotEmpty() && player.currentMediaItem?.mediaId == currentId) {
                player.addMediaItems(toAdd.map { it.toMediaItem() })
            }
        }
    }
}
