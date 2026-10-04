package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class UpdateChannelInfoUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(
        chatId: Long,
        title: String,
        description: String,
        username: String?,
        photoPath: String?,
    ): AppResult<Unit> = chatRepository.updateChannelInfo(
        chatId = chatId,
        title = title,
        description = description,
        username = username,
        photoPath = photoPath,
    )
}