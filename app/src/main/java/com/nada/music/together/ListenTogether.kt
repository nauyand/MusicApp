package com.nada.music.together

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.nada.music.data.Song
import com.nada.music.playback.toMediaItem
import com.nada.music.playback.toSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom

/**
 * Pintu masuk fitur "Dengar Bareng" untuk UI dan PlaybackService.
 * Pemutar dipasang oleh PlaybackService, jadi sinkronisasi tetap jalan di background.
 */
object ListenTogether {
    const val DEFAULT_BROKER = "ssl://broker.emqx.io:8883"

    private val main = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private var player: Player? = null
    private var session: RoomSession? = null

    private val _status = MutableStateFlow<RoomStatus>(RoomStatus.Idle)
    val status: StateFlow<RoomStatus> = _status.asStateFlow()

    val isListening: Boolean get() = session?.asHost == false

    fun init(context: Context) {
        prefs = context.getSharedPreferences("listen_together", Context.MODE_PRIVATE)
    }

    var brokerUrl: String
        get() = prefs.getString("broker", DEFAULT_BROKER)?.takeIf { it.isNotBlank() } ?: DEFAULT_BROKER
        set(value) = prefs.edit().putString("broker", value.trim()).apply()

    var displayName: String
        get() = prefs.getString("name", null) ?: "Teman"
        set(value) = prefs.edit().putString("name", value.trim()).apply()

    // ---- dipanggil PlaybackService ----

    fun attach(player: Player) {
        this.player = player
    }

    fun detach() {
        leave()
        player = null
    }

    fun onPlayerChanged() = session?.onPlayerChanged()

    // ---- dipanggil UI ----

    fun host(): Boolean {
        val p = player ?: return error("Pemutar belum siap. Putar satu lagu dulu, lalu coba lagi.")
        if (p.currentMediaItem == null) return error("Putar lagu dulu sebelum membuat room.")
        val code = (100000 + SecureRandom().nextInt(900000)).toString()
        start(code, asHost = true, p)
        return true
    }

    fun join(code: String): Boolean {
        val p = player ?: return error("Pemutar belum siap. Coba lagi sebentar.")
        val clean = code.filter(Char::isDigit)
        if (clean.length != 6) return error("Kode room harus 6 angka.")
        start(clean, asHost = false, p)
        return true
    }

    fun leave() {
        session?.close()
        session = null
        _status.value = RoomStatus.Idle
    }

    private fun error(message: String): Boolean {
        _status.value = RoomStatus.Error(message)
        return false
    }

    private fun start(code: String, asHost: Boolean, p: Player) {
        session?.close()
        session = RoomSession(
            brokerUrl = brokerUrl,
            code = code,
            asHost = asHost,
            displayName = displayName,
            player = ExoSyncPlayer(p),
            mainExecutor = { main.post(it) },
            onStatus = { status -> _status.value = status },
        ).also { it.start() }
    }

    /** Adaptor ExoPlayer → [SyncPlayer]. */
    private class ExoSyncPlayer(private val player: Player) : SyncPlayer {
        override fun snapshot(): SyncPlayer.Snapshot? {
            val item = player.currentMediaItem ?: return null
            return SyncPlayer.Snapshot(
                song = item.toSong(),
                positionMs = player.currentPosition,
                playing = player.isPlaying,
                speed = player.playbackParameters.speed,
            )
        }

        override fun currentSongId(): String? = player.currentMediaItem?.mediaId
        override fun positionMs(): Long = player.currentPosition
        override fun isReady(): Boolean = player.playbackState == Player.STATE_READY
        override fun isPlayRequested(): Boolean = player.playWhenReady

        override fun load(song: Song, positionMs: Long, play: Boolean) {
            player.setMediaItem(song.toMediaItem(), positionMs.coerceAtLeast(0))
            player.prepare()
            player.playWhenReady = play
        }

        override fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceAtLeast(0))

        override fun setPlaying(play: Boolean) {
            player.playWhenReady = play
        }
    }
}
