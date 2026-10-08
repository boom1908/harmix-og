package com.boom.harmix.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.harmix.data.local.LibraryRepository
import com.boom.harmix.data.local.PlaylistUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: -1L

    val playlist: StateFlow<PlaylistUi?> = libraryRepository.getPlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun rename(name: String) {
        viewModelScope.launch { libraryRepository.renamePlaylist(playlistId, name) }
    }

    fun removeSong(songUrl: String) {
        viewModelScope.launch { libraryRepository.removeSongFromPlaylist(playlistId, songUrl) }
    }

    fun deletePlaylist(onDone: () -> Unit) {
        viewModelScope.launch {
            libraryRepository.deletePlaylist(playlistId)
            onDone()
        }
    }
}
