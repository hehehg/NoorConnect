package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.repository.ChatModerationRepository
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

/**
 * Backs the "انضمام" button on a search result row (channels/groups tabs). [SearchUseCase]
 * already flags a never-before-seen chat for review the moment it appears in a search result,
 * so by the time this runs the chat is very likely already [ChatModerationStatus.PendingReview]
 * — but this still checks and flags again as a safety net for any path that reaches a join
 * button without having gone through search first (matching the person's requirement that
 * joining itself, not just appearing in search, is what guarantees a review).
 */
class JoinChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val moderationRepository: ChatModerationRepository,
) {
    suspend operator fun invoke(chatId: Long): AppResult<Unit> {
        val joinResult = chatRepository.joinChat(chatId)
        if (joinResult !is AppResult.Success) return joinResult

        val record = moderationRepository.getChannelRecord(chatId)
        val status = (record as? AppResult.Success)?.data?.status
        if (status == null || status is ChatModerationStatus.Unreviewed) {
            moderationRepository.flagForReview(chatId, "تم الانضمام إليها من نتائج البحث")
        }
        return joinResult
    }
}
