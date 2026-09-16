package com.noorconnect.feature.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatFolder
import com.noorconnect.domain.model.SearchResult
import com.noorconnect.domain.usecase.DownloadFileUseCase
import com.noorconnect.domain.usecase.GetChatsUseCase
import com.noorconnect.domain.usecase.GetFileStateUseCase
import com.noorconnect.domain.usecase.ManageFoldersUseCase
import com.noorconnect.domain.usecase.ManageChatUseCase
import com.noorconnect.domain.usecase.ObserveFoldersUseCase
import com.noorconnect.domain.usecase.SearchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Same shape as feature:chat's PhotoDownloadState — kept as its own small type here rather
 *  than a cross-module dependency, since feature:chats and feature:chat are sibling modules
 *  with no dependency between them (both only depend on :domain). */
sealed class ChatPhotoDownloadState {
    data object NotDownloaded : ChatPhotoDownloadState()
    data object Downloading : ChatPhotoDownloadState()
    data class Ready(val localPath: String) : ChatPhotoDownloadState()
    data object Failed : ChatPhotoDownloadState()
}

enum class SearchTab { CHATS, MESSAGES, PERSONAL_MESSAGES }

@HiltViewModel
class ChatsViewModel @Inject constructor(
    getChats: GetChatsUseCase,
    observeFolders: ObserveFoldersUseCase,
    private val manageFolders: ManageFoldersUseCase,
    private val manageChat: ManageChatUseCase,
    private val search: SearchUseCase,
    private val getFileState: GetFileStateUseCase,
    private val downloadFile: DownloadFileUseCase,
) : ViewModel() {

    val chats: StateFlow<List<Chat>> = getChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<ChatFolder>> = observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // null = "الكل" (All) — the always-present first tab, not a real folder.
    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId

    val visibleChats: StateFlow<List<Chat>> = combine(chats, folders, _selectedFolderId) { chats, folders, selectedId ->
        val active = chats.filterNot { it.isArchived }
        val folder = folders.find { it.id == selectedId } ?: return@combine active
        active.filter { it.id in folder.chatIds }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val archivedChats: StateFlow<List<Chat>> = chats
        .map { list -> list.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isArchiveExpanded = MutableStateFlow(false)
    val isArchiveExpanded: StateFlow<Boolean> = _isArchiveExpanded

    fun toggleArchiveSection() {
        _isArchiveExpanded.value = !_isArchiveExpanded.value
    }

    fun togglePin(chat: Chat) {
        viewModelScope.launch { manageChat.setPinned(chat.id, !chat.isPinned) }
    }

    fun toggleArchive(chat: Chat) {
        viewModelScope.launch { manageChat.setArchived(chat.id, !chat.isArchived) }
    }

    private val _isSearchActive = MutableStateFlow(false)
    val isSearchActive: StateFlow<Boolean> = _isSearchActive

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResult = MutableStateFlow<SearchResult?>(null)
    val searchResult: StateFlow<SearchResult?> = _searchResult

    private val _selectedSearchTab = MutableStateFlow(SearchTab.CHATS)
    val selectedSearchTab: StateFlow<SearchTab> = _selectedSearchTab

    /**
     * Keyed by TDLib file id (chat.photoFileId), NOT chat id — several chats never share a
     * photo id, but keying this way matches the download infra's natural key and means a
     * search-result chat and its chat-list counterpart, if they ever share a fileId, share
     * one download too. Safe to always auto-download on sight: GetChatsUseCase already sets
     * photoFileId to null for every chat that isn't [com.noorconnect.domain.model.ChatModerationStatus.Whitelisted]
     * (see Chat.isContentVisible), so a fileId only ever reaches this map when showing the real
     * photo is already the correct thing to do.
     */
    private val _chatPhotoStates = MutableStateFlow<Map<Int, ChatPhotoDownloadState>>(emptyMap())
    val chatPhotoStates: StateFlow<Map<Int, ChatPhotoDownloadState>> = _chatPhotoStates

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            chats.collect { list ->
                list.mapNotNull { it.photoFileId }.distinct().forEach { fileId ->
                    if (_chatPhotoStates.value.containsKey(fileId)) return@forEach
                    viewModelScope.launch { checkOrDownload(fileId) }
                }
            }
        }
        // Search results carry their own (also already-masked) photoFileIds — cover those too.
        viewModelScope.launch {
            _searchResult.collect { result ->
                val found = result as? SearchResult.Found ?: return@collect
                found.chats.mapNotNull { it.photoFileId }.distinct().forEach { fileId ->
                    if (_chatPhotoStates.value.containsKey(fileId)) return@forEach
                    viewModelScope.launch { checkOrDownload(fileId) }
                }
            }
        }
    }

    fun openSearch() {
        _isSearchActive.value = true
        _selectedSearchTab.value = SearchTab.CHATS
    }

    fun selectSearchTab(tab: SearchTab) {
        _selectedSearchTab.value = tab
    }

    fun closeSearch() {
        _isSearchActive.value = false
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResult.value = null
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResult.value = null
            return
        }
        // Small debounce so the query-vs-banned-word check and the TDLib calls in SearchUseCase
        // don't fire on every single keystroke.
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _searchResult.value = search(query)
        }
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { manageFolders.create(name) }
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch { manageFolders.rename(folderId, newName) }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            manageFolders.delete(folderId)
            if (_selectedFolderId.value == folderId) _selectedFolderId.value = null
        }
    }

    fun setChatFolderMembership(folderId: String, chatId: Long, isMember: Boolean) {
        viewModelScope.launch { manageFolders.setChatMembership(folderId, chatId, isMember) }
    }

    private suspend fun checkOrDownload(fileId: Int) {
        val state = getFileState(fileId)
        val remote = (state as? AppResult.Success)?.data
        val downloadedPath = remote?.localPath?.takeIf { remote.isDownloaded }
        if (downloadedPath != null) {
            _chatPhotoStates.value += (fileId to ChatPhotoDownloadState.Ready(downloadedPath))
            return
        }
        _chatPhotoStates.value += (fileId to ChatPhotoDownloadState.Downloading)
        val downloadResult = downloadFile(fileId)
        val newState = (downloadResult as? AppResult.Success)?.data?.localPath
            ?.let { ChatPhotoDownloadState.Ready(it) }
            ?: ChatPhotoDownloadState.Failed
        _chatPhotoStates.value += (fileId to newState)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
