package com.boom.harmix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.harmix.data.local.LibraryRepository
import com.boom.harmix.data.local.PlaylistUi
import com.boom.harmix.extractor.StreamItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val savedSongs: StateFlow<List<StreamItem>> = libraryRepository.getSavedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val likedSongs: StateFlow<List<StreamItem>> = libraryRepository.getLikedSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistUi>> = libraryRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { libraryRepository.createPlaylist(name) }
    }

    fun removeSong(item: StreamItem) {
        viewModelScope.launch { libraryRepository.removeSong(item) }
    }

    fun removeSongFromPlaylist(playlistId: Long, songUrl: String) {
        viewModelScope.launch { libraryRepository.removeSongFromPlaylist(playlistId, songUrl) }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch { libraryRepository.renamePlaylist(playlistId, name) }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch { libraryRepository.deletePlaylist(playlistId) }
    }
}
