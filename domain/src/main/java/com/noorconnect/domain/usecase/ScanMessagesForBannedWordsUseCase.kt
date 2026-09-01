package com.noorconnect.domain.usecase

import com.noorconnect.domain.model.Message
import com.noorconnect.domain.moderation.BannedWordMatcher
import com.noorconnect.domain.repository.ChatModerationRepository
import javax.inject.Inject

/**
 * Called by ChatViewModel every time new messages arrive for an open chat. A match sends the
 * chat back to the review queue immediately — including pulling it OUT of the whitelist, since
 * flagForReview always sets status back to "pending" regardless of what it was before.
 */
class ScanMessagesForBannedWordsUseCase @Inject constructor(
    private val moderationRepository: ChatModerationRepository,
) {
    suspend operator fun invoke(chatId: Long, messages: List<Message>, bannedWords: List<String>): String? {
        if (bannedWords.isEmpty()) return null
        val match = messages.firstNotNullOfOrNull { message ->
            BannedWordMatcher.findFirstMatch(message.text, bannedWords)
        } ?: return null

        moderationRepository.flagForReview(chatId, "تم العثور على كلمة محظورة: $match")
        return match
    }
}
