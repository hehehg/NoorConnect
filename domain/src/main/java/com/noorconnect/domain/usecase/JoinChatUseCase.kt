package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class JoinChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long): AppResult<Long> = chatRepository.joinChat(chatId)

    suspend fun byInviteLink(inviteLink: String): AppResult<Long> =
        chatRepository.joinChatByInviteLink(inviteLink)
}