package com.noorconnect.feature.chat

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Calendar
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.core.designsystem.NoorColors
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.MessageMediaType
import com.noorconnect.domain.model.MessagePhoto
import com.noorconnect.domain.model.ReportReason
import kotlin.math.absoluteValue

private fun ContentResolver.displayName(uri: Uri): String? =
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }

private fun uploadSuffix(fileName: String?, mimeType: String): String {
    val originalExtension = fileName
        ?.substringAfterLast('.', "")
        ?.takeIf { it.isNotBlank() && it.length <= 10 }
    val extension = originalExtension
        ?: MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
    return extension?.let { ".${it.lowercase()}" } ?: ".bin"
}

/** Public entry point for :app — reads chatId from the nav back stack via SavedStateHandle. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ChatRoute(onOpenChat: (Long) -> Unit = {}) {
    val viewModel: ChatViewModel = hiltViewModel()
    val accessState by viewModel.accessState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val senderNames by viewModel.senderNames.collectAsStateWithLifecycle()
    val senderUsernames by viewModel.senderUsernames.collectAsStateWithLifecycle()
    val senderPhotoFileIds by viewModel.senderPhotoFileIds.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val photoStates by viewModel.photoStates.collectAsStateWithLifecycle()
    val mediaSendState by viewModel.mediaSendState.collectAsStateWithLifecycle()
    val messageSendState by viewModel.messageSendState.collectAsStateWithLifecycle()
    val reportState by viewModel.reportState.collectAsStateWithLifecycle()
    val scheduledMessages by viewModel.scheduledMessages.collectAsStateWithLifecycle()

    ChatScreen(
        title = chat?.title ?: "محادثة",
        accessState = accessState,
        messages = messages,
        senderNames = senderNames,
        senderUsernames = senderUsernames,
        senderPhotoFileIds = senderPhotoFileIds,
        photoStates = photoStates,
        mediaSendState = mediaSendState,
        messageSendState = messageSendState,
        canSendMessages = chat?.canSendMessages ?: true,
        sendRestrictionReason = chat?.sendRestrictionReason,
        reportState = reportState,
        scheduledMessages = scheduledMessages,
        onSend = viewModel::send,
        onSendMedia = viewModel::sendMedia,
        onEdit = viewModel::edit,
        onDelete = viewModel::delete,
        onSendScheduledNow = viewModel::sendScheduledNow,
        onDownloadPhoto = viewModel::downloadPhoto,
        onOpenPrivateChat = viewModel::openPrivateChatWith,
        onReport = viewModel::report,
        onDismissReportState = viewModel::dismissReportState,
    )
    LaunchedEffect(Unit) {
        viewModel.openPrivateChatRequests.collect { onOpenChat(it) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatScreen(
    title: String,
    accessState: ChatAccessState,
    messages: List<Message>,
    senderNames: Map<Long, String>,
    senderUsernames: Map<Long, String>,
    senderPhotoFileIds: Map<Long, Int>,
    photoStates: Map<Int, PhotoDownloadState>,
    mediaSendState: MediaSendState,
    messageSendState: MessageSendState,
    canSendMessages: Boolean,
    sendRestrictionReason: String?,
    reportState: ReportState,
    scheduledMessages: List<Message>,
    onSend: (String, Int?) -> Unit,
    onSendMedia: (String, String, String, Int?) -> Unit,
    onEdit: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onSendScheduledNow: (Long) -> Unit,
    onDownloadPhoto: (Int) -> Unit,
    onOpenPrivateChat: (Long) -> Unit,
    onReport: (ReportReason, String) -> Unit,
    onDismissReportState: () -> Unit,
) {
    var showReportDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                actions = {
                    // "الإبلاغ عن المحادثة" — available on every chat (channel, group, or
                    // individual), not gated behind Allowed state: reporting is exactly how a
                    // person flags a chat that shouldn't have passed the automatic checks.
                    IconButton(onClick = { showReportDialog = true }) {
                        Icon(Icons.Filled.Flag, contentDescription = "الإبلاغ عن المحادثة")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (accessState) {
                ChatAccessState.Checking -> CheckingContent()
                is ChatAccessState.Denied -> DeniedContent(reason = accessState.reason)
                ChatAccessState.Allowed -> ChatContent(
                    messages = messages,
                    senderNames = senderNames,
                    senderUsernames = senderUsernames,
                    senderPhotoFileIds = senderPhotoFileIds,
                    photoStates = photoStates,
                    mediaSendState = mediaSendState,
                    messageSendState = messageSendState,
                    canSendMessages = canSendMessages,
                    sendRestrictionReason = sendRestrictionReason,
                    draft = draft,
                    onDraftChange = { draft = it },
                    scheduledMessages = scheduledMessages,
                    onSend = { text, scheduleDate -> onSend(text, scheduleDate); draft = "" },
                    onSendMedia = onSendMedia,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onSendScheduledNow = onSendScheduledNow,
                    onDownloadPhoto = onDownloadPhoto,
                    onOpenPrivateChat = onOpenPrivateChat,
                )
            }
        }
    }

    if (showReportDialog) {
        ReportChatDialog(
            reportState = reportState,
            onSubmit = onReport,
            onDismiss = {
                showReportDialog = false
                onDismissReportState()
            },
        )
    }
}

@Composable
private fun CheckingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DeniedContent(reason: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(reason, textAlign = TextAlign.Center)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ChatContent(
    messages: List<Message>,
    senderNames: Map<Long, String>,
    senderUsernames: Map<Long, String>,
    senderPhotoFileIds: Map<Long, Int>,
    photoStates: Map<Int, PhotoDownloadState>,
    mediaSendState: MediaSendState,
    messageSendState: MessageSendState,
    canSendMessages: Boolean,
    sendRestrictionReason: String?,
    scheduledMessages: List<Message>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: (String, Int?) -> Unit,
    onSendMedia: (String, String, String, Int?) -> Unit,
    onEdit: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onSendScheduledNow: (Long) -> Unit,
    onDownloadPhoto: (Int) -> Unit,
    onOpenPrivateChat: (Long) -> Unit,
) {
    val context = LocalContext.current
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var selectedMimeType by remember { mutableStateOf<String?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var pendingSavePath by remember { mutableStateOf<String?>(null) }
    var mediaError by remember { mutableStateOf<String?>(null) }
    var scheduleDate by remember { mutableStateOf<Int?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val resolver = context.contentResolver
        val originalName = resolver.displayName(uri)
        val mimeType = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                originalName?.substringAfterLast('.', "")?.lowercase(),
            )
            ?: "application/octet-stream"
        val path = File.createTempFile("upload-", uploadSuffix(originalName, mimeType), context.cacheDir)
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                path.outputStream().use { output -> input.copyTo(output) }
            } ?: error("تعذر فتح الوسائط المختارة")
            check(path.length() > 0) { "الوسائط المختارة فارغة" }
        }
        if (copied.isSuccess) {
            selectedPath = path.absolutePath
            selectedMimeType = mimeType
            selectedFileName = originalName ?: path.name
            mediaError = null
        } else {
            path.delete()
            mediaError = copied.exceptionOrNull()?.message ?: "تعذر تجهيز الوسائط"
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val sourcePath = pendingSavePath
        if (uri != null && sourcePath != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    File(sourcePath).inputStream().use { input -> input.copyTo(output) }
                } ?: error("تعذر فتح مكان الحفظ")
            }.onFailure { mediaError = it.message ?: "تعذر حفظ الوسائط" }
        }
        pendingSavePath = null
    }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        mediaError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        (mediaSendState as? MediaSendState.Failed)?.message?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        (messageSendState as? MessageSendState.Failed)?.message?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (scheduledMessages.isNotEmpty()) {
            Text("الرسائل المجدولة", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
            scheduledMessages.forEach { scheduled ->
                ListItem(
                    headlineContent = { Text(scheduled.text.ifBlank { "مرفق مجدول" }, maxLines = 1) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { onSendScheduledNow(scheduled.id) }) { Text("إرسال الآن") }
                            TextButton(onClick = { onDelete(scheduled.id) }) { Text("حذف") }
                        }
                    },
                )
            }
        }
        selectedFileName?.let { name ->
            Text("تم اختيار الوسائط: $name", modifier = Modifier.padding(horizontal = 12.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                val avatarFileId = if (message.isOutgoing) null else senderPhotoFileIds[message.senderId]
                MessageBubble(
                    message = message,
                    senderName = if (message.isOutgoing) "أنت" else senderNames[message.senderId] ?: "...",
                    senderUsername = if (message.isOutgoing) null else senderUsernames[message.senderId],
                    avatarPhotoState = avatarFileId?.let { photoStates[it] },
                    photoState = message.photo?.let { photoStates[it.fileId] } ?: PhotoDownloadState.NotDownloaded,
                    photoStates = photoStates,
                    onDownloadPhoto = { message.photo?.let { onDownloadPhoto(it.fileId) } },
                    onDownloadMedia = { message.mediaFileId?.let(onDownloadPhoto) },
                    onSaveMedia = { path, name ->
                        pendingSavePath = path
                        saveFile.launch(name)
                    },
                    onOpenPrivateChat = onOpenPrivateChat,
                )
            }
        }
        if (canSendMessages) {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { filePicker.launch(arrayOf("image/*", "video/*", "audio/*", "*/*")) }) {
                    Icon(Icons.Filled.AttachFile, contentDescription = "إرفاق ملف")
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("اكتب رسالة...") },
                )
                IconButton(onClick = {
                    val now = Calendar.getInstance()
                    DatePickerDialog(context, { _, year, month, day ->
                        TimePickerDialog(context, { _, hour, minute ->
                            scheduleDate = Calendar.getInstance().apply {
                                set(year, month, day, hour, minute, 0)
                            }.timeInMillis.div(1000).toInt()
                        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
                    }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
                }) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = "جدولة الرسالة")
                }
                Button(onClick = {
                    selectedPath?.let { path ->
                        onSendMedia(path, selectedMimeType ?: "application/octet-stream", draft, scheduleDate)
                        onDraftChange("")
                        selectedPath = null
                        selectedMimeType = null
                        selectedFileName = null
                    } ?: onSend(draft, scheduleDate)
                    scheduleDate = null
                }, modifier = Modifier.padding(start = 8.dp)) {
                    Text("إرسال")
                }
            }
        } else {
            Text(
                sendRestrictionReason ?: "لا يمكنك إرسال الرسائل في هذه المحادثة",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
    editingMessage?.let { message ->
        var editedText by remember(message.id) { mutableStateOf(message.text) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("تعديل الرسالة") },
            text = { OutlinedTextField(value = editedText, onValueChange = { editedText = it }) },
            confirmButton = {
                TextButton(onClick = {
                    onEdit(message.id, editedText)
                    editingMessage = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDelete(message.id)
                    editingMessage = null
                }) { Text("حذف") }
            },
        )
    }
}

