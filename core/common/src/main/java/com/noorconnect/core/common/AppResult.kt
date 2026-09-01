package com.noorconnect.core.common

/**
 * Every repository/use-case returns this instead of throwing or returning raw TDLib objects.
 * Keeping this in :core:common means feature modules never need to know TDLib exists.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(val code: Int, val message: String) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (Int, String) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(code, message)
    return this
}
