package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChatSendPermission
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class GetChatSendPermissionUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long): AppResult<ChatSendPermission> =
        chatRepository.getChatSendPermission(chatId)
}