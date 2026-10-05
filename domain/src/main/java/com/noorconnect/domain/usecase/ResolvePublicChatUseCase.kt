package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class ResolvePublicChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(username: String): AppResult<Chat> =
        chatRepository.resolvePublicChat(username)
}