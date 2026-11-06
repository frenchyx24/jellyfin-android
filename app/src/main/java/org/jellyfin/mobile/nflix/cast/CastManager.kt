package org.jellyfin.mobile.nflix.cast

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.NflixApi
import timber.log.Timber

/** A screen Nflix can play on: a Chromecast, or another Jellyfin app (TV, browser, DLNA renderer). */
@Immutable
data class CastTarget(val id: String, val name: String, val kind: Kind, val detail: String = "") {
    enum class Kind { Chromecast, Jellyfin }
}

/** What the remote screen is playing. Positions are Jellyfin ticks (100 ns). */
@Immutable
data class CastStatus(
    val target: CastTarget,
    val itemId: String? = null,
    val title: String? = null,
    val positionTicks: Long = 0,
    val durationTicks: Long? = null,
    val paused: Boolean = false,
    /** 0..1 */
    val volume: Float = 0.5f,
    val audioIndex: Int? = null,
    val subtitleIndex: Int? = null,
    val idle: Boolean = false,
)

/** One way of reaching remote screens. */
interface CastBackend {
    val targets: StateFlow<List<CastTarget>>
    val status: StateFlow<CastStatus?>

    /** Starts looking for screens (while the picker is open). */
    fun startDiscovery()
    fun stopDiscovery()

    suspend fun connect(target: CastTarget): Boolean
    suspend fun play(target: CastTarget, items: List<Item>, startTicks: Long, audioIndex: Int?, subtitleIndex: Int?)
    suspend fun command(target: CastTarget, command: CastCommand)
    suspend fun disconnect(target: CastTarget)
}

sealed interface CastCommand {
    data object Pause : CastCommand
    data object Resume : CastCommand
    data object Stop : CastCommand
    data class Seek(val ticks: Long) : CastCommand
    data class Volume(val level: Float) : CastCommand
    data class Audio(val index: Int) : CastCommand
    data class Subtitle(val index: Int) : CastCommand
}

/** Receiver message helpers shared by the Chromecast backend and its tests. */
object CastProtocol {
    const val NAMESPACE = "urn:x-cast:com.connectsdk"
    const val DEFAULT_RECEIVER = "F007D354"

    data class Credentials(val userId: String, val deviceId: String, val accessToken: String, val serverAddress: String, val serverId: String?, val receiverName: String?)

    /** Jellyfin receiver message; the token travels inside the encrypted Cast channel, never in a URL. */
    fun message(command: String, credentials: Credentials, options: JsonObject = JsonObject(emptyMap())): String = buildJsonObject {
        put("options", options)
        put("command", command)
        put("userId", credentials.userId)
        put("deviceId", credentials.deviceId)
        put("accessToken", credentials.accessToken)
        put("serverAddress", credentials.serverAddress)
        credentials.serverId?.let { put("serverId", it) }
        credentials.receiverName?.let { put("receiverName", it) }
    }.toString()

    fun playOptions(items: List<Item>, serverId: String?, startTicks: Long, audioIndex: Int?, subtitleIndex: Int?): JsonObject = buildJsonObject {
        put("items", JsonArray(items.map { item ->
            buildJsonObject {
                put("Id", item.id); serverId?.let { put("ServerId", it) }; put("Name", item.name); put("Type", item.type)
                put("MediaType", item.mediaType ?: "Video"); put("IsFolder", false)
            }
        }))
        if (startTicks > 0) put("startPositionTicks", startTicks)
        audioIndex?.let { put("audioStreamIndex", it) }
        subtitleIndex?.let { put("subtitleStreamIndex", it) }
    }

    fun commandMessage(command: CastCommand, credentials: Credentials): String? = when (command) {
        CastCommand.Pause -> message("Pause", credentials)
        CastCommand.Resume -> message("Unpause", credentials)
        CastCommand.Stop -> message("Stop", credentials)
        is CastCommand.Seek -> message("Seek", credentials, buildJsonObject { put("position", command.ticks / 10_000_000.0) })
        is CastCommand.Audio -> message("SetAudioStreamIndex", credentials, buildJsonObject { put("index", command.index) })
        is CastCommand.Subtitle -> message("SetSubtitleStreamIndex", credentials, buildJsonObject { put("index", command.index) })
        is CastCommand.Volume -> null // receiver volume goes through the Cast session API
    }

    /** Applies a receiver message ({type, data}) to the current status. */
    fun applyMessage(current: CastStatus, message: JsonObject): CastStatus {
        val type = (message["type"] as? JsonPrimitive)?.contentOrNull ?: return current
        val data = message["data"] as? JsonObject
        if (type == "playbackstop") return current.copy(idle = true, paused = true)
        if (data == null) return current
        return applyState(current, data).let { if (type == "playbackstart") it.copy(idle = false) else it }
    }

