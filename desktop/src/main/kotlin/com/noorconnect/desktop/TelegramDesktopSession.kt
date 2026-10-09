package com.noorconnect.desktop

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.ModerationSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed interface TelegramDesktopAuthState {
    data object NeedsApiCredentials : TelegramDesktopAuthState
    data object Initializing : TelegramDesktopAuthState
    data object WaitingForPhone : TelegramDesktopAuthState
    data object WaitingForCode : TelegramDesktopAuthState
    data object WaitingForPassword : TelegramDesktopAuthState
    data class Ready(val displayName: String) : TelegramDesktopAuthState
    data class Failed(val message: String) : TelegramDesktopAuthState
}

class TelegramDesktopSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val rootDirectory = Paths.get(System.getProperty("user.home"), ".noorconnect", "telegram")
    private val databaseDirectory = rootDirectory.resolve("database")
    private val filesDirectory = rootDirectory.resolve("files")
    private val clientReady = CompletableDeferred<Unit>()
    private var client: Client? = null

    private val _authState = MutableStateFlow<TelegramDesktopAuthState>(TelegramDesktopAuthState.NeedsApiCredentials)
    val authState: StateFlow<TelegramDesktopAuthState> = _authState.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()

    private val _messagesByChat = MutableStateFlow<Map<Long, List<Message>>>(emptyMap())
    val messagesByChat: StateFlow<Map<Long, List<Message>>> = _messagesByChat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun start(credentials: TelegramApiCredentials) {
        if (client != null) return
        if (credentials.apiId <= 0 || credentials.apiHash.isBlank()) {
            _authState.value = TelegramDesktopAuthState.Failed("بيانات Telegram API غير صالحة")
            return
        }

        _authState.value = TelegramDesktopAuthState.Initializing
        try {
            Files.createDirectories(databaseDirectory)
            Files.createDirectories(filesDirectory)
            client = Client.create({ update ->
                scope.launch {
                    clientReady.await()
                    handleUpdate(update, credentials)
                }
            }, null, null)
            clientReady.complete(Unit)
        } catch (error: Throwable) {
            _authState.value = TelegramDesktopAuthState.Failed(error.message ?: "تعذر بدء TDLib")
        }
    }

    fun submitPhone(phone: String) = submit {
        require(phone.isNotBlank()) { "أدخل رقم الهاتف مع مفتاح الدولة" }
        request(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
    }

    fun submitCode(code: String) = submit {
        require(code.isNotBlank()) { "أدخل كود Telegram" }
        request(TdApi.CheckAuthenticationCode(code.trim()))
    }

    fun submitPassword(password: String) = submit {
        require(password.isNotBlank()) { "أدخل كلمة مرور التحقق بخطوتين" }
        request(TdApi.CheckAuthenticationPassword(password))
    }

    fun openChat(chatId: Long) {
        scope.launch {
            runCatching {
                request(TdApi.OpenChat(chatId))
                val history = request(TdApi.GetChatHistory(chatId, 0, 0, 50, false))
                val messages = history.messages.orEmpty().filterNotNull().map { it.toDesktopMessage() }.reversed()
                _messagesByChat.value = _messagesByChat.value + (chatId to messages)
            }.onFailure { _error.value = it.message ?: "تعذر تحميل الرسائل" }
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        scope.launch {
            runCatching {
                val input = TdApi.InputMessageText(TdApi.FormattedText(content, emptyArray()), null, true)
                val sent = request(TdApi.SendMessage(chatId, null, null, null, null, input))
                addMessage(sent.toDesktopMessage())
                _error.value = null
            }.onFailure { _error.value = it.message ?: "تعذر إرسال الرسالة" }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun close() {
        val activeClient = client
        client = null
        if (activeClient != null) {
            runCatching { activeClient.send(TdApi.Close(), null) }
        }
        scope.cancel()
    }

    private fun submit(action: suspend () -> Unit) {
        scope.launch {
            runCatching {
                action()
                _error.value = null
            }.onFailure { _error.value = it.message ?: "تعذر تنفيذ طلب Telegram" }
        }
    }

    private fun handleUpdate(update: TdApi.Object, credentials: TelegramApiCredentials) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(update.authorizationState, credentials)
            is TdApi.UpdateNewChat -> upsertChat(update.chat)
            is TdApi.UpdateChatTitle -> _chats.value = _chats.value.map { chat ->
                if (chat.id == update.chatId) chat.copy(title = update.title) else chat
            }
            is TdApi.UpdateChatLastMessage -> _chats.value = _chats.value.map { chat ->
                if (chat.id == update.chatId) chat.copy(lastMessage = update.lastMessage?.toDesktopMessage()) else chat
            }
            is TdApi.UpdateChatReadInbox -> _chats.value = _chats.value.map { chat ->
                if (chat.id == update.chatId) chat.copy(unreadCount = update.unreadCount) else chat
            }
            is TdApi.UpdateNewMessage -> addMessage(update.message.toDesktopMessage())
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState, credentials: TelegramApiCredentials) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                val parameters = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = this@TelegramDesktopSession.databaseDirectory.toString()
                    filesDirectory = this@TelegramDesktopSession.filesDirectory.toString()
                    useMessageDatabase = true
                    useChatInfoDatabase = true
                    useFileDatabase = true
                    useTestDc = false
                    apiId = credentials.apiId
                    apiHash = credentials.apiHash
                    systemLanguageCode = System.getProperty("user.language", "en")
                    deviceModel = "NoorConnect Windows"
                    applicationVersion = "1.0.0"
                }
                scope.launch {
                    runCatching { request(parameters) }
                        .onFailure { _authState.value = TelegramDesktopAuthState.Failed(it.message ?: "تعذر إعداد TDLib") }
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramDesktopAuthState.WaitingForPhone
            is TdApi.AuthorizationStateWaitCode -> _authState.value = TelegramDesktopAuthState.WaitingForCode
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = TelegramDesktopAuthState.WaitingForPassword
            is TdApi.AuthorizationStateReady -> {
                _authState.value = TelegramDesktopAuthState.Ready("Telegram")
                scope.launch {
                    runCatching {
                        val me = request(TdApi.GetMe())
                        val name = listOf(me.firstName, me.lastName).filter { it.isNotBlank() }.joinToString(" ")
                        val username = me.usernames?.activeUsernames?.firstOrNull().orEmpty()
                        _authState.value = TelegramDesktopAuthState.Ready(name.ifBlank { username.ifBlank { "Telegram" } })
                        loadChats()
                    }.onFailure { _error.value = it.message ?: "تعذر تحميل المحادثات" }
                }
            }
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing,
            is TdApi.AuthorizationStateClosed -> {
                _authState.value = TelegramDesktopAuthState.NeedsApiCredentials
                _chats.value = emptyList()
                _messagesByChat.value = emptyMap()
            }
        }
    }

    private suspend fun loadChats() {
        repeat(MAX_CHAT_LOAD_ROUNDS) {
            try {
                request(TdApi.LoadChats(TdApi.ChatListMain(), CHATS_PER_LOAD))
            } catch (error: TdLibRequestException) {
                if (error.code == 404) return
                throw error
            }
        }
    }

    private fun upsertChat(chat: TdApi.Chat) {
        val chatType = chat.type
        val isChannel = chatType is TdApi.ChatTypeSupergroup && chatType.isChannel
        val isGroup = chatType is TdApi.ChatTypeBasicGroup ||
            (chatType is TdApi.ChatTypeSupergroup && !chatType.isChannel)
        val domainChat = Chat(
            id = chat.id,
            title = chat.title,
            lastMessage = chat.lastMessage?.toDesktopMessage(),
            unreadCount = chat.unreadCount,
            isChannel = isChannel,
            isGroup = isGroup,
        )
        _chats.value = (_chats.value.filterNot { it.id == chat.id } + domainChat)
            .sortedByDescending { it.lastMessage?.timestamp ?: 0L }
    }

    private fun addMessage(message: Message) {
        val current = _messagesByChat.value[message.chatId].orEmpty()
        if (current.none { it.id == message.id }) {
            _messagesByChat.value = _messagesByChat.value + (
                message.chatId to (current + message).sortedBy { it.timestamp }
            )
        }
        _chats.value = _chats.value.map { chat ->
            if (chat.id == message.chatId) chat.copy(lastMessage = message) else chat
        }.sortedByDescending { it.lastMessage?.timestamp ?: 0L }
    }

    private suspend fun <T : TdApi.Object> request(function: TdApi.Function<T>): T =
        suspendCancellableCoroutine { continuation ->
            val activeClient = client
            if (activeClient == null) {
                continuation.resumeWithException(IllegalStateException("TDLib is not initialized"))
                return@suspendCancellableCoroutine
            }
            activeClient.send(function) { response ->
                if (!continuation.isActive) return@send
                when (response) {
                    is TdApi.Error -> continuation.resumeWithException(TdLibRequestException(response.code, response.message))
                    else -> {
                        @Suppress("UNCHECKED_CAST")
                        continuation.resume(response as T)
                    }
                }
            }
        }

    private fun TdApi.Message.toDesktopMessage(): Message {
        val body = when (val messageContent = content) {
            is TdApi.MessageText -> messageContent.text.text
            is TdApi.MessagePhoto -> messageContent.caption?.text.orEmpty().ifBlank { "Photo" }
            is TdApi.MessageVideo -> messageContent.caption?.text.orEmpty().ifBlank { "Video" }
            is TdApi.MessageDocument -> messageContent.caption?.text.orEmpty().ifBlank { messageContent.document?.fileName.orEmpty() }
            is TdApi.MessageAudio -> messageContent.caption?.text.orEmpty().ifBlank { messageContent.audio?.title.orEmpty() }
            is TdApi.MessageVoiceNote -> "Voice message"
            else -> "Unsupported message"
        }
        val sender = if (isOutgoing) "You" else when (val messageSender = senderId) {
            is TdApi.MessageSenderUser -> "User ${messageSender.userId}"
            is TdApi.MessageSenderChat -> "Chat ${messageSender.chatId}"
            else -> "Telegram"
        }
        return Message(id = id, chatId = chatId, text = body, senderName = sender, timestamp = date.toLong(), isOutgoing = isOutgoing)
    }

    private class TdLibRequestException(val code: Int, message: String) : RuntimeException("TDLib $code: $message")

    private companion object {
        const val CHATS_PER_LOAD = 100
        const val MAX_CHAT_LOAD_ROUNDS = 20
    }
}