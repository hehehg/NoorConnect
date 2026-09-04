package com.noorconnect.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.ReportReason
import com.noorconnect.domain.usecase.CheckChatAccessUseCase
import com.noorconnect.domain.usecase.DownloadFileUseCase
import com.noorconnect.domain.usecase.GetChatByIdUseCase
import com.noorconnect.domain.usecase.GetFileStateUseCase
import com.noorconnect.domain.usecase.GetMessagesUseCase
import com.noorconnect.domain.usecase.GetUserDisplayNameUseCase
import com.noorconnect.domain.usecase.GetUserProfilePhotoUseCase
import com.noorconnect.domain.usecase.GetUserUsernameUseCase
import com.noorconnect.domain.usecase.ObserveBannedWordsUseCase
import com.noorconnect.domain.usecase.ReportChatUseCase
import com.noorconnect.domain.usecase.ScanMessagesForBannedWordsUseCase
import com.noorconnect.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ChatAccessState {
    data object Checking : ChatAccessState()
    data object Allowed : ChatAccessState()
    data class Denied(val reason: String) : ChatAccessState()
}

/**
 * One file's local-availability state, keyed by TDLib file id in [ChatViewModel.photoStates] —
 * shared by BOTH message-content photos and sender profile-photo avatars, since "check if it's
 * on disk, download if not, show a bitmap once ready" is the exact same flow for either one.
 * Only the download TRIGGER policy differs (see [ChatViewModel]'s init block): message photos
 * in a channel/group wait for a tap, everything else — DM photos, every avatar — downloads
 * automatically, since avatars are small thumbnails and were never the thing the person asked
 * to stop auto-loading.
 */
sealed class PhotoDownloadState {
    /** Not checked yet, or checked and confirmed not on disk — in a channel/group message
     *  photo this is the state the UI renders as a "tap to download" placeholder. */
    data object NotDownloaded : PhotoDownloadState()
    data class Downloading(val progress: Int? = null) : PhotoDownloadState()
    data class Ready(val localPath: String) : PhotoDownloadState()
    data object Failed : PhotoDownloadState()
}

sealed class ReportState {
    data object Idle : ReportState()
    data object Submitting : ReportState()
    data object Submitted : ReportState()
    data object Failed : ReportState()
}

sealed class MediaSendState {
    data object Idle : MediaSendState()
    data object Sending : MediaSendState()
    data class Failed(val message: String) : MediaSendState()
}

