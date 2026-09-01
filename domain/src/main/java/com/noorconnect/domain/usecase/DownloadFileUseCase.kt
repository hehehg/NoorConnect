package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.RemoteFile
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

/** Backs the explicit "tap to download" action on a channel/group photo, and the automatic
 *  one-shot download for a DM photo (feature:chat decides which case it is via [GetChatByIdUseCase]). */
class DownloadFileUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(fileId: Int): AppResult<RemoteFile> = chatRepository.downloadFile(fileId)
}
