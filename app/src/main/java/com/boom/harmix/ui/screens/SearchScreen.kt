package com.boom.harmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.components.EmptyState
import com.boom.harmix.ui.components.HarmixChip
import com.boom.harmix.ui.components.PageHeader
import com.boom.harmix.ui.components.TrackRow
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold
import com.boom.harmix.ui.viewmodel.SearchUiState
import com.boom.harmix.ui.viewmodel.SearchViewModel

private val QUICK_PICKS = listOf("Lo-fi", "Bollywood", "Hip-hop", "Chill", "Workout", "Romance")

@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    isGuest: Boolean,
    onItemClick: (StreamItem) -> Unit,
    onPlayNext: (StreamItem) -> Unit,
    onAddToQueue: (StreamItem) -> Unit,
    onAddToPlaylistRequest: (StreamItem) -> Unit,
    likedUrls: Set<String>,
    onToggleLike: (StreamItem) -> Unit
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchHistory by viewModel.history.collectAsStateWithLifecycle()
    var optionsSheetTarget by remember { mutableStateOf<StreamItem?>(null) }
    var isSearchFocused by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = "Search", subtitle = "Find any song, artist or mix")

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(CircleShape)
                .background(GlassFill)
                .border(1.dp, GlassBorder, CircleShape)
                .onFocusChanged { isSearchFocused = it.isFocused },
            placeholder = { Text("Search any song...", color = Sand) },
            singleLine = true,
            shape = CircleShape,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Search
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { viewModel.runSearch() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedTextColor = Bone,
                unfocusedTextColor = Bone,
                cursorColor = SunsetGold,
                focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QUICK_PICKS.take(4).forEach { pick ->
                HarmixChip(text = pick, selected = query.equals(pick, ignoreCase = true)) {
                    viewModel.onQueryChanged(pick)
                    viewModel.runSearch()
                }
            }
        }

        if (isSearchFocused && query.isBlank() && searchHistory.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recent searches", color = Bone, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Clear",
                    color = SunsetGold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(CircleShape).clickable { viewModel.clearHistory() }
                )
            }
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(searchHistory, key = { it }) { term ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectHistory(term) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = Sand
                        )
                        Text(
                            text = term,
                            color = Bone,
                            modifier = Modifier.weight(1f).padding(start = 12.dp)
                        )
                        androidx.compose.material3.IconButton(onClick = { viewModel.removeHistory(term) }) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove $term from search history",
                                tint = Sand
                            )
                        }
                    }
                }
            }
        } else when (val state = uiState) {
            is SearchUiState.Idle -> EmptyState(
                title = "Start typing",
                detail = "Search results will show up here."
            )

            is SearchUiState.Loading -> Text(
                text = "Searching...",
                color = Sand,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            is SearchUiState.Error -> com.boom.harmix.ui.components.ErrorRetryPanel(
                message = state.message,
                isOffline = state.offline,
                onRetry = { viewModel.runSearch() }
            )

            is SearchUiState.Success -> {
                if (state.items.isEmpty()) {
                    EmptyState(title = "No results", detail = "Try a different spelling or artist name.")
                } else {
                    LazyColumn(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                    ) {
                        items(state.items) { item ->
                            TrackRow(
                                title = item.title,
                                subtitle = item.uploader,
                                artworkUrl = item.thumbnailUrl,
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
