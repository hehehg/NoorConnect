package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long, text: String, scheduleDate: Int? = null): AppResult<Unit> =
        chatRepository.sendMessage(chatId, text, scheduleDate)

    suspend fun media(
        chatId: Long,
        path: String,
        mimeType: String,
        caption: String,
        scheduleDate: Int? = null,
    ): AppResult<Unit> = chatRepository.sendMedia(chatId, path, mimeType, caption, scheduleDate)

    suspend fun edit(chatId: Long, messageId: Long, text: String): AppResult<Unit> =
        chatRepository.editMessage(chatId, messageId, text)

    suspend fun delete(chatId: Long, messageId: Long): AppResult<Unit> =
        chatRepository.deleteMessage(chatId, messageId)

    suspend fun scheduled(chatId: Long): AppResult<List<com.noorconnect.domain.model.Message>> =
        chatRepository.getScheduledMessages(chatId)

    suspend fun sendScheduledNow(chatId: Long, messageId: Long): AppResult<Unit> =
        chatRepository.sendScheduledNow(chatId, messageId)
}
