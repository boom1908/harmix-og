package com.boom.harmix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.components.EmptyState
import com.boom.harmix.ui.components.PageHeader
import com.boom.harmix.ui.components.TrackRow
import com.boom.harmix.ui.theme.MistWhite
import com.boom.harmix.ui.viewmodel.ListeningHistoryViewModel

@Composable
fun ListeningHistoryScreen(
    onBack: () -> Unit,
    onItemClick: (StreamItem) -> Unit,
    likedUrls: Set<String>,
    onToggleLike: (StreamItem) -> Unit,
    viewModel: ListeningHistoryViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = "Listening history",
            subtitle = "Songs you've played recently",
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MistWhite)
                }
            }
        )

        if (history.isEmpty()) {
            EmptyState(
                title = "No listening history yet",
                detail = "Play a song for a little while and it will appear here."
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                items(history, key = { it.id }) { entry ->
                    TrackRow(
                        title = entry.song.title,
                        subtitle = entry.song.uploader,
                        artworkUrl = entry.song.thumbnailUrl,
                        onClick = { onItemClick(entry.song) },
                        isLiked = entry.song.url in likedUrls,
                        onLikeClick = { onToggleLike(entry.song) }
                    )
                }
            }
        }
    }
}