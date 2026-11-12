package com.nada.music.data

/** Satu lagu (sebenarnya satu video YouTube / YouTube Music). */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    /** Durasi dalam detik, atau -1 kalau tidak diketahui. */
    val durationSec: Long,
)

fun formatDuration(totalSec: Long): String {
    if (totalSec < 0) return "--:--"
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
