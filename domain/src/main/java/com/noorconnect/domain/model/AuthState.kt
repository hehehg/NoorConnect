package com.noorconnect.domain.model

/** Our own auth-state abstraction. core:tdlib maps TdApi's AuthorizationState into this. */
sealed class AuthState {
    data object Uninitialized : AuthState()
    data object WaitingForPhoneNumber : AuthState()
    data object WaitingForCode : AuthState()
    data object WaitingForPassword : AuthState()
    data object Ready : AuthState()
    data object LoggedOut : AuthState()
    data class Error(val message: String) : AuthState()
}
