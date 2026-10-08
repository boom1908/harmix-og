package com.boom.harmix

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.updateAll
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.boom.harmix.auth.UserSession
import com.boom.harmix.auth.UserSessionRepository
import com.boom.harmix.data.cloud.ListeningHistoryRepository
import com.boom.harmix.data.local.LibraryRepository
import com.boom.harmix.data.local.PlaylistUi
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.metadata.LyricsResult
import com.boom.harmix.metadata.MetadataRepository
import com.boom.harmix.playback.HarmixPlaybackService
import com.boom.harmix.playback.LastPlayedStore
import com.boom.harmix.playback.QueueItemUi
import com.boom.harmix.ui.screens.MainScreen
import com.boom.harmix.ui.theme.HarmixTheme
import com.boom.harmix.widget.NowPlayingWidget
import com.boom.harmix.widget.NowPlayingWidgetState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var libraryRepository: LibraryRepository

    @Inject
    lateinit var metadataRepository: MetadataRepository

    @Inject
    lateinit var userSessionRepository: UserSessionRepository

    @Inject
    lateinit var lastPlayedStore: LastPlayedStore

    @Inject
    lateinit var listeningHistoryRepository: ListeningHistoryRepository

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var currentSongTitle by mutableStateOf("Nothing playing")
    private var currentArtist by mutableStateOf("")
    private var currentArtworkUrl by mutableStateOf<String?>(null)
    private var currentTrackUrl by mutableStateOf<String?>(null)
    private var isPlaying by mutableStateOf(false)
    private var currentPositionMs by mutableLongStateOf(0L)
    private var rawPlayerDurationMs by mutableLongStateOf(0L)
    private var prefetchedDurationMs by mutableLongStateOf(0L)

    private val effectiveDurationMs: Long
        get() = if (rawPlayerDurationMs > 0) rawPlayerDurationMs else prefetchedDurationMs

    private var canSkipNext by mutableStateOf(false)
    private var canSkipPrevious by mutableStateOf(false)
    private var playlists by mutableStateOf<List<PlaylistUi>>(emptyList())
    private var isGuest by mutableStateOf(true)
    private var sessionState by mutableStateOf<UserSession>(UserSession.Loading)
    private var queueItems by mutableStateOf<List<QueueItemUi>>(emptyList())
    private var playlistDialogTarget by mutableStateOf<StreamItem?>(null)
    private var lyricsResult by mutableStateOf<LyricsResult?>(null)
    private var isExtendingQueue = false

    private var isLiked by mutableStateOf(false)
    private var likedUrls by mutableStateOf<Set<String>>(emptySet())
    private var isShuffleOn by mutableStateOf(false)
    private var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)

    private var isBuffering by mutableStateOf(false)
    private var pendingBufferingJob: Job? = null
    private var playbackSpeed by mutableStateOf(1f)
    private var sleepTimerJob: Job? = null
    private var sleepAtEndOfTrackUrl: String? = null
    private var sleepTimerDeadlineMs by mutableStateOf<Long?>(null)
    private var sleepTimerRemainingMs by mutableLongStateOf(0L)
    private var lastPositionPersistedAtMs = 0L
    private var restoringLastPlayed = false
    private var trackedPlaybackItem: StreamItem? = null
    private var trackedPlaybackUid: String? = null
    private var trackedPlaybackMs = 0L
    private var lastPlaybackSampleElapsedMs: Long? = null
    private var pendingListeningMs = 0L
    private var pendingListeningUid: String? = null
    private var autoRadioItemsAdded = 0
    private var personalizedQueueLoaded = false
    private var personalizedQueueFailures = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastPlayedStore.read()?.let { lastPlayed ->
            currentSongTitle = lastPlayed.title
            currentArtist = lastPlayed.artist
            currentArtworkUrl = lastPlayed.artworkUrl
            currentTrackUrl = lastPlayed.url
        }
        observePlaylists()
        lifecycleScope.launch {
            libraryRepository.getLikedUrls().collect { urls ->
                likedUrls = urls
                isLiked = currentTrackUrl?.let { urls.contains(it) } == true
                currentTrackUrl?.let { refreshWidgetLibraryState(it) }
            }
        }
        lifecycleScope.launch {
            userSessionRepository.session.collect {
                sessionState = it
                isGuest = it !is UserSession.Authenticated
            }
        }

        val sessionToken = SessionToken(this, ComponentName(this, HarmixPlaybackService::class.java))

        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync().also { future ->
            future.addListener(
                {
                    try {
                        mediaController = future.get()
                        attachPlayerListener()
                        startPositionTicker()
                        handleWidgetIntent(intent)
                    } catch (e: Exception) {
                        Log.e("Harmix", "Failed to connect MediaController", e)
                    }
                },
                MoreExecutors.directExecutor()
            )
        }

        setContent {
            HarmixTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen(
                        playTrack = { item -> playQueue(listOf(item), 0) },
                        onPlayQueue = ::playQueue,
                        onPlayNext = ::playNext,
                        onAddToQueue = ::addToQueue,
                        currentSongTitle = currentSongTitle,
                        currentArtist = currentArtist,
                        currentArtworkUrl = currentArtworkUrl,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        currentPositionMs = currentPositionMs,
                        durationMs = effectiveDurationMs,
                        canSkipNext = canSkipNext,
                        canSkipPrevious = canSkipPrevious,
                        queueItems = queueItems,
                        playlists = playlists,
                        likedUrls = likedUrls,
                        isGuest = isGuest,
                        sessionState = sessionState,
                        isLiked = isLiked,
                        isShuffleOn = isShuffleOn,
                        repeatMode = repeatMode,
                        onToggleLike = ::toggleLikeForCurrentTrack,
                        onToggleLikeForItem = ::toggleLikeForItem,
                        onToggleShuffle = ::toggleShuffle,
                        onCycleRepeat = ::cycleRepeat,
                        onSignIn = { },
                        onSignOut = { },
                        onPlayPauseClick = ::togglePlayPause,
                        onSkipNext = { mediaController?.seekToNext() },
                        onSkipPrevious = { mediaController?.seekToPrevious() },
                        onSeekTo = { positionMs -> mediaController?.seekTo(positionMs) },
                        onQueueItemClick = { index -> mediaController?.seekTo(index, 0L) },
                        onQueueItemRemove = ::removeQueueItem,
                        playlistDialogTarget = playlistDialogTarget,
                        onAddToPlaylistRequest = { item -> playlistDialogTarget = item },
                        onDismissPlaylistDialog = { playlistDialogTarget = null },
                        onSelectPlaylistForTarget = ::addTargetToPlaylist,
                        onCreatePlaylistForTarget = ::createPlaylistAndAddTarget,
                        currentTrackForPlaylist = currentStreamItemOrNull(),
                        lyricsResult = lyricsResult,
                        onRetrySession = userSessionRepository::refresh,
                        onLyricsClick = ::fetchLyricsForCurrentTrack,
                        playbackSpeed = playbackSpeed,
                        onPlaybackSpeedChange = ::applyPlaybackSpeed,
                        onSetSleepTimer = ::setSleepTimer,
                        sleepTimerActive = sleepTimerDeadlineMs != null || sleepAtEndOfTrackUrl != null,
                        sleepTimerRemainingMs = sleepTimerRemainingMs,
                        onCancelSleepTimer = ::cancelSleepTimer
                    )
                }
            }
        }
        handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYLIST_PICKER, false) != true) return
        val item = currentStreamItemOrNull() ?: return
        playlistDialogTarget = item
        intent.removeExtra(EXTRA_OPEN_PLAYLIST_PICKER)
    }

    private fun observePlaylists() {
        lifecycleScope.launch {
            libraryRepository.getPlaylists().collect { list ->
                playlists = list
                currentTrackUrl?.let { refreshWidgetLibraryState(it) }
            }
        }
    }

    private fun attachPlayerListener() {
        val controller = mediaController ?: return
        controller.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Keep the last known metadata visible while Media3 reconnects or skips
                // an unplayable stream. Replacing it with an error makes a cold start
                // look like the app lost the current track.
                Log.e("Harmix", "Playback error", error)
                clearBufferingImmediately()
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) clearBufferingImmediately()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING) scheduleBufferingIndicator()
                else clearBufferingImmediately()
                if (playbackState == Player.STATE_ENDED) {
                    finalizeTrackedPlayback(forceHistory = true)
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                finalizeTrackedPlayback(
                    forceHistory = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                )
                currentSongTitle = mediaItem?.mediaMetadata?.title?.toString() ?: "Nothing playing"
                currentArtist = mediaItem?.mediaMetadata?.artist?.toString() ?: ""
                currentArtworkUrl = mediaItem?.mediaMetadata?.artworkUri?.toString()
                currentTrackUrl = mediaItem?.mediaId
                isLiked = mediaItem?.mediaId?.let { likedUrls.contains(it) } == true
                prefetchedDurationMs = mediaItem?.mediaMetadata?.extras?.getLong("harmix_duration_ms") ?: 0L
                rawPlayerDurationMs = 0L
                lyricsResult = null
                isExtendingQueue = false
                trackedPlaybackItem = mediaItem?.toStreamItem()
                trackedPlaybackUid = (sessionState as? UserSession.Authenticated)?.uid
                trackedPlaybackMs = 0L
                lastPlaybackSampleElapsedMs = null
                if (!restoringLastPlayed) {
                    mediaItem?.let(::rememberLastPlayed)
                }
                if (sleepAtEndOfTrackUrl != null && sleepAtEndOfTrackUrl != mediaItem?.mediaId) {
                    controller.pause()
                    sleepAtEndOfTrackUrl = null
                }
                refreshQueueState()
                maybeExtendQueue()
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                refreshQueueState()
            }
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED, Player.EVENT_AVAILABLE_COMMANDS_CHANGED)) {
                    val reportedDuration = player.duration
                    if (reportedDuration != C.TIME_UNSET && reportedDuration > 0) {
                        rawPlayerDurationMs = reportedDuration
                    }
                    canSkipNext = player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)
                    canSkipPrevious = player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)
                }
            }
        })
        controller.setPlaybackSpeed(playbackSpeed)
        syncControllerState(controller)
    }

    private fun syncControllerState(controller: MediaController) {
        if (controller.mediaItemCount == 0) {
            restoreLastPlayed(controller)
        }
        isPlaying = controller.isPlaying
        currentPositionMs = controller.currentPosition.coerceAtLeast(0L)
        controller.currentMediaItem?.let { mediaItem ->
            currentSongTitle = mediaItem.mediaMetadata.title?.toString()
                .orEmpty()
                .ifBlank { currentSongTitle }
            currentArtist = mediaItem.mediaMetadata.artist?.toString().orEmpty()
            currentArtworkUrl = mediaItem.mediaMetadata.artworkUri?.toString()
            currentTrackUrl = mediaItem.mediaId
            prefetchedDurationMs = mediaItem.mediaMetadata.extras?.getLong("harmix_duration_ms") ?: 0L
            rememberLastPlayed(mediaItem)
        }
        refreshQueueState()
    }

    private fun restoreLastPlayed(controller: MediaController) {
        val saved = lastPlayedStore.read() ?: return
        val resumePositionMs = saved.positionMs.coerceAtLeast(0L)

        restoringLastPlayed = true
        try {
            controller.playWhenReady = false
            controller.setMediaItem(saved.toMediaItem(), resumePositionMs)
            controller.prepare()
            controller.seekTo(resumePositionMs)
        } finally {
            restoringLastPlayed = false
        }

        currentSongTitle = saved.title
        currentArtist = saved.artist
        currentArtworkUrl = saved.artworkUrl
        currentTrackUrl = saved.url
        currentPositionMs = resumePositionMs
        prefetchedDurationMs = saved.durationMs
        rawPlayerDurationMs = 0L
        lastPlayedStore.save(saved.copy(positionMs = resumePositionMs))
    }

    private fun scheduleBufferingIndicator() {
        if (pendingBufferingJob?.isActive == true) return
        pendingBufferingJob = lifecycleScope.launch {
            delay(300)
            isBuffering = true
        }
    }

    private fun clearBufferingImmediately() {
        pendingBufferingJob?.cancel()
        pendingBufferingJob = null
        isBuffering = false
    }

    private fun startPositionTicker() {
        lifecycleScope.launch {
            while (isActive) {
                val controller = mediaController
                currentPositionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
                samplePlayback(controller)
                val now = System.currentTimeMillis()
                if (now - lastPositionPersistedAtMs >= POSITION_PERSIST_INTERVAL_MS) {
                    controller?.currentMediaItem?.let {
                        rememberLastPlayed(it)
                        lastPositionPersistedAtMs = now
                    }
                }
                val timerTrack = sleepAtEndOfTrackUrl
                val deadline = sleepTimerDeadlineMs
                if (deadline != null) {
                    sleepTimerRemainingMs = (deadline - System.currentTimeMillis()).coerceAtLeast(0L)
                } else if (timerTrack != null && currentTrackUrl == timerTrack && effectiveDurationMs > 0L) {
                    sleepTimerRemainingMs = (effectiveDurationMs - currentPositionMs).coerceAtLeast(0L)
                } else if (timerTrack == null) {
                    sleepTimerRemainingMs = 0L
                }
                if (timerTrack != null && currentTrackUrl != timerTrack) {
                    controller?.pause()
                    clearSleepTimerState()
                } else if (
                    timerTrack != null &&
                    currentTrackUrl == timerTrack &&
                    effectiveDurationMs > 0L &&
                    currentPositionMs >= effectiveDurationMs - 750L
                ) {
                    controller?.pause()
                    clearSleepTimerState()
                }
                delay(500)
            }
        }
    }

    private fun samplePlayback(controller: MediaController?) {
        val mediaItem = controller?.currentMediaItem ?: run {
            lastPlaybackSampleElapsedMs = null
            return
        }
        val uid = (sessionState as? UserSession.Authenticated)?.uid
        if (uid == null || !controller.isPlaying) {
            lastPlaybackSampleElapsedMs = null
            return
        }
        if (trackedPlaybackItem?.url != mediaItem.mediaId) {
            trackedPlaybackItem = mediaItem.toStreamItem()
            trackedPlaybackUid = uid
            trackedPlaybackMs = 0L
        }

        val now = SystemClock.elapsedRealtime()
        lastPlaybackSampleElapsedMs?.let { previous ->
            val delta = (now - previous).coerceIn(0L, 2_000L)
            trackedPlaybackMs += delta
            pendingListeningMs += delta
        }
        lastPlaybackSampleElapsedMs = now
        pendingListeningUid = uid
        if (pendingListeningMs >= LISTENING_FLUSH_INTERVAL_MS) {
            flushListeningSeconds()
        }
    }

    private fun finalizeTrackedPlayback(forceHistory: Boolean) {
        val item = trackedPlaybackItem ?: return
        flushListeningSeconds()
        val uid = trackedPlaybackUid
        if (uid != null && (forceHistory || trackedPlaybackMs > HISTORY_THRESHOLD_MS)) {
            lifecycleScope.launch {
                runCatching {
                    listeningHistoryRepository.recordPlayedTrack(uid, item)
                }.onFailure { error ->
                    Log.e("Harmix", "Failed to record listening history", error)
                }
            }
        }
        trackedPlaybackItem = null
        trackedPlaybackUid = null
        trackedPlaybackMs = 0L
        lastPlaybackSampleElapsedMs = null
    }

    private fun flushListeningSeconds() {
        val uid = pendingListeningUid ?: return
        val seconds = pendingListeningMs / 1_000L
        if (seconds <= 0L) return
        pendingListeningMs -= seconds * 1_000L
        lifecycleScope.launch {
            runCatching {
                listeningHistoryRepository.addListeningSeconds(uid, seconds)
            }.onFailure { error ->
                Log.e("Harmix", "Failed to update listening time", error)
            }
        }
    }

    private fun refreshQueueState() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        queueItems = (0 until controller.mediaItemCount).map { index ->
            val item = controller.getMediaItemAt(index)
            QueueItemUi(
                index = index,
                title = item.mediaMetadata.title?.toString() ?: "Unknown title",
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString(),
                isCurrent = index == currentIndex
            )
        }
    }

    private fun maybeExtendQueue() {
        val controller = mediaController ?: return
        if (isExtendingQueue) return
        if ((controller.mediaItemCount - 1 - controller.currentMediaItemIndex) > 2) return
        val authenticated = sessionState as? UserSession.Authenticated
        isExtendingQueue = true
        lifecycleScope.launch {
            var addedCount = 0
            try {
                val wantsPersonalized = authenticated != null &&
                    autoRadioItemsAdded >= AUTO_RADIO_TRACK_LIMIT &&
                    personalizedQueueFailures < PERSONALIZED_QUEUE_MAX_ATTEMPTS
                if (wantsPersonalized && authenticated != null) {
                    addedCount = runCatching {
                        withTimeout(QUEUE_FETCH_TIMEOUT_MS) {
                            val artists = personalizedArtistSeeds(authenticated.uid)
                            if (artists.isEmpty()) {
                                Log.w("Harmix", "Personalized queue: no artist seeds available")
                                0
                            } else {
                                val related = metadataRepository.getForYou(artists, limit = 10)
                                Log.d("Harmix", "Personalized queue fetched ${related.size} tracks")
                                appendToQueue(controller, related)
                            }
                        }
                    }.getOrElse { error ->
                        Log.e("Harmix", "Personalized queue fetch failed, falling back to radio", error)
                        0
                    }
                    if (addedCount > 0) {
                        // Successful personalized batch: allow another one next time the
                        // queue runs low instead of locking the feature off forever.
                        personalizedQueueLoaded = true
                        personalizedQueueFailures = 0
                    } else {
                        personalizedQueueFailures += 1
                        Log.w(
                            "Harmix",
                            "Personalized queue added nothing (attempt $personalizedQueueFailures); using radio fallback"
                        )
                    }
                }

                if (addedCount == 0) {
                    // Fallback / default path: keep the radio going so there is ALWAYS a next track.
                    val videoId = extractVideoId(currentTrackUrl.orEmpty())
                    if (videoId == null) {
                        Log.w("Harmix", "Cannot extend queue: no video id for $currentTrackUrl")
                    } else {
                        addedCount = runCatching {
                            withTimeout(QUEUE_FETCH_TIMEOUT_MS) {
                                val related = metadataRepository.getUpNext(videoId, limit = 10)
                                Log.d("Harmix", "Radio queue fetched ${related.size} tracks")
                                appendToQueue(controller, related)
                            }
                        }.getOrElse { error ->
                            Log.e("Harmix", "Radio queue fetch failed", error)
                            0
                        }
                        if (authenticated != null) {
                            autoRadioItemsAdded += addedCount
                        }
                    }
                }

                if (addedCount == 0) {
                    Log.w("Harmix", "Queue extension produced no new tracks; next may be unavailable")
                }
                refreshQueueState()
            } catch (e: Exception) {
                Log.e("Harmix", "Queue extension error", e)
            } finally {
                isExtendingQueue = false
            }
        }
    }

    /** Appends items that are not already queued and returns how many were added. */
    private fun appendToQueue(controller: MediaController, items: List<StreamItem>): Int {
        if (items.isEmpty()) return 0
        val existingUrls = (0 until controller.mediaItemCount)
            .map { controller.getMediaItemAt(it).mediaId }
            .toMutableSet()
        var added = 0
        items.forEach { item ->
            if (existingUrls.add(item.url)) {
                controller.addMediaItem(item.toMediaItem())
                added += 1
            }
        }
        return added
    }

    private suspend fun personalizedArtistSeeds(uid: String): List<String> {
        val likedArtists = libraryRepository.getLikedSongs().first().map { it.uploader }
        val historyArtists = listeningHistoryRepository.getRecentHistory(uid).first()
            .map { it.song.uploader }
        return (likedArtists + historyArtists)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it.lowercase() }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.value.first() }
    }

    private fun toggleLikeForCurrentTrack() {
        val item = currentStreamItemOrNull() ?: return
        toggleLikeForItem(item)
    }

    private fun toggleLikeForItem(item: StreamItem) {
        lifecycleScope.launch {
            val liked = libraryRepository.toggleLike(item)
            if (item.url == currentTrackUrl) {
                isLiked = liked
                refreshWidgetLibraryState(item.url)
            }
        }
    }

    private fun toggleShuffle() {
        val controller = mediaController ?: return
        isShuffleOn = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = isShuffleOn
    }

    private fun cycleRepeat() {
        val controller = mediaController ?: return
        repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = repeatMode
    }

    private fun extractVideoId(url: String): String? = runCatching { Uri.parse(url).getQueryParameter("v") }.getOrNull()
    private fun currentStreamItemOrNull(): StreamItem? = currentTrackUrl?.let { StreamItem(title = currentSongTitle, url = it, thumbnailUrl = currentArtworkUrl, uploader = currentArtist) }
    private fun fetchLyricsForCurrentTrack() {
        lyricsResult = null
        lifecycleScope.launch { lyricsResult = metadataRepository.getLyrics(currentSongTitle, currentArtist, (effectiveDurationMs / 1000L).toInt()) }
    }

    private fun applyPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 2f)
        mediaController?.setPlaybackSpeed(playbackSpeed)
    }

    private fun setSleepTimer(durationMs: Long?, endOfCurrentTrack: Boolean) {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepAtEndOfTrackUrl = null
        sleepTimerDeadlineMs = null
        sleepTimerRemainingMs = 0L

        if (endOfCurrentTrack) {
            sleepAtEndOfTrackUrl = currentTrackUrl
            if (currentTrackUrl != null && effectiveDurationMs > 0L) {
                sleepTimerRemainingMs = (effectiveDurationMs - currentPositionMs).coerceAtLeast(0L)
            }
        } else if (durationMs != null && durationMs > 0L) {
            sleepTimerDeadlineMs = System.currentTimeMillis() + durationMs
            sleepTimerRemainingMs = durationMs
            sleepTimerJob = lifecycleScope.launch {
                delay(durationMs)
                mediaController?.pause()
                sleepTimerJob = null
                sleepTimerDeadlineMs = null
                sleepTimerRemainingMs = 0L
            }
        }
    }

    private fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        clearSleepTimerState()
    }

    private fun clearSleepTimerState() {
        sleepTimerJob = null
        sleepAtEndOfTrackUrl = null
        sleepTimerDeadlineMs = null
        sleepTimerRemainingMs = 0L
    }

    private fun rememberLastPlayed(mediaItem: MediaItem) {
        val url = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: return
        lastPlayedStore.save(
            com.boom.harmix.playback.LastPlayedTrack(
                title = mediaItem.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown title" },
                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                artworkUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
                url = url,
                positionMs = mediaController?.currentPosition?.coerceAtLeast(0L) ?: currentPositionMs,
                durationMs = effectiveDurationMs
            )
        )
    }
    private fun addTargetToPlaylist(playlistId: Long) {
        val item = playlistDialogTarget ?: return
        lifecycleScope.launch {
            libraryRepository.addSongToPlaylist(playlistId, item)
            playlistDialogTarget = null
            if (item.url == currentTrackUrl) refreshWidgetLibraryState(item.url)
        }
    }
    private fun createPlaylistAndAddTarget(name: String) {
        if (name.isBlank()) return
        val item = playlistDialogTarget ?: return
        lifecycleScope.launch {
            val newPlaylistId = libraryRepository.createPlaylist(name)
            libraryRepository.addSongToPlaylist(newPlaylistId, item)
            playlistDialogTarget = null
            if (item.url == currentTrackUrl) refreshWidgetLibraryState(item.url)
        }
    }

    private suspend fun refreshWidgetLibraryState(url: String) {
        NowPlayingWidgetState.refreshLibraryState(this, url)
        NowPlayingWidget.updateAll(this)
    }

    private fun playQueue(items: List<StreamItem>, startIndex: Int) {
        val controller = mediaController ?: return
        if (items.isEmpty()) return
        autoRadioItemsAdded = 0
        personalizedQueueLoaded = false
        personalizedQueueFailures = 0
        val mediaItems = items.map { it.toMediaItem() }
        val safeIndex = startIndex.coerceIn(0, mediaItems.lastIndex)
        isBuffering = true
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()
        val startItem = items[safeIndex]
        currentSongTitle = startItem.title
        currentArtist = startItem.uploader
        currentArtworkUrl = startItem.thumbnailUrl
        currentTrackUrl = startItem.url
        isLiked = likedUrls.contains(startItem.url)
        prefetchedDurationMs = (startItem.durationSeconds ?: 0) * 1000L
        rawPlayerDurationMs = 0L
        lastPlayedStore.save(
            com.boom.harmix.playback.LastPlayedTrack(
                title = startItem.title,
                artist = startItem.uploader,
                artworkUrl = startItem.thumbnailUrl,
                url = startItem.url,
                positionMs = 0L,
                durationMs = (startItem.durationSeconds ?: 0).toLong() * 1000L
            )
        )
    }

    private fun playNext(item: StreamItem) { mediaController?.let { isBuffering = true
        it.addMediaItem((it.currentMediaItemIndex + 1).coerceAtMost(it.mediaItemCount), item.toMediaItem()); refreshQueueState() } }
    private fun addToQueue(item: StreamItem) { mediaController?.let { it.addMediaItem(item.toMediaItem()); refreshQueueState() } }
    private fun removeQueueItem(index: Int) { mediaController?.let { it.removeMediaItem(index); refreshQueueState() } }
    private fun togglePlayPause() { mediaController?.let { if (it.isPlaying) it.pause() else it.play() } }

    override fun onStop() {
        samplePlayback(mediaController)
        flushListeningSeconds()
        mediaController?.currentMediaItem?.let(::rememberLastPlayed)
        super.onStop()
    }

    override fun onDestroy() {
        cancelSleepTimer()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_OPEN_PLAYLIST_PICKER = "com.boom.harmix.OPEN_PLAYLIST_PICKER"
    }
}

