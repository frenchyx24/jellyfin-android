package org.jellyfin.mobile.nflix.cast

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.player.cast.CastOptionsProvider
import timber.log.Timber

/** Google Cast is available in this flavour. */
object CastBackends {
    fun chromecast(context: Context, receiverId: () -> String, credentials: () -> CastProtocol.Credentials?, serverId: () -> String?): CastBackend? =
        runCatching { ChromecastBackend(context.applicationContext, receiverId, credentials, serverId) }
            .onFailure { Timber.w("Google Cast unavailable: %s", it.javaClass.simpleName) }
            .getOrNull()
}

/**
 * Chromecast through the Jellyfin receiver application, the same receiver and protocol as
 * jellyfin-web. The receiver streams from the server itself; the phone only sends commands.
 */
private class ChromecastBackend(
    private val context: Context,
    private val receiverId: () -> String,
    private val credentials: () -> CastProtocol.Credentials?,
    private val serverId: () -> String?,
) : CastBackend {
    private val json = Json { ignoreUnknownKeys = true }
    private val router = MediaRouter.getInstance(context)
    private val _targets = MutableStateFlow<List<CastTarget>>(emptyList())
    override val targets: StateFlow<List<CastTarget>> = _targets.asStateFlow()
    private val _status = MutableStateFlow<CastStatus?>(null)
    override val status: StateFlow<CastStatus?> = _status.asStateFlow()
    private var pendingSession: CompletableDeferred<CastSession?>? = null
    private var appliedReceiver: String? = null

    private fun castContext(): CastContext? = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    private fun selector(): MediaRouteSelector =
        MediaRouteSelector.Builder().addControlCategory(CastMediaControlIntent.categoryForCast(receiverId())).build()

    private val routerCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)
        override fun onSessionStartFailed(session: CastSession, error: Int) { pendingSession?.complete(null) }
        override fun onSessionEnded(session: CastSession, error: Int) { _status.value = null }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
        override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
    }

    init {
        castContext()?.sessionManager?.addSessionManagerListener(sessionListener, CastSession::class.java)
    }

    private fun applyReceiver() {
        val id = receiverId()
        if (id != appliedReceiver) {
            CastOptionsProvider.setAppId(id)
            appliedReceiver = id
        }
    }

    private fun refresh() {
        val selector = selector()
        _targets.value = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            .map { CastTarget(it.id, it.name, CastTarget.Kind.Chromecast, it.description?.toString().orEmpty()) }
    }

    override fun startDiscovery() {
        applyReceiver()
        router.addCallback(selector(), routerCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
    }

    override fun stopDiscovery() {
        router.removeCallback(routerCallback)
    }

    private fun attach(session: CastSession) {
        runCatching {
            session.setMessageReceivedCallbacks(CastProtocol.NAMESPACE) { _, _, message ->
                val parsed = runCatching { json.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return@setMessageReceivedCallbacks
                _status.value = _status.value?.let { CastProtocol.applyMessage(it, parsed) }
            }
        }
        val device = session.castDevice
        val target = _targets.value.firstOrNull { it.name == device?.friendlyName }
            ?: CastTarget(device?.deviceId ?: "cast", device?.friendlyName ?: "Chromecast", CastTarget.Kind.Chromecast)
        if (_status.value?.target?.id != target.id) _status.value = CastStatus(target, idle = true, volume = session.volume.toFloat())
        pendingSession?.complete(session)
    }

    private fun currentSession(): CastSession? = castContext()?.sessionManager?.currentCastSession?.takeIf { it.isConnected }

    override suspend fun connect(target: CastTarget): Boolean = withContext(Dispatchers.Main) {
        applyReceiver()
        currentSession()?.let { attach(it); return@withContext true }
        val route = router.routes.firstOrNull { it.id == target.id } ?: return@withContext false
        val deferred = CompletableDeferred<CastSession?>()
        pendingSession = deferred
        router.selectRoute(route)
        val session = withTimeoutOrNull(20_000) { deferred.await() }
        pendingSession = null
        session != null
    }

    private fun send(message: String) {
        val session = currentSession() ?: return
        session.sendMessage(CastProtocol.NAMESPACE, message)
    }

    override suspend fun play(target: CastTarget, items: List<Item>, startTicks: Long, audioIndex: Int?, subtitleIndex: Int?) = withContext(Dispatchers.Main) {
        val creds = credentials() ?: return@withContext
        val withName = creds.copy(receiverName = currentSession()?.castDevice?.friendlyName)
        send(CastProtocol.message("PlayNow", withName, CastProtocol.playOptions(items, serverId(), startTicks, audioIndex, subtitleIndex)))
        _status.value = CastStatus(target, items.firstOrNull()?.id, items.firstOrNull()?.name, startTicks, items.firstOrNull()?.runTimeTicks, volume = _status.value?.volume ?: 0.5f)
    }

    override suspend fun command(target: CastTarget, command: CastCommand) = withContext(Dispatchers.Main) {
        if (command is CastCommand.Volume) {
            currentSession()?.volume = command.level.toDouble().coerceIn(0.0, 1.0)
            _status.value = _status.value?.copy(volume = command.level)
            return@withContext
        }
        val creds = credentials() ?: return@withContext
        CastProtocol.commandMessage(command, creds)?.let(::send)
        _status.value = _status.value?.let { s ->
            when (command) {
                CastCommand.Pause -> s.copy(paused = true)
                CastCommand.Resume -> s.copy(paused = false)
                is CastCommand.Seek -> s.copy(positionTicks = command.ticks)
                CastCommand.Stop -> s.copy(idle = true)
                else -> s
            }
        }
    }

    override suspend fun disconnect(target: CastTarget) = withContext(Dispatchers.Main) {
        router.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
        castContext()?.sessionManager?.endCurrentSession(true)
        _status.value = null
    }
}
