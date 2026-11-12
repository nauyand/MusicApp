package com.nada.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // ---- Lagu ----

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIgnore(song: SongEntity): Long

    @Query(
        "UPDATE songs SET title = :title, artist = :artist, thumbnailUrl = :thumbnailUrl, " +
            "durationSec = CASE WHEN :durationSec > 0 THEN :durationSec ELSE durationSec END WHERE id = :id",
    )
    suspend fun updateSongMeta(id: String, title: String, artist: String, thumbnailUrl: String?, durationSec: Long)

    /** Simpan metadata lagu tanpa menghapus status suka / riwayat. */
    @Transaction
    suspend fun upsertSong(song: SongEntity) {
        if (insertSongIgnore(song) == -1L) {
            updateSongMeta(song.id, song.title, song.artist, song.thumbnailUrl, song.durationSec)
        }
    }

    @Query("UPDATE songs SET liked = :liked, likedAt = :at WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, at: Long?)

    @Query("UPDATE songs SET lastPlayedAt = :at, playCount = playCount + 1 WHERE id = :id")
    suspend fun markPlayed(id: String, at: Long)

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun observeLiked(id: String): Flow<Boolean?>

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun observeLikedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<SongEntity>>

    @Query("UPDATE songs SET localAddedAt = :at WHERE id = :id AND localAddedAt IS NULL")
    suspend fun markLocal(id: String, at: Long)

    @Query("UPDATE songs SET localAddedAt = NULL WHERE id = :id")
    suspend fun unmarkLocal(id: String)

    @Query("SELECT * FROM songs WHERE localAddedAt IS NOT NULL ORDER BY title COLLATE NOCASE")
    fun observeLocalSongs(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET lastPlayedAt = NULL")
    suspend fun clearHistory()

    // ---- Playlist ----

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT p.id, p.name, p.createdAt, COUNT(ps.songId) AS songCount,
            (SELECT s.thumbnailUrl FROM playlist_songs ps2 JOIN songs s ON s.id = ps2.songId
             WHERE ps2.playlistId = p.id ORDER BY ps2.addedAt LIMIT 1) AS thumbnailUrl
        FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id ORDER BY p.createdAt DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(entry: PlaylistSongEntity)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removePlaylistSong(playlistId: Long, songId: String)

    @Query(
        "SELECT s.* FROM songs s JOIN playlist_songs ps ON ps.songId = s.id " +
            "WHERE ps.playlistId = :playlistId ORDER BY ps.addedAt",
    )
    fun observePlaylistSongs(playlistId: Long): Flow<List<SongEntity>>
}
