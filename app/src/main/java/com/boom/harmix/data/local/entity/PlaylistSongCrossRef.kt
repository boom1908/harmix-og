package com.boom.harmix.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_song_cross_ref",
    primaryKeys = ["playlistId", "songUrl"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["playlistId"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedSongEntity::class,
            parentColumns = ["url"],
            childColumns = ["songUrl"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songUrl")]
)
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songUrl: String,
    val position: Int
)
