package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChannelRecord
import kotlinx.coroutines.flow.Flow

interface ChatModerationRepository {
    suspend fun getChannelRecord(chatId: Long): AppResult<ChannelRecord>

    /** Used both for "never reviewed, needs a first look" and "banned word found, pull back
     *  from whitelist for re-review" — same admin queue, same effect either way. */
    suspend fun flagForReview(chatId: Long, reason: String): AppResult<Unit>

    fun observeBannedWords(): Flow<List<String>>

    /**
     * Live map of every chat the backend has an opinion on (keyed by chat id), pushed on every
     * Firestore change — NOT a one-shot fetch. This is what lets a chat's avatar/content
     * disappear from the chat list the moment an admin blacklists it, instead of only being
     * enforced the next time the person happens to tap into that specific chat
     * ([com.noorconnect.domain.usecase.CheckChatAccessUseCase] alone was too late for that: it
     * only runs at open-time, so a blacklisted chat's photo stayed visible in the list forever).
     * A chat id with no entry in the returned map has never been reviewed — treat it exactly
     * like [com.noorconnect.domain.model.ChatModerationStatus.Unreviewed].
     */
    fun observeAllChannelRecords(): Flow<Map<Long, ChannelRecord>>
}
