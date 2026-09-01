package com.noorconnect.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.domain.model.Gender
import com.noorconnect.domain.model.OnboardingState
import com.noorconnect.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val state: StateFlow<OnboardingState?> = userPreferencesRepository.observeOnboardingState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null) // null = "not read yet"

    fun selectGender(gender: Gender) = viewModelScope.launch {
        userPreferencesRepository.completeOnboarding(gender)
    }
}
