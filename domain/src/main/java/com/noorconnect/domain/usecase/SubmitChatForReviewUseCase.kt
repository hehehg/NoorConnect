package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatModerationRepository
import javax.inject.Inject

class SubmitChatForReviewUseCase @Inject constructor(
    private val moderationRepository: ChatModerationRepository,
) {
    suspend operator fun invoke(chatId: Long, reason: String): AppResult<Unit> =
        moderationRepository.flagForReview(chatId, reason)
}