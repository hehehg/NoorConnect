package com.noorconnect.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.ModerationSettings

@Composable
fun TelegramDesktopRoot() {
    val credentialsStore = remember { TelegramApiCredentialsStore() }
    var session by remember { mutableStateOf(TelegramDesktopSession()) }
    val authState by session.authState.collectAsState()
    val error by session.error.collectAsState()
    val settingsStore = remember { SettingsStore() }
    var settings by remember { mutableStateOf(settingsStore.load()) }

    LaunchedEffect(Unit) {
        credentialsStore.load()?.let(session::start)
    }
    DisposableEffect(session) {
        onDispose { session.close() }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (val state = authState) {
                TelegramDesktopAuthState.NeedsApiCredentials -> ApiCredentialsScreen(
                    initialCredentials = credentialsStore.load(),
                    error = error,
                    onConnect = { credentials ->
                        credentialsStore.save(credentials)
                        session.close()
                        session = TelegramDesktopSession().also { it.start(credentials) }
                    },
                )
                TelegramDesktopAuthState.Initializing -> CenteredMessage("جار الاتصال بـTelegram…")
                TelegramDesktopAuthState.WaitingForPhone -> AuthInputScreen(
                    title = "تسجيل الدخول إلى Telegram",
                    label = "رقم الهاتف مع مفتاح الدولة",
                    error = error,
                    onSubmit = session::submitPhone,
                )
                TelegramDesktopAuthState.WaitingForCode -> AuthInputScreen(
                    title = "أدخل كود Telegram",
                    label = "كود التحقق",
                    error = error,
                    onSubmit = session::submitCode,
                )
                TelegramDesktopAuthState.WaitingForPassword -> AuthInputScreen(
                    title = "التحقق بخطوتين",
                    label = "كلمة المرور",
                    error = error,
                    onSubmit = session::submitPassword,
                    secret = true,
                )
                is TelegramDesktopAuthState.Ready -> TelegramWorkspace(
                    session = session,
                    displayName = state.displayName,
                    settings = settings,
                    onSettingsChanged = {
                        settings = it
                        settingsStore.save(it)
                    },
                    error = error,
                )
                is TelegramDesktopAuthState.Failed -> ApiCredentialsScreen(
                    initialCredentials = credentialsStore.load(),
                    error = error ?: state.message,
                    onConnect = { credentials ->
                        credentialsStore.save(credentials)
                        session.close()
                        session = TelegramDesktopSession().also { it.start(credentials) }
                    },
                )
            }
        }
    }
}

