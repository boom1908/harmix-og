package com.boom.harmix.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_songs")
data class SavedSongEntity(
    @PrimaryKey val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val uploader: String,
    val savedAtMillis: Long = System.currentTimeMillis(),
    /** True when the user tapped the heart — "Liked songs" in the library. */
    val liked: Boolean = false
)
