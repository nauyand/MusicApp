package com.nada.music.together

import com.nada.music.data.Song
import com.nada.music.data.local.LocalMusic
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Pemutar yang dikendalikan/diamati oleh sesi room. Semua method dipanggil di [RoomSession.mainExecutor]. */
interface SyncPlayer {
    /** Status pemutaran saat ini (untuk host), atau null kalau tidak ada lagu. */
    fun snapshot(): Snapshot?
    fun currentSongId(): String?
    fun positionMs(): Long
    fun isReady(): Boolean
    fun isPlayRequested(): Boolean
    fun load(song: Song, positionMs: Long, play: Boolean)
    fun seekTo(positionMs: Long)
    fun setPlaying(play: Boolean)

    data class Snapshot(val song: Song, val positionMs: Long, val playing: Boolean, val speed: Float)
}

sealed interface RoomStatus {
    data object Idle : RoomStatus
    data class Connecting(val code: String, val asHost: Boolean) : RoomStatus
    data class Hosting(val code: String, val members: List<String>) : RoomStatus
    data class Listening(
        val code: String,
        val hostName: String?,
        val nowPlaying: String?,
        /** Selisih posisi terhadap host (ms), null kalau belum diketahui. */
        val driftMs: Long?,
        val note: String? = null,
    ) : RoomStatus
    data class Error(val message: String) : RoomStatus
}

/**
 * Satu sesi "Dengar Bareng" lewat MQTT, sebagai host atau pendengar.
 *
 * Host mengirim [RoomState] setiap ada perubahan dan setiap [HEARTBEAT_MS].
 * Pendengar menyamakan jam dengan host (ping/pong), lalu menyesuaikan lagu,
 * posisi, dan status putar/jeda. Audio diambil masing-masing HP dari YouTube.
 */
