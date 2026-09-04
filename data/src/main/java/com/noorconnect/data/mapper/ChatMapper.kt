package com.noorconnect.data.mapper

import com.noorconnect.domain.model.Chat
import com.noorconnect.domain.model.Message
import com.noorconnect.domain.model.MessagePhoto
import org.drinkless.tdlib.TdApi

/**
 * The ONLY place TdApi.Chat/TdApi.Message get turned into our own model.
 * If TDLib's shape changes, this is the one file that needs to change.
 */
fun TdApi.Chat.toDomain(): Chat {
    val positions = positions?.filterNotNull().orEmpty()
    val archived = positions.isInArchiveList()
    val active = positions.activeChatPosition(archived)
    return Chat(
    id = id,
    title = title,
    lastMessage = lastMessage?.toDomain(),
    unreadCount = unreadCount,
    isChannel = type is TdApi.ChatTypeSupergroup && (type as TdApi.ChatTypeSupergroup).isChannel,
    isGroup = type is TdApi.ChatTypeBasicGroup ||
        (type is TdApi.ChatTypeSupergroup && !(type as TdApi.ChatTypeSupergroup).isChannel),
    // photo.small is the low-res version TDLib expects list/avatar UI to use; .big is only for
    // an opened profile view. Deliberately just the file id here, not a download call — see
    // Chat.photoFileId's kdoc for why that belongs in the UI/data layer that owns downloads.
    photoFileId = photo?.small?.id,
    isPinned = active?.isPinned ?: false,
    isArchived = archived,
    order = active?.order ?: 0L,
    isMember = positions.isNotEmpty(),
    // moderationStatus is intentionally NOT set from TdApi data — it doesn't come from Telegram
    // at all, it comes from our own Firestore backend. Leaving it at Chat's default
    // (Unreviewed) here is correct: GetChatsUseCase overwrites it with the real live value on
    // every emission, so a raw TdApi.Chat.toDomain() is never shown to a screen unfiltered.
    )
}

fun List<TdApi.ChatPosition>.isInArchiveList(): Boolean =
    any { it.list is TdApi.ChatListArchive && it.order != 0L }

fun List<TdApi.ChatPosition>.activeChatPosition(archived: Boolean = isInArchiveList()): TdApi.ChatPosition? {
    val target = if (archived) firstOrNull { it.list is TdApi.ChatListArchive }
    else firstOrNull { it.list is TdApi.ChatListMain }
    return target ?: firstOrNull()
}

fun TdApi.Message.toDomain(): Message {
    val messageContent = content
    val text = when (messageContent) {
        is TdApi.MessageText -> messageContent.text.text
        is TdApi.MessagePhoto -> messageContent.caption?.text.orEmpty()
        is TdApi.MessageVideo -> messageContent.caption?.text.orEmpty()
        is TdApi.MessageAudio -> messageContent.caption?.text.orEmpty()
        is TdApi.MessageVoiceNote -> messageContent.caption?.text.orEmpty()
        is TdApi.MessageDocument -> messageContent.caption?.text.orEmpty()
        else -> ""
    }

    val replyText = when (val replyTo = replyTo) {
        is TdApi.MessageReplyToMessage -> when (replyTo.content) {
            is TdApi.MessageText -> replyTo.content.text.text
            is TdApi.MessagePhoto -> replyTo.content.caption?.text.orEmpty()
            is TdApi.MessageVideo -> replyTo.content.caption?.text.orEmpty()
            is TdApi.MessageAudio -> replyTo.content.caption?.text.orEmpty()
            is TdApi.MessageVoiceNote -> replyTo.content.caption?.text.orEmpty()
            is TdApi.MessageDocument -> replyTo.content.caption?.text.orEmpty()
            else -> ""
        }
        else -> null
    }

    val mediaType = when (messageContent) {
        is TdApi.MessagePhoto -> MessageMediaType.PHOTO
        is TdApi.MessageVideo -> MessageMediaType.VIDEO
        is TdApi.MessageAudio -> MessageMediaType.AUDIO
        is TdApi.MessageVoiceNote -> MessageMediaType.VOICE
        is TdApi.MessageDocument -> MessageMediaType.DOCUMENT
        else -> MessageMediaType.TEXT
    }

    return Message(
        id = id,
        chatId = chatId,
        senderId = (senderId as? TdApi.MessageSenderUser)?.userId ?: 0L,
        text = text,
        timestamp = date.toLong(),
        isOutgoing = isOutgoing,
        replyTo = replyText?.takeIf { it.isNotBlank() }?.let { MessageReplyPreview(text = it) },
        mediaType = mediaType,
        mediaFileId = when (messageContent) {
            is TdApi.MessagePhoto -> messageContent.photo?.sizes
                ?.filterNotNull()
                ?.maxByOrNull { it.width }
                ?.photo?.id
            is TdApi.MessageVideo -> messageContent.video?.video?.id
            is TdApi.MessageAudio -> messageContent.audio?.audio?.id
            is TdApi.MessageVoiceNote -> messageContent.voiceNote?.voice?.id
            is TdApi.MessageDocument -> messageContent.document?.document?.id
            else -> null
        },
        mediaMimeType = when (messageContent) {
            is TdApi.MessageVideo -> messageContent.video?.mimeType
            is TdApi.MessageAudio -> messageContent.audio?.mimeType
            is TdApi.MessageVoiceNote -> messageContent.voiceNote?.mimeType
            is TdApi.MessageDocument -> messageContent.document?.mimeType
            else -> null
        },
        mediaName = when (messageContent) {
            is TdApi.MessageDocument -> messageContent.document?.fileName
            is TdApi.MessageAudio -> messageContent.audio?.fileName
            else -> null
        },
        // Largest available size — TDLib returns PhotoSize entries smallest-first, so the last
        // one is the highest resolution TDLib knows about for this photo.
        photo = (messageContent as? TdApi.MessagePhoto)?.photo?.sizes
            ?.filterNotNull()
            ?.maxByOrNull { it.width }
            ?.let { size -> MessagePhoto(fileId = size.photo.id, width = size.width, height = size.height) },
    )
}
