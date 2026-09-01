package com.noorconnect.domain.usecase

import com.noorconnect.domain.model.Chat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Deliberately built on top of [GetChatsUseCase] rather than [com.noorconnect.domain.repository.ChatRepository]
 * directly — that's what guarantees this returns the exact same masked/filtered [Chat] (photo
 * stripped, moderation status attached) the chat list itself would show, with no separate copy
 * of that logic to keep in sync. A chat access-denied by [CheckChatAccessUseCase] already stops
 * ChatScreen before this matters, so the only thing feature:chat needs this for is
 * [Chat.isChannel]/[Chat.isGroup] — to decide whether a photo message auto-downloads (DM) or
 * shows a tap-to-download placeholder (channel/group).
 */
class GetChatByIdUseCase @Inject constructor(
    private val getChats: GetChatsUseCase,
) {
    operator fun invoke(chatId: Long): Flow<Chat?> = getChats().map { chats -> chats.find { it.id == chatId } }
}
