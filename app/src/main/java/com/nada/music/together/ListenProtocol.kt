package com.nada.music.together

import org.json.JSONObject

/** Status pemutaran host yang dikirim ke semua pendengar. */
data class RoomState(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val positionMs: Long,
    val playing: Boolean,
    val speed: Float,
    /** Jam host (ms epoch) saat status ini dibuat. */
    val sentAt: Long,
)

/**
 * Format pesan MQTT "Dengar Bareng". Semua topik ada di bawah `spotiuy/v1/<kode>/`:
 * - `host`            (retained) nama host; kosong = host sudah keluar
 * - `state`           (retained) [RoomState] terbaru
 * - `members/<id>`    (retained) nama pendengar; kosong = sudah keluar
 * - `ping/<id>`, `pong/<id>`  sinkronisasi jam pendengar ↔ host
 */
object ListenProtocol {
    private const val ROOT = "spotiuy/v1"

    fun hostTopic(code: String) = "$ROOT/$code/host"
    fun stateTopic(code: String) = "$ROOT/$code/state"
    fun memberTopic(code: String, clientId: String) = "$ROOT/$code/members/$clientId"
    fun membersWildcard(code: String) = "$ROOT/$code/members/+"
    fun pingTopic(code: String, clientId: String) = "$ROOT/$code/ping/$clientId"
    fun pingWildcard(code: String) = "$ROOT/$code/ping/+"
    fun pongTopic(code: String, clientId: String) = "$ROOT/$code/pong/$clientId"

    fun lastSegment(topic: String) = topic.substringAfterLast('/')

    fun encodeState(s: RoomState): String = JSONObject()
        .put("id", s.songId)
        .put("t", s.title)
        .put("a", s.artist)
        .put("img", s.thumbnailUrl ?: JSONObject.NULL)
        .put("dur", s.durationSec)
        .put("pos", s.positionMs)
        .put("play", s.playing)
        .put("spd", s.speed.toDouble())
        .put("at", s.sentAt)
        .toString()

    fun decodeState(payload: String): RoomState? = runCatching {
        if (payload.isBlank()) return null
        val o = JSONObject(payload)
        RoomState(
            songId = o.getString("id"),
            title = o.optString("t"),
            artist = o.optString("a"),
            thumbnailUrl = if (o.isNull("img")) null else o.optString("img"),
            durationSec = o.optLong("dur", -1),
            positionMs = o.getLong("pos"),
            playing = o.getBoolean("play"),
            speed = o.optDouble("spd", 1.0).toFloat(),
            sentAt = o.getLong("at"),
        )
    }.getOrNull()

    fun encodeName(name: String): String = JSONObject().put("name", name).toString()

    fun decodeName(payload: String): String? =
        if (payload.isBlank()) null else runCatching { JSONObject(payload).getString("name") }.getOrNull()

    fun encodePing(t0: Long): String = JSONObject().put("t0", t0).toString()

    fun decodePing(payload: String): Long? = runCatching { JSONObject(payload).getLong("t0") }.getOrNull()

    fun encodePong(t0: Long, hostTime: Long): String = JSONObject().put("t0", t0).put("host", hostTime).toString()

    fun decodePong(payload: String): Pair<Long, Long>? = runCatching {
        val o = JSONObject(payload)
        o.getLong("t0") to o.getLong("host")
    }.getOrNull()
}

object SyncMath {
    /** Satu pengukuran ping: kirim di [t0], host menjawab dengan jamnya [hostTime], diterima di [t1]. */
    data class ClockSample(val t0: Long, val hostTime: Long, val t1: Long) {
        val rtt: Long get() = t1 - t0
        /** jam host − jam lokal, dengan asumsi perjalanan pergi = pulang. */
        val offset: Long get() = hostTime - (t0 + t1) / 2
    }

    /** Ambil sampel dengan waktu bolak-balik tercepat (paling sedikit gangguan jaringan). */
    fun bestOffset(samples: List<ClockSample>): Long? = samples.minByOrNull { it.rtt }?.offset

    /** Posisi lagu host saat jam host menunjukkan [hostNow]. */
    fun expectedPosition(state: RoomState, hostNow: Long): Long {
        val elapsed = (hostNow - state.sentAt).coerceAtLeast(0)
        val pos = if (state.playing) state.positionMs + (elapsed * state.speed).toLong() else state.positionMs
        return if (state.durationSec > 0) pos.coerceAtMost(state.durationSec * 1000) else pos
    }
}
