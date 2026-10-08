package com.boom.harmix.metadata

import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.core.NetworkMonitor
import com.chaquo.python.PyException
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

data class LyricLine(val timestampMs: Long, val text: String)

sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>) : LyricsResult()
    data class PlainOnly(val text: String) : LyricsResult()
    data object NotFound : LyricsResult()
}

@Singleton
class MetadataRepository @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {

    suspend fun getUpNext(videoId: String, limit: Int = 10): List<StreamItem> =
        callPython("get_up_next", videoId, limit)

    suspend fun search(query: String, limit: Int = 20): List<StreamItem> =
        callPython("search_songs", query, limit)

    suspend fun getTrending(limit: Int = 15): List<StreamItem> =
        callPython("get_trending", limit)

    suspend fun getForYou(artists: List<String>, limit: Int = 12): List<StreamItem> =
        callPython("get_for_you", JSONArray(artists).toString(), limit)

    suspend fun getLyrics(title: String, artist: String, durationSeconds: Int = 0): LyricsResult =
        withContext(Dispatchers.IO) {
            if (!networkMonitor.isOnline()) return@withContext LyricsResult.NotFound
            val python = Python.getInstance()
            val metadataModule = python.getModule("metadata_engine")

            try {
                val jsonResult = metadataModule.callAttr("get_lyrics", title, artist, durationSeconds).toString()
                val json = org.json.JSONObject(jsonResult)

                val syncedRaw = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
                val plainRaw = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" }

                when {
                    syncedRaw != null -> LyricsResult.Synced(parseLrc(syncedRaw))
                    plainRaw != null -> LyricsResult.PlainOnly(plainRaw)
                    else -> LyricsResult.NotFound
                }
            } catch (e: PyException) {
                LyricsResult.NotFound
            }
        }

    private suspend fun callPython(functionName: String, vararg args: Any): List<StreamItem> =
        withContext(Dispatchers.IO) {
            networkMonitor.requireOnline()
            val python = Python.getInstance()
            val metadataModule = python.getModule("metadata_engine")

            try {
                val jsonResult = metadataModule.callAttr(functionName, *args).toString()
                parseResults(jsonResult)
            } catch (e: PyException) {
                throw RuntimeException("ytmusicapi $functionName failed: ${e.message}", e)
            }
        }

    private fun parseResults(jsonArrayString: String): List<StreamItem> {
        val array = org.json.JSONArray(jsonArrayString)
        val items = mutableListOf<StreamItem>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONObject(i) ?: continue
            val videoId = entry.optString("videoId")
            if (videoId.isBlank()) continue
            val durationSeconds = if (entry.isNull("durationSeconds")) null else entry.optInt("durationSeconds")
            items.add(
                StreamItem(
                    title = entry.optString("title", "Unknown title"),
                    url = "https://www.youtube.com/watch?v=$videoId",
                    thumbnailUrl = entry.optString("thumbnailUrl").ifBlank { null },
                    uploader = entry.optString("artist", ""),
                    durationSeconds = durationSeconds
                )
            )
        }
        return items
    }

    private fun parseLrc(raw: String): List<LyricLine> {
        val lrcLinePattern = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})]\s*(.*)""")
        val lines = mutableListOf<LyricLine>()

        raw.lines().forEach { line ->
            val match = lrcLinePattern.find(line) ?: return@forEach
            val (minutes, seconds, fraction, text) = match.destructured

            val fractionMs = if (fraction.length == 2) fraction.toLong() * 10 else fraction.toLong()
            val timestampMs = (minutes.toLong() * 60_000L) + (seconds.toLong() * 1000L) + fractionMs

            lines.add(LyricLine(timestampMs, text.trim()))
        }
        return lines.sortedBy { it.timestampMs }
    }
}
