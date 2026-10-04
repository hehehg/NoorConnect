package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChannelInfo
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class GetChannelInfoUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(chatId: Long): AppResult<ChannelInfo> =
        chatRepository.getChannelInfo(chatId)
}