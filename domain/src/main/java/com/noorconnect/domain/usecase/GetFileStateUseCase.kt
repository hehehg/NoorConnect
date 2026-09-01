package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.RemoteFile
import com.noorconnect.domain.repository.ChatRepository
import javax.inject.Inject

/** Non-downloading check — feature:chat calls this first for every photo to decide whether it
 *  already has local bytes to show, before ever calling [DownloadFileUseCase]. */
class GetFileStateUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {
    suspend operator fun invoke(fileId: Int): AppResult<RemoteFile> = chatRepository.getFile(fileId)
}
