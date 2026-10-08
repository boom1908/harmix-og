package com.boom.harmix.playback

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.glance.appwidget.updateAll
import com.boom.harmix.extractor.YtDlpRepository
import com.boom.harmix.widget.NowPlayingWidget
import com.boom.harmix.widget.NowPlayingWidgetState
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/**
 * Playback is resolved lazily. Media items keep a `harmix://` placeholder URI and the
 * real audio stream is extracted by [ResolvingDataSource] the moment ExoPlayer starts
 * loading that item — so queueing a 200 song playlist is instant instead of running
 * yt-dlp 200 times up front. The next track is pre-resolved in the background.
 */
@AndroidEntryPoint
class HarmixPlaybackService : MediaLibraryService() {

    @Inject
    lateinit var ytDlpRepository: YtDlpRepository

    private lateinit var player: ExoPlayer
    private var librarySession: MediaLibrarySession? = null
    private var widgetUpdateJob: Job? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent(USER_AGENT)

        val resolvingFactory = ResolvingDataSource.Factory(
            DefaultDataSource.Factory(this, httpFactory),
            ResolvingDataSource.Resolver { dataSpec -> resolve(dataSpec) }
        )

        // Start playing as soon as a couple of seconds are buffered instead of
        // waiting for the default 2.5s/5s thresholds on a cold network.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 750,
                /* bufferForPlaybackAfterRebufferMs = */ 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                requestWidgetUpdate()
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                requestWidgetUpdate()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                prefetchUpcoming()
                requestWidgetUpdate()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                requestWidgetUpdate()
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Playback error", error)
                // A single unplayable track shouldn't kill the whole queue.
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                }
            }
        })

        librarySession = MediaLibrarySession.Builder(this, player, HarmixLibrarySessionCallback())
            .build()
        requestWidgetUpdate()
    }

    private fun requestWidgetUpdate() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = serviceScope.launch {
            NowPlayingWidgetState.persist(this@HarmixPlaybackService, player)
            NowPlayingWidget.updateAll(this@HarmixPlaybackService)
        }
    }

    private fun resolve(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != HARMIX_SCHEME) return dataSpec
        val source = Uri.decode(uri.schemeSpecificPart.removePrefix("//"))
        return try {
            val result = ytDlpRepository.getAudioStreamUrlBlocking(source)
            dataSpec.withUri(Uri.parse(result.url))
        } catch (e: Exception) {
            throw IOException(e.message ?: "Could not resolve audio stream", e)
        }
    }

    private fun prefetchUpcoming() {
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == androidx.media3.common.C.INDEX_UNSET) return
        val nextId = runCatching { player.getMediaItemAt(nextIndex).mediaId }.getOrNull() ?: return
        if (nextId.isBlank()) return
        serviceScope.launch { ytDlpRepository.prefetch(nextId) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val session = librarySession ?: return
        if (!session.player.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        widgetUpdateJob?.cancel()
        librarySession?.run {
            player.release()
            release()
            librarySession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class HarmixLibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            serviceScope.future {
                mediaItems.map { item -> item.withPlaceholderUri() }.toMutableList()
            }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("harmix_root")
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle("Harmix")
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> =
            Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
    }

    private companion object {
        const val TAG = "HarmixPlayback"
        const val HARMIX_SCHEME = "harmix"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

        fun MediaItem.withPlaceholderUri(): MediaItem {
            val source = requestMetadata.mediaUri?.toString() ?: mediaId
            return buildUpon()
                .setUri(Uri.parse("$HARMIX_SCHEME://${Uri.encode(source)}"))
                .build()
        }
    }
}