/**
 * Every message: framed bubble, distinct background from the screen's own background,
 * a colored sender name above it, and an avatar — on the RIGHT for the person's own
 * (outgoing) messages, on the LEFT for anyone else's, with the whole message block
 * following its avatar toward that same side. The app forces RTL globally
 * (NoorConnectTheme), so "Start" resolves to the visual right and "End" to the visual
 * left — that's deliberately what puts outgoing bubbles on the right here, matching how
 * every mainstream chat app (including Telegram itself, even in Arabic) keeps "my
 * messages" on the right regardless of text direction.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    senderName: String,
    senderUsername: String?,
    avatarPhotoState: PhotoDownloadState?,
    photoState: PhotoDownloadState,
    photoStates: Map<Int, PhotoDownloadState>,
    onDownloadPhoto: () -> Unit,
    onDownloadMedia: () -> Unit,
    onSaveMedia: (String, String) -> Unit,
    onOpenPrivateChat: (Long) -> Unit,
    onLongPress: () -> Unit = {},
) {
    val avatarColor = colorForId(message.senderId)
    val bubbleColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (message.isOutgoing) Arrangement.Start else Arrangement.End,
    ) {
        if (message.isOutgoing) {
            AvatarCircle(label = senderName, color = avatarColor, photoState = avatarPhotoState, onClick = {})
            Spacer(modifier = Modifier.size(6.dp))
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 280.dp).combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            ),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(senderName, color = avatarColor, style = MaterialTheme.typography.labelMedium)

                message.replyTo?.let { reply ->
                    Surface(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(text = reply.senderName ?: "إجابة", style = MaterialTheme.typography.labelSmall, color = avatarColor)
                            Text(text = reply.text.ifBlank { "محتوى" }, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }

                message.photo?.let { photo ->
                    Spacer(modifier = Modifier.size(4.dp))
                    MessagePhotoContent(photo = photo, state = photoState, onDownload = onDownloadPhoto)
                    (photoState as? PhotoDownloadState.Ready)?.let { ready ->
                        TextButton(onClick = { onSaveMedia(ready.localPath, "photo.jpg") }) {
                            Text("حفظ على الجهاز")
                        }
                    }
                    if (message.text.isNotBlank()) Spacer(modifier = Modifier.size(4.dp))
                }
                if (message.text.isNotBlank()) Text(message.text)
                if (message.mediaType != MessageMediaType.TEXT && message.mediaFileId != null && message.mediaMimeType != null) {
                    Spacer(modifier = Modifier.size(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = message.mediaName ?: when (message.mediaType) {
                                MessageMediaType.PHOTO -> "صورة"
                                MessageMediaType.VIDEO -> "فيديو"
                                MessageMediaType.AUDIO -> "مقطع صوتي"
                                MessageMediaType.VOICE -> "رسالة صوتية"
                                MessageMediaType.DOCUMENT -> "ملف"
                                else -> "وسائط"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    val filePath = (photoStates[message.mediaFileId] as? PhotoDownloadState.Ready)?.localPath
                    when (val state = photoStates[message.mediaFileId]) {
                        is PhotoDownloadState.Downloading -> {
                            Text(
                                state.progress?.let { "تحميل $it%" } ?: "جار التحميل...",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        else -> if (filePath == null) {
                            TextButton(onClick = onDownloadMedia) { Text("تحميل") }
                        } else {
                        TextButton(onClick = {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(filePath),
                            )
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                type = message.mediaMimeType ?: "application/octet-stream"
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = android.content.ClipData.newRawUri("media", uri)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: ActivityNotFoundException) {
                                Unit
                            }
                        }) { Text("فتح") }
                        TextButton(onClick = {
                            onSaveMedia(filePath, message.mediaName ?: "وسائط")
                        }) { Text("حفظ على الجهاز") }
                        }
                    }
                }
            }
        }

        if (!message.isOutgoing) {
            Spacer(modifier = Modifier.size(6.dp))
            Box {
                AvatarCircle(label = senderName, color = avatarColor, photoState = avatarPhotoState, onClick = { expanded = true })
                androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("مراسلة") },
                        leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onOpenPrivateChat(message.senderId)
                        },
                    )
                    if (!senderUsername.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("نسخ رابط الملف الشخصي") },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                            onClick = {
                                expanded = false
                                val profileUrl = "https://t.me/$senderUsername"
                                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("profile", profileUrl))
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Real profile photo when [photoState] is [PhotoDownloadState.Ready] (avatars auto-download —
 * see ChatViewModel's init block), colored initials otherwise: while it's still resolving,
 * on failure, or for the person's own messages (no self-lookup is done, see ChatViewModel).
 */
