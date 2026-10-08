package com.boom.harmix.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.harmix.auth.AccountSlot
import com.boom.harmix.auth.GoogleAccountInfo
import com.boom.harmix.auth.GoogleAccountsRepository
import com.boom.harmix.core.NetworkMonitor
import com.boom.harmix.core.OfflineException
import com.boom.harmix.auth.UserSession
import com.boom.harmix.auth.UserSessionRepository
import com.boom.harmix.data.cloud.ListeningHistoryRepository
import com.boom.harmix.sync.YtMusicSyncRepository
import com.boom.harmix.sync.YtConsentRequiredException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Done(val playlists: Int, val songs: Int) : SyncState()
    data class Failed(val message: String, val offline: Boolean) : SyncState()
    /** Google asked for extra consent — the screen must launch this intent. */
    data class NeedsConsent(val intent: Intent) : SyncState()
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accounts: GoogleAccountsRepository,
    private val ytSync: YtMusicSyncRepository,
    private val userSessionRepository: UserSessionRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val mainAccount: StateFlow<GoogleAccountInfo?> = accounts.mainAccount
    val ytAccount: StateFlow<GoogleAccountInfo?> = accounts.ytAccount
    val authError: StateFlow<String?> = accounts.authError

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _totalListeningSeconds = MutableStateFlow(0L)
    val totalListeningSeconds: StateFlow<Long> = _totalListeningSeconds.asStateFlow()

    init {
        viewModelScope.launch {
            userSessionRepository.session.collectLatest { session ->
                val authenticated = session as? UserSession.Authenticated
                if (authenticated == null) {
                    _totalListeningSeconds.value = 0L
                } else {
                    listeningHistoryRepository.getTotalListeningSeconds(authenticated.uid)
                        .collect { _totalListeningSeconds.value = it }
                }
            }
        }
    }

    fun signInIntent(slot: AccountSlot): Intent = accounts.signInIntent(slot)

    fun onSignInResult(slot: AccountSlot, data: Intent?) {
        viewModelScope.launch {
            val info = accounts.handleSignInResult(slot, data)
            if (info != null && slot == AccountSlot.YT_SYNC) {
                // Playlists sync the moment the YouTube account connects.
                syncNow()
            }
        }
    }

    fun signOut(slot: AccountSlot) {
        accounts.signOut(slot)
        if (slot == AccountSlot.YT_SYNC) _syncState.value = SyncState.Idle
    }

    fun syncNow() {
        if (_syncState.value is SyncState.Syncing) return
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            _syncState.value = try {
                val summary = withTimeout(20_000) { ytSync.syncPlaylists() }
                SyncState.Done(summary.playlists, summary.songs)
            } catch (e: YtConsentRequiredException) {
                SyncState.NeedsConsent(e.intent)
            } catch (e: TimeoutCancellationException) {
                android.util.Log.e("HarmixSync", "YouTube sync timed out", e)
                SyncState.Failed("Timed out after 20s. Check your connection and try again.", offline = false)
            } catch (e: OfflineException) {
                SyncState.Failed(e.message ?: "You're offline.", offline = true)
            } catch (e: Exception) {
                android.util.Log.e("HarmixSync", "YouTube sync failed", e)
                SyncState.Failed(e.message ?: "Sync failed (${e::class.java.simpleName}).", offline = false)
            }
        }
    }

    /** Called after the user finishes the Google consent screen. */
    fun onConsentResult(granted: Boolean) {
        if (granted) syncNow()
        else _syncState.value = SyncState.Failed("Permission denied for YouTube access.", offline = false)
    }
}