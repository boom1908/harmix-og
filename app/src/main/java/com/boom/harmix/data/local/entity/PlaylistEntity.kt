package com.boom.harmix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** YouTube playlist id when this playlist came from a sync; null for local playlists. */
    val remoteId: String? = null
)