private fun com.boom.harmix.playback.LastPlayedTrack.toMediaItem(): MediaItem {
    val extras = android.os.Bundle().apply {
        if (durationMs > 0L) putLong("harmix_duration_ms", durationMs)
    }
    return MediaItem.Builder()
        .setMediaId(url)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(Uri.parse(url))
                .build()
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setExtras(extras)
                .apply { artworkUrl?.let { setArtworkUri(Uri.parse(it)) } }
                .build()
        )
        .build()
}

private fun MediaItem.toStreamItem(): StreamItem = StreamItem(
    title = mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown title" },
    url = mediaId,
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    uploader = mediaMetadata.artist?.toString().orEmpty(),
    durationSeconds = mediaMetadata.extras
        ?.getLong("harmix_duration_ms", 0L)
        ?.takeIf { it > 0L }
        ?.div(1_000L)
        ?.toInt()
)

private fun StreamItem.toMediaItem(): MediaItem {
    val extras = android.os.Bundle().apply { durationSeconds?.let { putLong("harmix_duration_ms", it.toLong() * 1000L) } }
    return MediaItem.Builder().setMediaId(url)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(Uri.parse(url)).build())
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(uploader).setExtras(extras).apply { thumbnailUrl?.let { setArtworkUri(Uri.parse(it)) } }.build())
        .build()
}

private const val POSITION_PERSIST_INTERVAL_MS = 5_000L
private const val LISTENING_FLUSH_INTERVAL_MS = 5_000L
private const val HISTORY_THRESHOLD_MS = 20_000L
private const val AUTO_RADIO_TRACK_LIMIT = 10
private const val PERSONALIZED_QUEUE_MAX_ATTEMPTS = 3
private const val QUEUE_FETCH_TIMEOUT_MS = 12_000L
