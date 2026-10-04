package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

class ResolveChannelInviteUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(inviteLink: String): AppResult<Long> =
        chatRepository.resolveChannelInviteId(inviteLink)
}