package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.AuthState
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeAuthState(): Flow<AuthState>
    suspend fun sendPhoneNumber(phone: String): AppResult<Unit>
    suspend fun sendCode(code: String): AppResult<Unit>
    suspend fun sendPassword(password: String): AppResult<Unit>
    suspend fun logOut(): AppResult<Unit>
}
