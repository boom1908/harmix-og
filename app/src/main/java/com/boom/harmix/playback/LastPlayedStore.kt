package com.boom.harmix.playback

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class LastPlayedTrack(
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val url: String,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * Remembers whatever was playing last so a cold start can paint the mini player
 * immediately instead of flashing "Nothing playing" while the session reconnects.
 */
@Singleton
class LastPlayedStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("harmix_last_played", Context.MODE_PRIVATE)

    fun save(track: LastPlayedTrack) {
        prefs.edit()
            .putString("title", track.title)
            .putString("artist", track.artist)
            .putString("artwork", track.artworkUrl)
            .putString("url", track.url)
            .putLong("positionMs", track.positionMs.coerceAtLeast(0L))
            .putLong("durationMs", track.durationMs.coerceAtLeast(0L))
            .apply()
    }

    fun read(): LastPlayedTrack? {
        val title = prefs.getString("title", null) ?: return null
        val url = prefs.getString("url", null) ?: return null
        return LastPlayedTrack(
            title = title,
            artist = prefs.getString("artist", "").orEmpty(),
            artworkUrl = prefs.getString("artwork", null),
            url = url,
            positionMs = prefs.getLong("positionMs", 0L).coerceAtLeast(0L),
            durationMs = prefs.getLong("durationMs", 0L).coerceAtLeast(0L)
        )
    }
}
