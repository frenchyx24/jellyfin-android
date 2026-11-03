package org.jellyfin.mobile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.ApiClientController
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.mobile.nflix.NflixLogin
import org.jellyfin.mobile.nflix.NflixSession
import java.util.UUID

class MainViewModel(
    app: Application,
    private val apiClientController: ApiClientController,
    private val nflixSession: NflixSession,
) : AndroidViewModel(app) {
    private val _serverState: MutableStateFlow<ServerState> = MutableStateFlow(ServerState.Pending)
    val serverState: StateFlow<ServerState> get() = _serverState

    private val _userState: MutableStateFlow<UserState> = MutableStateFlow(UserState.Pending)
    val userState: StateFlow<UserState> get() = _userState

    private val _miraLoginRequests = MutableStateFlow(false)

    /** Set when Mira Connect opened Nflix asking for an immediate Mira sign-in. */
    val miraLoginRequests: StateFlow<Boolean> get() = _miraLoginRequests

    init {
        viewModelScope.launch {
            ensureNflixServer()
            refreshServer()
            refreshUser()
        }
    }

    /** Nflix only talks to its own server: make sure it is the selected one. */
    private suspend fun ensureNflixServer() {
        val saved = apiClientController.loadSavedServer()
        if (saved?.hostname?.trimEnd('/') != BuildConfig.NFLIX_SERVER_URL) {
            apiClientController.setupServer(BuildConfig.NFLIX_SERVER_URL)
        }
    }

    suspend fun switchServer(hostname: String) {
        apiClientController.setupServer(hostname)
        refreshServer()
        refreshUser()
    }

    suspend fun setupUser(serverId: Long, userId: UUID, accessToken: String) {
        apiClientController.setupUser(serverId, userId, accessToken)
        refreshUser()
    }

    /** Stores a session obtained by the native Nflix login screen. */
    suspend fun completeNflixLogin(serverId: Long, login: NflixLogin, method: String) {
        nflixSession.serverId = login.serverId
        nflixSession.userId = login.rawUserId
        nflixSession.userName = login.userName
        nflixSession.lastMethod = method
        nflixSession.accessToken = login.accessToken
        setupUser(serverId, login.userId, login.accessToken)
    }

    fun signOut() {
        viewModelScope.launch {
            apiClientController.signOutCurrentUser()
            nflixSession.clear()
            refreshUser()
        }
    }

    /** Re-emits the current server so the web client is loaded again after a connection error. */
    fun retryConnection() {
        viewModelScope.launch {
            refreshServer()
            refreshUser()
        }
    }

    fun requestMiraLogin() {
        _miraLoginRequests.value = true
    }

    fun consumeMiraLoginRequest() {
        _miraLoginRequests.value = false
    }

    private suspend fun refreshServer() {
        val serverEntity = apiClientController.loadSavedServer()
        _serverState.value = serverEntity?.let { entity -> ServerState.Available(entity) } ?: ServerState.Unset
    }

    private suspend fun refreshUser() {
        val userEntity = apiClientController.loadSavedUser()?.takeIf { !it.accessToken.isNullOrBlank() }
        if (userEntity != null) {
            nflixSession.accessToken = userEntity.accessToken
            if (nflixSession.userId == null) nflixSession.userId = userEntity.userId.toString().replace("-", "")
        } else {
            nflixSession.accessToken = null
        }
        _userState.value = userEntity?.let { entity -> UserState.Available(entity) } ?: UserState.Unset
    }

    /**
     * Temporarily unset the selected server to be able to connect to a different one
     */
    fun resetServer() {
        _serverState.value = ServerState.Unset
        _userState.value = UserState.Unset
    }
}

sealed class ServerState {
    open val server: ServerEntity? = null

    object Pending : ServerState()
    object Unset : ServerState()
    class Available(override val server: ServerEntity) : ServerState()
}

sealed class UserState {
    open val user: UserEntity? = null

    object Pending : UserState()
    object Unset : UserState()
    class Available(override val user: UserEntity) : UserState()
}
