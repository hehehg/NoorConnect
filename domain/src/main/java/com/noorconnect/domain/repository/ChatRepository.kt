package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.RemoteFile
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeMessages(chatId: Long): Flow<List<Message>>
    suspend fun sendMessage(chatId: Long, text: String, scheduleDate: Int? = null): AppResult<Unit>
    suspend fun sendMedia(
        chatId: Long,
        path: String,
        mimeType: String,
        caption: String,
        scheduleDate: Int? = null,
    ): AppResult<Unit>
    suspend fun getScheduledMessages(chatId: Long): AppResult<List<Message>>
    suspend fun editMessage(chatId: Long, messageId: Long, text: String): AppResult<Unit>
    suspend fun deleteMessage(chatId: Long, messageId: Long): AppResult<Unit>
    suspend fun sendScheduledNow(chatId: Long, messageId: Long): AppResult<Unit>

    /**
     * Raw TDLib public-chat/channel/group search (TdApi.SearchPublicChats), unfiltered by
     * moderation — callers must go through [com.noorconnect.domain.usecase.SearchUseCase],
     * never call this directly from a screen, or the whole point of the Islamic search
     * (banned-word query gate, blacklist exclusion, name-only masking) is bypassed.
     */
    suspend fun searchPublicChats(query: String): AppResult<List<Chat>>

    /**
     * Raw TDLib global message search across the account's chats (TdApi.SearchMessages) —
     * same warning as [searchPublicChats]: go through SearchUseCase, not this, from UI code.
     */
    suspend fun searchMessages(query: String): AppResult<List<Message>>

    suspend fun searchPersonalMessages(query: String): AppResult<List<Message>>

    /**
     * Current local download state for a file (TdApi.GetFile) — does NOT trigger a download,
     * safe to call just to check "is this already on disk" before deciding whether to show a
     * "tap to download" placeholder.
     */
    suspend fun getFile(fileId: Int): AppResult<RemoteFile>

    /**
     * Triggers (and waits for) a download (TdApi.DownloadFile). Only call this in response to
     * an explicit user action (opening a DM where photos auto-load, or an actual tap on a
     * channel/group photo placeholder) — never speculatively, since that's exactly the
     * behavior the person asked to remove from channels/groups.
     */
    suspend fun downloadFile(fileId: Int): AppResult<RemoteFile>

    suspend fun setChatPinned(chatId: Long, isPinned: Boolean): AppResult<Unit>

    suspend fun setChatArchived(chatId: Long, isArchived: Boolean): AppResult<Unit>
}
