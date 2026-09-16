package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class GetMessageLinkUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long, messageId: Long): AppResult<String> =
        chatRepository.getMessageLink(chatId, messageId)
}