package com.boom.harmix.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.boom.harmix.metadata.LyricsResult
import com.boom.harmix.playback.QueueItemUi
import com.boom.harmix.ui.components.Artwork
import com.boom.harmix.ui.components.SunsetBrush
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold
import java.util.concurrent.TimeUnit

@Composable
fun FullScreenPlayerScreen(
    songTitle: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
    isGuest: Boolean,
    isLiked: Boolean,
    isShuffleOn: Boolean,
    repeatMode: Int,
    queueItems: List<QueueItemUi>,
    lyricsResult: LyricsResult?,
    onPlayPauseClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onAddCurrentTrackToPlaylistRequest: () -> Unit,
    onQueueItemClick: (index: Int) -> Unit,
    onQueueItemRemove: (index: Int) -> Unit,
    onLyricsClick: () -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (durationMs: Long?, endOfCurrentTrack: Boolean) -> Unit,
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    onCancelSleepTimer: () -> Unit,
    onCollapse: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showPlayerMenu by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showPlaybackSpeedSheet by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MidnightBlack)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .pointerInput(onCollapse) {
                var downwardDistance = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0f && scrollState.value == 0) {
                            downwardDistance += dragAmount
                            change.consume()
                        } else if (dragAmount < 0f) {
                            downwardDistance = 0f
                        }
                    },
                    onDragEnd = {
                        if (downwardDistance >= dismissThresholdPx) onCollapse()
                        downwardDistance = 0f
                    },
                    onDragCancel = { downwardDistance = 0f }
                )
            }
    ) {
        // Warm glow behind the artwork, like the web preview.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(SunsetGold.copy(alpha = 0.18f), EmberRed.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Collapse player", tint = Bone)
                    }
                }
                Text(
                    text = "NOW PLAYING",
                    color = Sand,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(2f),
                    maxLines = 1
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isGuest) {
                            IconButton(onClick = onAddCurrentTrackToPlaylistRequest) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = Sand)
                            }
                        }
                        Box {
                            IconButton(onClick = { showPlayerMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Player options", tint = Sand)
                            }
                            DropdownMenu(
                                expanded = showPlayerMenu,
                                onDismissRequest = { showPlayerMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sleep Timer") },
                                    onClick = {
                                        showPlayerMenu = false
                                        showSleepTimerSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Playback Speed") },
                                    onClick = {
                                        showPlayerMenu = false
                                        showPlaybackSpeedSheet = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Artwork(
                url = artworkUrl,
                contentDescription = songTitle,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(top = 12.dp)
                    .border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = songTitle,
                        color = Bone,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist.ifBlank { "Unknown artist" },
                        color = Sand,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (!isGuest) {
                    IconButton(onClick = onToggleLike) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isLiked) "Remove from liked songs" else "Add to liked songs",
                            tint = if (isLiked) SunsetGold else Sand
                        )
                    }
                }
            }

            val sliderPosition = if (isDragging) dragPositionMs else currentPositionMs.toFloat()
            val sliderMax = durationMs.coerceAtLeast(1L).toFloat()

            Slider(
                value = sliderPosition.coerceIn(0f, sliderMax),
                onValueChange = { isDragging = true; dragPositionMs = it },
                onValueChangeFinished = { onSeekTo(dragPositionMs.toLong()); isDragging = false },
                valueRange = 0f..sliderMax,
                colors = SliderDefaults.colors(
                    thumbColor = SunsetGold,
                    activeTrackColor = SunsetGold,
                    inactiveTrackColor = Sand.copy(alpha = 0.25f)
                ),
                modifier = Modifier.padding(top = 14.dp)
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMillis(sliderPosition.toLong()), color = Sand, style = MaterialTheme.typography.labelSmall)
                Text(formatMillis(durationMs), color = Sand, style = MaterialTheme.typography.labelSmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleShuffle) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleOn) SunsetGold else Sand
                    )
                }
                IconButton(onClick = onSkipPrevious, enabled = canSkipPrevious) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "Previous",
                        tint = if (canSkipPrevious) Bone else Sand.copy(alpha = 0.4f),
                        modifier = Modifier.size(34.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(SunsetBrush)
                        .clickable(enabled = !(isBuffering && !isPlaying), onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering && !isPlaying) {
                        CircularProgressIndicator(
                            color = MidnightBlack,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MidnightBlack,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(onClick = onSkipNext, enabled = canSkipNext) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "Next",
                        tint = if (canSkipNext) Bone else Sand.copy(alpha = 0.4f),
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = onCycleRepeat) {
                    Icon(
                        imageVector = if (repeatMode == REPEAT_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == REPEAT_OFF) Sand else SunsetGold
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(GlassFill)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable { showQueueSheet = true }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Up next", color = Sand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(GlassFill)
                        .border(1.dp, GlassBorder, CircleShape)
                        .clickable {
                            showLyricsSheet = true
                            onLyricsClick()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Lyrics", color = Sand, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(120.dp))
        }
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queueItems = queueItems,
            onDismiss = { showQueueSheet = false },
            onItemClick = onQueueItemClick,
            onRemoveItem = onQueueItemRemove
        )
    }

    if (showLyricsSheet) {
        LyricsBottomSheet(
            lyricsResult = lyricsResult,
            currentPositionMs = currentPositionMs,
            onDismiss = { showLyricsSheet = false }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerBottomSheet(
            onDismiss = { showSleepTimerSheet = false },
            isTimerActive = sleepTimerActive,
            remainingMs = sleepTimerRemainingMs,
            onCancel = onCancelSleepTimer,
            onApply = { durationMs, endOfCurrentTrack ->
                onSetSleepTimer(durationMs, endOfCurrentTrack)
                showSleepTimerSheet = false
            }
        )
    }

    if (showPlaybackSpeedSheet) {
        PlaybackSpeedBottomSheet(
            currentSpeed = playbackSpeed,
            onDismiss = { showPlaybackSpeedSheet = false },
            onApply = {
                onPlaybackSpeedChange(it)
                showPlaybackSpeedSheet = false
            }
        )
    }
}

private const val REPEAT_OFF = 0
private const val REPEAT_ONE = 1

private fun formatMillis(millis: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(millis.coerceAtLeast(0L))
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
