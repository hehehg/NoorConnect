package com.noorconnect.core.tdlib

import com.noorconnect.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.noorconnect.domain.model.AuthState
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * THE single choke point for the Telegram protocol in the whole app.
 *
 * Why this shape matters for extensibility:
 *  - No other module ever imports org.drinkless.tdlib.* — only this file does.
 *  - The TDLib Client is created ONCE (`start()`), not per-collector — every screen shares
 *    the same connection and the same auth state.
 *  - If you ever swap TDLib for something else (or add a second backend, e.g. a plain
 *    Bot-API-only mode for a "lite" build), you rewrite this ONE class. Nothing above it moves.
 *  - Repositories (in :data) depend on this + on domain models, never on TdApi types directly.
 */
@Singleton
class TdLibManager @Inject constructor(
    private val config: TdLibConfig,
) {
    // Own supervisor scope: TDLib callbacks arrive on TDLib's own thread, not a coroutine —
    // this scope is only used to bridge them into suspend-land safely.
    private val scope = CoroutineScope(SupervisorJob())

    private var client: Client? = null
    private var started = false

    private val _authState = MutableStateFlow<AuthState>(AuthState.Uninitialized)
    val authState: StateFlow<AuthState> = _authState

    // replay = 0: this is a live event bus, not a cache. Repositories that need history
    // (chats, messages) build their own running state from it — see ChatRepositoryImpl.
    private val _updates = MutableSharedFlow<TdApi.Object>(replay = 0, extraBufferCapacity = 64)
    val updates: SharedFlow<TdApi.Object> = _updates

    /** Call once, e.g. from App.onCreate() via Hilt's EntryPoint, or lazily on first use. */
    @Synchronized
    fun start() {
        if (started) return
        started = true
        client = Client.create(
            { obj -> handleIncoming(obj) },
            null,
            { },
        )
    }

    private fun handleIncoming(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) {
            handleAuthorizationState(obj.authorizationState)
        }
        scope.launch { _updates.emit(obj) }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendTdlibParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = AuthState.WaitingForPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> _authState.value = AuthState.WaitingForCode
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = AuthState.WaitingForPassword
            is TdApi.AuthorizationStateReady -> _authState.value = AuthState.Ready
            is TdApi.AuthorizationStateLoggingOut,
            is TdApi.AuthorizationStateClosing,
            is TdApi.AuthorizationStateClosed -> _authState.value = AuthState.LoggedOut
            else -> Unit
        }
    }

    private fun sendTdlibParameters() {
        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = config.databaseDirectory
            filesDirectory = config.filesDirectory
            useTestDc = config.useTestDc
            apiId = config.apiId
            apiHash = config.apiHash
            systemLanguageCode = "ar"
            deviceModel = "Android"
            applicationVersion = config.appVersion
        }
        client?.send(parameters) { result ->
            if (result is TdApi.Error) {
                _authState.value = AuthState.Error(result.message)
            }
        }
    }

    /** Generic suspend bridge: send any TdApi.Function, get its typed result back. */
    suspend fun <R : TdApi.Object> send(function: TdApi.Function<R>): AppResult<R> =
        suspendCoroutine { cont ->
            val current = client
            if (current == null) {
                cont.resume(AppResult.Failure(-1, "Client not started — call TdLibManager.start() first"))
                return@suspendCoroutine
            }
            current.send(function) { result ->
                @Suppress("UNCHECKED_CAST")
                when (result) {
                    is TdApi.Error -> cont.resume(AppResult.Failure(result.code, result.message))
                    else -> cont.resume(AppResult.Success(result as R))
                }
            }
        }

    suspend fun setPhoneNumber(phone: String): AppResult<Unit> =
        when (val r = send(TdApi.SetAuthenticationPhoneNumber(phone, null))) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> r
            is AppResult.Loading -> AppResult.Loading
        }

    suspend fun checkCode(code: String): AppResult<Unit> =
        when (val r = send(TdApi.CheckAuthenticationCode(code))) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> r
            is AppResult.Loading -> AppResult.Loading
        }

    suspend fun checkPassword(password: String): AppResult<Unit> =
        when (val r = send(TdApi.CheckAuthenticationPassword(password))) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> r
            is AppResult.Loading -> AppResult.Loading
        }
}
