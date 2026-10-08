package com.boom.harmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.boom.harmix.playback.QueueItemUi
import com.boom.harmix.ui.components.Artwork
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold

/**
 * The one and only "Up next" list — a draggable partial-height sheet in the
 * sunset gold theme. Used by the full-screen player's Up next button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queueItems: List<QueueItemUi>,
    onDismiss: () -> Unit,
    onItemClick: (index: Int) -> Unit,
    onRemoveItem: (index: Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val listState = rememberLazyListState()
    val currentIndex = queueItems.indexOfFirst { it.isCurrent }

    // Open the sheet already positioned on the track that is playing, roughly
    // centred, instead of at the top of the queue.
    LaunchedEffect(currentIndex, queueItems.size) {
        if (currentIndex >= 0) {
            listState.scrollToItem(index = currentIndex, scrollOffset = -160)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightBlack,
        contentColor = Bone
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.62f)) {
            Text(
                text = "Up next",
                color = Bone,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp)
            )

            if (queueItems.isEmpty()) {
                Text(
                    text = "Queue is empty.",
                    color = Sand,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                    items(queueItems, key = { it.index }) { queueItem ->
                        QueueRow(
                            item = queueItem,
                            onClick = { onItemClick(queueItem.index) },
                            onRemove = { onRemoveItem(queueItem.index) }
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItemUi,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (item.isCurrent) Modifier.background(GlassFill) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(item.thumbnailUrl, item.title, modifier = Modifier.size(46.dp))

        Text(
            text = item.title,
            color = if (item.isCurrent) SunsetGold else Bone,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )

        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove from queue", tint = Sand)
        }
    }
}
