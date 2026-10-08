package com.boom.harmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boom.harmix.data.local.PlaylistUi
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.components.EmptyState
import com.boom.harmix.ui.components.HarmixChip
import com.boom.harmix.ui.components.PageHeader
import com.boom.harmix.ui.components.SunsetBrush
import com.boom.harmix.ui.components.TrackRow
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.viewmodel.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onPlayQueue: (List<StreamItem>, Int) -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val savedSongs by viewModel.savedSongs.collectAsStateWithLifecycle()
    val likedSongs by viewModel.likedSongs.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf("Playlists") }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = "Your Library",
            subtitle = "${playlists.size} playlists · ${savedSongs.size} songs",
            trailing = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SunsetBrush)
                        .clickable { showCreateDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create playlist", tint = MidnightBlack)
                }
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Playlists", "Liked", "Songs").forEach { label ->
                HarmixChip(text = label, selected = tab == label) { tab = label }
            }
        }

        if (tab == "Playlists") {
            if (playlists.isEmpty()) {
                EmptyState(
                    title = "No playlists yet",
                    detail = "Tap the gold + button to create your first one."
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistCard(playlist) { onOpenPlaylist(playlist.id) }
                    }
                }
            }
        } else {
            val songs = if (tab == "Liked") likedSongs else savedSongs
            if (songs.isEmpty()) {
                EmptyState(
                    title = if (tab == "Liked") "Nothing liked yet" else "No saved songs",
                    detail = "Tap the heart in the player to add songs here."
                )
            } else {
                LazyColumn(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    items(songs) { song ->
                        TrackRow(
                            title = song.title,
                            subtitle = song.uploader,
                            artworkUrl = song.thumbnailUrl,
                            onClick = { onPlayQueue(songs, songs.indexOf(song)) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PlaylistCard(playlist: PlaylistUi, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(EmberRed.copy(alpha = .55f), MidnightBlack))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.LibraryMusic,
                contentDescription = playlist.name,
                tint = com.boom.harmix.ui.theme.SunsetGold,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = playlist.name,
            color = Bone,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )
        Text(
            text = "${playlist.songs.size} songs",
            color = Sand,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
