package org.jellyfin.mobile.nflix.tv

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.data.NflixHttpException
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** A Nflix TV waiting to be signed in, found on the Wi-Fi, scanned from its QR code or typed in. */
data class TvRequest(val code: String, val name: String, val source: Source) {
    enum class Source { Network, QrCode, Manual }
}

/**
 * Signs Nflix Android TV in from the phone.
 *
 * The TV shows a Jellyfin Quick Connect code and advertises it on the local network
 * (`_nflixtv._tcp` DNS-SD, TXT `code` and `name`). The phone, already signed in, offers to connect
 * it and approves the code with its own session (`POST /QuickConnect/Authorize`). The TV then gets
 * its own token; no password or token ever leaves the phone.
 */
object TvLink {
    private val _request = MutableStateFlow<TvRequest?>(null)

    /** The TV currently proposed to the user, if any. */
    val request: StateFlow<TvRequest?> = _request.asStateFlow()

    private val _manualOpen = MutableStateFlow(false)

    /** True while the "Connecter une TV" code entry dialog is shown. */
    val manualOpen: StateFlow<Boolean> = _manualOpen.asStateFlow()

    private val _discoveryOpen = MutableStateFlow(false)

    /** True while the "Connecter une TV" discovery screen is open. */
    val discoveryOpen: StateFlow<Boolean> = _discoveryOpen.asStateFlow()

    /** Codes the user already answered (connected or ignored) in this process. */
    private val handled = ConcurrentHashMap.newKeySet<String>()

    fun openDiscovery() {
        _discoveryOpen.value = true
    }

    fun closeDiscovery() {
        _discoveryOpen.value = false
    }

    fun openManual() {
        _manualOpen.value = true
    }

    fun closeManual() {
        _manualOpen.value = false
    }

    /** [explicit] = the user picked this TV or typed its code, so an earlier dismissal does not block it. */
    fun propose(request: TvRequest, explicit: Boolean = false) {
        if (!isValidCode(request.code)) return
        if (request.source == TvRequest.Source.Network && !explicit && request.code in handled) return
        val current = _request.value
        // A scanned or typed code always wins over a TV found on the network
        if (current != null && current.source != TvRequest.Source.Network && request.source == TvRequest.Source.Network) return
        _request.value = request
    }

    fun dismiss(code: String) {
        handled += code
        if (_request.value?.code == code) _request.value = null
    }

    fun fromUri(uri: Uri?): TvRequest? = uri?.let { parseLink(it.toString()) }

    /** Parses `nflix://tv-link?code=123456&name=Salon` (QR code shown on the TV). Pure JVM, unit tested. */
    fun parseLink(raw: String): TvRequest? {
        val uri = runCatching { java.net.URI(raw) }.getOrNull() ?: return null
        if (uri.scheme != "nflix" || uri.host != "tv-link") return null
        val params = uri.rawQuery.orEmpty().split('&').mapNotNull { part ->
            val key = part.substringBefore('=', "")
            if (key.isEmpty()) null else key to runCatching { java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8") }.getOrDefault("")
        }.toMap()
        val code = params["code"]?.trim() ?: return null
        if (!isValidCode(code)) return null
        val name = params["name"]?.filterNot(Char::isISOControl)?.trim()?.take(60)?.ifBlank { null } ?: "TV Nflix"
        return TvRequest(code, name, TvRequest.Source.QrCode)
    }

    /** Jellyfin Quick Connect codes are exactly 6 digits. */
    fun isValidCode(code: String) = code.length == CODE_LENGTH && code.all { it in '0'..'9' }

    const val CODE_LENGTH = 6

    /** Readable message for a failed authorization; [enabled] = server Quick Connect state, when known. */
    fun authorizeError(httpCode: Int?, enabled: Boolean?): String = when {
        enabled == false -> "La connexion par code (Quick Connect) est désactivée sur le serveur Nflix."
        httpCode == 400 || httpCode == 404 -> "Code inconnu ou expiré. Vérifiez le code affiché sur la TV (il change toutes les 9 minutes)."
        httpCode == 401 -> "Votre session Nflix a expiré : reconnectez-vous sur le téléphone."
        httpCode == 403 -> "Votre compte n'a pas le droit de connecter un appareil."
        httpCode == null -> "Serveur Nflix injoignable. Vérifiez la connexion du téléphone."
        else -> "Connexion de la TV impossible (erreur $httpCode)."
    }

    /** Approves the TV's Quick Connect code for the signed-in user (their own session token). */
    suspend fun authorize(api: NflixApi, code: String): Result<Unit> {
        if (!isValidCode(code)) return Result.failure(IllegalArgumentException("Le code doit contenir $CODE_LENGTH chiffres."))
        return try {
            val result = api.send("POST", "QuickConnect/Authorize", mapOf("code" to code, "userId" to api.userId))
            // Jellyfin answers `true` when the pending request was found and approved
            if (result != null && result.toString().trim() == "false") error(authorizeError(404, null))
            handled += code
            Result.success(Unit)
        } catch (e: NflixHttpException) {
            val enabled = if (e.code in setOf(401, 403)) quickConnectEnabled(api) else null
            Timber.w("TV authorization refused: HTTP %d", e.code) // never the token or the code
            Result.failure(IllegalStateException(authorizeError(e.code, enabled)))
        } catch (e: java.io.IOException) {
            Result.failure(IllegalStateException(authorizeError(null, null)))
        } catch (e: IllegalStateException) {
            Result.failure(e)
        }
    }

    private suspend fun quickConnectEnabled(api: NflixApi): Boolean? =
        runCatching { api.getJson("QuickConnect/Enabled").toString().trim() == "true" }.getOrNull()
}
