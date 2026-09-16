package com.noorconnect.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.domain.model.ModerationSettings
import com.noorconnect.domain.repository.ModerationSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val moderationSettingsRepository: ModerationSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<ModerationSettings> = moderationSettingsRepository.observeSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModerationSettings())

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
}