    /** Reads a Jellyfin player state ({PlayState, NowPlayingItem}), as sent by receivers and /Sessions. */
    fun applyState(current: CastStatus, data: JsonObject): CastStatus {
        val play = data["PlayState"] as? JsonObject
        val item = data["NowPlayingItem"] as? JsonObject
        fun JsonObject?.long(key: String) = (this?.get(key) as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
        fun JsonObject?.bool(key: String) = (this?.get(key) as? JsonPrimitive)?.booleanOrNull
        fun JsonObject?.str(key: String) = (this?.get(key) as? JsonPrimitive)?.contentOrNull
        val volume = play.long("VolumeLevel")?.let { (it / 100f).coerceIn(0f, 1f) }
        return current.copy(
            itemId = item.str("Id") ?: current.itemId,
            title = item.str("Name") ?: current.title,
            positionTicks = play.long("PositionTicks") ?: current.positionTicks,
            durationTicks = item.long("RunTimeTicks") ?: current.durationTicks,
            paused = play.bool("IsPaused") ?: current.paused,
            volume = volume ?: current.volume,
            audioIndex = play.long("AudioStreamIndex")?.toInt() ?: current.audioIndex,
            subtitleIndex = play.long("SubtitleStreamIndex")?.toInt() ?: current.subtitleIndex,
            idle = item == null && play == null && current.idle,
        )
    }
}

/**
 * Other Jellyfin apps signed in to the user's account and accepting remote control: TV apps,
 * browsers, and DLNA renderers when the server's DLNA plugin exposes them. Controlled through the
 * server API with the app's own session; nothing sensitive is sent to the screen.
 */
class JellyfinSessionsBackend(private val api: NflixApi, private val ownDeviceId: () -> String) : CastBackend {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _targets = MutableStateFlow<List<CastTarget>>(emptyList())
    override val targets: StateFlow<List<CastTarget>> = _targets.asStateFlow()
    private val _status = MutableStateFlow<CastStatus?>(null)
    override val status: StateFlow<CastStatus?> = _status.asStateFlow()
    private var discovery: Job? = null
    private var polling: Job? = null

    override fun startDiscovery() {
        if (discovery?.isActive == true) return
        discovery = scope.launch {
            while (isActive) {
                refreshTargets()
                delay(8_000)
            }
        }
    }

    override fun stopDiscovery() {
        discovery?.cancel()
    }

