package com.noorconnect.core.tdlib

import com.noorconnect.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.noorconnect.domain.model.AuthState
import java.io.File
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
 *  - One TDLib Client is active at a time; switching account closes it and starts an isolated
 *    client against that account's own database and file directories.
 *  - If you ever swap TDLib for something else (or add a second backend, e.g. a plain
 *    Bot-API-only mode for a "lite" build), you rewrite this ONE class. Nothing above it moves.
 *  - Repositories (in :data) depend on this + on domain models, never on TdApi types directly.
 */
@Singleton
class TdLibManager @Inject constructor(
    private val config: TdLibConfig,
    private val accountStore: TdLibAccountStore,
) {
    // Own supervisor scope: TDLib callbacks arrive on TDLib's own thread, not a coroutine —
    // this scope is only used to bridge them into suspend-land safely.
    private val scope = CoroutineScope(SupervisorJob())

    @Volatile private var client: Client? = null
    private var started = false
    @Volatile private var generation = 0L
    @Volatile private var switchingAccount = false
    private var databaseDirectory = config.databaseDirectory
    private var filesDirectory = config.filesDirectory
    private val switchMutex = Mutex()

    private val _activeAccountId = MutableStateFlow(accountStore.activeAccountId.value)
    val activeAccountId: StateFlow<String> = _activeAccountId

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
        startForAccount(accountStore.activeAccountId.value)
    }

    suspend fun switchAccount(accountId: String): AppResult<Unit> = switchMutex.withLock {
        if (accountStore.accounts.value.none { it.id == accountId }) {
            return@withLock AppResult.Failure(404, "الحساب غير موجود")
        }
        if (_activeAccountId.value == accountId) return@withLock AppResult.Success(Unit)

        switchingAccount = true
        val currentClient = client
        val closeResult = if (currentClient == null) {
            AppResult.Success(Unit)
        } else {
            kotlinx.coroutines.suspendCancellableCoroutine<AppResult<Unit>> { continuation ->
                currentClient.send(TdApi.Close()) { result ->
                    val response = when (result) {
                        is TdApi.Error -> AppResult.Failure(result.code, result.message)
                        else -> AppResult.Success(Unit)
                    }
                    if (continuation.isActive) continuation.resume(response)
                }
            }
        }
        if (closeResult !is AppResult.Success) {
            switchingAccount = false
            return@withLock closeResult
        }

        synchronized(this@TdLibManager) {
            generation++
            client = null
            started = false
            _authState.value = AuthState.Uninitialized
            accountStore.activate(accountId)
            _activeAccountId.value = accountId
            startForAccount(accountId)
            switchingAccount = false
        }
        AppResult.Success(Unit)
    }

    private fun startForAccount(accountId: String) {
        val accountDatabaseDirectory = accountStore.sessionDirectory(accountId)
        val accountFilesDirectory = accountStore.sessionFilesDirectory(accountId)
        accountDatabaseDirectory.mkdirs()
        accountFilesDirectory.mkdirs()
        databaseDirectory = accountDatabaseDirectory.absolutePath
        filesDirectory = accountFilesDirectory.absolutePath
        started = true
        generation++
        val clientGeneration = generation
        client = Client.create(
            { obj -> handleIncoming(obj, clientGeneration) },
            null,
            { },
        )
    }

    private fun handleIncoming(obj: TdApi.Object, clientGeneration: Long) {
        if (clientGeneration != generation || switchingAccount) return
        if (obj is TdApi.UpdateAuthorizationState) {
            handleAuthorizationState(obj.authorizationState)
        }
        scope.launch {
            if (clientGeneration == generation && !switchingAccount) _updates.emit(obj)
        }
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
            databaseDirectory = this@TdLibManager.databaseDirectory
            filesDirectory = this@TdLibManager.filesDirectory
            useMessageDatabase = true
            useChatInfoDatabase = true
            useFileDatabase = true
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
            if (switchingAccount) {
                cont.resume(AppResult.Failure(-1, "جار تبديل الحساب"))
                return@suspendCoroutine
            }
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
