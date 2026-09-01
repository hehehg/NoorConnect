package com.noorconnect.feature.chat

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.core.designsystem.NoorColors
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.MessagePhoto
import com.noorconnect.domain.model.ReportReason
import kotlin.math.absoluteValue

/** Public entry point for :app — reads chatId from the nav back stack via SavedStateHandle. */
@Composable
fun ChatRoute() {
    val viewModel: ChatViewModel = hiltViewModel()
    val accessState by viewModel.accessState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val senderNames by viewModel.senderNames.collectAsStateWithLifecycle()
    val senderPhotoFileIds by viewModel.senderPhotoFileIds.collectAsStateWithLifecycle()
    val chat by viewModel.chat.collectAsStateWithLifecycle()
    val photoStates by viewModel.photoStates.collectAsStateWithLifecycle()
    val reportState by viewModel.reportState.collectAsStateWithLifecycle()

    ChatScreen(
        title = chat?.title ?: "محادثة",
        accessState = accessState,
        messages = messages,
        senderNames = senderNames,
        senderPhotoFileIds = senderPhotoFileIds,
        photoStates = photoStates,
        reportState = reportState,
        onSend = viewModel::send,
        onDownloadPhoto = viewModel::downloadPhoto,
        onReport = viewModel::report,
        onDismissReportState = viewModel::dismissReportState,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatScreen(
    title: String,
    accessState: ChatAccessState,
    messages: List<Message>,
    senderNames: Map<Long, String>,
    senderPhotoFileIds: Map<Long, Int>,
    photoStates: Map<Int, PhotoDownloadState>,
    reportState: ReportState,
    onSend: (String) -> Unit,
    onDownloadPhoto: (Int) -> Unit,
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
                    senderPhotoFileIds = senderPhotoFileIds,
                    photoStates = photoStates,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onSend = { onSend(draft); draft = "" },
                    onDownloadPhoto = onDownloadPhoto,
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
private fun ChatContent(
    messages: List<Message>,
    senderNames: Map<Long, String>,
    senderPhotoFileIds: Map<Long, Int>,
    photoStates: Map<Int, PhotoDownloadState>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDownloadPhoto: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp)) {
            items(messages, key = { it.id }) { message ->
                val avatarFileId = if (message.isOutgoing) null else senderPhotoFileIds[message.senderId]
                MessageBubble(
                    message = message,
                    senderName = if (message.isOutgoing) "أنت" else senderNames[message.senderId] ?: "...",
                    avatarPhotoState = avatarFileId?.let { photoStates[it] },
                    photoState = message.photo?.let { photoStates[it.fileId] } ?: PhotoDownloadState.NotDownloaded,
                    onDownloadPhoto = { message.photo?.let { onDownloadPhoto(it.fileId) } },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("اكتب رسالة...") },
            )
            Button(onClick = onSend, modifier = Modifier.padding(start = 8.dp)) {
                Text("إرسال")
            }
        }
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
@Composable
private fun MessageBubble(
    message: Message,
    senderName: String,
    avatarPhotoState: PhotoDownloadState?,
    photoState: PhotoDownloadState,
    onDownloadPhoto: () -> Unit,
) {
    val avatarColor = colorForId(message.senderId)
    val bubbleColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (message.isOutgoing) Arrangement.Start else Arrangement.End,
    ) {
        if (message.isOutgoing) {
            AvatarCircle(label = senderName, color = avatarColor, photoState = avatarPhotoState)
            Spacer(modifier = Modifier.size(6.dp))
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    senderName,
                    color = avatarColor,
                    style = MaterialTheme.typography.labelMedium,
                )
                message.photo?.let { photo ->
                    Spacer(modifier = Modifier.size(4.dp))
                    MessagePhotoContent(photo = photo, state = photoState, onDownload = onDownloadPhoto)
                    if (message.text.isNotBlank()) Spacer(modifier = Modifier.size(4.dp))
                }
                if (message.text.isNotBlank()) Text(message.text)
            }
        }

        if (!message.isOutgoing) {
            Spacer(modifier = Modifier.size(6.dp))
            AvatarCircle(label = senderName, color = avatarColor, photoState = avatarPhotoState)
        }
    }
}

/**
 * Real profile photo when [photoState] is [PhotoDownloadState.Ready] (avatars auto-download —
 * see ChatViewModel's init block), colored initials otherwise: while it's still resolving,
 * on failure, or for the person's own messages (no self-lookup is done, see ChatViewModel).
 */
@Composable
private fun AvatarCircle(label: String, color: Color, photoState: PhotoDownloadState?) {
    Box(
        modifier = Modifier.size(36.dp).background(color = color, shape = CircleShape),
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
        PhotoDownloadState.Downloading -> Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

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
