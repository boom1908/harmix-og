package com.boom.harmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.components.EmptyState
import com.boom.harmix.ui.components.GhostPillButton
import com.boom.harmix.ui.components.GoldPillButton
import com.boom.harmix.ui.components.TrackRow
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold
import com.boom.harmix.ui.viewmodel.PlaylistDetailViewModel

@Composable
fun PlaylistDetailScreen(
    onBack: () -> Unit,
    onPlayQueue: (List<StreamItem>, Int) -> Unit,
    onAddToQueue: (StreamItem) -> Unit,
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var optionsTarget by remember { mutableStateOf<StreamItem?>(null) }

    val songs = playlist?.songs.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back to library", tint = Bone)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showRename = true }) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename playlist", tint = Sand)
            }
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = EmberRed)
            }
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(Brush.linearGradient(listOf(SunsetGold.copy(alpha = .45f), EmberRed.copy(alpha = .5f), MidnightBlack)))
                            .border(1.dp, GlassBorder, RoundedCornerShape(26.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            tint = SunsetGold,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    Text(
                        text = playlist?.name ?: "Playlist",
                        color = SunsetGold,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "${songs.size} songs",
                        color = Sand,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GoldPillButton(text = "Play", icon = Icons.Filled.PlayArrow) {
                            if (songs.isNotEmpty()) onPlayQueue(songs, 0)
                        }
                        GhostPillButton(text = "Shuffle", icon = Icons.Filled.Shuffle) {
                            if (songs.isNotEmpty()) onPlayQueue(songs.shuffled(), 0)
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    EmptyState(
                        title = "No songs here yet",
                        detail = "Add songs from Search, or sync your YouTube playlists from Account."
                    )
                }
            } else {
                itemsIndexed(songs) { index, song ->
                    TrackRow(
                        title = song.title,
                        subtitle = song.uploader,
                        artworkUrl = song.thumbnailUrl,
                        index = index,
                        onClick = { onPlayQueue(songs, index) },
                        onMoreClick = { optionsTarget = song }
                    )
                }
            }
        }
    }

    optionsTarget?.let { song ->
        PlaylistSongOptionsSheet(
            song = song,
            onDismiss = { optionsTarget = null },
            onAddToQueue = {
                onAddToQueue(song)
                optionsTarget = null
            },
            onRemove = {
                viewModel.removeSong(song.url)
                optionsTarget = null
            }
        )
    }

    if (showRename) {
        var name by remember { mutableStateOf(playlist?.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist", color = Bone) },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(name)
                    showRename = false
                }) { Text("Save", color = SunsetGold) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) { Text("Cancel", color = Sand) }
            }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete playlist?", color = Bone) },
            text = { Text("This removes the playlist from your library. Saved songs stay.", color = Sand) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deletePlaylist(onBack)
                }) { Text("Delete", color = EmberRed) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Cancel", color = Sand) }
            }
        )
    }
}
