package com.noorconnect.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.ModerationSettings
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NoorConnect",
        state = rememberWindowState(width = 1280.dp, height = 820.dp),
    ) {
        NoorConnectDesktopApp()
    }
}

@Composable
fun NoorConnectDesktopApp() {
    val store = remember { SettingsStore() }
    var settings by remember { mutableStateOf(store.load()) }
    val chats = remember {
        listOf(
            Chat(
                id = 1L,
                title = "Quran Circle",
                lastMessage = Message(
                    id = 100L,
                    senderName = "Aisha",
                    text = "New session is live.",
                    timestamp = Instant.now().epochSecond,
                ),
                unreadCount = 3,
                isChannel = true,
                isGroup = false,
                moderationStatus = ChatModerationStatus.Unreviewed,
            ),
            Chat(
                id = 2L,
                title = "Community Admin",
                lastMessage = Message(
                    id = 101L,
                    senderName = "Support",
                    text = "Approved by the team.",
                    timestamp = Instant.now().epochSecond,
                ),
                unreadCount = 0,
                isChannel = false,
                isGroup = true,
                moderationStatus = ChatModerationStatus.Whitelisted,
            ),
            Chat(
                id = 3L,
                title = "SPAM team",
                lastMessage = Message(
                    id = 102L,
                    senderName = "Bot",
                    text = "Daily reminder.",
                    timestamp = Instant.now().epochSecond,
                ),
                unreadCount = 1,
                isChannel = false,
                isGroup = true,
                moderationStatus = ChatModerationStatus.Unreviewed,
            ),
        )
    }

    val visibleChats = chats.filter { NoorConnectWindowsFilter.isAllowed(it, settings) }
    val allChatsCount = chats.size
    val visibleCount = visibleChats.size

    MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                Sidebar(modifier = Modifier.width(220.dp))
                Column(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    HeaderCard(title = "NoorConnect desktop", subtitle = "$visibleCount of $allChatsCount chats accessible")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatCard(title = "Allowed", value = visibleCount.toString(), accent = Color(0xFF2E7D32), modifier = Modifier.weight(1f))
                        StatCard(title = "Blocked", value = (allChatsCount - visibleCount).toString(), accent = Color(0xFFD32F2F), modifier = Modifier.weight(1f))
                        StatCard(title = "Settings", value = if (settings.notificationsEnabled) "Enabled" else "Muted", accent = Color(0xFF1976D2), modifier = Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF7F9FC), RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Chats", style = MaterialTheme.typography.titleMedium)
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                visibleChats.forEach { chat ->
                                    ChatRow(chat = chat)
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f).fillMaxHeight().background(Color(0xFFF7F9FC), RoundedCornerShape(16.dp)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Moderation settings", style = MaterialTheme.typography.titleMedium)
                            SettingsEditor(settings = settings, onChanged = {
                                settings = it
                                store.save(it)
                            })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Sidebar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxHeight().background(Color(0xFF111827)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("NoorConnect", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Divider(color = Color(0xFF374151))
        SidebarItem("Overview")
        SidebarItem("Messages")
        SidebarItem("Moderation")
        SidebarItem("Settings")
        Spacer(modifier = Modifier.weight(1f))
        SidebarItem("Status: ready")
    }
}

@Composable
private fun SidebarItem(label: String) {
    Box(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF1F2937), RoundedCornerShape(10.dp)).padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun HeaderCard(title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEEF2FF))) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, color = accent)
        }
    }
}

@Composable
private fun ChatRow(chat: Chat) {
    val isAllowed = NoorConnectWindowsFilter.isAllowed(chat, ModerationSettings())
    val status = if (isAllowed) "Allowed" else "Blocked"
    val color = if (isAllowed) Color(0xFF2E7D32) else Color(0xFFD32F2F)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(chat.title, style = MaterialTheme.typography.titleMedium)
                Text(chat.lastMessage?.text ?: "No messages yet")
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(status, color = color)
                if (chat.unreadCount > 0) Text("${chat.unreadCount} unread")
            }
        }
    }
}

@Composable
private fun SettingsEditor(settings: ModerationSettings, onChanged: (ModerationSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToggleRow(
            label = "Allow unverified channels",
            checked = settings.allowUnverifiedChannels,
            onChanged = { onChanged(settings.copy(allowUnverifiedChannels = it)) },
        )
        ToggleRow(
            label = "Allow groups",
            checked = settings.allowGroups,
            onChanged = { onChanged(settings.copy(allowGroups = it)) },
        )
        ToggleRow(
            label = "Desktop notifications",
            checked = settings.notificationsEnabled,
            onChanged = { onChanged(settings.copy(notificationsEnabled = it)) },
        )
        OutlinedTextField(
            value = settings.blockedKeywords.joinToString(", "),
            onValueChange = { input ->
                val words = input.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                onChanged(settings.copy(blockedKeywords = words))
            },
            label = { Text("Blocked keywords") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Serializable
private data class DesktopMessagePayload(
    val title: String,
    val text: String,
    val timestamp: String,
)
