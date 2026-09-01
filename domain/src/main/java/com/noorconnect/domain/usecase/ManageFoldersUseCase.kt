package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.FolderRepository
import javax.inject.Inject

/**
 * One use case for all folder CRUD, not four separate classes — these are small, related
 * operations on the same aggregate (a person's set of folders), not independent business
 * rules. Split it up only if one of these grows real logic of its own.
 */
class ManageFoldersUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
) {
    suspend fun create(name: String): AppResult<Unit> = folderRepository.createFolder(name)
    suspend fun rename(folderId: String, newName: String): AppResult<Unit> = folderRepository.renameFolder(folderId, newName)
    suspend fun delete(folderId: String): AppResult<Unit> = folderRepository.deleteFolder(folderId)
    suspend fun setChatMembership(folderId: String, chatId: Long, isMember: Boolean): AppResult<Unit> =
        folderRepository.setChatInFolder(folderId, chatId, isMember)
}