@Composable
private fun ApiCredentialsScreen(
    initialCredentials: TelegramApiCredentials?,
    error: String?,
    onConnect: (TelegramApiCredentials) -> Unit,
) {
    var apiId by remember(initialCredentials) { mutableStateOf(initialCredentials?.apiId?.toString().orEmpty()) }
    var apiHash by remember(initialCredentials) { mutableStateOf(initialCredentials?.apiHash.orEmpty()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    CenteredForm(title = "إعداد Telegram") {
        Text("أدخل بيانات تطبيق Telegram API. تحصل عليها من my.telegram.org/apps.")
        OutlinedTextField(apiId, { apiId = it }, label = { Text("API ID") }, singleLine = true)
        OutlinedTextField(apiHash, { apiHash = it }, label = { Text("API hash") }, singleLine = true)
        Button(onClick = {
            val parsedId = apiId.toIntOrNull()
            if (parsedId == null || parsedId <= 0 || apiHash.isBlank()) {
                validationError = "أدخل API ID صحيحًا وAPI hash"
            } else {
                validationError = null
                onConnect(TelegramApiCredentials(parsedId, apiHash.trim()))
            }
        }) {
            Text("متابعة")
        }
        ErrorText(validationError ?: error)
    }
}

@Composable
private fun AuthInputScreen(
    title: String,
    label: String,
    error: String?,
    onSubmit: (String) -> Unit,
    secret: Boolean = false,
) {
    var value by remember(title) { mutableStateOf("") }
    CenteredForm(title) {
        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            label = { Text(label) },
            singleLine = true,
        )
        Button(onClick = { onSubmit(value) }, enabled = value.isNotBlank()) {
            Text(if (secret) "تأكيد" else "متابعة")
        }
        ErrorText(error)
    }
}

@Composable
private fun CenteredForm(title: String, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.width(440.dp), shape = RoundedCornerShape(8.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    content()
                },
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun TelegramWorkspace(
    session: TelegramDesktopSession,
    displayName: String,
    settings: ModerationSettings,
    onSettingsChanged: (ModerationSettings) -> Unit,
    error: String?,
) {
    val chats by session.chats.collectAsState()
    val messagesByChat by session.messagesByChat.collectAsState()
    val visibleChats = chats.filter { NoorConnectWindowsFilter.isAllowed(it, settings) }
    var selectedChatId by remember { mutableStateOf<Long?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(visibleChats) {
        if (selectedChatId == null && visibleChats.isNotEmpty()) {
            selectedChatId = visibleChats.first().id
            session.openChat(visibleChats.first().id)
        }
    }

    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().background(Color(0xFFF4F6F8), RoundedCornerShape(8.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Telegram", style = MaterialTheme.typography.titleLarge)
            Text("$displayName · ${visibleChats.size} محادثة")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                visibleChats.forEach { chat ->
                    ChatListItem(chat, selected = chat.id == selectedChatId) {
                        selectedChatId = chat.id
                        session.openChat(chat.id)
                    }
                }
                if (chats.isNotEmpty() && visibleChats.isEmpty()) {
                    Text("كل المحادثات مخفية حسب إعدادات الفلترة الحالية.")
                } else if (chats.isEmpty()) {
                    Text("جار تحميل المحادثات…")
                }
            }
            OutlinedButton(onClick = { showSettings = !showSettings }) {
                Text(if (showSettings) "إخفاء الإعدادات" else "إعدادات الفلترة")
            }
        }

        val selectedChat = chats.firstOrNull { it.id == selectedChatId }
        MessagePane(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            chat = selectedChat,
            messages = messagesByChat[selectedChatId].orEmpty(),
            error = error,
            onSend = { text -> selectedChatId?.let { session.sendMessage(it, text) } },
        )

        if (showSettings) {
            Column(
                Modifier.width(300.dp).fillMaxHeight().background(Color(0xFFF4F6F8), RoundedCornerShape(8.dp)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("إعدادات الفلترة", style = MaterialTheme.typography.titleMedium)
                SettingsEditor(settings, onSettingsChanged)
            }
        }
    }
}

@Composable
private fun ChatListItem(chat: Chat, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) Color(0xFFDCE8F7) else Color.White
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(6.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chat.title, style = MaterialTheme.typography.titleSmall)
                if (chat.unreadCount > 0) Text(chat.unreadCount.toString())
            }
            Text(chat.lastMessage?.text ?: "", maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessagePane(
    modifier: Modifier,
    chat: Chat?,
    messages: List<Message>,
    error: String?,
    onSend: (String) -> Unit,
) {
    var draft by remember(chat?.id) { mutableStateOf("") }
    Column(
        modifier.background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(chat?.title ?: "اختر محادثة", style = MaterialTheme.typography.titleLarge)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            messages.forEach { message ->
                MessageItem(message)
            }
        }
        ErrorText(error)
        if (chat != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("اكتب رسالة") },
                    singleLine = true,
                )
                Button(onClick = {
                    onSend(draft)
                    draft = ""
                }, enabled = draft.isNotBlank()) {
                    Text("إرسال")
                }
            }
        }
    }
}

@Composable
private fun MessageItem(message: Message) {
    val alignment = if (message.senderName == "You") Alignment.CenterEnd else Alignment.CenterStart
    Box(Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Card(colors = CardDefaults.cardColors(containerColor = if (message.senderName == "You") Color(0xFFDCEFE2) else Color.White)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(message.senderName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(message.text)
            }
        }
    }
}

@Composable
private fun ErrorText(error: String?) {
    if (!error.isNullOrBlank()) {
        Text(error, color = MaterialTheme.colorScheme.error)
    }
}