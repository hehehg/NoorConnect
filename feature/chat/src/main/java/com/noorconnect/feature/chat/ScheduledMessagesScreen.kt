package com.noorconnect.feature.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ScheduledChatMessage
import com.noorconnect.domain.usecase.GetAllScheduledMessagesUseCase
import com.noorconnect.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ScheduledMessageKey(val chatId: Long, val messageId: Long)

@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val getAllScheduledMessages: GetAllScheduledMessagesUseCase,
    private val sendMessage: SendMessageUseCase,
) : ViewModel() {
    private val _messages = MutableStateFlow<List<ScheduledChatMessage>>(emptyList())
    val messages: StateFlow<List<ScheduledChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _workingMessageId = MutableStateFlow<Long?>(null)
    val workingMessageId: StateFlow<Long?> = _workingMessageId

    private val _editCompletedKey = MutableStateFlow<ScheduledMessageKey?>(null)
    val editCompletedKey: StateFlow<ScheduledMessageKey?> = _editCompletedKey

    init {
        refresh()
    }

    fun refresh() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _messages.value = getAllScheduledMessages()
            } catch (exception: Exception) {
                _error.value = exception.message ?: "تعذر تحميل الرسائل المجدولة"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendNow(item: ScheduledChatMessage) = perform(
        item = item,
        action = { sendMessage.sendScheduledNow(item.chatId, item.message.id) },
    )

    fun delete(item: ScheduledChatMessage) = perform(
        item = item,
        action = { sendMessage.delete(item.chatId, item.message.id) },
    )

    fun edit(item: ScheduledChatMessage, text: String) {
        _editCompletedKey.value = null
        perform(
            item = item,
            action = { sendMessage.edit(item.chatId, item.message.id, text) },
            onSuccess = { _editCompletedKey.value = ScheduledMessageKey(item.chatId, item.message.id) },
        )
    }

    private fun perform(
        item: ScheduledChatMessage,
        action: suspend () -> AppResult<Unit>,
        onSuccess: () -> Unit = {},
    ) {
        if (_workingMessageId.value != null) return
        viewModelScope.launch {
            _workingMessageId.value = item.message.id
            _error.value = null
            try {
                when (val result = action()) {
                    is AppResult.Success -> {
                        _messages.value = getAllScheduledMessages()
                        onSuccess()
                    }
                    is AppResult.Failure -> _error.value = result.message
                    is AppResult.Loading -> _error.value = "جار تنفيذ العملية"
                }
            } catch (exception: Exception) {
                _error.value = exception.message ?: "تعذر تنفيذ العملية"
            } finally {
                _workingMessageId.value = null
            }
        }
    }
}

@Composable
fun ScheduledMessagesRoute(
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    viewModel: ScheduledMessagesViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val workingMessageId by viewModel.workingMessageId.collectAsStateWithLifecycle()
    val editCompletedKey by viewModel.editCompletedKey.collectAsStateWithLifecycle()

    ScheduledMessagesScreen(
        messages = messages,
        isLoading = isLoading,
        error = error,
        workingMessageId = workingMessageId,
        editCompletedKey = editCompletedKey,
        onBack = onBack,
        onOpenChat = onOpenChat,
        onRefresh = viewModel::refresh,
        onSendNow = viewModel::sendNow,
        onDelete = viewModel::delete,
        onEdit = viewModel::edit,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScheduledMessagesScreen(
    messages: List<ScheduledChatMessage>,
    isLoading: Boolean,
    error: String?,
    workingMessageId: Long?,
    editCompletedKey: ScheduledMessageKey?,
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onRefresh: () -> Unit,
    onSendNow: (ScheduledChatMessage) -> Unit,
    onDelete: (ScheduledChatMessage) -> Unit,
    onEdit: (ScheduledChatMessage, String) -> Unit,
) {
    var editing by remember { mutableStateOf<ScheduledChatMessage?>(null) }

    androidx.compose.runtime.LaunchedEffect(editCompletedKey) {
        val edited = editing
        if (edited != null && editCompletedKey == ScheduledMessageKey(edited.chatId, edited.message.id)) {
            editing = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الرسائل المجدولة") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "تحديث الرسائل المجدولة")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    textAlign = TextAlign.Center,
                )
            }
            when {
                isLoading && messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                messages.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("لا توجد رسائل مجدولة", textAlign = TextAlign.Center)
                }
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(messages, key = { "${it.chatId}:${it.message.id}" }) { item ->
                        val messageText = item.message.text.ifBlank {
                            item.message.mediaName ?: "رسالة مجدولة"
                        }
                        ListItem(
                            headlineContent = { Text(messageText, maxLines = 2) },
                            supportingContent = {
                                Column {
                                    Text(item.chatTitle, style = MaterialTheme.typography.labelMedium)
                                    Text(formatScheduledTime(item.message.scheduledAt ?: item.message.timestamp))
                                }
                            },
                            trailingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                    IconButton(
                                        onClick = { onSendNow(item) },
                                        enabled = workingMessageId == null,
                                    ) {
                                        Icon(Icons.Filled.Send, contentDescription = "إرسال الآن")
                                    }
                                    IconButton(
                                        onClick = { editing = item },
                                        enabled = workingMessageId == null,
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = "تعديل الرسالة")
                                    }
                                    IconButton(
                                        onClick = { onDelete(item) },
                                        enabled = workingMessageId == null,
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "حذف الرسالة")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { onOpenChat(item.chatId) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { item ->
        var text by remember(item.chatId, item.message.id) { mutableStateOf(item.message.text) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("تعديل الرسالة المجدولة") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEdit(item, text)
                    },
                    enabled = text.isNotBlank() && workingMessageId == null,
                ) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("إلغاء") } },
        )
    }
}

private fun formatScheduledTime(timestampSeconds: Long): String =
    "موعد الإرسال: ${SimpleDateFormat("EEEE، d MMMM yyyy • HH:mm", Locale("ar")).format(Date(timestampSeconds * 1000L))}"