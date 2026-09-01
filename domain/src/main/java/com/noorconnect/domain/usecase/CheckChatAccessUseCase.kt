package com.noorconnect.domain.usecase

import com.noorconnect.core.common.AppResult
import com.noorconnect.domain.model.ChannelAudience
import com.noorconnect.domain.model.ChatModerationStatus
import com.noorconnect.domain.model.Gender
import com.noorconnect.domain.repository.ChatModerationRepository
import com.noorconnect.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * The gate every chat passes through before its message list is ever shown — see the ONLY
 * caller, ChatViewModel, which checks this before rendering anything from GetMessagesUseCase.
 * Runs at chat-open time (not chat-list time): the list itself shows every chat TDLib knows
 * about, and this decides whether tapping into one actually opens it.
 */
class CheckChatAccessUseCase @Inject constructor(
    private val moderationRepository: ChatModerationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    sealed class Result {
        data object Allowed : Result()
        data class Denied(val reason: String) : Result()
    }

    suspend operator fun invoke(chatId: Long): Result {
        val record = when (val result = moderationRepository.getChannelRecord(chatId)) {
            is AppResult.Success -> result.data
            else -> return Result.Denied("تعذر التحقق من حالة المحادثة الآن، حاول لاحقًا")
        }

        return when (val status = record.status) {
            is ChatModerationStatus.Blacklisted ->
                Result.Denied(status.reason ?: "هذه المحادثة محظورة")

            is ChatModerationStatus.PendingReview ->
                Result.Denied(status.reason ?: "هذه المحادثة قيد المراجعة حاليًا")

            ChatModerationStatus.Unreviewed -> {
                moderationRepository.flagForReview(chatId, "لم تتم مراجعتها بعد")
                Result.Denied("هذه المحادثة قيد المراجعة حاليًا")
            }

            ChatModerationStatus.Whitelisted -> checkAudience(record.audience)
        }
    }

    private suspend fun checkAudience(audience: ChannelAudience): Result {
        if (audience == ChannelAudience.BOTH) return Result.Allowed
        val gender = userPreferencesRepository.observeOnboardingState().first().gender
        val matches = (audience == ChannelAudience.MALE && gender == Gender.MALE) ||
            (audience == ChannelAudience.FEMALE && gender == Gender.FEMALE)
        return if (matches) Result.Allowed else Result.Denied("هذه المحادثة غير مناسبة لتصنيفك")
    }
}
