package com.boom.harmix.extractor

data class StreamItem(
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val uploader: String = "",
    val durationSeconds: Int? = null
)
