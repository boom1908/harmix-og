package com.boom.harmix.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.boom.harmix.metadata.LyricLine
import com.boom.harmix.metadata.LyricsResult
import com.boom.harmix.ui.components.KeepScreenOn
import com.boom.harmix.ui.theme.CoolGray
import com.boom.harmix.ui.theme.DeepMidnight
import com.boom.harmix.ui.theme.MistWhite
import com.boom.harmix.ui.theme.ZenCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsBottomSheet(
    lyricsResult: LyricsResult?,
    currentPositionMs: Long,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepMidnight,
        contentColor = MistWhite
    ) {
        // Keep the display awake for the entire time the lyrics sheet is open.
        KeepScreenOn()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.70f)
                .padding(horizontal = 20.dp)
        ) {
            when (lyricsResult) {
                null -> Text(text = "Loading lyrics...", color = CoolGray)
                LyricsResult.NotFound -> Text(text = "No lyrics found for this track.", color = CoolGray)
                is LyricsResult.PlainOnly -> {
                    LazyColumn {
                        item {
                            Text(
                                text = lyricsResult.text,
                                color = MistWhite,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
                is LyricsResult.Synced -> SyncedLyricsList(lyricsResult.lines, currentPositionMs)
            }
        }
    }
}

@Composable
private fun SyncedLyricsList(lines: List<LyricLine>, currentPositionMs: Long) {
    val listState = rememberLazyListState()
    // Keep blank timestamped entries available to buildLyricRows: LRCLIB uses
    // them as explicit instrumental-section markers.
    val lyricLines = remember(lines) { lines }
    val rows = remember(lyricLines) { buildLyricRows(lyricLines) }
    val activeIndex = lyricLines.indexOfLast {
        it.text.isNotBlank() && it.timestampMs <= currentPositionMs
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val rowIndex = rows.indexOfFirst { it is LyricRow.Line && it.index == activeIndex }
            if (rowIndex >= 0) listState.animateScrollToItem((rowIndex - 2).coerceAtLeast(0))
        }
    }

    if (rows.isEmpty()) {
        Text("No synced lyrics found for this track.", color = CoolGray)
        return
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        items(rows, key = { row ->
            when (row) {
                is LyricRow.Line -> "line-${row.index}"
                is LyricRow.Gap -> "gap-${row.startMs}-${row.endMs}"
            }
        }) { row ->
            when (row) {
                is LyricRow.Line -> {
                    val isActive = row.index == activeIndex
                    Text(
                        text = row.line.text,
                        color = if (isActive) ZenCyan else CoolGray,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        style = if (isActive) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is LyricRow.Gap -> InstrumentalGap(row, currentPositionMs)
            }
        }
    }
}

private sealed interface LyricRow {
    data class Line(val index: Int, val line: LyricLine) : LyricRow
    data class Gap(val startMs: Long, val endMs: Long) : LyricRow
}

private fun buildLyricRows(lines: List<LyricLine>): List<LyricRow> {
    if (lines.isEmpty()) return emptyList()
    val rows = mutableListOf<LyricRow>()
    var instrumentalMarkerMs: Long? = null

    lines.forEachIndexed { index, line ->
        if (line.text.isBlank()) {
            if (instrumentalMarkerMs == null) {
                instrumentalMarkerMs = line.timestampMs
            }
            return@forEachIndexed
        }

        instrumentalMarkerMs?.let { gapStartMs ->
            val gapDurationMs = line.timestampMs - gapStartMs
            if (gapDurationMs > GAP_THRESHOLD_MS) {
                rows += LyricRow.Gap(gapStartMs, line.timestampMs)
            }
        }
        rows += LyricRow.Line(index, line)
        instrumentalMarkerMs = null
    }
    return rows
}

@Composable
private fun InstrumentalGap(gap: LyricRow.Gap, currentPositionMs: Long) {
    val progress = ((currentPositionMs - gap.startMs).toFloat() / (gap.endMs - gap.startMs))
        .coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, label = "instrumental-gap-progress")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(modifier = Modifier.size(42.dp)) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = CoolGray.copy(alpha = 0.25f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            drawArc(
                color = ZenCyan,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = stroke
            )
        }
        val duration = formatGapTime(gap.endMs - gap.startMs)
        val elapsed = formatGapTime((currentPositionMs - gap.startMs).coerceIn(0L, gap.endMs - gap.startMs))
        Text(
            text = "Instrumental · $elapsed / $duration",
            color = CoolGray,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatGapTime(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

private const val GAP_THRESHOLD_MS = 4_000L