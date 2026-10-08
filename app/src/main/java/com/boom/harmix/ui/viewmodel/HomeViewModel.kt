package com.boom.harmix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.harmix.auth.UserSession
import com.boom.harmix.auth.UserSessionRepository
import com.boom.harmix.data.cloud.ListeningHistoryRepository
import com.boom.harmix.data.local.LibraryRepository
import com.boom.harmix.extractor.StreamItem
import com.boom.harmix.metadata.MetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val items: List<StreamItem>,
        val forYou: List<StreamItem> = emptyList()
    ) : HomeUiState()
    data class Error(val message: String, val offline: Boolean = false) : HomeUiState()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val libraryRepository: LibraryRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loaded = false
    private var loadedForSignedInUser: Boolean? = null

    fun retry() {
        loaded = false
        loadedForSignedInUser = null
        loadRecommendations()
    }

    fun loadRecommendations() {
        val authenticated = userSessionRepository.session.value as? UserSession.Authenticated
        val signedIn = authenticated != null
        if (loaded && loadedForSignedInUser == signedIn) return
        loaded = true
        loadedForSignedInUser = signedIn

        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val trending = metadataRepository.getTrending()
                val forYou = if (authenticated != null) {
                    loadForYou(authenticated.uid)
                } else {
                    emptyList()
                }
                _uiState.value = if (trending.isEmpty()) {
                    HomeUiState.Error("YouTube Music returned no trending tracks")
                } else {
                    HomeUiState.Success(trending, forYou)
                }
            } catch (e: com.boom.harmix.core.OfflineException) {
                _uiState.value = HomeUiState.Error(e.message ?: "You're offline.", offline = true)
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Unknown error fetching trending tracks")
            }
        }
    }

    private suspend fun loadForYou(uid: String): List<StreamItem> {
        val liked = libraryRepository.getLikedSongs().first()
        val history = listeningHistoryRepository.getRecentHistory(uid).first()
        val artistNames = (liked.map { it.uploader } + history.map { it.song.uploader })
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it.lowercase() }
            .entries
            .sortedByDescending { it.value.size }
            .take(5)
            .map { it.value.first() }
        return if (artistNames.isEmpty()) {
            emptyList()
        } else {
            metadataRepository.getForYou(artistNames, limit = 12)
        }
    }
}