@Composable
private fun AvatarCircle(
    label: String,
    color: Color,
    photoState: PhotoDownloadState?,
    onClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(color = color, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val readyPath = (photoState as? PhotoDownloadState.Ready)?.localPath
        val bitmap = readyPath?.let { path -> remember(path) { BitmapFactory.decodeFile(path) } }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Text(
                text = label.trim().firstOrNull()?.uppercase() ?: "؟",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Same id -> deterministic color mapping every time, reused from the shared
 * [NoorColors.AvatarPalette] so the same sender looks the same everywhere they appear
 * (chat list rows use the same function — see feature:chats' ChatRow) — and so there's a
 * consistent fallback color behind a real avatar photo while it loads, or if it never
 * resolves.
 */
private fun colorForId(id: Long): Color =
    NoorColors.AvatarPalette[id.hashCode().absoluteValue % NoorColors.AvatarPalette.size]

/**
 * The tap-to-download rule lives entirely in ChatViewModel (its autoDownload flag); this
 * composable only ever renders whatever [PhotoDownloadState] it's given and, for
 * [PhotoDownloadState.NotDownloaded], calls [onDownload] on a tap — it never decides to
 * download on its own.
 */
@Composable
private fun MessagePhotoContent(photo: MessagePhoto, state: PhotoDownloadState, onDownload: () -> Unit) {
    val aspectRatio = if (photo.height > 0) photo.width.toFloat() / photo.height.toFloat() else 1f

    when (state) {
        is PhotoDownloadState.Ready -> {
            val bitmap = remember(state.localPath) { BitmapFactory.decodeFile(state.localPath) }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                PhotoPlaceholder(aspectRatio = aspectRatio, icon = Icons.Filled.ImageIcon, label = "تعذّر عرض الصورة")
            }
        }
        is PhotoDownloadState.Downloading -> Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                state.progress?.let { Text("تحميل $it%") }
            }
        }

        PhotoDownloadState.NotDownloaded -> PhotoPlaceholder(
            aspectRatio = aspectRatio,
            icon = Icons.Filled.Download,
            label = "اضغط لتحميل الصورة",
            onClick = onDownload,
        )

        PhotoDownloadState.Failed -> PhotoPlaceholder(
            aspectRatio = aspectRatio,
            icon = Icons.Filled.Download,
            label = "فشل التحميل — اضغط للمحاولة مرة أخرى",
            onClick = onDownload,
        )
    }
}

@Composable
private fun PhotoPlaceholder(
    aspectRatio: Float,
    icon: ImageVector,
    label: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReportChatDialog(
    reportState: ReportState,
    onSubmit: (ReportReason, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedReason by remember { mutableStateOf(ReportReason.UN_ISLAMIC_CONTENT) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الإبلاغ عن هذه المحادثة") },
        text = {
            when (reportState) {
                ReportState.Submitted -> Text("تم إرسال البلاغ، وسيتم مراجعة المحادثة قريبًا. جزاك الله خيرًا.")
                ReportState.Failed -> Text("تعذّر إرسال البلاغ، حاول مرة أخرى.")
                else -> Column {
                    // Compose Material3 API note: menuAnchor() is parameterless at this
                    // project's BOM (2024.06.00). Material3 1.3+ requires
                    // menuAnchor(MenuAnchorType.PrimaryNotEditable) instead — if this stops
                    // compiling after a BOM bump, that's why.
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                        OutlinedTextField(
                            value = selectedReason.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("سبب البلاغ") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                        ) {
                            ReportReason.entries.forEach { reason ->
                                DropdownMenuItem(
                                    text = { Text(reason.label) },
                                    onClick = { selectedReason = reason; expanded = false },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("تفاصيل إضافية (اختياري)") },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        },
        confirmButton = {
            when (reportState) {
                ReportState.Submitted, ReportState.Failed -> TextButton(onClick = onDismiss) { Text("تم") }
                ReportState.Submitting -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                ReportState.Idle -> TextButton(onClick = { onSubmit(selectedReason, details) }) { Text("إرسال البلاغ") }
            }
        },
        dismissButton = {
            if (reportState == ReportState.Idle) TextButton(onClick = onDismiss) { Text("إلغاء") }
        },
    )
}
