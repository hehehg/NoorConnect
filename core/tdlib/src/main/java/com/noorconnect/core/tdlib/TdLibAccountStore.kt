package com.noorconnect.core.tdlib

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class StoredTdLibAccount(
    val id: String,
    val phoneNumber: String?,
)

@Singleton
class TdLibAccountStore @Inject constructor(
    @ApplicationContext context: Context,
    private val config: TdLibConfig,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _accounts = MutableStateFlow(readAccounts())
    val accounts: StateFlow<List<StoredTdLibAccount>> = _accounts

    private val _activeAccountId = MutableStateFlow(readActiveAccountId())
    val activeAccountId: StateFlow<String> = _activeAccountId

    @Synchronized
    fun addAccount(): StoredTdLibAccount? {
        if (_accounts.value.size >= MAX_ACCOUNTS) return null
        val account = StoredTdLibAccount(UUID.randomUUID().toString(), null)
        val updated = _accounts.value + account
        preferences.edit(commit = true) { putStringSet(KEY_ACCOUNT_IDS, updated.map { it.id }.toSet()) }
        _accounts.value = updated
        return account
    }

    @Synchronized
    fun activate(accountId: String): Boolean {
        if (_accounts.value.none { it.id == accountId }) return false
        preferences.edit(commit = true) { putString(KEY_ACTIVE_ACCOUNT_ID, accountId) }
        _activeAccountId.value = accountId
        return true
    }

    @Synchronized
    fun updatePhoneNumber(accountId: String, phoneNumber: String) {
        if (_accounts.value.none { it.id == accountId }) return
        preferences.edit(commit = true) { putString(phoneKey(accountId), phoneNumber) }
        _accounts.value = _accounts.value.map {
            if (it.id == accountId) it.copy(phoneNumber = phoneNumber) else it
        }
    }

    @Synchronized
    fun remove(accountId: String): Boolean {
        if (_accounts.value.size <= 1 || _accounts.value.none { it.id == accountId }) return false
        val updated = _accounts.value.filterNot { it.id == accountId }
        val activeId = if (_activeAccountId.value == accountId) updated.first().id else _activeAccountId.value
        preferences.edit(commit = true) {
            putStringSet(KEY_ACCOUNT_IDS, updated.map { it.id }.toSet())
            putString(KEY_ACTIVE_ACCOUNT_ID, activeId)
            remove(phoneKey(accountId))
        }
        _accounts.value = updated
        _activeAccountId.value = activeId
        sessionDirectory(accountId).deleteRecursively()
        sessionFilesDirectory(accountId).deleteRecursively()
        return true
    }

    fun sessionDirectory(accountId: String): File =
        if (accountId == PRIMARY_ACCOUNT_ID) {
            File(config.databaseDirectory)
        } else {
            File(config.databaseDirectory, "accounts/$accountId")
        }

    fun sessionFilesDirectory(accountId: String): File =
        if (accountId == PRIMARY_ACCOUNT_ID) {
            File(config.filesDirectory)
        } else {
            File(config.filesDirectory, "accounts/$accountId")
        }

    private fun readAccounts(): List<StoredTdLibAccount> {
        val ids = preferences.getStringSet(KEY_ACCOUNT_IDS, null).orEmpty().toList().ifEmpty {
            preferences.edit(commit = true) { putStringSet(KEY_ACCOUNT_IDS, setOf(PRIMARY_ACCOUNT_ID)) }
            listOf(PRIMARY_ACCOUNT_ID)
        }
        return ids.take(MAX_ACCOUNTS).map { id ->
            StoredTdLibAccount(id, preferences.getString(phoneKey(id), null))
        }
    }

    private fun readActiveAccountId(): String {
        val stored = preferences.getString(KEY_ACTIVE_ACCOUNT_ID, null)
        return stored?.takeIf { id -> _accounts.value.any { it.id == id } }
            ?: _accounts.value.first().id.also { id ->
                preferences.edit(commit = true) { putString(KEY_ACTIVE_ACCOUNT_ID, id) }
            }
    }

    private fun phoneKey(accountId: String) = "$KEY_PHONE_PREFIX$accountId"

    private companion object {
        const val PREFERENCES_NAME = "telegram_accounts"
        const val KEY_ACCOUNT_IDS = "account_ids"
        const val KEY_ACTIVE_ACCOUNT_ID = "active_account_id"
        const val KEY_PHONE_PREFIX = "phone_"
        const val MAX_ACCOUNTS = 3
        const val PRIMARY_ACCOUNT_ID = "primary"
    }
}