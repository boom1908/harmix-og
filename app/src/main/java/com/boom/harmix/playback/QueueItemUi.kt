package com.boom.harmix.playback

data class QueueItemUi(
    val index: Int,
    val title: String,
    val thumbnailUrl: String?,
    val isCurrent: Boolean
)
