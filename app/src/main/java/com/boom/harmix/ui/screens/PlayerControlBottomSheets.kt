package com.boom.harmix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.MidnightBlack
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerBottomSheet(
    onDismiss: () -> Unit,
    onApply: (durationMs: Long?, endOfCurrentTrack: Boolean) -> Unit,
    isTimerActive: Boolean = false,
    remainingMs: Long = 0L,
    onCancel: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var durationMinutes by remember { mutableFloatStateOf(30f) }
    var endOfCurrentTrack by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightBlack,
        contentColor = Bone
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.62f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Sleep timer", color = Bone, fontWeight = FontWeight.Black)
            Text(
                text = if (isTimerActive) {
                    if (remainingMs > 0L) "Time remaining: ${formatTimerMillis(remainingMs)}"
                    else "Ends after the current track"
                } else if (endOfCurrentTrack) {
                    "Ends after the current track"
                } else {
                    "${durationMinutes.roundToInt()} minutes"
                },
                color = SunsetGold
            )

            Slider(
                value = durationMinutes,
                onValueChange = { durationMinutes = it },
                valueRange = 5f..120f,
                steps = 22,
                enabled = !endOfCurrentTrack,
                colors = SliderDefaults.colors(
                    thumbColor = SunsetGold,
                    activeTrackColor = SunsetGold,
                    inactiveTrackColor = Sand.copy(alpha = 0.25f),
                    disabledThumbColor = Sand.copy(alpha = 0.5f),
                    disabledActiveTrackColor = Sand.copy(alpha = 0.35f)
                )
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("End of current track", color = Bone)
                    Text("Pause playback when this song finishes", color = Sand)
                }
                Switch(
                    checked = endOfCurrentTrack,
                    onCheckedChange = { endOfCurrentTrack = it }
                )
            }

            Button(
                onClick = {
                    onApply(
                        if (endOfCurrentTrack) null else durationMinutes.toLong() * 60_000L,
                        endOfCurrentTrack
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SunsetGold,
                    contentColor = MidnightBlack
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply")
            }

            if (isTimerActive) {
                Button(
                    onClick = {
                        onCancel()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Sand.copy(alpha = 0.18f),
                        contentColor = Bone
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Turn off sleep timer")
                }
            }
        }
    }
}

private fun formatTimerMillis(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    return "%02d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSpeedBottomSheet(
    currentSpeed: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var speed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MidnightBlack,
        contentColor = Bone
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.42f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Playback speed", color = Bone, fontWeight = FontWeight.Black)
            Text(text = "${"%.2f".format(speed)}×", color = SunsetGold)
            Slider(
                value = speed,
                onValueChange = { speed = it },
                valueRange = 0.5f..2f,
                steps = 5,
                colors = SliderDefaults.colors(
                    thumbColor = SunsetGold,
                    activeTrackColor = SunsetGold,
                    inactiveTrackColor = Sand.copy(alpha = 0.25f)
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0.5×", color = Sand)
                Text("2×", color = Sand)
            }
            Button(
                onClick = { onApply(speed) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SunsetGold,
                    contentColor = MidnightBlack
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply")
            }
        }
    }
}