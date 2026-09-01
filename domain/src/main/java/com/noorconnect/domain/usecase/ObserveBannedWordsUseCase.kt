package com.noorconnect.domain.usecase

import com.noorconnect.domain.repository.ChatModerationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBannedWordsUseCase @Inject constructor(
    private val moderationRepository: ChatModerationRepository,
) {
    operator fun invoke(): Flow<List<String>> = moderationRepository.observeBannedWords()
}
