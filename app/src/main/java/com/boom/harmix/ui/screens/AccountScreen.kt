package com.boom.harmix.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.boom.harmix.auth.AccountSlot
import com.boom.harmix.auth.GoogleAccountInfo
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.CoolGray
import com.boom.harmix.ui.theme.DeepMidnight
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.MistWhite
import com.boom.harmix.ui.theme.SunsetGold
import com.boom.harmix.ui.theme.ZenCyan
import com.boom.harmix.ui.viewmodel.AccountViewModel
import com.boom.harmix.ui.viewmodel.SyncState

@Composable
fun AccountScreen(
    onOpenSettings: () -> Unit,
    onOpenListeningHistory: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val mainAccount by viewModel.mainAccount.collectAsState()
    val ytAccount by viewModel.ytAccount.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val totalListeningSeconds by viewModel.totalListeningSeconds.collectAsState()

    val mainLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onSignInResult(AccountSlot.MAIN, result.data)
        }
    }

    val ytLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onSignInResult(AccountSlot.YT_SYNC, result.data)
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    // Google asked for extra permission: show its screen instead of spinning forever.
    androidx.compose.runtime.LaunchedEffect(syncState) {
        val state = syncState
        if (state is SyncState.NeedsConsent) consentLauncher.launch(state.intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Account",
                color = MistWhite,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (mainAccount != null) {
                androidx.compose.material3.IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = SunsetGold)
                }
            }
        }

        authError?.let {
            Text(text = it, color = EmberRed, style = MaterialTheme.typography.bodySmall)
        }

        HarmixAccountCard(
            account = mainAccount,
            onSignIn = { mainLauncher.launch(viewModel.signInIntent(AccountSlot.MAIN)) },
            onSignOut = { viewModel.signOut(AccountSlot.MAIN) }
        )

        if (mainAccount != null) {
            ListeningStatsCard(
                totalListeningSeconds = totalListeningSeconds,
                onOpenHistory = onOpenListeningHistory
            )
            YouTubeSyncCard(
                account = ytAccount,
                syncState = syncState,
                onConnect = { ytLauncher.launch(viewModel.signInIntent(AccountSlot.YT_SYNC)) },
                onDisconnect = { viewModel.signOut(AccountSlot.YT_SYNC) },
                onResync = { viewModel.syncNow() }
            )
        }

        Text(
            text = if (mainAccount != null) {
                "Your Harmix account and your YouTube Music account are kept separate. " +
                    "Swap the YouTube one any time to pull in a different set of playlists."
            } else {
                "Sign up to unlock playlists, liked songs, and personalized recommendations."
            },
            color = CoolGray,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ListeningStatsCard(
    totalListeningSeconds: Long,
    onOpenHistory: () -> Unit
) {
    SectionCard {
        AccountHeader(
            title = "Your listening",
            subtitle = "${formatListeningTime(totalListeningSeconds)} listened",
            icon = Icons.Filled.LibraryMusic
        )
        OutlinedButton(
            onClick = onOpenHistory,
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ZenCyan),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(text = "Listening history")
        }
    }
}

private fun formatListeningTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val days = seconds / 86_400L
    val hours = (seconds % 86_400L) / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    return when {
        days > 0L -> "${days}d ${hours}h"
        hours > 0L -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

@Composable
private fun SectionCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassFill, RoundedCornerShape(24.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
private fun AccountHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(GlassFill, CircleShape)
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = SunsetGold, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column {
            Text(text = title, color = Bone, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = CoolGray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HarmixAccountCard(
    account: GoogleAccountInfo?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    SectionCard {
        AccountHeader(
            title = if (account == null) "Harmix account" else account.displayName,
            subtitle = account?.email
                ?: "Sign up to unlock playlists, liked songs, and personalized recommendations",
            icon = Icons.Filled.AccountCircle
        )

        if (account == null) {
            Button(
                onClick = onSignIn,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = DeepMidnight),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = "Sign in with Google", fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(
                onClick = onSignOut,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MistWhite),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = "Sign out")
            }
        }
    }
}

@Composable
private fun YouTubeSyncCard(
    account: GoogleAccountInfo?,
    syncState: SyncState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onResync: () -> Unit
) {
    SectionCard {
        AccountHeader(
            title = "YouTube Music sync",
            subtitle = account?.email ?: "Connect any Google account to import playlists",
            icon = Icons.Filled.LibraryMusic
        )

        when (syncState) {
            is SyncState.Syncing -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = SunsetGold, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(10.dp))
                Text(text = "Syncing your playlists...", color = CoolGray, style = MaterialTheme.typography.bodySmall)
            }

            is SyncState.Done -> Text(
                text = "Imported ${syncState.playlists} playlists and ${syncState.songs} songs.",
                color = SunsetGold,
                style = MaterialTheme.typography.bodySmall
            )

            is SyncState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (syncState.offline) "You're offline. Reconnect and try again." else syncState.message,
                    color = EmberRed,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start
                )
                OutlinedButton(
                    onClick = onResync,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SunsetGold)
                ) { Text(text = "Retry sync") }
            }

            is SyncState.NeedsConsent -> Text(
                text = "Waiting for your Google permission…",
                color = SunsetGold,
                style = MaterialTheme.typography.bodySmall
            )

            SyncState.Idle -> Unit
        }

        if (account == null) {
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ZenCyan, contentColor = DeepMidnight),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text(text = "Connect YouTube account", fontWeight = FontWeight.Bold)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onResync,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SunsetGold, contentColor = DeepMidnight),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text(text = "Sync now", fontWeight = FontWeight.Bold) }

                OutlinedButton(
                    onClick = onDisconnect,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MistWhite),
                    modifier = Modifier.weight(1f).height(50.dp)
                ) { Text(text = "Disconnect") }
            }
            Button(
                onClick = onConnect,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassFill, contentColor = SunsetGold),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) { Text(text = "Switch to another YouTube account") }
        }
    }
}
