package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ScheduledChatMessage
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GetAllScheduledMessagesUseCase @Inject constructor(
    private val getChats: GetChatsUseCase,
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(): List<ScheduledChatMessage> {
        val approvedChats = getChats().first().filter { it.isContentVisible }
        return buildList {
            approvedChats.forEach { chat ->
                val messages = (chatRepository.getScheduledMessages(chat.id) as? AppResult.Success)?.data
                    .orEmpty()
                messages.forEach { message ->
                    add(ScheduledChatMessage(chatId = chat.id, chatTitle = chat.title, message = message))
                }
            }
        }.sortedBy { it.message.scheduledAt ?: it.message.timestamp }
    }
}