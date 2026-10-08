package com.boom.harmix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.Sand
import com.boom.harmix.ui.theme.SunsetGold

/** Slim strip shown at the top of the app whenever there is no internet. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(EmberRed.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CloudOff, contentDescription = null, tint = SunsetGold, modifier = Modifier.size(18.dp))
        Text(
            text = "You're offline",
            color = Bone,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp).weight(1f)
        )
        TextButton(onClick = onRetry) {
            Text(text = "Retry", color = SunsetGold, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Friendly full-panel error with a retry button. Used anywhere a fetch failed,
 * so nothing ever crashes just because the internet dropped.
 */
@Composable
fun ErrorRetryPanel(
    message: String,
    modifier: Modifier = Modifier,
    isOffline: Boolean = false,
    onRetry: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(GlassFill, RoundedCornerShape(20.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.Refresh,
            contentDescription = null,
            tint = SunsetGold,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = if (isOffline) "No internet connection" else "Something went wrong",
            color = Bone,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(text = message, color = Sand, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) {
            Text(text = "Try again", color = SunsetGold, fontWeight = FontWeight.Bold)
        }
    }
}