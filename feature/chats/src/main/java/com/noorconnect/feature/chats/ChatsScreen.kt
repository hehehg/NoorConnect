package com.noorconnect.feature.chats

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.core.designsystem.NoorColors
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatFolder
import com.noorconnect.domain.model.SearchMessageResult
import com.noorconnect.domain.model.SearchResult
import kotlin.math.absoluteValue

/** Public entry point for :app — same pattern as AuthRoute. */
@Composable
fun ChatsRoute(onOpenChat: (Long) -> Unit, onOpenSettings: () -> Unit) {
    val viewModel: ChatsViewModel = hiltViewModel()
    val chats by viewModel.visibleChats.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedFolderId by viewModel.selectedFolderId.collectAsStateWithLifecycle()
    val isSearchActive by viewModel.isSearchActive.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResult by viewModel.searchResult.collectAsStateWithLifecycle()
    val selectedSearchTab by viewModel.selectedSearchTab.collectAsStateWithLifecycle()
    val joinStates by viewModel.joinStates.collectAsStateWithLifecycle()
    val chatPhotoStates by viewModel.chatPhotoStates.collectAsStateWithLifecycle()

    ChatsScreen(
        chats = chats,
        folders = folders,
        selectedFolderId = selectedFolderId,
        isSearchActive = isSearchActive,
        searchQuery = searchQuery,
        searchResult = searchResult,
        selectedSearchTab = selectedSearchTab,
        joinStates = joinStates,
        chatPhotoStates = chatPhotoStates,
        onOpenChat = onOpenChat,
        onOpenSettings = onOpenSettings,
        onSelectFolder = viewModel::selectFolder,
        onCreateFolder = viewModel::createFolder,
        onRenameFolder = viewModel::renameFolder,
        onDeleteFolder = viewModel::deleteFolder,
        onToggleChatInFolder = viewModel::setChatFolderMembership,
        onOpenSearch = viewModel::openSearch,
        onCloseSearch = viewModel::closeSearch,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSelectSearchTab = viewModel::selectSearchTab,
        onJoin = viewModel::join,
        onDownloadAttachment = viewModel::downloadAttachment,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class) // defensive — TopAppBar/FilterChip stability has drifted across BOM versions before
private fun ChatsScreen(
    chats: List<Chat>,
    folders: List<ChatFolder>,
    selectedFolderId: String?,
    isSearchActive: Boolean,
    searchQuery: String,
    searchResult: SearchResult?,
    selectedSearchTab: SearchTab,
    joinStates: Map<Long, JoinState>,
    chatPhotoStates: Map<Int, ChatPhotoDownloadState>,
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onToggleChatInFolder: (folderId: String, chatId: Long, isMember: Boolean) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectSearchTab: (SearchTab) -> Unit,
    onJoin: (Long) -> Unit,
    onDownloadAttachment: (Int) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderPendingEdit by remember { mutableStateOf<ChatFolder?>(null) }
    var chatPendingFolderPicker by remember { mutableStateOf<Chat?>(null) }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                SearchTopBar(query = searchQuery, onQueryChange = onSearchQueryChange, onClose = onCloseSearch)
            } else {
                TopAppBar(
                    title = { Text("المحادثات") },
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "بحث")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "الإعدادات")
                        }
                    },
                )
            }
        },
    ) { padding ->
        if (isSearchActive) {
            SearchResultsContent(
                query = searchQuery,
                result = searchResult,
                selectedTab = selectedSearchTab,
                joinStates = joinStates,
                chatPhotoStates = chatPhotoStates,
                onSelectTab = onSelectSearchTab,
                onJoin = onJoin,
                onDownloadAttachment = onDownloadAttachment,
                onOpenChat = onOpenChat,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                FolderTabsRow(
                    folders = folders,
                    selectedFolderId = selectedFolderId,
                    onSelect = onSelectFolder,
                    onAddClick = { showCreateDialog = true },
                    onLongPressFolder = { folderPendingEdit = it },
                )
                HorizontalDivider()

                if (chats.isEmpty()) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text("لسه مفيش محادثات هنا")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(chats, key = { it.id }) { chat ->
                            ChatRow(
                                chat = chat,
                                photoState = chat.photoFileId?.let { chatPhotoStates[it] },
                                onClick = { onOpenChat(chat.id) },
                                onManageFolders = { chatPendingFolderPicker = chat },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFolderDialog(
            onConfirm = { name -> onCreateFolder(name); showCreateDialog = false },
            onDismiss = { showCreateDialog = false },
        )
    }

    folderPendingEdit?.let { folder ->
        FolderActionsDialog(
            folder = folder,
            onRename = { newName -> onRenameFolder(folder.id, newName) },
            onDelete = { onDeleteFolder(folder.id) },
            onDismiss = { folderPendingEdit = null },
        )
    }

    chatPendingFolderPicker?.let { chat ->
        ChatFolderPickerDialog(
            chat = chat,
            folders = folders,
            onToggle = { folderId, isMember -> onToggleChatInFolder(folderId, chat.id, isMember) },
            onDismiss = { chatPendingFolderPicker = null },
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("بحث في المحادثات والرسائل...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق البحث")
            }
        },
    )
}

/**
 * Renders exactly what [com.noorconnect.domain.usecase.SearchUseCase] decided — this composable
 * makes no moderation decisions of its own. [SearchResult.QueryBlocked] shows a generic notice
 * (never which word matched — see that sealed type's kdoc for why), and every chat in
 * [SearchResult.Found] that isn't [Chat.isContentVisible] renders name-only, same as
 * [ChatRow] does for the ordinary chat list. One tab per result type — [SearchTab] — so a
 * query like "مسلم" doesn't dump channels, groups, and messages into one undifferentiated list.
 */
@Composable
private fun SearchResultsContent(
    query: String,
    result: SearchResult?,
    selectedTab: SearchTab,
    joinStates: Map<Long, JoinState>,
    chatPhotoStates: Map<Int, ChatPhotoDownloadState>,
    onSelectTab: (SearchTab) -> Unit,
    onJoin: (Long) -> Unit,
    onDownloadAttachment: (Int) -> Unit,
    onOpenChat: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        when {
            query.isBlank() -> Box(modifier = Modifier.padding(24.dp)) {
                Text("اكتب كلمة للبحث في القنوات والمجموعات والرسائل")
            }
            result == null -> Box(modifier = Modifier.padding(24.dp)) {
                Text("جارٍ البحث...")
            }
            result is SearchResult.QueryBlocked -> Box(modifier = Modifier.padding(24.dp)) {
                Text("هذا البحث غير متاح")
            }
            result is SearchResult.Found -> {
                SearchTabsRow(
                    selectedTab = selectedTab,
                    channelsCount = result.channels.size,
                    groupsCount = result.groups.size,
                    messagesCount = result.messages.size,
                    filesCount = result.files.size,
                    audioCount = result.audio.size,
                    photosCount = result.photos.size,
                    videosCount = result.videos.size,
                    onSelectTab = onSelectTab,
                )
                HorizontalDivider()

                val tabIsEmpty = when (selectedTab) {
                    SearchTab.CHANNELS -> result.channels.isEmpty()
                    SearchTab.GROUPS -> result.groups.isEmpty()
                    SearchTab.MESSAGES -> result.messages.isEmpty()
                    SearchTab.FILES -> result.files.isEmpty()
                    SearchTab.AUDIO -> result.audio.isEmpty()
                    SearchTab.PHOTOS -> result.photos.isEmpty()
                    SearchTab.VIDEOS -> result.videos.isEmpty()
                }

                if (result.isEmpty) {
                    Box(modifier = Modifier.padding(24.dp)) { Text("لا توجد نتائج") }
                } else if (tabIsEmpty) {
                    Box(modifier = Modifier.padding(24.dp)) { Text("لا توجد نتائج في هذا التبويب") }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            SearchTab.CHANNELS -> items(result.channels, key = { "ch-${it.id}" }) { chat ->
                                ChatRow(
                                    chat = chat,
                                    photoState = chat.photoFileId?.let { chatPhotoStates[it] },
                                    onClick = { onOpenChat(chat.id) },
                                    onManageFolders = null,
                                    joinState = joinStates[chat.id],
                                    onJoin = { onJoin(chat.id) },
                                )
                                HorizontalDivider()
                            }
                            SearchTab.GROUPS -> items(result.groups, key = { "gr-${it.id}" }) { chat ->
                                ChatRow(
                                    chat = chat,
                                    photoState = chat.photoFileId?.let { chatPhotoStates[it] },
                                    onClick = { onOpenChat(chat.id) },
                                    onManageFolders = null,
                                    joinState = joinStates[chat.id],
                                    onJoin = { onJoin(chat.id) },
                                )
                                HorizontalDivider()
                            }
                            SearchTab.MESSAGES -> items(result.messages, key = { "msg-${it.message.id}" }) { hit ->
                                SearchMessageRow(hit = hit, onClick = { onOpenChat(hit.chatId) })
                                HorizontalDivider()
                            }
                            SearchTab.FILES -> items(result.files, key = { "file-${it.message.id}" }) { hit ->
                                MediaResultRow(
                                    hit = hit,
                                    downloadState = hit.message.document?.fileId?.let { chatPhotoStates[it] },
                                    label = hit.message.document?.fileName ?: hit.chatTitle,
                                    onClick = { onOpenChat(hit.chatId) },
                                    onDownload = { hit.message.document?.fileId?.let(onDownloadAttachment) },
                                )
                                HorizontalDivider()
                            }
                            SearchTab.AUDIO -> items(result.audio, key = { "audio-${it.message.id}" }) { hit ->
                                val audio = hit.message.audio
                                MediaResultRow(
                                    hit = hit,
                                    downloadState = audio?.fileId?.let { chatPhotoStates[it] },
                                    label = audio?.title?.takeIf { it.isNotBlank() } ?: (audio?.performer ?: hit.chatTitle),
                                    onClick = { onOpenChat(hit.chatId) },
                                    onDownload = { audio?.fileId?.let(onDownloadAttachment) },
                                )
                                HorizontalDivider()
                            }
                            SearchTab.PHOTOS -> items(result.photos, key = { "photo-${it.message.id}" }) { hit ->
                                MediaResultRow(
                                    hit = hit,
                                    downloadState = hit.message.photo?.fileId?.let { chatPhotoStates[it] },
                                    label = hit.chatTitle,
                                    onClick = { onOpenChat(hit.chatId) },
                                    onDownload = null, // photos already auto-download when visible — see ChatsViewModel
                                )
                                HorizontalDivider()
                            }
                            SearchTab.VIDEOS -> items(result.videos, key = { "video-${it.message.id}" }) { hit ->
                                MediaResultRow(
                                    hit = hit,
                                    downloadState = hit.message.video?.fileId?.let { chatPhotoStates[it] },
                                    label = hit.chatTitle,
                                    onClick = { onOpenChat(hit.chatId) },
                                    onDownload = { hit.message.video?.fileId?.let(onDownloadAttachment) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One row in the files/audio/photos/videos search tabs. Same visibility rule as
 *  [SearchMessageRow]: when [SearchMessageResult.isContentVisible] is false, the label falls
 *  back to a generic placeholder and no download action is offered — the attachment itself
 *  must stay hidden until the chat is reviewed, same as [ChatRow] withholds a chat's photo. */
@Composable
private fun MediaResultRow(
    hit: SearchMessageResult,
    downloadState: ChatPhotoDownloadState?,
    label: String,
    onClick: () -> Unit,
    onDownload: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(if (hit.isContentVisible) label else hit.chatTitle) },
        supportingContent = { Text(if (hit.isContentVisible) hit.chatTitle else "قيد المراجعة") },
        leadingContent = { AvatarBadge(label = hit.chatTitle, contentVisible = hit.isContentVisible, photoState = null) },
        trailingContent = if (hit.isContentVisible && onDownload != null) {
            {
                when (downloadState) {
                    is ChatPhotoDownloadState.Ready -> Icon(Icons.Filled.Check, contentDescription = "تم التحميل")
                    ChatPhotoDownloadState.Downloading -> CircularProgressIndicator(modifier = Modifier.padding(4.dp), strokeWidth = 2.dp)
                    else -> TextButton(onClick = onDownload) { Text("تحميل") }
                }
            }
        } else {
            null
        },
        modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onClick),
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SearchTabsRow(
    selectedTab: SearchTab,
    channelsCount: Int,
    groupsCount: Int,
    messagesCount: Int,
    filesCount: Int,
    audioCount: Int,
    photosCount: Int,
    videosCount: Int,
    onSelectTab: (SearchTab) -> Unit,
) {
    // ScrollableTabRow, not TabRow: seven tabs don't comfortably fit a fixed-width row on a
    // phone-sized screen — a fixed TabRow would squeeze every label down to an unreadable width.
    ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 8.dp) {
        Tab(
            selected = selectedTab == SearchTab.CHANNELS,
            onClick = { onSelectTab(SearchTab.CHANNELS) },
            text = { Text("القنوات ($channelsCount)") },
        )
        Tab(
            selected = selectedTab == SearchTab.GROUPS,
            onClick = { onSelectTab(SearchTab.GROUPS) },
            text = { Text("المجموعات ($groupsCount)") },
        )
        Tab(
            selected = selectedTab == SearchTab.MESSAGES,
            onClick = { onSelectTab(SearchTab.MESSAGES) },
            text = { Text("الرسائل ($messagesCount)") },
        )
        Tab(
            selected = selectedTab == SearchTab.FILES,
            onClick = { onSelectTab(SearchTab.FILES) },
            text = { Text("الملفات ($filesCount)") },
        )
        Tab(
            selected = selectedTab == SearchTab.AUDIO,
            onClick = { onSelectTab(SearchTab.AUDIO) },
            text = { Text("الصوتيات ($audioCount)") },
        )
        Tab(
            selected = selectedTab == SearchTab.PHOTOS,
            onClick = { onSelectTab(SearchTab.PHOTOS) },
            text = { Text("الصور ($photosCount)") },
        )
        Tab(
            selected = selectedTab == SearchTab.VIDEOS,
            onClick = { onSelectTab(SearchTab.VIDEOS) },
            text = { Text("الفيديوهات ($videosCount)") },
        )
    }
}

@Composable
private fun SearchMessageRow(hit: SearchMessageResult, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(hit.chatTitle) },
        supportingContent = { Text(hit.textPreview, maxLines = 1) },
        leadingContent = { AvatarBadge(label = hit.chatTitle, contentVisible = true, photoState = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
private fun FolderTabsRow(
    folders: List<ChatFolder>,
    selectedFolderId: String?,
    onSelect: (String?) -> Unit,
    onAddClick: () -> Unit,
    onLongPressFolder: (ChatFolder) -> Unit,
) {
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp)) {
        item {
            FilterChip(
                selected = selectedFolderId == null,
                onClick = { onSelect(null) },
                label = { Text("الكل") },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        items(folders, key = { it.id }) { folder ->
            FilterChip(
                selected = selectedFolderId == folder.id,
                onClick = { onSelect(folder.id) },
                label = { Text(folder.name) },
                modifier = Modifier
                    .padding(end = 6.dp)
                    .combinedClickable(
                        onClick = { onSelect(folder.id) },
                        onLongClick = { onLongPressFolder(folder) },
                    ),
            )
        }
        item {
            IconButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "مجلد جديد")
            }
        }
    }
}

@Composable
private fun ChatRow(
    chat: Chat,
    photoState: ChatPhotoDownloadState?,
    onClick: () -> Unit,
    onManageFolders: (() -> Unit)?,
    joinState: JoinState? = null,
    onJoin: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(chat.title) },
        supportingContent = {
            if (chat.isContentVisible) {
                chat.lastMessage?.let { Text(it.text, maxLines = 1) }
            } else {
                // Content deliberately withheld — see Chat.isContentVisible's kdoc. The name
                // still shows (headlineContent above), only the preview/photo are hidden.
                Text("قيد المراجعة", style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            AvatarBadge(label = chat.title, contentVisible = chat.isContentVisible, photoState = photoState)
        },
        trailingContent = {
            Row {
                if (chat.unreadCount > 0) Text(chat.unreadCount.toString(), modifier = Modifier.padding(end = 8.dp))
                if (onManageFolders != null) {
                    IconButton(onClick = onManageFolders) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "إضافة لمجلد")
                    }
                }
                if (onJoin != null) {
                    JoinButton(state = joinState ?: JoinState.Idle, onClick = onJoin)
                }
            }
        },
        modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onClick),
    )
}

/** Small trailing action for a search result row — a channel/group the person doesn't already
 *  have open isn't necessarily "joined" from TDLib's point of view, so this button is what
 *  actually calls TdApi.JoinChat (see [com.noorconnect.domain.usecase.JoinChatUseCase]),
 *  independent of tapping the row itself. */
@Composable
private fun JoinButton(state: JoinState, onClick: () -> Unit) {
    when (state) {
        JoinState.Idle -> TextButton(onClick = onClick) { Text("انضمام") }
        JoinState.Joining -> CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
        JoinState.Joined -> Text("تم الانضمام", style = MaterialTheme.typography.labelSmall)
        JoinState.Failed -> TextButton(onClick = onClick) { Text("إعادة المحاولة") }
    }
}

/**
 * Real chat photo when [photoState] is [ChatPhotoDownloadState.Ready] — colored initials while
 * it's loading/absent, or a lock icon when [contentVisible] is false. Note [photoState] being
 * non-null already implies content is visible in practice (see [ChatsViewModel.chatPhotoStates]'s
 * kdoc for why a masked chat's fileId never reaches this map at all) — [contentVisible] is
 * still checked explicitly here anyway, so this function's own safety doesn't quietly depend on
 * that upstream guarantee holding forever.
 */
@Composable
private fun AvatarBadge(label: String, contentVisible: Boolean, photoState: ChatPhotoDownloadState?) {
    val color = NoorColors.AvatarPalette[label.hashCode().absoluteValue % NoorColors.AvatarPalette.size]
    Box(
        modifier = Modifier.size(40.dp).background(color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val readyPath = (photoState as? ChatPhotoDownloadState.Ready)?.localPath
        val bitmap = if (contentVisible && readyPath != null) {
            remember(readyPath) { BitmapFactory.decodeFile(readyPath) }
        } else {
            null
        }
        when {
            !contentVisible -> Icon(Icons.Filled.Lock, contentDescription = "قيد المراجعة", tint = Color.White)
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(CircleShape),
            )
            else -> Text(text = label.trim().firstOrNull()?.uppercase() ?: "؟", color = Color.White)
        }
    }
}

@Composable
private fun CreateFolderDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مجلد جديد") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("اسم المجلد") }) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun FolderActionsDialog(
    folder: ChatFolder,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var renaming by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(folder.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (renaming) "إعادة تسمية المجلد" else folder.name) },
        text = {
            if (renaming) {
                OutlinedTextField(value = name, onValueChange = { name = it })
            } else {
                Text("عايز تعمل إيه في المجلد ده؟")
            }
        },
        confirmButton = {
            if (renaming) {
                TextButton(onClick = { onRename(name); onDismiss() }, enabled = name.isNotBlank()) { Text("حفظ") }
            } else {
                TextButton(onClick = { renaming = true }) { Text("إعادة تسمية") }
            }
        },
        dismissButton = {
            if (renaming) {
                TextButton(onClick = onDismiss) { Text("إلغاء") }
            } else {
                TextButton(onClick = { onDelete(); onDismiss() }) { Text("حذف") }
            }
        },
    )
}

@Composable
private fun ChatFolderPickerDialog(
    chat: Chat,
    folders: List<ChatFolder>,
    onToggle: (folderId: String, isMember: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مجلدات \"${chat.title}\"") },
        text = {
            if (folders.isEmpty()) {
                Text("لسه مفيش مجلدات — اعمل واحد الأول من زرار + جنب \"الكل\"")
            } else {
                Column {
                    folders.forEach { folder ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Checkbox(
                                checked = chat.id in folder.chatIds,
                                onCheckedChange = { checked -> onToggle(folder.id, checked) },
                            )
                            Text(folder.name, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
    )
}
