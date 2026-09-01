package com.noorconnect.domain.usecase

import com.noorconnect.domain.model.ChatFolder
import com.noorconnect.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveFoldersUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
) {
    operator fun invoke(): Flow<List<ChatFolder>> = folderRepository.observeFolders()
}
