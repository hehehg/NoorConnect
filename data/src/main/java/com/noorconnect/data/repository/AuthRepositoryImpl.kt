package com.noorconnect.data.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.AuthState
import com.noorconnect.core.tdlib.TdLibManager
import com.noorconnect.core.tdlib.TdLibAccountStore
import com.noorconnect.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val tdLib: TdLibManager,
    private val accountStore: TdLibAccountStore,
) : AuthRepository {
    override fun observeAuthState(): Flow<AuthState> = tdLib.authState

    override suspend fun sendPhoneNumber(phone: String): AppResult<Unit> = when (val result = tdLib.setPhoneNumber(phone)) {
        is AppResult.Success -> {
            accountStore.updatePhoneNumber(tdLib.activeAccountId.value, phone)
            AppResult.Success(Unit)
        }
        is AppResult.Failure -> result
        is AppResult.Loading -> AppResult.Loading
    }

    override suspend fun sendCode(code: String): AppResult<Unit> =
        tdLib.checkCode(code)

    override suspend fun sendPassword(password: String): AppResult<Unit> =
        tdLib.checkPassword(password)

    override suspend fun logOut(): AppResult<Unit> =
        when (val r = tdLib.send(org.drinkless.tdlib.TdApi.LogOut())) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> r
            is AppResult.Loading -> AppResult.Loading
        }
}
