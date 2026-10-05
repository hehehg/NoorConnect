package com.noorconnect.feature.channelinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.AuthState
import com.noorconnect.domain.model.ChannelInfo
import com.noorconnect.domain.repository.AuthRepository
import com.noorconnect.domain.usecase.CheckChatAccessUseCase
import com.noorconnect.domain.usecase.DownloadFileUseCase
import com.noorconnect.domain.usecase.GetChatReviewInfoUseCase
import com.noorconnect.domain.usecase.GetChannelInfoUseCase
import com.noorconnect.domain.usecase.GetFileStateUseCase
import com.noorconnect.domain.usecase.JoinChatUseCase
import com.noorconnect.domain.usecase.ResolvePublicChatUseCase
import com.noorconnect.domain.usecase.SubmitChatForReviewUseCase
import com.noorconnect.domain.usecase.UpdateChannelInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed class ChannelInfoState {
    data object Loading : ChannelInfoState()
    data object SignInRequired : ChannelInfoState()
    data class Loaded(val info: ChannelInfo) : ChannelInfoState()
    data class JoinRequired(
        val title: String,
        val chatId: Long? = null,
        val inviteLink: String? = null,
        val requiresReview: Boolean = true,
        val isJoining: Boolean = false,
    ) : ChannelInfoState()
    data class ReviewSubmitted(val title: String) : ChannelInfoState()
    data class Error(val message: String) : ChannelInfoState()
}

sealed class ChannelPhotoState {
    data object Loading : ChannelPhotoState()
    data class Ready(val path: String) : ChannelPhotoState()
    data object Missing : ChannelPhotoState()
    data object Failed : ChannelPhotoState()
}

