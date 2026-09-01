package com.noorconnect.data.repository

import com.noorconnect.core.common.AppResult
import com.noorconnect.core.tdlib.TdLibManager
import com.noorconnect.data.mapper.toDomain
import com.noorconnect.domain.model.AuthState
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.RemoteFile
import com.noorconnect.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * @Singleton is load-bearing here, not decoration: this repository holds the live chat-list
 * state itself (see _chats below), and Hilt only reuses one instance — sharing that state
 * across every screen that asks for it — when the class is scoped. Without @Singleton, Hilt
 * would build a fresh instance (and a fresh, empty cache) per injection site.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val tdLib: TdLibManager,
) : ChatRepository {

    // Own long-lived scope — this repository lives for the whole app process (it's a Hilt
    // singleton), independent of any ViewModel or screen's lifecycle. That's the fix for
    // "chat list resets to empty and slowly refills after leaving/returning to the screen":
    // previously the list was rebuilt from scratch (via runningFold on a cold Flow) every time
    // a new collector subscribed, because SharingStarted.WhileSubscribed cancels the upstream
    // after 5s with no subscribers. Now the state lives here permanently; a screen re-subscribing
    // just reads the current cache instantly, nothing rebuilds.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Keyed by chat id so out-of-order updates (last message vs. read count vs. new chat)
    // always land on the right chat regardless of arrival order.
    private val chatsById = MutableStateFlow<Map<Long, Chat>>(emptyMap())

    init {
        scope.launch { collectChatUpdates() }
        scope.launch { loadAllChatsOnceAuthenticated() }
    }

    private suspend fun collectChatUpdates() {
        tdLib.updates.collect { update ->
            when (update) {
                is TdApi.UpdateNewChat -> {
                    val chat = update.chat.toDomain()
                    chatsById.update { it + (chat.id to chat) }
                }
                is TdApi.UpdateChatLastMessage -> chatsById.update { map ->
                    val chat = map[update.chatId] ?: return@update map
                    map + (chat.id to chat.copy(lastMessage = update.lastMessage?.toDomain()))
                }
                is TdApi.UpdateChatReadInbox -> chatsById.update { map ->
                    val chat = map[update.chatId] ?: return@update map
                    map + (chat.id to chat.copy(unreadCount = update.unreadCount))
                }
                else -> Unit
            }
        }
    }

    /**
     * TDLib does NOT send updateNewChat for a user's existing chats on its own — it only
     * populates the chat list in response to an explicit loadChats request (this is documented
     * TDLib behavior, not a bug on our end). Without this, the chat list stays near-empty.
     * Loops until TDLib returns error code 404 ("all chats loaded"), which is its documented
     * signal for "nothing more to load" — capped so a huge account can't loop forever.
     */
    private suspend fun loadAllChatsOnceAuthenticated() {
        tdLib.authState.first { it == AuthState.Ready }
        repeat(MAX_LOAD_CHATS_ROUNDS) {
            val result = tdLib.send(TdApi.LoadChats(TdApi.ChatListMain(), CHATS_PER_LOAD_ROUND))
            if (result is AppResult.Failure && result.code == 404) return
        }
    }

    /**
     * Known simplification (intentional, not an oversight): this doesn't yet sort by
     * TdApi.ChatPosition, so the list is in "first loaded" order rather than "most recent
     * activity" order. Tracked as the next thing to add here, not silently pretended to be done.
     */
    override fun observeChats(): Flow<List<Chat>> = chatsById.map { it.values.toList() }

    /**
     * Same TDLib pattern as the chat list: opening a chat does NOT retroactively push its
     * message history — only new messages arrive automatically via UpdateNewMessage. The
     * initial history has to be requested explicitly via GetChatHistory.
     *
     * Retries a few times with a short delay: GetChatHistory called immediately after OpenChat
     * can legitimately fail because TDLib hasn't finished syncing the chat yet — this is a
     * known TDLib race, not something OpenChat's return value tells you about. If it still
     * fails after retrying, `initial` stays empty and only new live messages will appear —
     * that's the exact "only the last message shows" symptom, now with a real fix attempt
     * instead of just silently swallowing the failure.
     *
     * Known simplification (intentional): this doesn't call TdApi.CloseChat when the screen
     * is left, and doesn't yet support scrolling up for older messages (GetChatHistory is
     * only called once, for the most recent page). Both are natural next additions here, not
     * silently assumed to be handled.
     */
    override fun observeMessages(chatId: Long): Flow<List<Message>> = flow {
        tdLib.send(TdApi.OpenChat(chatId))

        var historyMessages: List<TdApi.Message>? = null
        repeat(HISTORY_FETCH_RETRIES) { attempt ->
            if (historyMessages != null) return@repeat
            val history = tdLib.send(TdApi.GetChatHistory(chatId, 0, 0, INITIAL_MESSAGE_PAGE_SIZE, false))
            when (history) {
                is AppResult.Success -> historyMessages = history.data.messages?.filterNotNull().orEmpty()
                is AppResult.Failure -> if (attempt < HISTORY_FETCH_RETRIES - 1) delay(HISTORY_FETCH_RETRY_DELAY_MS)
                is AppResult.Loading -> Unit
            }
        }

        val initial = historyMessages
            ?.map { it.toDomain() }
            ?.reversed() // TDLib returns newest-first; the chat UI wants oldest-first (top to bottom)
            .orEmpty()

        emitAll(
            tdLib.updates
                .filterIsInstance<TdApi.UpdateNewMessage>()
                .map { it.message.toDomain() }
                .runningFold(initial) { acc, message ->
                    if (message.chatId == chatId) acc + message else acc
                },
        )
    }

    override suspend fun sendMessage(chatId: Long, text: String): AppResult<Unit> {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true)
        // TDLib API note: newer TDLib replaced the old (messageThreadId: Long, replyToMessageId: Long)
        // pair with typed objects (TdApi.MessageTopic?, TdApi.InputMessageReplyTo?) — null means
        // "no thread / not a reply" now, where 0 used to. If you rebuild TDLib later and this
        // breaks again, check TdApi.SendMessage's constructor in the generated TdApi.java first —
        // this is exactly the kind of signature TDLib tends to evolve between versions.
        val function = TdApi.SendMessage(chatId, null, null, null, null, content)
        return when (val result = tdLib.send(function)) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> result
            is AppResult.Loading -> AppResult.Loading
        }
    }

    /**
     * Backs SearchUseCase's chat/channel/group search — this method itself does NOT filter by
     * moderation, that's entirely SearchUseCase's job (see the warning on the interface method).
     * SearchPublicChats only returns chat ids, so each id needs a follow-up GetChat to get the
     * actual TdApi.Chat to map.
     *
     * TDLib API note (same caveat as SendMessage above — verify against your built TdApi.java):
     * TdApi.SearchPublicChats(query) is the call across recent TDLib versions, returning
     * TdApi.Chats(totalCount, chatIds: LongArray). GetChat(chatId) resolves each id to a
     * TdApi.Chat synchronously (TDLib keeps chats it already knows about in memory).
     */
    override suspend fun searchPublicChats(query: String): AppResult<List<Chat>> {
        val searchResult = tdLib.send(TdApi.SearchPublicChats(query, null))
        val chatIds = when (searchResult) {
            is AppResult.Success -> searchResult.data.chatIds?.filterNotNull().orEmpty()
            is AppResult.Failure -> return searchResult
            is AppResult.Loading -> return AppResult.Loading
        }

        val chats = chatIds.mapNotNull { chatId ->
            when (val getChatResult = tdLib.send(TdApi.GetChat(chatId))) {
                is AppResult.Success -> getChatResult.data.toDomain()
                else -> null // one chat failing to resolve shouldn't fail the whole search
            }
        }
        return AppResult.Success(chats)
    }

    /**
     * Backs SearchUseCase's message search. TDLib API note (verify against your built
     * TdApi.java — this constructor's field order/count has changed between TDLib versions in
     * the past, see the SendMessage note above for exactly this kind of drift): as of recent
     * TDLib, global message search across all chats is TdApi.SearchMessages(chatList,
     * onlyInChannels, query, offset, limit, filter, minDate, maxDate) with chatList =
     * TdApi.ChatListMain(), offset = "", filter = null, minDate = 0, maxDate = 0 for "search
     * everywhere, no extra constraints". If this constructor doesn't match after building
     * TDLib, TdApi.java is the source of truth, not this comment.
     */
    override suspend fun searchMessages(query: String): AppResult<List<Message>> {
        val function = TdApi.SearchMessages(TdApi.ChatListMain(), query, "", MESSAGE_SEARCH_LIMIT, null, null, 0, 0)
        return when (val result = tdLib.send(function)) {
            is AppResult.Success -> AppResult.Success(
                result.data.messages?.filterNotNull()?.map { it.toDomain() }.orEmpty(),
            )
            is AppResult.Failure -> result
            is AppResult.Loading -> AppResult.Loading
        }
    }

    /** Non-downloading check (TdApi.GetFile) — see the interface kdoc for why this must never
     *  itself trigger a download. */
    override suspend fun getFile(fileId: Int): AppResult<RemoteFile> =
        when (val result = tdLib.send(TdApi.GetFile(fileId))) {
            is AppResult.Success -> AppResult.Success(result.data.toRemoteFile())
            is AppResult.Failure -> result
            is AppResult.Loading -> AppResult.Loading
        }

    /**
     * TDLib API note (verify against your built TdApi.java): TdApi.DownloadFile(fileId,
     * priority, offset, limit, synchronous) — synchronous = true makes TDLib's response wait
     * until the download actually finishes (or fails), which is exactly what a "tap to
     * download" action wants (show a spinner, then the image) rather than firing-and-forgetting
     * and polling separately. priority 32 is TDLib's normal/default priority (1-32 range,
     * higher = more urgent) — nothing here needs to jump the queue.
     */
    override suspend fun downloadFile(fileId: Int): AppResult<RemoteFile> {
        val function = TdApi.DownloadFile(fileId, DOWNLOAD_PRIORITY, 0, 0, true)
        return when (val result = tdLib.send(function)) {
            is AppResult.Success -> AppResult.Success(result.data.toRemoteFile())
            is AppResult.Failure -> result
            is AppResult.Loading -> AppResult.Loading
        }
    }

    private fun TdApi.File.toRemoteFile(): RemoteFile = RemoteFile(
        fileId = id,
        localPath = local?.path?.takeIf { it.isNotBlank() },
        isDownloaded = local?.isDownloadingCompleted ?: false,
    )

    companion object {
        private const val MAX_LOAD_CHATS_ROUNDS = 20
        private const val CHATS_PER_LOAD_ROUND = 100
        private const val INITIAL_MESSAGE_PAGE_SIZE = 50
        private const val HISTORY_FETCH_RETRIES = 3
        private const val HISTORY_FETCH_RETRY_DELAY_MS = 400L
        private const val MESSAGE_SEARCH_LIMIT = 50
        private const val DOWNLOAD_PRIORITY = 32
    }
}
