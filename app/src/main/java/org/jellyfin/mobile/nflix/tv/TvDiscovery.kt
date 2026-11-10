package org.jellyfin.mobile.nflix.tv

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/** A Nflix TV advertising a sign-in code on the local network. */
data class DiscoveredTv(
    val name: String,
    val host: String?,
    val port: Int,
    val code: String,
) {
    /** Same TV = same name on the same address, even if mDNS reports it twice. */
    val key: String get() = "${name.lowercase()}|${host.orEmpty()}"
}

/** What the "Connecter une TV" screen shows while it watches the network. */
sealed interface TvDiscoveryState {
    data object Idle : TvDiscoveryState

    /** No Wi-Fi (or Ethernet): DNS-SD cannot see the TV. */
    data object NoWifi : TvDiscoveryState

    /** Discovery is running and nothing came up yet. */
    data object Searching : TvDiscoveryState

    /** Discovery ran long enough: no TV is waiting on this network. */
    data object Empty : TvDiscoveryState

    data class Found(val tvs: List<DiscoveredTv>) : TvDiscoveryState

    data class Failed(val message: String) : TvDiscoveryState
}

/** Reads the TXT record the TV advertises; pure JVM so it is unit tested. */
internal fun discoveredTv(serviceName: String, host: String?, port: Int, attributes: Map<String, ByteArray>): DiscoveredTv? {
    val code = attributes["code"]?.toString(Charsets.UTF_8)?.trim() ?: return null
    if (!TvLink.isValidCode(code)) return null
    val declared = attributes["name"]?.toString(Charsets.UTF_8)?.filterNot(Char::isISOControl)?.trim()?.take(60)
    val name = declared?.ifBlank { null }
        ?: serviceName.removePrefix("Nflix TV").trim().take(60).ifBlank { "TV Nflix" }
    return DiscoveredTv(name = name, host = host, port = port, code = code)
}

/** Keeps one entry per TV, keyed by name + address; a lost service removes its entry. */
internal class DiscoveryIndex {
    private val tvs = LinkedHashMap<String, DiscoveredTv>()
    private val keyByService = HashMap<String, String>()

    /** Returns true when the list changed. */
    fun add(serviceName: String, tv: DiscoveredTv): Boolean {
        keyByService[serviceName] = tv.key
        val previous = tvs[tv.key]
        tvs[tv.key] = tv
        // Same TV, same code: duplicate record. A new code (renewed on the TV) must replace it.
        return previous == null || previous.code != tv.code
    }

    fun removeService(serviceName: String): Boolean {
        val key = keyByService.remove(serviceName) ?: return false
        // Another mDNS record may still point at the same TV
        if (keyByService.containsValue(key)) return false
        return tvs.remove(key) != null
    }

    fun values(): List<DiscoveredTv> = tvs.values.toList()

    fun clear() {
        tvs.clear()
        keyByService.clear()
    }
}

/**
 * Watches the local network for Nflix TVs showing a sign-in code (`_nflixtv._tcp` DNS-SD).
 * Runs only while the "Connecter une TV" screen is open: the multicast lock and the discovery
 * are released on [stop], and a failed start is retried with a short backoff.
 */
