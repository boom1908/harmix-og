package com.boom.harmix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.boom.harmix.data.local.entity.PlaylistEntity
import com.boom.harmix.data.local.entity.PlaylistSongCrossRef
import com.boom.harmix.data.local.entity.PlaylistWithSongs
import com.boom.harmix.data.local.entity.SavedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("SELECT * FROM playlists ORDER BY createdAtMillis DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAtMillis DESC")
    fun getPlaylistsWithSongs(): Flow<List<PlaylistWithSongs>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    fun getPlaylist(playlistId: Long): Flow<PlaylistEntity?>

    /** Songs in the order the user (or the sync) added them. */
    @Query(
        """
        SELECT s.* FROM saved_songs s
        INNER JOIN playlist_song_cross_ref r ON s.url = r.songUrl
        WHERE r.playlistId = :playlistId
        ORDER BY r.position ASC
        """
    )
    fun getPlaylistSongs(playlistId: Long): Flow<List<SavedSongEntity>>

    @Query("SELECT * FROM playlists WHERE remoteId = :remoteId LIMIT 1")
    suspend fun findPlaylistByRemoteId(remoteId: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE remoteId = :remoteId ORDER BY playlistId ASC")
    suspend fun findPlaylistsByRemoteId(remoteId: String): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE remoteId IS NULL AND name = :name LIMIT 1")
    suspend fun findLocalPlaylistByName(name: String): PlaylistEntity?

    @Query("UPDATE playlists SET remoteId = :remoteId WHERE playlistId = :playlistId")
    suspend fun setRemoteId(playlistId: Long, remoteId: String)

    @Query("UPDATE playlists SET name = :name WHERE playlistId = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songUrl = :songUrl)")
    suspend fun isSongInPlaylist(playlistId: Long, songUrl: String): Boolean

    @Query("SELECT * FROM playlist_song_cross_ref WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongRefs(playlistId: Long): List<PlaylistSongCrossRef>

    @Query("DELETE FROM playlist_song_cross_ref WHERE playlistId = :playlistId AND songUrl = :songUrl")
    suspend fun removeSongFromPlaylist(playlistId: Long, songUrl: String)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_song_cross_ref WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}
