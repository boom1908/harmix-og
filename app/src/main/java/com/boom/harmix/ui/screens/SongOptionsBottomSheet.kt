package com.boom.harmix.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongOptionsBottomSheet(
    item: StreamItem,
    isGuest: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: (StreamItem) -> Unit,
    onAddToQueue: (StreamItem) -> Unit,
    onAddToPlaylistRequest: (StreamItem) -> Unit
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MidnightBlack
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = item.title,
                color = Bone,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            OptionRow(
                icon = Icons.Filled.PlaylistPlay,
                label = "Play Next",
                onClick = { onPlayNext(item); onDismiss() }
            )
            OptionRow(
                icon = Icons.Filled.QueueMusic,
                label = "Add to Queue",
                onClick = { onAddToQueue(item); onDismiss() }
            )

            if (!isGuest) {
                OptionRow(
                    icon = Icons.Filled.PlaylistAdd,
                    label = "Add to Playlist",
                    onClick = { onAddToPlaylistRequest(item); onDismiss() }
                )
            }

            OptionRow(
                icon = Icons.Filled.Share,
                label = "Share",
                onClick = {
                    copyLinkToClipboard(context, item.url)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = SunsetGold)
        Text(
            text = label,
            color = Bone,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

private fun copyLinkToClipboard(context: Context, url: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboardManager.setPrimaryClip(ClipData.newPlainText("Harmix song link", url))
    Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
}

/** Options for a song that already lives inside a playlist. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSongOptionsSheet(
    song: StreamItem,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemove: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MidnightBlack) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = song.title,
                color = Bone,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            OptionRow(
                icon = Icons.Filled.QueueMusic,
                label = "Add to queue",
                onClick = onAddToQueue
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Filled.Delete, contentDescription = "Remove", tint = EmberRed)
                Text(
                    text = "Remove from playlist",
                    color = EmberRed,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
