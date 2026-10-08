package com.boom.harmix.extractor

import android.util.Log
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class AudioStreamResult(
    val url: String,
    val durationSeconds: Int?
)

@Singleton
class YtDlpRepository @Inject constructor() {

    private data class CacheEntry(val result: AudioStreamResult, val expiresAtMillis: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /** Blocking resolve — safe to call from ExoPlayer's loader thread. */
    fun getAudioStreamUrlBlocking(videoIdOrUrl: String): AudioStreamResult {
        cache[videoIdOrUrl]?.let { entry ->
            if (entry.expiresAtMillis > System.currentTimeMillis()) return entry.result
            cache.remove(videoIdOrUrl)
        }

        val started = System.currentTimeMillis()
        val module = Python.getInstance().getModule("harmix_engine")
        val raw = try {
            module.callAttr("get_audio_url", videoIdOrUrl).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $videoIdOrUrl", e)
            throw RuntimeException("Couldn't load this song: ${e.message}", e)
        }

        val result = if (raw.startsWith("http")) {
            AudioStreamResult(url = raw, durationSeconds = null)
        } else {
            val json = JSONObject(raw)
            val url = json.optString("url")
            if (url.isBlank()) throw RuntimeException("Couldn't load this song (empty stream URL).")
            AudioStreamResult(
                url = url,
                durationSeconds = if (json.isNull("durationSeconds")) null else json.optInt("durationSeconds")
            )
        }

        cache[videoIdOrUrl] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        Log.d(TAG, "Resolved $videoIdOrUrl in ${System.currentTimeMillis() - started}ms")
        return result
    }

    suspend fun getAudioStreamUrl(videoIdOrUrl: String): AudioStreamResult =
        withContext(Dispatchers.IO) { getAudioStreamUrlBlocking(videoIdOrUrl) }

    /** Warms the cache for an upcoming track so the next skip starts instantly. */
    suspend fun prefetch(videoIdOrUrl: String) = withContext(Dispatchers.IO) {
        val cached = cache[videoIdOrUrl]
        if (cached != null && cached.expiresAtMillis > System.currentTimeMillis()) return@withContext
        runCatching { getAudioStreamUrlBlocking(videoIdOrUrl) }
            .onFailure { Log.w(TAG, "Prefetch failed for $videoIdOrUrl: ${it.message}") }
        Unit
    }

    private companion object {
        const val TAG = "HarmixExtractor"
        const val CACHE_TTL_MS = 4L * 60 * 60 * 1000
    }
}
