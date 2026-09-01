package com.noorconnect.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.domain.model.Gender

/**
 * Public entry point for :app — same pattern as every other feature's Route composable.
 * Shown only once: if UserPreferencesRepository already has hasCompletedOnboarding = true
 * (checked here, same "auto-advance if already satisfied" pattern as AuthRoute), this skips
 * straight to onFinished() without ever drawing the question — no separate "have I done this
 * before" check needed anywhere else in the app.
 */
@Composable
fun OnboardingRoute(onFinished: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        null -> Unit // preferences not read from disk yet — draw nothing for a frame rather than flash the question
        else -> if (current.hasCompletedOnboarding) {
            LaunchedEffect(Unit) { onFinished() }
        } else {
            OnboardingScreen(onGenderSelected = { gender ->
                viewModel.selectGender(gender)
                onFinished()
            })
        }
    }
}

@Composable
private fun OnboardingScreen(onGenderSelected: (Gender) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "أهلًا بك في نور كونكت",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "قبل ما نبدأ، حابين نتعرف عليك أكتر",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
        )
        Text("أنت:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

        Button(
            onClick = { onGenderSelected(Gender.MALE) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) { Text("ذكر") }

        Button(
            onClick = { onGenderSelected(Gender.FEMALE) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) { Text("أنثى") }

        OutlinedButton(
            onClick = { onGenderSelected(Gender.PREFER_NOT_TO_SAY) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("أفضل عدم الإجابة") }
    }
}
