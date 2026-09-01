package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChannelAudience
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.model.Gender
import com.noorconnect.domain.model.SearchMessageResult
import com.noorconnect.domain.model.SearchResult
import com.noorconnect.domain.moderation.BannedWordMatcher
import com.noorconnect.domain.repository.ChatModerationRepository
import com.noorconnect.domain.repository.ChatRepository
import com.noorconnect.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The single entry point feature:chats' search screen calls — never [ChatRepository.searchPublicChats]
 * or [ChatRepository.searchMessages] directly (see the warnings on those methods).
 *
 * Order of operations, matching the person's spec:
 *  1. The query itself is checked against the banned-word list FIRST, before any TDLib call is
 *     made — a banned query returns [SearchResult.QueryBlocked] and nothing is searched.
 *  2. Otherwise, TDLib is asked for matching chats and messages.
 *  3. Every result chat is looked up against the backend's per-chat record:
 *     - [ChatModerationStatus.Blacklisted] -> dropped from the results entirely (never shown,
 *       matching "لا يظهر في البحث اي قناة او مجموعه من القائمة المحظورة").
 *     - Anything else that isn't yet [ChatModerationStatus.Whitelisted] (i.e. pending or never
 *       reviewed) -> kept, but with photo AND last message stripped so only the name shows, and
 *       pushed into the admin review queue right away if it had never been seen before — the
 *       person doesn't have to tap "join" first for that to happen.
 *     - Whitelisted -> shown normally.
 *     - An audience mismatch (channel marked male-only/female-only) is treated the same as
 *       blacklisted for this person: dropped, not just masked, since showing it name-only would
 *       still reveal that a not-for-them channel exists by that exact name.
 */
class SearchUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val moderationRepository: ChatModerationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(query: String): SearchResult {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SearchResult.Found(emptyList(), emptyList())

        val bannedWords = moderationRepository.observeBannedWords().first()
        if (BannedWordMatcher.containsAny(trimmed, bannedWords)) return SearchResult.QueryBlocked

        val myGender = userPreferencesRepository.observeOnboardingState().first().gender

        val foundChats = (chatRepository.searchPublicChats(trimmed) as? AppResult.Success)?.data.orEmpty()
        val maskedChats = foundChats.mapNotNull { chat -> maskOrDrop(chat, myGender) }

        val foundMessages = (chatRepository.searchMessages(trimmed) as? AppResult.Success)?.data.orEmpty()
        val messageResults = foundMessages.mapNotNull { message ->
            // We only have the message here, not its chat's title — SearchUseCase intentionally
            // doesn't re-fetch full Chat objects for every message hit (that's a chat-list-sized
            // batch of lookups on every keystroke); it relies on the same masked chat list this
            // same call already produced when the message's chat is also a chat-search hit, and
            // otherwise checks the backend record directly by id.
            val record = moderationRepository.getChannelRecord(message.chatId)
            val status = (record as? AppResult.Success)?.data?.status ?: ChatModerationStatus.Unreviewed
            if (status is ChatModerationStatus.Blacklisted) return@mapNotNull null

            val chatTitle = maskedChats.firstOrNull { it.id == message.chatId }?.title ?: return@mapNotNull null
            val isVisible = status is ChatModerationStatus.Whitelisted
            SearchMessageResult(
                message = message,
                chatId = message.chatId,
                chatTitle = chatTitle,
                textPreview = if (isVisible) message.text else REDACTED_PREVIEW,
            )
        }

        return SearchResult.Found(maskedChats, messageResults)
    }

    private suspend fun maskOrDrop(chat: Chat, myGender: Gender?): Chat? {
        val recordResult = moderationRepository.getChannelRecord(chat.id)
        val record = (recordResult as? AppResult.Success)?.data
        val status = record?.status ?: ChatModerationStatus.Unreviewed
        if (status is ChatModerationStatus.Blacklisted) return null

        val audience = record?.audience ?: ChannelAudience.BOTH
        val audienceMatches = audience == ChannelAudience.BOTH ||
            (audience == ChannelAudience.MALE && myGender == Gender.MALE) ||
            (audience == ChannelAudience.FEMALE && myGender == Gender.FEMALE)
        if (!audienceMatches) return null

        if (status is ChatModerationStatus.Unreviewed) {
            // First time this chat has ever surfaced anywhere in the app — same "push into the
            // queue immediately" behavior CheckChatAccessUseCase applies at open-time, just
            // triggered earlier (at search-time, before the person can even tap "join").
            moderationRepository.flagForReview(chat.id, "ظهرت في نتائج البحث ولم تتم مراجعتها بعد")
        }

        val withStatus = chat.copy(moderationStatus = status)
        return if (withStatus.isContentVisible) {
            withStatus
        } else {
            withStatus.copy(photoFileId = null, lastMessage = null)
        }
    }

    private companion object {
        const val REDACTED_PREVIEW = "محتوى مخفي حتى تتم مراجعة هذه المحادثة"
    }
}
