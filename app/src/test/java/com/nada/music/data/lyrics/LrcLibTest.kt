package com.nada.music.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcLibTest {

    @Test
    fun parsesSyncedLyrics() {
        val lrc = """
            [ar:Noah]
            [00:12.34]Baris pertama
            [00:15.5]Baris kedua
            [01:02.123]Baris ketiga
            [00:20.00][00:40.00]Reff diulang
        """.trimIndent()

        val lines = LrcLib.parseLrc(lrc)

        assertEquals(5, lines.size)
        assertEquals(LyricLine(12_340, "Baris pertama"), lines[0])
        assertEquals(LyricLine(15_500, "Baris kedua"), lines[1])
        assertEquals(LyricLine(20_000, "Reff diulang"), lines[2])
        assertEquals(LyricLine(40_000, "Reff diulang"), lines[3])
        assertEquals(LyricLine(62_123, "Baris ketiga"), lines[4])
    }

    @Test
    fun cleansYoutubeTitles() {
        assertEquals("Separuh Aku", TitleCleaner.clean("Separuh Aku (Official Music Video)"))
        assertEquals("Noah - Separuh Aku", TitleCleaner.clean("Noah - Separuh Aku [Lirik Video]"))
        assertEquals("Hello", TitleCleaner.clean("Hello | Official Video"))
        assertEquals("Shape of You", TitleCleaner.clean("Shape of You (feat. Someone) [HD]"))
    }

    @Test
    fun buildsArtistTitleCandidates() {
        val candidates = TitleCleaner.candidates("Noah - Separuh Aku [Lirik Video]", "91 FM")
        assertEquals("Noah" to "Separuh Aku", candidates.first())
        assertTrue(("91 FM" to "Separuh Aku") in candidates)

        val topic = TitleCleaner.candidates("Separuh Aku", "Noah - Topic")
        assertEquals(listOf("Noah" to "Separuh Aku"), topic)
    }
}
