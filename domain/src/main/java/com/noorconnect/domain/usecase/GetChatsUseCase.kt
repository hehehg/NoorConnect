package com.noorconnect.domain.usecase

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.moderation.ContentFilter
import com.noorconnect.domain.repository.ChatModerationRepository
import com.noorconnect.domain.repository.ChatRepository
import com.noorconnect.domain.repository.ModerationSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Use cases are where product-specific rules live — repositories stay dumb and reusable.
 * Combines three live streams: the raw chat list, the person's local [com.noorconnect.domain.model.ModerationSettings],
 * and the backend's per-chat [com.noorconnect.domain.model.ChannelRecord] map. Two independent
 * gates apply, in order:
 *
 *  1. [ContentFilter] — the existing local, user-editable rules (unverified channels/groups/
 *     keywords). Unchanged from before.
 *  2. Backend status — NEW. A [ChatModerationStatus.Blacklisted] chat is removed from the list
 *     entirely, every single time this combine re-runs, regardless of chat type (channel,
 *     group, or an individual one-to-one chat) and regardless of whether the person already has
 *     it open/subscribed. That's the explicit fix for "a banned chat's photo/content used to
 *     stay visible in the list even after an admin blacklisted it" — this use case is the only
 *     place the chat list is built, so there's no other path a banned chat's photo could leak
 *     through. A chat that is merely [ChatModerationStatus.PendingReview] or
 *     [ChatModerationStatus.Unreviewed] still appears (so the person doesn't lose an existing
 *     conversation over a first-time review), but with its photo stripped — see [Chat.isContentVisible].
 */
class GetChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val moderationSettingsRepository: ModerationSettingsRepository,
    private val moderationRepository: ChatModerationRepository,
    private val contentFilter: ContentFilter,
) {
    operator fun invoke(): Flow<List<Chat>> =
        combine(
            chatRepository.observeChats(),
            moderationSettingsRepository.observeSettings(),
            moderationRepository.observeAllChannelRecords(),
        ) { chats, settings, records ->
            chats
                .filter { contentFilter.isAllowed(it, settings) }
                .mapNotNull { chat ->
                    val status = records[chat.id]?.status ?: ChatModerationStatus.Unreviewed
                    // Never shown again, full stop — no partial "name only" version for a
                    // blacklisted chat the way search does it for unreviewed ones.
                    if (status is ChatModerationStatus.Blacklisted) return@mapNotNull null

                    val withStatus = chat.copy(moderationStatus = status)
                    if (withStatus.isContentVisible) withStatus else withStatus.copy(photoFileId = null)
                }
        }
}
