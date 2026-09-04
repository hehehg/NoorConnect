package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class ManageChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend fun setPinned(chatId: Long, isPinned: Boolean): AppResult<Unit> =
        chatRepository.setChatPinned(chatId, isPinned)

    suspend fun setArchived(chatId: Long, isArchived: Boolean): AppResult<Unit> =
        chatRepository.setChatArchived(chatId, isArchived)
}