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
