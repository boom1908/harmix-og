package com.boom.harmix.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import com.boom.harmix.auth.UserSession
import com.boom.harmix.data.local.PlaylistUi
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.metadata.LyricsResult
import com.boom.harmix.navigation.HarmixNavHost
import com.boom.harmix.navigation.bottomNavItemsFor
import com.boom.harmix.playback.QueueItemUi
import com.boom.harmix.ui.components.Artwork
import com.boom.harmix.ui.components.OfflineBanner
import com.boom.harmix.ui.components.SunsetBrush
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold
import com.boom.harmix.ui.viewmodel.NetworkViewModel
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill

@Composable
fun MainScreen(
    playTrack: (StreamItem) -> Unit,
    onPlayQueue: (List<StreamItem>, Int) -> Unit,
    onPlayNext: (StreamItem) -> Unit,
    onAddToQueue: (StreamItem) -> Unit,
    currentSongTitle: String,
    currentArtist: String,
    currentArtworkUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    canSkipNext: Boolean,
    canSkipPrevious: Boolean,
    queueItems: List<QueueItemUi>,
    playlists: List<PlaylistUi>,
    isGuest: Boolean,
    sessionState: UserSession,
    likedUrls: Set<String>,
    isLiked: Boolean,
    isShuffleOn: Boolean,
    repeatMode: Int,
    onRetrySession: () -> Unit,
    onToggleLike: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    lyricsResult: LyricsResult?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onQueueItemClick: (index: Int) -> Unit,
    onQueueItemRemove: (index: Int) -> Unit,
    onLyricsClick: () -> Unit,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    onSetSleepTimer: (durationMs: Long?, endOfCurrentTrack: Boolean) -> Unit,
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    onCancelSleepTimer: () -> Unit,
    onToggleLikeForItem: (StreamItem) -> Unit,
    playlistDialogTarget: StreamItem?,
    currentTrackForPlaylist: StreamItem?,
    onAddToPlaylistRequest: (StreamItem) -> Unit,
    onDismissPlaylistDialog: () -> Unit,
    onSelectPlaylistForTarget: (playlistId: Long) -> Unit,
    onCreatePlaylistForTarget: (name: String) -> Unit
) {
    when (val session = sessionState) {
        UserSession.Loading -> {
            SessionLoadingScreen()
            return
        }
        is UserSession.Error -> {
            SessionErrorScreen(message = session.message, onRetry = onRetrySession)
            return
        }
        is UserSession.NeedsUsername -> {
            UsernameSetupScreen()
            return
        }
        UserSession.Guest,
        is UserSession.Authenticated -> Unit
    }

    val navController = rememberNavController()
    var isFullPlayerExpanded by remember { mutableStateOf(false) }
    val networkViewModel: NetworkViewModel = hiltViewModel()
    val isOnline by networkViewModel.isOnline.collectAsState()

    BackHandler(enabled = isFullPlayerExpanded) {
        isFullPlayerExpanded = false
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                if (!isOnline) {
                    OfflineBanner(onRetry = { networkViewModel.recheck() })
                }
            },
            bottomBar = {
                Column {
                    MiniPlayer(
                        songTitle = currentSongTitle,
                        artist = currentArtist,
                        artworkUrl = currentArtworkUrl,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onPlayPauseClick = onPlayPauseClick,
                        onExpandClick = { isFullPlayerExpanded = true }
                    )
                    HarmixBottomBar(navController = navController, isGuest = isGuest)
                }
            }
        ) { innerPadding ->
            HarmixNavHost(
                navController = navController,
                playTrack = playTrack,
                onPlayQueue = onPlayQueue,
                isGuest = isGuest,
                username = (sessionState as? UserSession.Authenticated)?.username,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylistRequest = onAddToPlaylistRequest,
                likedUrls = likedUrls,
                onToggleLike = onToggleLikeForItem,
                onOpenSettings = { navController.navigate("settings") },
                onOpenListeningHistory = { navController.navigate("listening-history") },
                modifier = Modifier.padding(innerPadding)
            )
        }

        AnimatedVisibility(
            visible = isFullPlayerExpanded,
            enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }),
            exit = slideOutVertically(targetOffsetY = { fullHeight -> fullHeight })
        ) {
            FullScreenPlayerScreen(
                songTitle = currentSongTitle,
                artist = currentArtist,
                artworkUrl = currentArtworkUrl,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                canSkipNext = canSkipNext,
                canSkipPrevious = canSkipPrevious,
                isGuest = isGuest,
                isLiked = isLiked,
                isShuffleOn = isShuffleOn,
                repeatMode = repeatMode,
                onToggleLike = onToggleLike,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                queueItems = queueItems,
                lyricsResult = lyricsResult,
                onPlayPauseClick = onPlayPauseClick,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeekTo = onSeekTo,
                onAddCurrentTrackToPlaylistRequest = {
                    currentTrackForPlaylist?.let { onAddToPlaylistRequest(it) }
                },
                onQueueItemClick = onQueueItemClick,
                onQueueItemRemove = onQueueItemRemove,
                onLyricsClick = onLyricsClick,
                 playbackSpeed = playbackSpeed,
                 onPlaybackSpeedChange = onPlaybackSpeedChange,
                 onSetSleepTimer = onSetSleepTimer,
                  sleepTimerActive = sleepTimerActive,
                  sleepTimerRemainingMs = sleepTimerRemainingMs,
                  onCancelSleepTimer = onCancelSleepTimer,
                onCollapse = { isFullPlayerExpanded = false }
            )
        }
    }

    if (playlistDialogTarget != null) {
        PlaylistSelectionDialog(
            playlists = playlists,
            onDismiss = onDismissPlaylistDialog,
            onSelectPlaylist = onSelectPlaylistForTarget,
            onCreateAndSelect = onCreatePlaylistForTarget
        )
    }
}

@Composable
private fun MiniPlayer(
    songTitle: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onExpandClick: () -> Unit
) {
    val idle = songTitle == "Nothing playing"
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(68.dp)
            .clip(shape)
            .background(GlassFill)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onExpandClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(artworkUrl, songTitle, modifier = Modifier.size(48.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = songTitle,
                color = if (idle) Sand else Bone,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!idle) {
                Text(
                    text = artist.ifBlank { "Unknown artist" },
                    color = Sand,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isBuffering && !isPlaying) {
            CircularProgressIndicator(
                color = SunsetGold,
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.size(12.dp))
        } else {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(SunsetBrush)
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MidnightBlack
                )
            }
        }
    }
}

@Composable
private fun HarmixBottomBar(navController: androidx.navigation.NavHostController, isGuest: Boolean) {
    val items = bottomNavItemsFor(isGuest)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MidnightBlack)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { destination ->
            val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable {
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(CircleShape)
                        .then(if (selected) Modifier.background(SunsetBrush) else Modifier)
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        destination.icon,
                        contentDescription = destination.label,
                        tint = if (selected) MidnightBlack else Sand,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = destination.label,
                    color = if (selected) SunsetGold else Sand,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}
