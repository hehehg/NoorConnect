package com.noorconnect.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Gender
import com.noorconnect.domain.model.OnboardingState
import com.noorconnect.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

private object UserPreferencesKeys {
    val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
    val GENDER = stringPreferencesKey("gender")
}

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : UserPreferencesRepository {

    override fun observeOnboardingState(): Flow<OnboardingState> =
        context.userPreferencesDataStore.data.map { prefs ->
            OnboardingState(
                hasCompletedOnboarding = prefs[UserPreferencesKeys.HAS_COMPLETED_ONBOARDING] ?: false,
                gender = prefs[UserPreferencesKeys.GENDER]?.let { runCatching { Gender.valueOf(it) }.getOrNull() },
            )
        }

    override suspend fun completeOnboarding(gender: Gender): AppResult<Unit> {
        context.userPreferencesDataStore.edit { prefs ->
            prefs[UserPreferencesKeys.HAS_COMPLETED_ONBOARDING] = true
            prefs[UserPreferencesKeys.GENDER] = gender.name
        }
        return AppResult.Success(Unit)
    }
}
