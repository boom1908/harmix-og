package com.boom.harmix.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boom.harmix.auth.UserSessionRepository
import com.boom.harmix.auth.UsernameSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UsernameUiState(
    val saving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class UsernameViewModel @Inject constructor(
    private val userSession: UserSessionRepository
) : ViewModel() {
    val session: StateFlow<com.boom.harmix.auth.UserSession> = userSession.session

    private val _uiState = MutableStateFlow(UsernameUiState())
    val uiState: StateFlow<UsernameUiState> = _uiState.asStateFlow()

    fun saveUsername(rawName: String) {
        val name = rawName
        if (!USERNAME_REGEX.matches(name)) {
            _uiState.value = UsernameUiState(
                error = "Use only lowercase letters, digits, dots, and underscores."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = UsernameUiState(saving = true)
            _uiState.value = when (val result = userSession.saveUsername(name)) {
                is UsernameSaveResult.Success -> UsernameUiState(
                    successMessage = "Username saved."
                )
                UsernameSaveResult.UsernameTaken -> UsernameUiState(
                    error = "Username already taken."
                )
                is UsernameSaveResult.Failure -> UsernameUiState(error = result.message)
            }
        }
    }

    companion object {
        val USERNAME_REGEX = Regex("^[a-z0-9._]+$")
    }
}