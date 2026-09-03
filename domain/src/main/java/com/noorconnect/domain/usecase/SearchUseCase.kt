package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChannelAudience
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.model.Gender
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.SearchMessageResult
import com.noorconnect.domain.model.SearchResult
import com.noorconnect.domain.moderation.BannedWordMatcher
import com.noorconnect.domain.repository.ChatModerationRepository
import com.noorconnect.domain.repository.ChatRepository
import com.noorconnect.domain.repository.MediaKind
import com.noorconnect.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The single entry point feature:chats' search screen calls — never [ChatRepository.searchChats],
 * [ChatRepository.searchMessages], or [ChatRepository.searchMediaMessages] directly (see the
 * warnings on those methods).
 *
 * Order of operations, matching the person's spec:
 *  1. The query itself is checked against the banned-word list FIRST, before any TDLib call is
 *     made — a banned query returns [SearchResult.QueryBlocked] and nothing is searched.
 *  2. Otherwise, TDLib is asked for matching chats, text messages, and each media type
 *     (files/audio/photos/videos), concurrently.
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
 *  4. Every message/media hit goes through the exact same per-chat blacklist-drop and
 *     whitelist-vs-not masking rule as chats (see [toResultOrNull]) — a file/photo/audio/video
 *     from an unreviewed chat is just as hidden as its text would be.
 *  5. Chat results are split into [SearchResult.Found.channels] / [SearchResult.Found.groups] so
 *     the UI can render one tab per type without re-deriving the split itself.
 */
class SearchUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val moderationRepository: ChatModerationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    suspend operator fun invoke(query: String): SearchResult = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope SearchResult.Found(emptyList(), emptyList(), emptyList())

        val bannedWords = moderationRepository.observeBannedWords().first()
        if (BannedWordMatcher.containsAny(trimmed, bannedWords)) return@coroutineScope SearchResult.QueryBlocked

        val myGender = userPreferencesRepository.observeOnboardingState().first().gender

        // Every TDLib round trip below is independent of the others, so they run concurrently
        // rather than one after another — otherwise adding four media searches on top of the
        // existing chat/message search would make every keystroke noticeably slower.
        val chatsDeferred = async { (chatRepository.searchChats(trimmed) as? AppResult.Success)?.data.orEmpty() }
        val messagesDeferred = async { (chatRepository.searchMessages(trimmed) as? AppResult.Success)?.data.orEmpty() }
        val filesDeferred = async { searchMedia(trimmed, MediaKind.DOCUMENT) }
        val audioDeferred = async { searchMedia(trimmed, MediaKind.AUDIO) }
        val photosDeferred = async { searchMedia(trimmed, MediaKind.PHOTO) }
        val videosDeferred = async { searchMedia(trimmed, MediaKind.VIDEO) }

        val foundChats = chatsDeferred.await()
        val maskedChats = foundChats.mapNotNull { chat -> maskOrDrop(chat, myGender) }
        val channels = maskedChats.filter { it.isChannel }
        val groups = maskedChats.filter { it.isGroup && !it.isChannel }

        val (messages, files, audio, photos, videos) = awaitAll(
            async { messagesDeferred.await().mapNotNull { toResultOrNull(it, maskedChats) } },
            async { filesDeferred.await().mapNotNull { toResultOrNull(it, maskedChats) } },
            async { audioDeferred.await().mapNotNull { toResultOrNull(it, maskedChats) } },
            async { photosDeferred.await().mapNotNull { toResultOrNull(it, maskedChats) } },
            async { videosDeferred.await().mapNotNull { toResultOrNull(it, maskedChats) } },
        )

        SearchResult.Found(channels, groups, messages, files, audio, photos, videos)
    }

    private suspend fun searchMedia(query: String, kind: MediaKind): List<Message> =
        (chatRepository.searchMediaMessages(query, kind) as? AppResult.Success)?.data.orEmpty()

    /**
     * Shared by text/file/audio/photo/video search: drops a message whose chat is blacklisted,
     * and marks the rest visible only if their chat is whitelisted — same rule
     * [SearchResult.Found]'s kdoc documents for chats, applied per-message so an attachment from
     * a pending/unreviewed chat is never shown before review either.
     */
    private suspend fun toResultOrNull(message: Message, maskedChats: List<Chat>): SearchMessageResult? {
        val record = moderationRepository.getChannelRecord(message.chatId)
        val status = (record as? AppResult.Success)?.data?.status ?: ChatModerationStatus.Unreviewed
        if (status is ChatModerationStatus.Blacklisted) return null

        // Previously this dropped the whole message result when the chat wasn't ALSO a hit in
        // the same call's chat search (i.e. whenever the query matched message text but not the
        // chat's own title) — which silently emptied the messages tab in the common case.
        // Falling back to a direct chat-title lookup fixes that; it only runs for the (much
        // smaller) set of hits whose chat isn't already in maskedChats, not for every hit.
        val chatTitle = maskedChats.firstOrNull { it.id == message.chatId }?.title
            ?: (chatRepository.getChatTitle(message.chatId) ?: return null)

        val isVisible = status is ChatModerationStatus.Whitelisted
        return SearchMessageResult(
            message = message,
            chatId = message.chatId,
            chatTitle = chatTitle,
            textPreview = if (isVisible) message.text else REDACTED_PREVIEW,
            isContentVisible = isVisible,
        )
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
