package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class ResolvePublicChannelUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(username: String): AppResult<Long> =
        chatRepository.resolvePublicChannelId(username)
}