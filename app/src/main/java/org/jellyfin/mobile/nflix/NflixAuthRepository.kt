package org.jellyfin.mobile.nflix

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A Jellyfin session obtained natively. */
data class NflixLogin(
    val userId: UUID,
    val rawUserId: String,
    val userName: String,
    val accessToken: String,
    val serverId: String?,
)

/** Raised with a message that can be shown to the user as-is. */
class NflixAuthException(message: String) : Exception(message)

/** Public OIDC configuration published by the Nflix Auth server plugin. */
data class MiraConfig(val issuer: String, val clientId: String)

/**
 * Native authentication against the Nflix server:
 * - username/password (Jellyfin LDAP plugin, backed by the Mira Authentik directory)
 * - Mira Connect: an Authentik ID token exchanged by the Nflix Auth plugin
 */
class NflixAuthRepository(private val apiClient: ApiClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private val server = BuildConfig.NFLIX_SERVER_URL.trimEnd('/')

    suspend fun miraConfig(): MiraConfig = withContext(Dispatchers.IO) {
        runCatching {
            val body = request("GET", "/NflixAuth/Config", null)
            MiraConfig(
                body["Issuer"]?.jsonPrimitive?.contentOrNull ?: BuildConfig.MIRA_ISSUER,
                body["ClientId"]?.jsonPrimitive?.contentOrNull ?: BuildConfig.MIRA_CLIENT_ID,
            )
        }.getOrElse { error ->
            Timber.w(error, "Falling back to bundled Mira configuration")
            MiraConfig(BuildConfig.MIRA_ISSUER, BuildConfig.MIRA_CLIENT_ID)
        }
    }

    suspend fun signInWithPassword(username: String, password: String): NflixLogin = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("Username", username)
            put("Pw", password)
        }
        parseLogin(request("POST", "/Users/AuthenticateByName", body))
    }

    suspend fun signInWithMira(idToken: String): NflixLogin = withContext(Dispatchers.IO) {
        val device = apiClient.deviceInfo
        val client = apiClient.clientInfo
        val body = buildJsonObject {
            put("IdToken", idToken)
            put("DeviceId", device.id)
            put("DeviceName", device.name.ifBlank { "Android" })
            put("App", client.name)
            put("AppVersion", client.version)
        }
        parseLogin(request("POST", "/NflixAuth/Mira", body))
    }

    private fun parseLogin(body: JsonObject): NflixLogin {
        val token = body["AccessToken"]?.jsonPrimitive?.contentOrNull
        val user = body["User"]?.jsonObject
        val rawId = user?.get("Id")?.jsonPrimitive?.contentOrNull
        val id = rawId?.toUUIDOrNull()
        if (token.isNullOrBlank() || rawId == null || id == null) throw NflixAuthException("Réponse de Nflix incomplète. Réessayez.")
        return NflixLogin(
            userId = id,
            rawUserId = rawId,
            userName = user["Name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            accessToken = token,
            serverId = body["ServerId"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun request(method: String, path: String, body: JsonObject?): JsonObject {
        val device = apiClient.deviceInfo
        val client = apiClient.clientInfo
        val authorization = AuthorizationHeaderBuilder.buildHeader(
            clientName = client.name,
            clientVersion = client.version,
            deviceId = device.id,
            deviceName = device.name.ifBlank { "Android" },
            accessToken = null,
        )
        val connection = (URL(server + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", authorization)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) return json.parseToJsonElement(text).jsonObject
            val serverMessage = runCatching { json.parseToJsonElement(text).jsonObject["Message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
            throw NflixAuthException(
                when {
                    path.startsWith("/NflixAuth") && !serverMessage.isNullOrBlank() -> serverMessage
                    code == HttpURLConnection.HTTP_UNAUTHORIZED -> "Identifiant ou mot de passe incorrect."
                    code == HttpURLConnection.HTTP_FORBIDDEN -> "Accès à Nflix refusé pour ce compte."
                    code == HttpURLConnection.HTTP_NOT_FOUND -> "Service de connexion Nflix indisponible."
                    code >= HttpURLConnection.HTTP_INTERNAL_ERROR -> "Nflix rencontre un problème. Réessayez dans un instant."
                    else -> "Connexion refusée ($code)."
                },
            )
        } catch (e: IOException) {
            Timber.e(e, "Nflix request %s failed", path)
            throw NflixAuthException("Impossible de joindre Nflix. Vérifiez votre connexion.")
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT = 20_000
    }
}
