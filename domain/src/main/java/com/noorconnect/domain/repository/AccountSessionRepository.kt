package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.AccountSession
import kotlinx.coroutines.flow.Flow

interface AccountSessionRepository {
    fun observeAccounts(): Flow<List<AccountSession>>
    suspend fun addAccountAndSwitch(): AppResult<AccountSession>
    suspend fun switchTo(accountId: String): AppResult<Unit>
    suspend fun remove(accountId: String): AppResult<Unit>
}