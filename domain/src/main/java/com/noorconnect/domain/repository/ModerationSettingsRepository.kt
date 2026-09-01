package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ModerationSettings
import kotlinx.coroutines.flow.Flow

interface ModerationSettingsRepository {
    fun observeSettings(): Flow<ModerationSettings>
    suspend fun updateSettings(settings: ModerationSettings): AppResult<Unit>
}
