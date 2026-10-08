package com.boom.harmix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.components.EmptyState
import com.boom.harmix.ui.components.MediaCard
import com.boom.harmix.ui.components.PageHeader
import com.boom.harmix.ui.components.SectionTitle
import com.boom.harmix.ui.components.Shelf
import com.boom.harmix.ui.components.TrackRow
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.viewmodel.HomeUiState
import com.boom.harmix.ui.viewmodel.HomeViewModel
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    isGuest: Boolean,
    username: String?,
    onItemClick: (StreamItem) -> Unit,
    onPlayNext: (StreamItem) -> Unit,
    onAddToQueue: (StreamItem) -> Unit,
    onAddToPlaylistRequest: (StreamItem) -> Unit,
    likedUrls: Set<String>,
    onToggleLike: (StreamItem) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var optionsSheetTarget by remember { mutableStateOf<StreamItem?>(null) }

    LaunchedEffect(isGuest) { viewModel.loadRecommendations() }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = greetingForCurrentTime() + username?.let { ", $it" }.orEmpty(),
            subtitle = if (username != null) "Picked from your listening and what's trending"
            else "Here's what's trending right now"
        )

        when (val state = uiState) {
            is HomeUiState.Loading -> Text(
                text = "Loading recommendations...",
                color = Sand,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            is HomeUiState.Error -> com.boom.harmix.ui.components.ErrorRetryPanel(
                message = state.message,
                isOffline = state.offline,
                onRetry = { viewModel.retry() }
            )

            is HomeUiState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyState(
                        title = "Nothing trending right now",
                        detail = "Pull up a search and start listening — your feed will fill up."
                    )
                } else {
                    val personalized = state.forYou
                    val featured = (personalized.ifEmpty { state.items }).take(8)
                    val rest = if (personalized.isNotEmpty()) {
                        state.items
                    } else {
                        state.items.drop(8)
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            Shelf(
                                title = if (personalized.isNotEmpty()) "For You" else "Made for you",
                                items = featured
                            ) { item ->
                                MediaCard(
                                    title = item.title,
                                    subtitle = item.uploader.ifBlank { "Unknown artist" },
                                    artworkUrl = item.thumbnailUrl,
                                    onClick = { onItemClick(item) },
                                    onMoreClick = { optionsSheetTarget = item },
                                    isLiked = item.url in likedUrls,
                                    onLikeClick = if (!isGuest) ({ onToggleLike(item) }) else null
                                )
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                        item { SectionTitle("Trending now") }
                        items(rest.ifEmpty { state.items }) { item ->
                            TrackRow(
                                title = item.title,
                                subtitle = item.uploader,
                                artworkUrl = item.thumbnailUrl,
                                modifier = Modifier.padding(horizontal = 8.dp),
                                onClick = { onItemClick(item) },
                                onMoreClick = { optionsSheetTarget = item },
                                isLiked = item.url in likedUrls,
                                onLikeClick = if (!isGuest) ({ onToggleLike(item) }) else null
                            )
                        }
                    }
                }
            }
        }
    }

    optionsSheetTarget?.let { target ->
        SongOptionsBottomSheet(
            item = target,
            isGuest = isGuest,
            onDismiss = { optionsSheetTarget = null },
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylistRequest = onAddToPlaylistRequest
        )
    }
}

private fun greetingForCurrentTime(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
}
