package com.noorconnect.domain.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.RemoteFile
import kotlinx.coroutines.flow.Flow

/** Media types the "الملفات/الصوتيات/الصور/الفيديوهات" search tabs each map to one
 *  TdApi.SearchMessagesFilter — see [ChatRepository.searchMediaMessages]. */
enum class MediaKind { DOCUMENT, AUDIO, PHOTO, VIDEO }

interface ChatRepository {
    fun observeChats(): Flow<List<Chat>>
    fun observeMessages(chatId: Long): Flow<List<Message>>
    suspend fun sendMessage(chatId: Long, text: String): AppResult<Unit>

    /**
     * Raw TDLib chat/channel/group search, unfiltered by moderation — callers must go through
     * [com.noorconnect.domain.usecase.SearchUseCase], never call this directly from a screen,
     * or the whole point of the Islamic search (banned-word query gate, blacklist exclusion,
     * name-only masking) is bypassed.
     *
     * Merges TdApi.SearchPublicChats (chats never opened before) with TdApi.SearchChatsOnServer
     * (chats already known to the account) — SearchPublicChats alone explicitly excludes chats
     * already in the account's chat list per its own TDLib documentation, which is why search
     * previously failed to find a channel/group the person had already joined.
     */
    suspend fun searchChats(query: String): AppResult<List<Chat>>

    /**
     * Raw TDLib join (TdApi.JoinChat) — adds the current user to a public channel/group. Only
     * call this through [com.noorconnect.domain.usecase.JoinChatUseCase], which also makes sure
     * an unreviewed chat gets pushed into the moderation queue as part of joining it.
     */
    suspend fun joinChat(chatId: Long): AppResult<Unit>

    /**
     * Cheap title-only lookup (TdApi.GetChat, which TDLib resolves from its in-memory cache for
     * chats it already knows about — no network round trip beyond what a message search hit
     * already implies). Used by [com.noorconnect.domain.usecase.SearchUseCase] to backfill a
     * message search result's chat title when that chat wasn't itself a chat-search hit in the
     * same call. Returns null if the chat can't be resolved.
     */
    suspend fun getChatTitle(chatId: Long): String?

    /**
     * Raw TDLib global message search across the account's chats (TdApi.SearchMessages) —
     * same warning as [searchChats]: go through SearchUseCase, not this, from UI code.
     */
    suspend fun searchMessages(query: String): AppResult<List<Message>>

    /**
     * Raw TDLib global message search restricted to one media type — same warning as
     * [searchMessages]: go through [com.noorconnect.domain.usecase.SearchUseCase], never call
     * this directly from a screen. Backs the "الملفات"/"الصوتيات"/"الصور"/"الفيديوهات" search
     * tabs: [MediaKind] maps to a TdApi.SearchMessagesFilter under the hood.
     */
    suspend fun searchMediaMessages(query: String, kind: MediaKind): AppResult<List<Message>>

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
}
