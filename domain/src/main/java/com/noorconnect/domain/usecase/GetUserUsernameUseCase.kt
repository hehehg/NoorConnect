package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.repository.UserRepository
import javax.inject.Inject

class GetUserUsernameUseCase @Inject constructor(
    private val userRepository: UserRepository,
) {
    suspend operator fun invoke(userId: Long): AppResult<String?> = userRepository.getUsername(userId)
}
