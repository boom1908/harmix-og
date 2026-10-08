package com.boom.harmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.boom.harmix.data.local.PlaylistUi
import com.boom.harmix.ui.theme.CoolGray
import com.boom.harmix.ui.theme.DeepMidnight
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.MistWhite
import com.boom.harmix.ui.theme.ZenCyan

@Composable
fun PlaylistSelectionDialog(
    playlists: List<PlaylistUi>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (playlistId: Long) -> Unit,
    onCreateAndSelect: (name: String) -> Unit
) {
    var showCreateRow by remember { mutableStateOf(playlists.isEmpty()) }
    var newPlaylistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepMidnight,
        title = { Text(text = "Add to Playlist", color = MistWhite) },
        text = {
            Column {
                if (playlists.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.height(240.dp)) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(GlassFill)
                                    .clickable { onSelectPlaylist(playlist.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LibraryMusic,
                                    contentDescription = null,
                                    tint = ZenCyan
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(text = playlist.name, color = MistWhite, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${playlist.songs.size} songs",
                                        color = CoolGray,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }

                if (showCreateRow) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("New playlist name", color = CoolGray) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ZenCyan,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = MistWhite,
                            unfocusedTextColor = MistWhite,
                            cursorColor = ZenCyan
                        )
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clickable { showCreateRow = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = ZenCyan)
                        Text(
                            text = "Create new playlist",
                            color = ZenCyan,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateRow) {
                Button(
                    onClick = { onCreateAndSelect(newPlaylistName.trim()) },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenCyan, contentColor = DeepMidnight),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Create & Add")
                }
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GlassFill, contentColor = MistWhite),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepMidnight,
        title = { Text(text = "New Playlist", color = MistWhite) },
        text = {
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                placeholder = { Text("Playlist name", color = CoolGray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZenCyan,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = MistWhite,
                    unfocusedTextColor = MistWhite,
                    cursorColor = ZenCyan
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { onCreate(playlistName.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = ZenCyan, contentColor = DeepMidnight),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GlassFill, contentColor = MistWhite),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}
