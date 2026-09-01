package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ReportReason
import com.noorconnect.domain.repository.ChatModerationRepository
import javax.inject.Inject

/**
 * Backing use case for the "الإبلاغ عن المحادثة" button feature:moderation shows on every chat
 * (channel, group, AND individual one-to-one chats — reporting isn't restricted to channels).
 * Reporting never decides the chat's fate itself: it always calls the same
 * [ChatModerationRepository.flagForReview] that pulls a chat's status back to
 * [com.noorconnect.domain.model.ChatModerationStatus.PendingReview] — the exact same queue
 * [CheckChatAccessUseCase] and [ScanMessagesForBannedWordsUseCase] already feed. Only the admin
 * panel (separate project, see README) moves a pending chat to whitelist or blacklist; this
 * use case's whole job is getting a chat IN FRONT of that queue with a clear reason attached.
 */
class ReportChatUseCase @Inject constructor(
    private val moderationRepository: ChatModerationRepository,
) {
    suspend operator fun invoke(chatId: Long, reason: ReportReason, details: String?): AppResult<Unit> {
        val cleanedDetails = details?.trim().orEmpty()
        val fullReason = if (cleanedDetails.isEmpty()) {
            "بلاغ من مستخدم: ${reason.label}"
        } else {
            "بلاغ من مستخدم: ${reason.label} — $cleanedDetails"
        }
        return moderationRepository.flagForReview(chatId, fullReason)
    }
}
