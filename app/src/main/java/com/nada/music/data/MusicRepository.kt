package com.nada.music.data

import com.nada.music.data.db.MusicDao
import com.nada.music.data.db.PlaylistEntity
import com.nada.music.data.db.PlaylistSongEntity
import com.nada.music.data.db.PlaylistSummary
import com.nada.music.data.db.toEntity
import com.nada.music.data.lyrics.LrcLib
import com.nada.music.data.lyrics.Lyrics
import com.nada.music.data.youtube.SearchResult
import com.nada.music.data.youtube.YouTubeMusic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.schabi.newpipe.extractor.Page

/** Satu pintu untuk semua data: YouTube Music, database lokal, dan lirik. */
class MusicRepository(
    private val dao: MusicDao,
    private val lrcLib: LrcLib,
) {
    // ---- Online ----

    suspend fun search(query: String): SearchResult = YouTubeMusic.search(query)

    suspend fun searchMore(query: String, filter: String, page: Page): SearchResult =
        YouTubeMusic.searchMore(query, filter, page)

    suspend fun suggestions(query: String): List<String> = YouTubeMusic.suggestions(query)

    suspend fun related(songId: String): List<Song> = YouTubeMusic.related(songId)

    suspend fun lyrics(song: Song): Lyrics = lrcLib.lyricsFor(song)

    // ---- Lokal ----

    val history: Flow<List<Song>> = dao.observeHistory(50).map { list -> list.map { it.toSong() } }

    val mostPlayed: Flow<List<Song>> = dao.observeMostPlayed(20).map { list -> list.map { it.toSong() } }

    val likedSongs: Flow<List<Song>> = dao.observeLikedSongs().map { list -> list.map { it.toSong() } }

    val playlists: Flow<List<PlaylistSummary>> = dao.observePlaylists()

    fun isLiked(songId: String): Flow<Boolean> = dao.observeLiked(songId).map { it == true }

    suspend fun recordPlay(song: Song) {
        dao.upsertSong(song.toEntity())
        dao.markPlayed(song.id, System.currentTimeMillis())
    }

    suspend fun setLiked(song: Song, liked: Boolean) {
        dao.upsertSong(song.toEntity())
        dao.setLiked(song.id, liked, if (liked) System.currentTimeMillis() else null)
    }

    suspend fun clearHistory() = dao.clearHistory()

    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.observePlaylist(id)

    fun playlistSongs(id: Long): Flow<List<Song>> =
        dao.observePlaylistSongs(id).map { list -> list.map { it.toSong() } }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim()))

    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())

    suspend fun deletePlaylist(id: Long) = dao.deletePlaylist(id)

    suspend fun addToPlaylist(playlistId: Long, song: Song) {
        dao.upsertSong(song.toEntity())
        dao.insertPlaylistSong(PlaylistSongEntity(playlistId, song.id))
    }

    suspend fun removeFromPlaylist(playlistId: Long, songId: String) =
        dao.removePlaylistSong(playlistId, songId)
}
