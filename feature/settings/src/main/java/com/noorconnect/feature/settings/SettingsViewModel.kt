package com.noorconnect.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.AccountSession
import com.noorconnect.domain.model.AuthState
import com.noorconnect.domain.model.ModerationSettings
import com.noorconnect.domain.repository.AccountSessionRepository
import com.noorconnect.domain.repository.AuthRepository
import com.noorconnect.domain.repository.ModerationSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val moderationSettingsRepository: ModerationSettingsRepository,
    private val accountSessionRepository: AccountSessionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val settings: StateFlow<ModerationSettings> = moderationSettingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModerationSettings())

    val accounts: StateFlow<List<AccountSession>> = accountSessionRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _accountBusy = MutableStateFlow(false)
    val accountBusy: StateFlow<Boolean> = _accountBusy

    private val _accountError = MutableStateFlow<String?>(null)
    val accountError: StateFlow<String?> = _accountError

    fun addAccount(onAuthenticationRequired: () -> Unit) {
        runAccountAction {
            when (val result = accountSessionRepository.addAccountAndSwitch()) {
                is AppResult.Success -> onAuthenticationRequired()
                is AppResult.Failure -> _accountError.value = result.message
                is AppResult.Loading -> Unit
            }
        }
    }

    fun switchAccount(
        accountId: String,
        onAuthenticationRequired: () -> Unit,
        onSwitched: () -> Unit,
    ) {
        runAccountAction {
            when (val result = accountSessionRepository.switchTo(accountId)) {
                is AppResult.Success -> {
                    val authState = authRepository.observeAuthState().first { it != AuthState.Uninitialized }
                    if (authState == AuthState.Ready) onSwitched() else onAuthenticationRequired()
                }
                is AppResult.Failure -> _accountError.value = result.message
                is AppResult.Loading -> Unit
            }
        }
    }

    fun removeAccount(accountId: String) {
        runAccountAction {
            when (val result = accountSessionRepository.remove(accountId)) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> _accountError.value = result.message
                is AppResult.Loading -> Unit
            }
        }
    }

    fun setAllowUnverifiedChannels(allow: Boolean) = update { it.copy(allowUnverifiedChannels = allow) }
    fun setAllowGroups(allow: Boolean) = update { it.copy(allowGroups = allow) }
    fun setNotificationsEnabled(value: Boolean) = update { it.copy(notificationsEnabled = value) }
    fun setNotificationPreview(value: Boolean) = update { it.copy(notificationPreview = value) }
    fun setNotificationSound(value: Boolean) = update { it.copy(notificationSound = value) }
    fun setNotificationVibration(value: Boolean) = update { it.copy(notificationVibration = value) }
    fun setInAppSounds(value: Boolean) = update { it.copy(inAppSounds = value) }
    fun setAutoDownloadPhotos(value: Boolean) = update { it.copy(autoDownloadPhotos = value) }
    fun setAutoDownloadVideos(value: Boolean) = update { it.copy(autoDownloadVideos = value) }
    fun setAutoDownloadFiles(value: Boolean) = update { it.copy(autoDownloadFiles = value) }
    fun setSaveToGallery(value: Boolean) = update { it.copy(saveToGallery = value) }
    fun setAutoplayVideos(value: Boolean) = update { it.copy(autoplayVideos = value) }
    fun setAutoplayGifs(value: Boolean) = update { it.copy(autoplayGifs = value) }
    fun setSendByEnter(value: Boolean) = update { it.copy(sendByEnter = value) }
    fun setReduceDataUsage(value: Boolean) = update { it.copy(reduceDataUsage = value) }

    fun addBlockedKeyword(keyword: String) {
        if (keyword.isBlank()) return
        update { it.copy(blockedKeywords = it.blockedKeywords + keyword.trim()) }
    }

    fun removeBlockedKeyword(keyword: String) = update { it.copy(blockedKeywords = it.blockedKeywords - keyword) }

    private fun update(transform: (ModerationSettings) -> ModerationSettings) {
        viewModelScope.launch {
            moderationSettingsRepository.updateSettings(transform(settings.value))
        }
    }

    private fun runAccountAction(action: suspend () -> Unit) {
        if (_accountBusy.value) return
        viewModelScope.launch {
            _accountBusy.value = true
            _accountError.value = null
            try {
                action()
            } catch (exception: Exception) {
                _accountError.value = exception.message ?: "تعذر تنفيذ العملية"
            } finally {
                _accountBusy.value = false
            }
        }
    }
}
