package com.boom.harmix.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.FontWeight
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.boom.harmix.MainActivity
import com.boom.harmix.R
import com.boom.harmix.data.local.LibraryRepository
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.playback.HarmixPlaybackService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class NowPlayingSnapshot(
    val hasTrack: Boolean,
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val artworkPath: String?,
    val artworkUrl: String?,
    val trackUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val isLiked: Boolean,
    val isInPlaylist: Boolean
)

object NowPlayingWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val snapshot = NowPlayingWidgetState.read(context)
            val artwork = snapshot.artworkPath
                ?.let { BitmapFactory.decodeFile(it) }
                ?: BitmapFactory.decodeResource(context.resources, R.drawable.harmix_logo)

            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ColorProvider(Color(0xFF0A1118)))
                    .cornerRadius(20.dp)
            ) {
                Image(
                    provider = ImageProvider(artwork),
                    contentDescription = "Current track artwork",
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                )
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(ColorProvider(Color(0x66000000)))
                        .cornerRadius(20.dp)
                        .clickable(actionStartActivity<MainActivity>())
                ) {}
                Column(
                    modifier = GlanceModifier.fillMaxSize().padding(14.dp)
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.harmix_logo),
                            contentDescription = "Harmix",
                            modifier = GlanceModifier.size(30.dp).cornerRadius(15.dp)
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Text(
                            text = "Harmix",
                            modifier = GlanceModifier
                                .background(ColorProvider(Color(0xE6F7F3EC)))
                                .cornerRadius(14.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF15110C)),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = if (snapshot.hasTrack) snapshot.title else "Nothing playing",
                        maxLines = 1,
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (snapshot.hasTrack) snapshot.artist else "Open Harmix to start listening",
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Color(0xFFE6E1D9)), fontSize = 14.sp)
                    )
                    Spacer(modifier = GlanceModifier.height(10.dp))
                    ProgressPill(snapshot)
                    Spacer(modifier = GlanceModifier.height(9.dp))
                    PlaybackControls(snapshot)
                }
            }
        }
    }
}

@Composable
private fun ProgressPill(snapshot: NowPlayingSnapshot) {
    val fraction = if (snapshot.durationMs > 0L) {
        (snapshot.positionMs.toFloat() / snapshot.durationMs).coerceIn(0f, 1f)
    } else 0f
    val fullWidth = 180
    val playedWidth = (fullWidth * fraction).toInt().coerceAtLeast(2)
    Row(modifier = GlanceModifier.width(fullWidth.dp).height(4.dp)) {
        Spacer(
            modifier = GlanceModifier
                .width(playedWidth.dp)
                .height(4.dp)
                .background(ColorProvider(Color.White))
                .cornerRadius(2.dp)
        )
        Spacer(
            modifier = GlanceModifier
                .defaultWeight()
                .height(4.dp)
                .background(ColorProvider(Color(0x66FFFFFF)))
                .cornerRadius(2.dp)
        )
    }
}

@Composable
private fun PlaybackControls(snapshot: NowPlayingSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹|",
            modifier = GlanceModifier.padding(8.dp).clickable(actionRunCallback<SkipPreviousAction>()),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = if (snapshot.isPlaying) "Ⅱ" else "▶",
            modifier = GlanceModifier
                .size(48.dp)
                .background(ColorProvider(Color.White))
                .cornerRadius(16.dp)
                .padding(13.dp)
                .clickable(actionRunCallback<TogglePlaybackAction>()),
            style = TextStyle(color = ColorProvider(Color(0xFF15110C)), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = "|›",
            modifier = GlanceModifier.padding(8.dp).clickable(actionRunCallback<SkipNextAction>()),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = if (snapshot.isLiked) "♥" else "♡",
            modifier = GlanceModifier.padding(8.dp).clickable(actionRunCallback<ToggleLikeAction>()),
            style = TextStyle(
                color = ColorProvider(if (snapshot.isLiked) Color(0xFFF5B942) else Color.White),
                fontSize = 24.sp
            )
        )
        Text(
            text = if (snapshot.isInPlaylist) "✓" else "+",
            modifier = GlanceModifier.padding(8.dp).clickable(
                actionStartActivity<MainActivity>(
                    actionParametersOf(OpenPlaylistPickerKey to true)
                )
            ),
            style = TextStyle(color = ColorProvider(Color.White), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        )
    }
}

val OpenPlaylistPickerKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_PLAYLIST_PICKER)

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget
}

object NowPlayingWidgetState {
    private const val PREFS_NAME = "now_playing_widget"
    private const val KEY_HAS_TRACK = "has_track"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_ARTWORK_URL = "artwork_url"
    private const val KEY_ARTWORK_PATH = "artwork_path"
    private const val KEY_TRACK_URL = "track_url"
    private const val KEY_POSITION_MS = "position_ms"
    private const val KEY_DURATION_MS = "duration_ms"
    private const val KEY_IS_LIKED = "is_liked"
    private const val KEY_IS_IN_PLAYLIST = "is_in_playlist"
    private const val ARTWORK_FILE = "now_playing_artwork.jpg"

