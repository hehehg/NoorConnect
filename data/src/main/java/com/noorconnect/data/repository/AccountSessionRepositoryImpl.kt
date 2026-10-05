package com.noorconnect.data.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.core.tdlib.TdLibAccountStore
import com.noorconnect.core.tdlib.TdLibManager
import com.noorconnect.domain.model.AccountSession
import com.noorconnect.domain.repository.AccountSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class AccountSessionRepositoryImpl @Inject constructor(
    private val accountStore: TdLibAccountStore,
    private val tdLib: TdLibManager,
) : AccountSessionRepository {

    override fun observeAccounts(): Flow<List<AccountSession>> = combine(
        accountStore.accounts,
        accountStore.activeAccountId,
    ) { accounts, activeId ->
        accounts.map { account ->
            AccountSession(
                id = account.id,
                phoneNumber = account.phoneNumber,
                isActive = account.id == activeId,
            )
        }
    }

    override suspend fun addAccountAndSwitch(): AppResult<AccountSession> {
        val account = accountStore.addAccount()
            ?: return AppResult.Failure(400, "يمكنك إضافة 3 حسابات كحد أقصى")
        return when (val result = tdLib.switchAccount(account.id)) {
            is AppResult.Success -> AppResult.Success(
                AccountSession(account.id, account.phoneNumber, isActive = true),
            )
            is AppResult.Failure -> {
                accountStore.remove(account.id)
                result
            }
            is AppResult.Loading -> AppResult.Loading
        }
    }

    override suspend fun switchTo(accountId: String): AppResult<Unit> = tdLib.switchAccount(accountId)

    override suspend fun remove(accountId: String): AppResult<Unit> {
        val accounts = accountStore.accounts.value
        if (accounts.size <= 1) return AppResult.Failure(400, "يجب الاحتفاظ بحساب واحد على الأقل")
        if (accounts.none { it.id == accountId }) return AppResult.Failure(404, "الحساب غير موجود")

        if (accountStore.activeAccountId.value == accountId) {
            val replacement = accounts.first { it.id != accountId }
            when (val switched = tdLib.switchAccount(replacement.id)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> return switched
                is AppResult.Loading -> return AppResult.Loading
            }
        }
        return if (accountStore.remove(accountId)) AppResult.Success(Unit)
        else AppResult.Failure(-1, "تعذر إزالة الحساب")
    }
}