package com.noorconnect.data.mapper

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.MessagePhoto
import org.drinkless.tdlib.TdApi

/**
 * The ONLY place TdApi.Chat/TdApi.Message get turned into our own model.
 * If TDLib's shape changes, this is the one file that needs to change.
 */
fun TdApi.Chat.toDomain(): Chat = Chat(
    id = id,
    title = title,
    lastMessage = lastMessage?.toDomain(),
    unreadCount = unreadCount,
    isChannel = type is TdApi.ChatTypeSupergroup && (type as TdApi.ChatTypeSupergroup).isChannel,
    isGroup = type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup,
    // photo.small is the low-res version TDLib expects list/avatar UI to use; .big is only for
    // an opened profile view. Deliberately just the file id here, not a download call — see
    // Chat.photoFileId's kdoc for why that belongs in the UI/data layer that owns downloads.
    photoFileId = photo?.small?.id,
    // moderationStatus is intentionally NOT set from TdApi data — it doesn't come from Telegram
    // at all, it comes from our own Firestore backend. Leaving it at Chat's default
    // (Unreviewed) here is correct: GetChatsUseCase overwrites it with the real live value on
    // every emission, so a raw TdApi.Chat.toDomain() is never shown to a screen unfiltered.
)

fun TdApi.Message.toDomain(): Message {
    val messageContent = content
    return Message(
        id = id,
        chatId = chatId,
        senderId = (senderId as? TdApi.MessageSenderUser)?.userId ?: 0L,
        text = when (messageContent) {
            is TdApi.MessageText -> messageContent.text.text
            is TdApi.MessagePhoto -> messageContent.caption?.text.orEmpty()
            else -> ""
        },
        timestamp = date.toLong(),
        isOutgoing = isOutgoing,
        // Largest available size — TDLib returns PhotoSize entries smallest-first, so the last
        // one is the highest resolution TDLib knows about for this photo.
        photo = (messageContent as? TdApi.MessagePhoto)?.photo?.sizes
            ?.filterNotNull()
            ?.maxByOrNull { it.width }
            ?.let { size -> MessagePhoto(fileId = size.photo.id, width = size.width, height = size.height) },
    )
}