class RoomSession(
    private val brokerUrl: String,
    val code: String,
    val asHost: Boolean,
    private val displayName: String,
    private val player: SyncPlayer,
    private val mainExecutor: (Runnable) -> Unit,
    private val onStatus: (RoomStatus) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val clientId = "spotiuy-" + UUID.randomUUID().toString().replace("-", "").take(12)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "room-$code").apply { isDaemon = true }
    }
    private var client: MqttAsyncClient? = null
    private val tasks = mutableListOf<ScheduledFuture<*>>()
    @Volatile private var closed = false

    // Host
    private val members = LinkedHashMap<String, String>()

    // Pendengar
    private val clockSamples = ArrayList<SyncMath.ClockSample>()
    @Volatile private var clockOffset: Long? = null
    @Volatile private var lastState: RoomState? = null
    @Volatile private var hostName: String? = null
    @Volatile private var hostGone = false
    private var lastCorrectionAt = 0L
    private var lastDrift: Long? = null

    fun start() {
        onStatus(RoomStatus.Connecting(code, asHost))
        val c = MqttAsyncClient(brokerUrl, clientId, MemoryPersistence())
        client = c
        c.setCallback(Callback())
        val options = MqttConnectOptions().apply {
            isCleanSession = true
            isAutomaticReconnect = true
            connectionTimeout = 15
            keepAliveInterval = 30
            if (asHost) {
                // Kalau koneksi host putus tiba-tiba, broker mengumumkan host sudah keluar.
                setWill(ListenProtocol.hostTopic(code), ByteArray(0), 1, true)
            } else {
                setWill(ListenProtocol.memberTopic(code, clientId), ByteArray(0), 1, true)
            }
        }
        try {
            c.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) = Unit
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    fail("Tidak bisa terhubung ke server MQTT ($brokerUrl): ${exception?.message ?: "gagal"}")
                }
            })
        } catch (e: Exception) {
            fail("Tidak bisa terhubung ke server MQTT: ${e.message}")
        }
    }

    /** Dipanggil saat pemutar host berubah (lagu, putar/jeda, seek, kecepatan). */
    fun onPlayerChanged() {
        if (asHost && !closed) mainExecutor(Runnable { publishState() })
    }

    fun close() {
        if (closed) return
        closed = true
        tasks.forEach { it.cancel(false) }
        val c = client
        scheduler.execute {
            runCatching {
                if (c != null && c.isConnected) {
                    if (asHost) {
                        // Hapus pesan retained supaya room ini benar-benar selesai.
                        c.publish(ListenProtocol.stateTopic(code), ByteArray(0), 1, true).waitForCompletion(3000)
                        c.publish(ListenProtocol.hostTopic(code), ByteArray(0), 1, true).waitForCompletion(3000)
                    } else {
                        c.publish(ListenProtocol.memberTopic(code, clientId), ByteArray(0), 1, true)
                            .waitForCompletion(3000)
                    }
                    c.disconnect().waitForCompletion(3000)
                }
            }
            runCatching { c?.close() }
            scheduler.shutdown()
        }
    }

    private fun fail(message: String) {
        if (closed) return
        mainExecutor(Runnable { onStatus(RoomStatus.Error(message)) })
    }

    private fun publish(topic: String, payload: String, retained: Boolean) {
        val c = client ?: return
        if (!c.isConnected) return
        runCatching { c.publish(topic, payload.toByteArray(), 1, retained) }
    }

    private fun every(periodMs: Long, initialDelayMs: Long = periodMs, block: () -> Unit) {
        tasks += scheduler.scheduleWithFixedDelay(
            { if (!closed) runCatching(block) },
            initialDelayMs,
            periodMs,
            TimeUnit.MILLISECONDS,
        )
    }

    // ---------------- Host ----------------

    private fun onHostConnected() {
        val c = client ?: return
        publish(ListenProtocol.hostTopic(code), ListenProtocol.encodeName(displayName), retained = true)
        c.subscribe(ListenProtocol.pingWildcard(code), 0)
        c.subscribe(ListenProtocol.membersWildcard(code), 1)
        mainExecutor(Runnable {
            publishState()
            emitHostStatus()
        })
        if (tasks.isEmpty()) every(HEARTBEAT_MS) { mainExecutor(Runnable { publishState() }) }
    }

    private fun publishState() {
        if (closed) return
        val snap = player.snapshot() ?: return
        val state = RoomState(
            songId = snap.song.id,
            title = snap.song.title,
            artist = snap.song.artist,
            thumbnailUrl = snap.song.thumbnailUrl,
            durationSec = snap.song.durationSec,
            positionMs = snap.positionMs,
            playing = snap.playing,
            speed = snap.speed,
            sentAt = clock(),
        )
        val payload = ListenProtocol.encodeState(state)
        scheduler.execute { publish(ListenProtocol.stateTopic(code), payload, retained = true) }
    }

    private fun emitHostStatus() {
        onStatus(RoomStatus.Hosting(code, synchronized(members) { members.values.toList() }))
    }

    private fun onHostMessage(topic: String, payload: String) {
        when {
            topic.startsWith(ListenProtocol.pingWildcard(code).removeSuffix("+")) -> {
                val t0 = ListenProtocol.decodePing(payload) ?: return
                val from = ListenProtocol.lastSegment(topic)
                publish(ListenProtocol.pongTopic(code, from), ListenProtocol.encodePong(t0, clock()), retained = false)
            }
            topic.startsWith(ListenProtocol.membersWildcard(code).removeSuffix("+")) -> {
                val id = ListenProtocol.lastSegment(topic)
                val name = ListenProtocol.decodeName(payload)
                synchronized(members) { if (name == null) members.remove(id) else members[id] = name }
                mainExecutor(Runnable { emitHostStatus() })
                // Pendengar baru: kirim status terbaru segera.
                if (name != null) mainExecutor(Runnable { publishState() })
            }
        }
    }

    // ---------------- Pendengar ----------------

    private fun onListenerConnected() {
        val c = client ?: return
        c.subscribe(ListenProtocol.hostTopic(code), 1)
        c.subscribe(ListenProtocol.stateTopic(code), 1)
        c.subscribe(ListenProtocol.pongTopic(code, clientId), 0)
        publish(ListenProtocol.memberTopic(code, clientId), ListenProtocol.encodeName(displayName), retained = true)
        if (tasks.isEmpty()) {
            // Sinkronisasi jam: beberapa ping cepat di awal, lalu sesekali.
            every(PING_BURST_MS, initialDelayMs = 0) {
                val needMore = synchronized(clockSamples) { clockSamples.size < 8 }
                if (needMore) sendPing()
            }
            every(RESYNC_MS) {
                synchronized(clockSamples) { clockSamples.clear() }
            }
            every(CORRECTION_MS) { mainExecutor(Runnable { applyState() }) }
        }
        emitListenerStatus()
    }

    private fun sendPing() {
        publish(ListenProtocol.pingTopic(code, clientId), ListenProtocol.encodePing(clock()), retained = false)
    }

    private fun onListenerMessage(topic: String, payload: String) {
        when (topic) {
            ListenProtocol.hostTopic(code) -> {
                hostName = ListenProtocol.decodeName(payload)
                hostGone = hostName == null
                if (hostGone) mainExecutor(Runnable { player.setPlaying(false) })
                emitListenerStatus()
            }
            ListenProtocol.stateTopic(code) -> {
                lastState = ListenProtocol.decodeState(payload)
                mainExecutor(Runnable { applyState() })
            }
            ListenProtocol.pongTopic(code, clientId) -> {
                val (t0, hostTime) = ListenProtocol.decodePong(payload) ?: return
                val sample = SyncMath.ClockSample(t0, hostTime, clock())
                val offset = synchronized(clockSamples) {
                    clockSamples += sample
                    SyncMath.bestOffset(clockSamples)
                }
                clockOffset = offset
            }
        }
    }

    /** Samakan lagu, posisi, dan status putar dengan host. Jalan di main thread. */
    private fun applyState() {
        if (closed || asHost) return
        val state = lastState
        val offset = clockOffset
        if (state == null || hostGone) {
            emitListenerStatus()
            return
        }
        if (LocalMusic.isLocal(state.songId)) {
            player.setPlaying(false)
            emitListenerStatus(note = "Host memutar musik lokal, jadi tidak bisa didengar bareng.")
            return
        }
        if (offset == null) {
            emitListenerStatus(note = "Menyamakan jam dengan host…")
            return
        }

        val now = clock()
        val expected = SyncMath.expectedPosition(state, now + offset)
        if (player.currentSongId() != state.songId) {
            val song = Song(state.songId, state.title, state.artist, state.thumbnailUrl, state.durationSec)
            // Beri sedikit "lead" karena lagu baru perlu waktu buffering.
            player.load(song, expected + if (state.playing) LOAD_LEAD_MS else 0, state.playing)
            lastCorrectionAt = now
            lastDrift = null
            emitListenerStatus()
            return
        }

        if (state.playing != player.isPlayRequested()) player.setPlaying(state.playing)

        if (player.isReady()) {
            val drift = player.positionMs() - expected
            lastDrift = drift
            if (abs(drift) > DRIFT_TOLERANCE_MS && now - lastCorrectionAt > CORRECTION_COOLDOWN_MS) {
                player.seekTo(expected + if (state.playing) SEEK_LEAD_MS else 0)
                lastCorrectionAt = now
            }
        }
        emitListenerStatus()
    }

    private fun emitListenerStatus(note: String? = null) {
        if (closed) return
        val state = lastState
        val status = RoomStatus.Listening(
            code = code,
            hostName = hostName,
            nowPlaying = state?.let { "${it.title} — ${it.artist}" },
            driftMs = lastDrift,
            note = note ?: when {
                hostGone -> "Host sudah keluar dari room."
                hostName == null && state == null -> "Menunggu host…"
                else -> null
            },
        )
        mainExecutor(Runnable { onStatus(status) })
    }

    private inner class Callback : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            runCatching { if (asHost) onHostConnected() else onListenerConnected() }
                .onFailure { fail("Gagal bergabung ke room: ${it.message}") }
        }

        override fun connectionLost(cause: Throwable?) {
            if (!closed) {
                mainExecutor(Runnable { onStatus(RoomStatus.Connecting(code, asHost)) })
            }
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            if (closed) return
            val payload = String(message.payload)
            if (asHost) onHostMessage(topic, payload) else onListenerMessage(topic, payload)
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
    }

    companion object {
        const val HEARTBEAT_MS = 3000L
        const val PING_BURST_MS = 300L
        const val RESYNC_MS = 60_000L
        const val CORRECTION_MS = 1500L
        const val DRIFT_TOLERANCE_MS = 450L
        const val CORRECTION_COOLDOWN_MS = 2500L
        const val LOAD_LEAD_MS = 600L
        const val SEEK_LEAD_MS = 150L
    }
}
