package com.nada.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.nada.music.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Penghubung antara UI (Compose) dan [PlaybackService] lewat MediaController.
 * Semua state pemutar diekspos sebagai StateFlow.
 */
class PlayerConnection(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        scope.launch {
            val c = MediaController.Builder(context, token).buildAsync().await()
            controller = c
            c.addListener(listener)
            syncAll(c)
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncAll(player)
        }
    }

    private fun syncAll(p: Player) {
        _currentSong.value = p.currentMediaItem?.toSong()
        _isPlaying.value = p.isPlaying
        _isBuffering.value = p.playbackState == Player.STATE_BUFFERING
        _currentIndex.value = p.currentMediaItemIndex
        _shuffle.value = p.shuffleModeEnabled
        _repeatMode.value = p.repeatMode
        _error.value = p.playerError?.let { "Gagal memutar: ${it.cause?.message ?: it.errorCodeName}" }
        val items = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
        if (items.size != _queue.value.size || items.map { it.mediaId } != _queue.value.map { it.id }) {
            _queue.value = items.map(MediaItem::toSong)
        }
    }

    /** Posisi sekarang dalam milidetik (dibaca berkala oleh UI). */
    val position: Long get() = controller?.currentPosition ?: 0L

    val duration: Long get() = controller?.duration?.takeIf { it > 0 } ?: 0L

    fun playSongs(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        c.shuffleModeEnabled = shuffle
        val start = if (shuffle) songs.indices.random() else startIndex
        c.setMediaItems(songs.map { it.toMediaItem() }, start, 0L)
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song))
        c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) return playSongs(listOf(song))
        c.addMediaItem(song.toMediaItem())
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.playerError != null) {
            c.prepare()
            c.play()
        } else if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = controller?.seekToNext() ?: Unit
    fun previous() = controller?.seekToPrevious() ?: Unit
    fun seekTo(ms: Long) = controller?.seekTo(ms) ?: Unit

    fun skipTo(index: Int) {
        val c = controller ?: return
        c.seekToDefaultPosition(index)
        c.play()
    }

    fun removeFromQueue(index: Int) = controller?.removeMediaItem(index) ?: Unit

    fun moveInQueue(from: Int, to: Int) = controller?.moveMediaItem(from, to) ?: Unit

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
        scope.cancel()
    }
}
