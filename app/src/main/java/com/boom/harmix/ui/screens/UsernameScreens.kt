package com.boom.harmix.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.boom.harmix.auth.UserSession
import com.boom.harmix.ui.components.GhostPillButton
import com.boom.harmix.ui.components.HarmixLogo
import com.boom.harmix.ui.components.PageHeader
import com.boom.harmix.ui.theme.Bone
import com.boom.harmix.ui.theme.CoolGray
import com.boom.harmix.ui.theme.DeepMidnight
import com.boom.harmix.ui.theme.EmberRed
import com.boom.harmix.ui.theme.GlassBorder
import com.boom.harmix.ui.theme.GlassFill
import com.boom.harmix.ui.theme.MistWhite
import com.boom.harmix.ui.theme.SunsetGold
import com.boom.harmix.ui.viewmodel.UsernameViewModel

@Composable
fun UsernameSetupScreen(
    viewModel: UsernameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        HarmixLogo(size = 72)
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Choose your username",
            color = SunsetGold,
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "This is required to finish setting up your Harmix account.",
            color = CoolGray,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        UsernameForm(
            initialUsername = "",
            submitLabel = "Continue",
            uiState = uiState,
            onSubmit = viewModel::saveUsername
        )
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: UsernameViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentUsername = (session as? UserSession.Authenticated)?.username.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = "Settings",
            subtitle = "Manage your Harmix account",
            showLogo = false,
            trailing = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MistWhite)
                }
            }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Profile",
                color = Bone,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            UsernameForm(
                initialUsername = currentUsername,
                submitLabel = "Save username",
                uiState = uiState,
                onSubmit = viewModel::saveUsername
            )
        }
    }
}

@Composable
private fun UsernameForm(
    initialUsername: String,
    submitLabel: String,
    uiState: com.boom.harmix.ui.viewmodel.UsernameUiState,
    onSubmit: (String) -> Unit
) {
    var username by remember(initialUsername) { mutableStateOf(initialUsername) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassFill, RoundedCornerShape(24.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Change username",
            color = Bone,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Lowercase letters, digits, dots, and underscores only.",
            color = CoolGray,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Username") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SunsetGold,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = MistWhite,
                unfocusedTextColor = MistWhite,
                focusedLabelColor = SunsetGold,
                unfocusedLabelColor = CoolGray,
                cursorColor = SunsetGold
            )
        )
        uiState.error?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = EmberRed)
                Text(
                    text = it,
                    color = EmberRed,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
        uiState.successMessage?.let {
            Text(text = it, color = SunsetGold)
        }
        Button(
            onClick = { onSubmit(username) },
            enabled = username.isNotBlank() && !uiState.saving,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SunsetGold,
                contentColor = DeepMidnight
            ),
            shape = RoundedCornerShape(18.dp)
        ) {
            if (uiState.saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = DeepMidnight,
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = submitLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SessionLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = SunsetGold)
    }
}

@Composable
fun SessionErrorScreen(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Couldn't load your account", color = Bone, fontWeight = FontWeight.Bold)
        Text(
            message,
            color = CoolGray,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        GhostPillButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
fun GuestLockedScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Settings, contentDescription = null, tint = SunsetGold, modifier = Modifier.size(42.dp))
        Text("Sign in to unlock your library", color = Bone, fontWeight = FontWeight.Bold)
        Text(
            "Playlists and liked songs are available after you create a Harmix account.",
            color = CoolGray,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}