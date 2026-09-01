package com.noorconnect.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.domain.model.AuthState
import com.noorconnect.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.observeAuthState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Uninitialized)

    fun submitPhone(phone: String) = viewModelScope.launch {
        authRepository.sendPhoneNumber(phone)
    }

    fun submitCode(code: String) = viewModelScope.launch {
        authRepository.sendCode(code)
    }

    fun submitPassword(password: String) = viewModelScope.launch {
        authRepository.sendPassword(password)
    }
}
