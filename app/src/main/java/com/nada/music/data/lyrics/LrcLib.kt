package com.nada.music.data.lyrics

import com.nada.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object NotFound : Lyrics
}

/**
 * Lirik dari LRCLIB (https://lrclib.net), database lirik terbuka dan gratis.
 */
class LrcLib(private val client: OkHttpClient) {

    private val cache = java.util.concurrent.ConcurrentHashMap<String, Lyrics>()

    suspend fun lyricsFor(song: Song): Lyrics = withContext(Dispatchers.IO) {
        cache.getOrPut(song.id) { find(song) }
    }

    private fun find(song: Song): Lyrics {
        val candidates = TitleCleaner.candidates(song.title, song.artist)
        var plain: Lyrics.Plain? = null

        for ((artist, title) in candidates) {
            val exact = get(
                "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                    .addQueryParameter("artist_name", artist)
                    .addQueryParameter("track_name", title)
                    .apply { if (song.durationSec > 0) addQueryParameter("duration", song.durationSec.toString()) }
                    .build().toString(),
            )?.let { runCatching { JSONObject(it) }.getOrNull() }
            exact?.toLyrics()?.let { lyrics ->
                if (lyrics is Lyrics.Synced) return lyrics
                if (lyrics is Lyrics.Plain && plain == null) plain = lyrics
            }
        }

        for ((artist, title) in candidates) {
            val body = get(
                "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                    .addQueryParameter("q", "$artist $title".trim())
                    .build().toString(),
            ) ?: continue
            val items = runCatching { JSONArray(body) }.getOrNull() ?: continue
            val list = (0 until items.length()).map { items.getJSONObject(it) }
                .filter { song.durationSec <= 0 || abs(it.optDouble("duration", 0.0) - song.durationSec) <= 8 }
            list.firstOrNull { !it.isNull("syncedLyrics") }?.toLyrics()?.let { return it }
            if (plain == null) plain = list.firstNotNullOfOrNull { it.toLyrics() as? Lyrics.Plain }
        }
        return plain ?: Lyrics.NotFound
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Nada Music (open-source Android player)")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw java.io.IOException("LRCLIB HTTP ${response.code}")
            return response.body?.string()
        }
    }

    private fun JSONObject.toLyrics(): Lyrics? {
        if (optBoolean("instrumental")) return Lyrics.Plain("♪ Instrumental ♪")
        optStringOrNull("syncedLyrics")?.let { lrc ->
            val lines = parseLrc(lrc)
            if (lines.isNotEmpty()) return Lyrics.Synced(lines)
        }
        return optStringOrNull("plainLyrics")?.let { Lyrics.Plain(it) }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    companion object {
        private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

        /** Mengubah format LRC (`[01:23.45]teks`) menjadi daftar baris bertimestamp. */
        fun parseLrc(lrc: String): List<LyricLine> {
            val lines = mutableListOf<LyricLine>()
            lrc.lineSequence().forEach { raw ->
                val tags = TIME_TAG.findAll(raw).toList()
                if (tags.isEmpty()) return@forEach
                val text = raw.substring(tags.last().range.last + 1).trim()
                tags.forEach { tag ->
                    val (min, sec, frac) = tag.destructured
                    val fracMs = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    lines += LyricLine(min.toLong() * 60_000 + sec.toLong() * 1000 + fracMs, text)
                }
            }
            return lines.sortedBy { it.timeMs }
        }
    }
}

/** Membersihkan judul video YouTube supaya cocok dengan database lirik. */
object TitleCleaner {
    private val BRACKETS = Regex("""\s*[(\[【][^)\]】]*[)\]】]""")
    private val NOISE = Regex(
        """(?i)\s*[-|]?\s*\b(official\s*(music\s*)?(video|audio|lyric(s)?\s*video|mv)|lyric(s)?\s*video|video\s*lirik|lirik|lyrics?|audio|hd|hq|4k|mv)\b.*$""",
    )
    private val FEAT = Regex("""(?i)\s*\b(feat\.?|ft\.?|featuring)\s.*$""")

    fun clean(title: String): String =
        title.replace(BRACKETS, "")
            .replace(NOISE, "")
            .substringBefore(" | ")
            .replace(FEAT, "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '|', '"', '\'')

    /** Pasangan (artis, judul) yang dicoba berurutan. */
    fun candidates(title: String, artist: String): List<Pair<String, String>> {
        val cleanArtist = artist.removeSuffix(" - Topic").removeSuffix("VEVO").replace(FEAT, "").trim()
        val cleaned = clean(title)
        val result = mutableListOf<Pair<String, String>>()
        val dash = cleaned.split(" - ", " – ", " — ", limit = 2)
        if (dash.size == 2) {
            result += dash[0].trim() to clean(dash[1])
            result += cleanArtist to clean(dash[1])
        }
        result += cleanArtist to cleaned
        return result.filter { it.second.isNotBlank() }.distinct()
    }
}
