package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Gender
import com.noorconnect.domain.model.OnboardingState
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    fun observeOnboardingState(): Flow<OnboardingState>
    suspend fun completeOnboarding(gender: Gender): AppResult<Unit>
}
