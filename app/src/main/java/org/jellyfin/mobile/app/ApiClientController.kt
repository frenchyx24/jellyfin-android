package org.jellyfin.mobile.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.data.KeystoreTokenCipher
import org.jellyfin.mobile.data.TokenCodec
import org.jellyfin.mobile.data.dao.ServerDao
import org.jellyfin.mobile.data.dao.UserDao
import org.jellyfin.mobile.data.entity.ServerEntity
import org.jellyfin.mobile.data.entity.ServerUser
import org.jellyfin.mobile.data.entity.UserEntity
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID

class ApiClientController(
    private val appPreferences: AppPreferences,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
    private val tokenCodec: TokenCodec = TokenCodec(KeystoreTokenCipher()),
) {
    private val baseDeviceInfo: DeviceInfo
        get() = jellyfin.options.deviceInfo!!

    /**
     * Store server with [hostname] in the database.
     */
    suspend fun setupServer(hostname: String) {
        appPreferences.currentServerId = withContext(Dispatchers.IO) {
            serverDao.getServerByHostname(hostname)?.id ?: serverDao.insert(hostname)
        }
        apiClient.update(baseUrl = hostname)
    }

    suspend fun setupUser(serverId: Long, userId: UUID, accessToken: String) {
        appPreferences.currentUserId = withContext(Dispatchers.IO) {
            userDao.upsert(serverId, userId, tokenCodec.encode(accessToken))
        }
        configureApiClientUser(userId, accessToken)
    }

    /** Forgets the access token of the current user (Nflix sign out). */
    suspend fun signOutCurrentUser() {
        withContext(Dispatchers.IO) {
            appPreferences.currentUserId?.let(userDao::logout)
        }
        appPreferences.currentUserId = null
        resetApiClientUser()
    }

    suspend fun loadSavedServer(): ServerEntity? {
        val server = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            serverDao.getServer(serverId)
        }
        configureApiClientServer(server)
        return server
    }

    suspend fun loadSavedUser(): UserEntity? = withContext(Dispatchers.IO) {
        val userId = appPreferences.currentUserId ?: return@withContext null
        userDao.getUser(userId)?.let(::withDecodedToken)
    }

    suspend fun loadSavedServerUser(): ServerUser? {
        val serverUser = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            val userId = appPreferences.currentUserId ?: return@withContext null
            userDao.getServerUser(serverId, userId)
        }

        val user = withContext(Dispatchers.IO) {
            serverUser?.let { it.copy(user = withDecodedToken(it.user)) }
        }
        configureApiClientServer(user?.server)

        if (user?.user?.accessToken != null) {
            configureApiClientUser(user.user.userId, user.user.accessToken)
        } else {
            resetApiClientUser()
        }

        return user
    }

    suspend fun loadPreviouslyUsedServers(): List<ServerEntity> = withContext(Dispatchers.IO) {
        serverDao.getAllServers().filterNot { server ->
            server.id == appPreferences.currentServerId
        }
    }

    /**
     * Decrypts the stored access token, re-sealing legacy plaintext in place.
     * An unreadable token (broken keystore) becomes null: the user is signed out, never crashed.
     */
    private fun withDecodedToken(user: UserEntity): UserEntity {
        val stored = user.accessToken ?: return user
        val token = tokenCodec.read(stored) { replacement -> userDao.update(user.id, replacement) }
        return user.copy(accessToken = token)
    }

    private fun configureApiClientServer(server: ServerEntity?) {
        apiClient.update(baseUrl = server?.hostname)
    }

    private fun configureApiClientUser(userId: UUID, accessToken: String) {
        apiClient.update(
            accessToken = accessToken,
            // Append user id to device id to ensure uniqueness across sessions
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + userId),
        )
    }

    private fun resetApiClientUser() {
        apiClient.update(
            accessToken = null,
            deviceInfo = baseDeviceInfo,
        )
    }

    fun getApiClient(server: Long, user: Long): ApiClient {
        val serverUser = userDao.getServerUser(server, user) ?: error("Invalid server user combination (server=$server, user=$user)")
        val token = withDecodedToken(serverUser.user).accessToken

        return jellyfin.createApi(
            baseUrl = serverUser.server.hostname,
            accessToken = token,
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + serverUser.user.userId),
        )
    }
}
