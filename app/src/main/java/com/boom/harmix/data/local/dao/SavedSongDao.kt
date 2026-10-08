package com.boom.harmix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.boom.harmix.data.local.entity.SavedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSongDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongIfAbsent(song: SavedSongEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SavedSongEntity)

    @Delete
    suspend fun deleteSong(song: SavedSongEntity)

    @Query("SELECT * FROM saved_songs ORDER BY savedAtMillis DESC")
    fun getAllSongs(): Flow<List<SavedSongEntity>>

    @Query("SELECT * FROM saved_songs WHERE liked = 1 ORDER BY savedAtMillis DESC")
    fun getLikedSongs(): Flow<List<SavedSongEntity>>

    @Query("SELECT url FROM saved_songs WHERE liked = 1")
    fun getLikedUrls(): Flow<List<String>>

    @Query("UPDATE saved_songs SET liked = :liked WHERE url = :url")
    suspend fun setLiked(url: String, liked: Boolean)

    @Query("SELECT COALESCE((SELECT liked FROM saved_songs WHERE url = :url), 0)")
    suspend fun isLiked(url: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM saved_songs WHERE url = :url)")
    suspend fun isSongSaved(url: String): Boolean

    @Query("DELETE FROM saved_songs WHERE url = :url")
    suspend fun deleteSongByUrl(url: String)
}
