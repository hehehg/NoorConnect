package com.noorconnect.domain.model

data class OnboardingState(
    val hasCompletedOnboarding: Boolean = false,
    val gender: Gender? = null,
)
