package com.boom.harmix.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.boom.harmix.data.local.dao.PlaylistDao
import com.boom.harmix.data.local.dao.SavedSongDao
import com.boom.harmix.data.local.entity.PlaylistEntity
import com.boom.harmix.data.local.entity.PlaylistSongCrossRef
import com.boom.harmix.data.local.entity.SavedSongEntity

@Database(
    entities = [
        SavedSongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HarmixDatabase : RoomDatabase() {
    abstract fun savedSongDao(): SavedSongDao
    abstract fun playlistDao(): PlaylistDao
}