    fun read(context: Context): NowPlayingSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return NowPlayingSnapshot(
            hasTrack = prefs.getBoolean(KEY_HAS_TRACK, false),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            artworkPath = prefs.getString(KEY_ARTWORK_PATH, null),
            artworkUrl = prefs.getString(KEY_ARTWORK_URL, null),
            trackUrl = prefs.getString(KEY_TRACK_URL, null),
            positionMs = prefs.getLong(KEY_POSITION_MS, 0L),
            durationMs = prefs.getLong(KEY_DURATION_MS, 0L),
            isLiked = prefs.getBoolean(KEY_IS_LIKED, false),
            isInPlaylist = prefs.getBoolean(KEY_IS_IN_PLAYLIST, false)
        )
    }

    suspend fun persist(context: Context, player: Player) {
        val item = player.currentMediaItem
        val trackUrl = item?.mediaId?.takeIf { it.isNotBlank() }
        val artworkUrl = item?.mediaMetadata?.artworkUri?.toString()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentArtworkUrl = prefs.getString(KEY_ARTWORK_URL, null)

        val artworkPath = withContext(Dispatchers.IO) {
            if (item == null) {
                File(context.filesDir, ARTWORK_FILE).delete()
                null
            } else if (artworkUrl != null && artworkUrl != currentArtworkUrl) {
                val destination = File(context.filesDir, ARTWORK_FILE)
                downloadArtwork(artworkUrl, destination)
                destination.takeIf { it.exists() }?.absolutePath
            } else {
                prefs.getString(KEY_ARTWORK_PATH, null)
            }
        }

        val libraryState = trackUrl?.let { url -> readLibraryState(context, url) }

        prefs.edit()
            .putBoolean(KEY_HAS_TRACK, item != null)
            .putBoolean(KEY_IS_PLAYING, player.isPlaying)
            .putString(KEY_TITLE, item?.mediaMetadata?.title?.toString().orEmpty())
            .putString(KEY_ARTIST, item?.mediaMetadata?.artist?.toString().orEmpty())
            .putString(KEY_ARTWORK_URL, artworkUrl)
            .putString(KEY_ARTWORK_PATH, artworkPath)
            .putString(KEY_TRACK_URL, trackUrl)
            .putLong(KEY_POSITION_MS, player.currentPosition.coerceAtLeast(0L))
            .putLong(KEY_DURATION_MS, player.duration.takeIf { it > 0L } ?: 0L)
            .putBoolean(KEY_IS_LIKED, libraryState?.first ?: prefs.getBoolean(KEY_IS_LIKED, false))
            .putBoolean(
                KEY_IS_IN_PLAYLIST,
                libraryState?.second ?: prefs.getBoolean(KEY_IS_IN_PLAYLIST, false)
            )
            .apply()
    }

    suspend fun refreshLibraryState(context: Context, url: String) {
        val state = readLibraryState(context, url) ?: return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_IS_LIKED, state.first)
            .putBoolean(KEY_IS_IN_PLAYLIST, state.second)
            .apply()
    }

    private suspend fun readLibraryState(context: Context, url: String): Pair<Boolean, Boolean>? =
        withTimeoutOrNull(2_000L) {
            val repository = widgetEntryPoint(context).libraryRepository()
            val liked = repository.getLikedUrls().first().contains(url)
            val inPlaylist = repository.getPlaylists().first().any { playlist ->
                playlist.songs.any { song -> song.url == url }
            }
            liked to inPlaylist
        }

    private fun downloadArtwork(url: String, destination: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
            doInput = true
        }
        try {
            connection.inputStream.use { input ->
                BitmapFactory.decodeStream(input)?.let { bitmap ->
                    destination.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output)
                    }
                    bitmap.recycle()
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetLibraryEntryPoint {
    fun libraryRepository(): LibraryRepository
}

private fun widgetEntryPoint(context: Context): WidgetLibraryEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetLibraryEntryPoint::class.java)

private suspend fun connectToPlayback(context: Context): MediaController {
    val token = SessionToken(
        context,
        ComponentName(context, HarmixPlaybackService::class.java)
    )
    return MediaController.Builder(context, token).buildAsync().await()
}

class TogglePlaybackAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val controller = connectToPlayback(context)
        try {
            if (controller.isPlaying) controller.pause() else controller.play()
            NowPlayingWidgetState.persist(context, controller)
            NowPlayingWidget.updateAll(context)
        } finally {
            controller.release()
        }
    }
}

class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val controller = connectToPlayback(context)
        try {
            if (controller.hasPreviousMediaItem()) controller.seekToPrevious()
            NowPlayingWidgetState.persist(context, controller)
            NowPlayingWidget.updateAll(context)
        } finally {
            controller.release()
        }
    }
}

class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val controller = connectToPlayback(context)
        try {
            if (controller.hasNextMediaItem()) controller.seekToNext()
            NowPlayingWidgetState.persist(context, controller)
            NowPlayingWidget.updateAll(context)
        } finally {
            controller.release()
        }
    }
}

class ToggleLikeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val snapshot = NowPlayingWidgetState.read(context)
        val url = snapshot.trackUrl ?: return
        runCatching {
            widgetEntryPoint(context).libraryRepository().toggleLike(
                StreamItem(
                    title = snapshot.title,
                    url = url,
                    thumbnailUrl = snapshot.artworkUrl,
                    uploader = snapshot.artist
                )
            )
            NowPlayingWidgetState.refreshLibraryState(context, url)
            NowPlayingWidget.updateAll(context)
        }.onFailure { error ->
            Log.e("HarmixWidget", "Failed to update liked state", error)
        }
    }
}