    suspend fun refreshTargets() {
        try {
            val sessions = api.getJson("Sessions", mapOf("controllableByUserId" to api.userId, "activeWithinSeconds" to 960)) as? JsonArray ?: return
            _targets.value = sessions.filterIsInstance<JsonObject>().mapNotNull { s ->
                val id = (s["Id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val device = (s["DeviceId"] as? JsonPrimitive)?.contentOrNull
                val remote = (s["SupportsRemoteControl"] as? JsonPrimitive)?.booleanOrNull == true
                val types = (s["PlayableMediaTypes"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                if (!remote || device == ownDeviceId() || types.none { it.equals("Video", true) }) return@mapNotNull null
                val name = (s["DeviceName"] as? JsonPrimitive)?.contentOrNull ?: "Écran"
                val client = (s["Client"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                CastTarget(id, name, CastTarget.Kind.Jellyfin, client)
            }.distinctBy { it.id }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w("Remote sessions unavailable")
        }
    }

    override suspend fun connect(target: CastTarget): Boolean {
        _status.value = CastStatus(target, idle = true)
        polling?.cancel()
        polling = scope.launch {
            while (isActive) {
                poll(target)
                delay(2_000)
            }
        }
        return true
    }

    private suspend fun poll(target: CastTarget) {
        try {
            val sessions = api.getJson("Sessions", mapOf("controllableByUserId" to api.userId)) as? JsonArray ?: return
            val session = sessions.filterIsInstance<JsonObject>().firstOrNull { (it["Id"] as? JsonPrimitive)?.contentOrNull == target.id }
            val current = _status.value ?: return
            if (current.target.id != target.id) return
            _status.value = if (session == null) current.copy(idle = true) else CastProtocol.applyState(current.copy(idle = session["NowPlayingItem"] == null), session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.w("Remote session status unavailable")
        }
    }

    override suspend fun play(target: CastTarget, items: List<Item>, startTicks: Long, audioIndex: Int?, subtitleIndex: Int?) {
        api.send("POST", "Sessions/${target.id}/Playing", body = buildJsonObject {
            put("playCommand", "PlayNow")
            putJsonArray("itemIds") { items.forEach { add(JsonPrimitive(it.id)) } }
            if (startTicks > 0) put("startPositionTicks", startTicks)
            audioIndex?.let { put("audioStreamIndex", it) }
            subtitleIndex?.let { put("subtitleStreamIndex", it) }
        })
        _status.value = CastStatus(target, items.firstOrNull()?.id, items.firstOrNull()?.name, startTicks, items.firstOrNull()?.runTimeTicks)
    }

    override suspend fun command(target: CastTarget, command: CastCommand) {
        when (command) {
            CastCommand.Pause -> api.send("POST", "Sessions/${target.id}/Playing/Pause")
            CastCommand.Resume -> api.send("POST", "Sessions/${target.id}/Playing/Unpause")
            CastCommand.Stop -> api.send("POST", "Sessions/${target.id}/Playing/Stop")
            is CastCommand.Seek -> api.send("POST", "Sessions/${target.id}/Playing/Seek", mapOf("seekPositionTicks" to command.ticks))
            is CastCommand.Volume -> general(target, "SetVolume", "Volume", (command.level * 100).toInt().coerceIn(0, 100).toString())
            is CastCommand.Audio -> general(target, "SetAudioStreamIndex", "Index", command.index.toString())
            is CastCommand.Subtitle -> general(target, "SetSubtitleStreamIndex", "Index", command.index.toString())
        }
        _status.value = _status.value?.let { s ->
            when (command) {
                CastCommand.Pause -> s.copy(paused = true)
                CastCommand.Resume -> s.copy(paused = false)
                is CastCommand.Seek -> s.copy(positionTicks = command.ticks)
                is CastCommand.Volume -> s.copy(volume = command.level)
                is CastCommand.Audio -> s.copy(audioIndex = command.index)
                is CastCommand.Subtitle -> s.copy(subtitleIndex = command.index)
                CastCommand.Stop -> s.copy(idle = true)
            }
        }
    }

    private suspend fun general(target: CastTarget, name: String, argument: String, value: String) {
        api.send("POST", "Sessions/${target.id}/Command", body = buildJsonObject {
            put("Name", name)
            put("Arguments", buildJsonObject { put(argument, value) })
        })
    }

    override suspend fun disconnect(target: CastTarget) {
        polling?.cancel()
        _status.value = null
    }
}

/** Receiver application chosen by the server admin (user configuration), Jellyfin's by default. */
object CastReceiver {
    @Volatile var id: String = CastProtocol.DEFAULT_RECEIVER
}

/** Single entry point for the UI: merges every backend, remembers the active screen. */
class CastManager(private val backends: List<CastBackend>) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _targets = MutableStateFlow<List<CastTarget>>(emptyList())
    val targets: StateFlow<List<CastTarget>> = _targets.asStateFlow()
    private val _active = MutableStateFlow<CastStatus?>(null)

    /** The connected screen and what it plays; null while playing on the phone. */
    val active: StateFlow<CastStatus?> = _active.asStateFlow()
    private var activeBackend: CastBackend? = null

    init {
        backends.forEach { backend ->
            scope.launch { backend.targets.collect { _targets.value = backends.flatMap { it.targets.value } } }
            scope.launch { backend.status.collect { status -> if (backend === activeBackend) _active.value = status } }
        }
    }

    val connected: Boolean get() = _active.value != null

    fun startDiscovery() = backends.forEach { it.startDiscovery() }
    fun stopDiscovery() = backends.forEach { it.stopDiscovery() }

    private fun backendFor(target: CastTarget) = backends.firstOrNull { b -> b.targets.value.any { it.id == target.id } } ?: backends.firstOrNull { b ->
        (target.kind == CastTarget.Kind.Jellyfin) == (b is JellyfinSessionsBackend)
    }

    suspend fun connect(target: CastTarget): Boolean {
        val backend = backendFor(target) ?: return false
        activeBackend?.takeIf { it !== backend }?.let { old -> _active.value?.let { old.disconnect(it.target) } }
        activeBackend = backend
        val ok = runCatching { backend.connect(target) }.getOrDefault(false)
        if (!ok) activeBackend = null
        _active.value = if (ok) backend.status.value ?: CastStatus(target, idle = true) else null
        return ok
    }

    /** Plays on the connected screen; false when nothing is connected (the phone plays instead). */
    suspend fun play(items: List<Item>, startTicks: Long, audioIndex: Int? = null, subtitleIndex: Int? = null): Boolean {
        val backend = activeBackend ?: return false
        val target = _active.value?.target ?: return false
        if (items.isEmpty()) return false
        backend.play(target, items, startTicks, audioIndex, subtitleIndex)
        return true
    }

    suspend fun command(command: CastCommand) {
        val backend = activeBackend ?: return
        val target = _active.value?.target ?: return
        runCatching { backend.command(target, command) }.onFailure { if (it is CancellationException) throw it; Timber.w("Cast command failed") }
    }

    /** Stops the screen and returns what it was playing, to continue on the phone. */
    suspend fun takeBack(): CastStatus? {
        val status = _active.value ?: return null
        command(CastCommand.Stop)
        disconnect()
        return status
    }

    suspend fun disconnect() {
        val backend = activeBackend ?: return
        _active.value?.let { runCatching { backend.disconnect(it.target) } }
        activeBackend = null
        _active.value = null
    }
}
