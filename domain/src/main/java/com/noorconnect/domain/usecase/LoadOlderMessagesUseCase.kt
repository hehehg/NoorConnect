package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class LoadOlderMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long, fromMessageId: Long, limit: Int): AppResult<List<Message>> =
        chatRepository.loadOlderMessages(chatId, fromMessageId, limit)
}