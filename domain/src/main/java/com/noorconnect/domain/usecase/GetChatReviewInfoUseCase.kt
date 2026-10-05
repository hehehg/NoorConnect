package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChatReviewInfo
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class GetChatReviewInfoUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long): AppResult<ChatReviewInfo> =
        chatRepository.getChatReviewInfo(chatId)
}