sealed class MessageSendState {
    data object Idle : MessageSendState()
    data class Failed(val message: String) : MessageSendState()
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getMessages: GetMessagesUseCase,
    getChatById: GetChatByIdUseCase,
    private val sendMessage: SendMessageUseCase,
    private val checkChatAccess: CheckChatAccessUseCase,
    private val scanMessagesForBannedWords: ScanMessagesForBannedWordsUseCase,
    private val getUserDisplayName: GetUserDisplayNameUseCase,
    private val getUserUsername: GetUserUsernameUseCase,
    private val getUserProfilePhoto: GetUserProfilePhotoUseCase,
    private val getFileState: GetFileStateUseCase,
    private val downloadFile: DownloadFileUseCase,
    private val reportChat: ReportChatUseCase,
    observeBannedWords: ObserveBannedWordsUseCase,
) : ViewModel() {

    // Nav arg — see NoorConnectNavHost's "chat/{chatId}" route.
    private val chatId: Long = checkNotNull(savedStateHandle["chatId"])

    private val _accessState = MutableStateFlow<ChatAccessState>(ChatAccessState.Checking)
    val accessState: StateFlow<ChatAccessState> = _accessState

    val messages: StateFlow<List<Message>> = getMessages(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Used for the top bar title and to decide auto-download (DM) vs tap-to-download
     *  (channel/group) for photo MESSAGES — see [Chat.isChannel]/[Chat.isGroup]. */
    val chat: StateFlow<Chat?> = getChatById(chatId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // senderId -> resolved display name. "أنت" for the person's own messages never needs a
    // TDLib lookup (see ChatScreen), so this map only ever holds OTHER senders' names.
    private val _senderNames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val senderNames: StateFlow<Map<Long, String>> = _senderNames

    // senderId -> public username for profile links / direct-user actions in the avatar menu.
    private val _senderUsernames = MutableStateFlow<Map<Long, String>>(emptyMap())
    val senderUsernames: StateFlow<Map<Long, String>> = _senderUsernames

    // senderId -> that sender's avatar file id, only present once resolved AND the sender
    // actually has a profile photo (see resolveSender below for the "no photo" case).
    private val _senderPhotoFileIds = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val senderPhotoFileIds: StateFlow<Map<Long, Int>> = _senderPhotoFileIds

    private val _openPrivateChatRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val openPrivateChatRequests: SharedFlow<Long> = _openPrivateChatRequests

    // Shared by message-content photos and sender-avatar photos alike — see PhotoDownloadState's
    // kdoc for why one map covers both.
    private val _photoStates = MutableStateFlow<Map<Int, PhotoDownloadState>>(emptyMap())
    val photoStates: StateFlow<Map<Int, PhotoDownloadState>> = _photoStates

    private val _mediaSendState = MutableStateFlow<MediaSendState>(MediaSendState.Idle)
    val mediaSendState: StateFlow<MediaSendState> = _mediaSendState

    private val _messageSendState = MutableStateFlow<MessageSendState>(MessageSendState.Idle)
    val messageSendState: StateFlow<MessageSendState> = _messageSendState

    private val _reportState = MutableStateFlow<ReportState>(ReportState.Idle)
    val reportState: StateFlow<ReportState> = _reportState

    private val _scheduledMessages = MutableStateFlow<List<Message>>(emptyList())
    val scheduledMessages: StateFlow<List<Message>> = _scheduledMessages

    // Tracks which senders we've already asked TDLib about, independent of whether they turned
    // out to have a photo — without this, a sender with NO profile photo would be re-queried on
    // every single message list recomposition instead of just once.
    private val attemptedSenderLookups = mutableSetOf<Long>()

    init {
        // Gate: runs once per screen open. See CheckChatAccessUseCase for what it checks
        // (moderation status, then audience match against the user's own onboarding gender).
        viewModelScope.launch {
            _accessState.value = when (val result = checkChatAccess(chatId)) {
                is CheckChatAccessUseCase.Result.Allowed -> ChatAccessState.Allowed
                is CheckChatAccessUseCase.Result.Denied -> ChatAccessState.Denied(result.reason)
            }
        }

        viewModelScope.launch {
            when (val result = sendMessage.scheduled(chatId)) {
                is AppResult.Success -> _scheduledMessages.value = result.data
                else -> Unit
            }
        }

        // Live scan: every time the message list or the banned-word list changes, re-check.
        // A match flips accessState to Denied even if the chat was already open and Allowed —
        // this is the "auto-close and send for review" behavior, not just a pre-open check.
        viewModelScope.launch {
            combine(messages, observeBannedWords()) { msgs, words -> msgs to words }
                .collect { (msgs, words) ->
                    val match = scanMessagesForBannedWords(chatId, msgs, words)
                    if (match != null) {
                        _accessState.value = ChatAccessState.Denied(
                            "تم إغلاق المحادثة تلقائيًا: تم رصد كلمة محظورة ($match)، وأُرسلت للمراجعة",
                        )
                    }
                }
        }

        // Resolve name + avatar for any sender we haven't looked up yet. One TDLib call
        // (GetUser, via UserRepositoryImpl) backs both — see resolveSender.
        viewModelScope.launch {
            messages.collect { msgs ->
                val unresolved = msgs
                    .filterNot { it.isOutgoing }
                    .map { it.senderId }
                    .distinct()
                    .filterNot { attemptedSenderLookups.contains(it) }
                unresolved.forEach { senderId ->
                    attemptedSenderLookups += senderId
                    viewModelScope.launch { resolveSender(senderId) }
                }
            }
        }

        // For every MESSAGE photo we haven't looked at yet: check its local state once chat
        // type is known. A DM photo that isn't downloaded yet is auto-downloaded right here — a
        // channel/group photo is left as NotDownloaded so ChatScreen renders a tap-to-download
        // placeholder instead. This is the one place that decision is made; ChatScreen never
        // triggers a download on its own initiative, only in response to an explicit tap.
        viewModelScope.launch {
            combine(messages, chat.filterNotNull()) { msgs, currentChat -> msgs to currentChat }
                .collect { (msgs, currentChat) ->
                    val isChannelOrGroup = currentChat.isChannel || currentChat.isGroup
                    msgs.mapNotNull { it.mediaFileId }.distinct().forEach { fileId ->
                        if (_photoStates.value.containsKey(fileId)) return@forEach
                        viewModelScope.launch { checkPhotoState(fileId, autoDownload = !isChannelOrGroup) }
                    }
                }
        }
    }

    fun send(text: String, scheduleDate: Int? = null) {
        if (text.isBlank()) return
        viewModelScope.launch {
            when (val result = sendMessage(chatId, text, scheduleDate)) {
                is AppResult.Success -> {
                    _messageSendState.value = MessageSendState.Idle
                    if (scheduleDate != null) refreshScheduled()
                }
                is AppResult.Failure -> _messageSendState.value = MessageSendState.Failed(result.message)
                is AppResult.Loading -> Unit
            }
        }
    }

    fun sendMedia(path: String, mimeType: String, caption: String, scheduleDate: Int? = null) {
        viewModelScope.launch {
            _mediaSendState.value = MediaSendState.Sending
            when (val result = sendMessage.media(chatId, path, mimeType, caption, scheduleDate)) {
                is AppResult.Success -> {
                    _mediaSendState.value = MediaSendState.Idle
                    if (scheduleDate != null) refreshScheduled()
                }
                is AppResult.Failure -> _mediaSendState.value = MediaSendState.Failed(result.message)
                is AppResult.Loading -> _mediaSendState.value = MediaSendState.Sending
            }
        }
    }

    fun edit(messageId: Long, text: String) {
        viewModelScope.launch { sendMessage.edit(chatId, messageId, text) }
    }

    fun delete(messageId: Long) {
        viewModelScope.launch {
            sendMessage.delete(chatId, messageId)
            refreshScheduled()
        }
    }

    fun sendScheduledNow(messageId: Long) {
        viewModelScope.launch {
            sendMessage.sendScheduledNow(chatId, messageId)
            refreshScheduled()
        }
    }

    private suspend fun refreshScheduled() {
        when (val result = sendMessage.scheduled(chatId)) {
            is AppResult.Success -> _scheduledMessages.value = result.data
            else -> Unit
        }
    }

    /** The only entry point ChatScreen calls for a channel/group MESSAGE photo tap — avatars
     *  never go through a tap, they're always auto-downloaded (see the init block above). */
    fun downloadPhoto(fileId: Int) {
        if (_photoStates.value[fileId] is PhotoDownloadState.Downloading) return
        _photoStates.value += (fileId to PhotoDownloadState.Downloading())
        viewModelScope.launch {
            when (val result = downloadFile(fileId)) {
                is AppResult.Success -> {
                    var file = result.data
                    while (!file.isDownloaded) {
                        val progress = file.expectedSize.takeIf { it > 0 }?.let {
                            ((file.downloadedSize * 100) / it).toInt().coerceIn(0, 99)
                        }
                        _photoStates.value += (fileId to PhotoDownloadState.Downloading(progress))
                        delay(250)
                        file = when (val state = getFileState(fileId)) {
                            is AppResult.Success -> state.data
                            else -> break
                        }
                    }
                    _photoStates.value += (fileId to file.toPhotoDownloadState())
                }
                else -> _photoStates.value += (fileId to result.toPhotoDownloadState())
            }
        }
    }

    fun openPrivateChatWith(userId: Long) {
        viewModelScope.launch {
            val result = when (val created = sendMessage.createPrivateChat(userId)) {
                is AppResult.Success -> created.data
                is AppResult.Failure -> return@launch
                is AppResult.Loading -> return@launch
            }
            _openPrivateChatRequests.tryEmit(result)
        }
    }

    fun report(reason: ReportReason, details: String) {
        viewModelScope.launch {
            _reportState.value = ReportState.Submitting
            _reportState.value = when (reportChat(chatId, reason, details)) {
                is AppResult.Success -> ReportState.Submitted
                else -> ReportState.Failed
            }
        }
    }

    fun dismissReportState() {
        _reportState.value = ReportState.Idle
    }

    private suspend fun resolveSender(senderId: Long) {
        when (val nameResult = getUserDisplayName(senderId)) {
            is AppResult.Success -> _senderNames.value += (senderId to nameResult.data)
            else -> Unit // leave unresolved — ChatScreen falls back to a generic label
        }

        when (val usernameResult = getUserUsername(senderId)) {
            is AppResult.Success -> _senderUsernames.value += (senderId to usernameResult.data.orEmpty())
            else -> Unit
        }

        when (val photoResult = getUserProfilePhoto(senderId)) {
            is AppResult.Success -> {
                val fileId = photoResult.data ?: return // no profile photo set — nothing to download
                _senderPhotoFileIds.value += (senderId to fileId)
                if (!_photoStates.value.containsKey(fileId)) checkPhotoState(fileId, autoDownload = true)
            }
            else -> Unit
        }
    }

    private suspend fun checkPhotoState(fileId: Int, autoDownload: Boolean) {
        val state = getFileState(fileId)
        val remote = (state as? AppResult.Success)?.data
        val downloadedPath = remote?.localPath?.takeIf { remote.isDownloaded }
        when {
            downloadedPath != null -> _photoStates.value += (fileId to PhotoDownloadState.Ready(downloadedPath))
            autoDownload -> downloadPhoto(fileId)
            state is AppResult.Success -> _photoStates.value += (fileId to PhotoDownloadState.NotDownloaded)
            else -> _photoStates.value += (fileId to PhotoDownloadState.Failed)
        }
    }

    private fun AppResult<com.noorconnect.domain.model.RemoteFile>.toPhotoDownloadState(): PhotoDownloadState =
        when (this) {
            is AppResult.Success -> data.localPath?.let { PhotoDownloadState.Ready(it) } ?: PhotoDownloadState.Failed
            else -> PhotoDownloadState.Failed
        }
}
