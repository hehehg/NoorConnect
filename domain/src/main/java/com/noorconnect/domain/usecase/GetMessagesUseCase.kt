package com.noorconnect.domain.usecase

import com.noorconnect.domain.model.Message
import com.noorconnect.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    operator fun invoke(chatId: Long): Flow<List<Message>> = chatRepository.observeMessages(chatId)
}