class TvDiscovery(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val index = DiscoveryIndex()
    private val resolving = ConcurrentHashMap.newKeySet<String>()
    private val _state = MutableStateFlow<TvDiscoveryState>(TvDiscoveryState.Idle)
    val state: StateFlow<TvDiscoveryState> = _state.asStateFlow()

    private var listener: NsdManager.DiscoveryListener? = null
    private var lock: WifiManager.MulticastLock? = null
    private var retry: Job? = null
    private var settle: Job? = null
    private var running = false
    private var attempts = 0

    fun start() {
        if (running) return
        running = true
        attempts = 0
        index.clear()
        resolving.clear()
        if (!onLocalNetwork()) {
            running = false
            _state.value = TvDiscoveryState.NoWifi
            return
        }
        _state.value = TvDiscoveryState.Searching
        discover()
        settle = scope.launch {
            delay(EMPTY_AFTER_MS)
            if (running && index.values().isEmpty() && listener != null) _state.value = TvDiscoveryState.Empty
        }
    }

    fun stop() {
        running = false
        retry?.cancel()
        retry = null
        settle?.cancel()
        settle = null
        listener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        listener = null
        releaseLock()
        resolving.clear()
        index.clear()
        _state.value = TvDiscoveryState.Idle
    }

    fun retry() {
        stop()
        start()
    }

    private fun discover() {
        val manager = nsd ?: run {
            running = false
            _state.value = TvDiscoveryState.Failed("La recherche de TV n'est pas disponible sur ce téléphone.")
            return
        }
        acquireLock()
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.d("TV discovery failed to start: %d", errorCode)
                listener = null
                releaseLock()
                scheduleRetry()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceLost(service: NsdServiceInfo) {
                scope.launch { if (index.removeService(service.serviceName)) publish() }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains(SERVICE_TYPE)) return
                resolve(manager, service)
            }
        }
        listener = discovery
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery) }
            .onFailure {
                Timber.d("TV discovery unavailable: %s", it.javaClass.simpleName)
                listener = null
                releaseLock()
                scheduleRetry()
            }
    }

    private fun scheduleRetry() {
        if (!running) return
        attempts++
        if (attempts > MAX_ATTEMPTS) {
            running = false
            _state.value = TvDiscoveryState.Failed("Impossible de lancer la recherche sur ce réseau.")
            return
        }
        _state.value = TvDiscoveryState.Searching
        retry?.cancel()
        retry = scope.launch {
            delay(RETRY_MS * attempts)
            if (running) discover()
        }
    }

    private fun acquireLock() {
        releaseLock()
        lock = runCatching {
            wifi?.createMulticastLock("nflix-tv-discovery")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }.getOrNull()
    }

    private fun releaseLock() {
        runCatching { if (lock?.isHeld == true) lock?.release() }
        lock = null
    }

    private fun onLocalNetwork(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return false) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun resolve(manager: NsdManager, service: NsdServiceInfo) {
        val key = service.serviceName
        if (!resolving.add(key)) return
        if (Build.VERSION.SDK_INT >= 34) {
            val callback = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                    resolving.remove(key)
                }

                override fun onServiceUpdated(info: NsdServiceInfo) {
                    accept(info)
                    runCatching { manager.unregisterServiceInfoCallback(this) }
                }

                override fun onServiceLost() {
                    resolving.remove(key)
                    scope.launch { if (index.removeService(key)) publish() }
                }

                override fun onServiceInfoCallbackUnregistered() {
                    resolving.remove(key)
                }
            }
            runCatching { manager.registerServiceInfoCallback(service, ContextCompat.getMainExecutor(appContext), callback) }
                .onFailure { resolving.remove(key) }
        } else {
            runCatching {
                @Suppress("DEPRECATION")
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolving.remove(key)
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        resolving.remove(key)
                        accept(info)
                    }
                })
            }.onFailure { resolving.remove(key) }
        }
    }

    private fun accept(info: NsdServiceInfo) {
        val tv = discoveredTv(info.serviceName, info.host?.hostAddress, info.port, info.attributes) ?: return
        scope.launch {
            if (index.add(info.serviceName, tv)) publish()
        }
    }

    private fun publish() {
        val tvs = index.values()
        _state.value = if (tvs.isEmpty()) TvDiscoveryState.Searching else TvDiscoveryState.Found(tvs)
    }

    private companion object {
        const val SERVICE_TYPE = "_nflixtv._tcp"
        const val EMPTY_AFTER_MS = 7_000L
        const val RETRY_MS = 1_200L
        const val MAX_ATTEMPTS = 4
    }
}