@HiltViewModel
class ChannelInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val checkChatAccess: CheckChatAccessUseCase,
    private val getChatReviewInfo: GetChatReviewInfoUseCase,
    private val getChannelInfo: GetChannelInfoUseCase,
    private val joinChat: JoinChatUseCase,
    private val submitChatForReview: SubmitChatForReviewUseCase,
    private val resolvePublicChat: ResolvePublicChatUseCase,
    private val updateChannelInfo: UpdateChannelInfoUseCase,
    private val getFileState: GetFileStateUseCase,
    private val downloadFile: DownloadFileUseCase,
) : ViewModel() {

    private val routeChatId: Long? = savedStateHandle["channelId"]
    private val routeUsername: String? = savedStateHandle["username"]
    private val routeInviteHash: String? = savedStateHandle["inviteHash"]
    private var resolvedChatId: Long? = null
    private var photoJob: Job? = null

    private val _state = MutableStateFlow<ChannelInfoState>(ChannelInfoState.Loading)
    val state: StateFlow<ChannelInfoState> = _state

    private val _photoState = MutableStateFlow<ChannelPhotoState>(ChannelPhotoState.Missing)
    val photoState: StateFlow<ChannelPhotoState> = _photoState

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages
    private val _saveSuccessEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveSuccessEvents: SharedFlow<Unit> = _saveSuccessEvents
    private val openChatRequestsChannel = Channel<Long>(Channel.BUFFERED)
    val openChatRequests = openChatRequestsChannel.receiveAsFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = ChannelInfoState.Loading
        viewModelScope.launch {
            try {
                val authState = authRepository.observeAuthState().first { it != AuthState.Uninitialized }
                if (authState != AuthState.Ready) {
                    _state.value = ChannelInfoState.SignInRequired
                    return@launch
                }

                routeInviteHash?.let { inviteHash ->
                    _state.value = ChannelInfoState.JoinRequired(
                        title = "محادثة عبر رابط دعوة",
                        inviteLink = "https://t.me/+$inviteHash",
                    )
                    return@launch
                }

                val resolvedChat = routeUsername?.let { username ->
                    when (val result = resolvePublicChat(username)) {
                        is AppResult.Success -> result.data
                        is AppResult.Failure -> {
                            _state.value = ChannelInfoState.Error(result.message.ifBlank { "القناة غير موجودة" })
                            return@launch
                        }
                        is AppResult.Loading -> {
                            _state.value = ChannelInfoState.Error("تعذر تحميل القناة")
                            return@launch
                        }
                    }
                }
                val chatId = routeChatId ?: resolvedChat?.id
                if (chatId == null) {
                    _state.value = ChannelInfoState.Error("رابط القناة غير صالح")
                    return@launch
                }
                resolvedChatId = chatId

                when (val access = checkChatAccess(chatId)) {
                    is CheckChatAccessUseCase.Result.Denied -> _state.value = ChannelInfoState.Error(access.reason)
                    CheckChatAccessUseCase.Result.NeedsReview -> {
                        val chat = resolvedChat
                        if (chat != null && !chat.isChannel && !chat.isGroup) {
                            openChatRequestsChannel.send(chat.id)
                        } else {
                            _state.value = ChannelInfoState.JoinRequired(
                                title = chat?.title ?: reviewTitle(chatId),
                                chatId = chatId,
                            )
                        }
                    }
                    CheckChatAccessUseCase.Result.Allowed -> {
                        if (resolvedChat != null) {
                            if ((!resolvedChat.isChannel && !resolvedChat.isGroup) || resolvedChat.isMember) {
                                openChatRequestsChannel.send(chatId)
                            } else {
                                _state.value = ChannelInfoState.JoinRequired(
                                    title = resolvedChat.title,
                                    chatId = chatId,
                                    requiresReview = false,
                                )
                            }
                        } else {
                            refreshInfo(chatId)
                        }
                        return@launch
                    }
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _state.value = ChannelInfoState.Error(exception.message ?: "تعذر تحميل القناة")
            }
        }
    }

    fun joinAndRequestReview() {
        val pending = _state.value as? ChannelInfoState.JoinRequired ?: return
        if (pending.isJoining) return
        _state.value = pending.copy(isJoining = true)
        viewModelScope.launch {
            val joined = when {
                pending.inviteLink != null -> joinChat.byInviteLink(pending.inviteLink)
                pending.chatId != null -> joinChat(pending.chatId)
                else -> AppResult.Failure(400, "رابط الدعوة غير صالح")
            }
            when (joined) {
                is AppResult.Success -> when (val access = checkChatAccess(joined.data)) {
                    is CheckChatAccessUseCase.Result.Allowed -> {
                        openChatRequestsChannel.send(joined.data)
                        _state.value = ChannelInfoState.Loading
                    }
                    is CheckChatAccessUseCase.Result.Denied -> _state.value = ChannelInfoState.Error(access.reason)
                    CheckChatAccessUseCase.Result.NeedsReview -> {
                        when (val submitted = submitChatForReview(joined.data, "محادثة جماعية جديدة بعد الانضمام")) {
                            is AppResult.Success -> _state.value = ChannelInfoState.ReviewSubmitted(
                                if (pending.title == "محادثة عبر رابط دعوة") reviewTitle(joined.data) else pending.title,
                            )
                            is AppResult.Failure -> _state.value = ChannelInfoState.Error(submitted.message)
                            is AppResult.Loading -> _state.value = ChannelInfoState.Error("تعذر إرسال المحادثة للمراجعة")
                        }
                    }
                }
                is AppResult.Failure -> _state.value = ChannelInfoState.Error(joined.message)
                is AppResult.Loading -> _state.value = pending.copy(isJoining = false)
            }
        }
    }

    private suspend fun reviewTitle(chatId: Long): String =
        (getChatReviewInfo(chatId) as? AppResult.Success)?.data?.title ?: "المحادثة"

    private suspend fun refreshInfo(chatId: Long) {
        when (val result = getChannelInfo(chatId)) {
            is AppResult.Success -> {
                _state.value = ChannelInfoState.Loaded(result.data)
                loadPhoto(result.data.photoFileId)
            }
            is AppResult.Failure -> _state.value = ChannelInfoState.Error(result.message)
            is AppResult.Loading -> _state.value = ChannelInfoState.Error("تعذر تحميل معلومات القناة")
        }
    }

    private fun loadPhoto(fileId: Int?) {
        photoJob?.cancel()
        if (fileId == null) {
            _photoState.value = ChannelPhotoState.Missing
            return
        }
        _photoState.value = ChannelPhotoState.Loading
        photoJob = viewModelScope.launch {
            val current = getFileState(fileId)
            var remote = (current as? AppResult.Success)?.data
            if (remote == null || !remote.isDownloaded) {
                remote = (downloadFile(fileId) as? AppResult.Success)?.data
            }
            for (attempt in 0 until PHOTO_DOWNLOAD_CHECKS) {
                if (remote?.isDownloaded == true) break
                delay(PHOTO_DOWNLOAD_INTERVAL_MS)
                remote = (getFileState(fileId) as? AppResult.Success)?.data
            }
            _photoState.value = remote?.localPath?.takeIf { remote?.isDownloaded == true }
                ?.let(ChannelPhotoState::Ready)
                ?: ChannelPhotoState.Failed
        }
    }

    fun save(title: String, description: String, username: String?, photoPath: String?) {
        val chatId = resolvedChatId ?: return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                when (val result = updateChannelInfo(chatId, title, description, username, photoPath)) {
                    is AppResult.Success -> {
                        refreshInfo(chatId)
                        _messages.emit("تم حفظ معلومات القناة")
                        _saveSuccessEvents.emit(Unit)
                    }
                    is AppResult.Failure -> {
                        refreshInfo(chatId)
                        _messages.emit(result.message)
                    }
                    is AppResult.Loading -> _messages.emit("تعذر حفظ التغييرات")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                _messages.emit(exception.message ?: "تعذر حفظ التغييرات")
            } finally {
                _isSaving.value = false
            }
        }
    }

    private companion object {
        const val PHOTO_DOWNLOAD_CHECKS = 30
        const val PHOTO_DOWNLOAD_INTERVAL_MS = 250L
    }
}