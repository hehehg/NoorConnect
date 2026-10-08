package com.noorconnect.desktop

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.model.ModerationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WindowsDesktopTests {

    @Test
    fun `unverified channels stay blocked by default`() {
        val settings = ModerationSettings(allowUnverifiedChannels = false)
        val chat = Chat(
            id = 42L,
            title = "Weekly update",
            lastMessage = null,
            unreadCount = 0,
            isChannel = true,
            isGroup = false,
            moderationStatus = ChatModerationStatus.Unreviewed,
        )

        assertFalse(NoorConnectWindowsFilter.isAllowed(chat, settings))
    }

    @Test
    fun `keywords are normalized and match case-insensitively`() {
        val settings = ModerationSettings(
            allowUnverifiedChannels = true,
            blockedKeywords = setOf("كلمة محظورة", "spam"),
        )
        val chat = Chat(
            id = 7L,
            title = "SPAM team",
            lastMessage = null,
            unreadCount = 0,
            isChannel = false,
            isGroup = true,
            moderationStatus = ChatModerationStatus.Whitelisted,
        )

        assertFalse(NoorConnectWindowsFilter.isAllowed(chat, settings))
    }

    @Test
    fun `settings store saves and restores values`() {
        val tempFile = createTempFile("noorconnect-settings", ".json")
        val store = SettingsStore(tempFile.absolutePath)

        val original = ModerationSettings(
            allowUnverifiedChannels = true,
            allowGroups = false,
            blockedKeywords = setOf("ممنوع", "test"),
            notificationsEnabled = true,
            notificationPreview = false,
            sendByEnter = true,
        )

        store.save(original)
        val restored = store.load()

        assertEquals(original, restored)
        tempFile.delete()
    }
}
