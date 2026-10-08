package com.boom.harmix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.harmix.auth.UserSession
import com.boom.harmix.auth.UserSessionRepository
import com.boom.harmix.data.cloud.ListeningHistoryEntry
import com.boom.harmix.data.cloud.ListeningHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListeningHistoryViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val listeningHistoryRepository: ListeningHistoryRepository
) : ViewModel() {
    private val _history = MutableStateFlow<List<ListeningHistoryEntry>>(emptyList())
    val history: StateFlow<List<ListeningHistoryEntry>> = _history.asStateFlow()

    init {
        viewModelScope.launch {
            userSessionRepository.session.collectLatest { session ->
                val authenticated = session as? UserSession.Authenticated
                if (authenticated == null) {
                    _history.value = emptyList()
                } else {
                    listeningHistoryRepository.getRecentHistory(authenticated.uid)
                        .collect { _history.value = it }
                }
            }
        }
    }
}