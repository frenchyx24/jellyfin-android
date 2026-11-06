package org.jellyfin.mobile.nflix

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.app.StorageManager
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.nflix.data.HorusRepository
import org.jellyfin.mobile.nflix.data.JellyfinRepository
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.data.PluginRepository
import org.jellyfin.mobile.nflix.music.MusicController
import org.jellyfin.mobile.nflix.ui.AppDeps
import org.jellyfin.mobile.nflix.ui.Navigator
import org.jellyfin.mobile.nflix.ui.NflixRoot
import org.jellyfin.mobile.nflix.ui.NflixTheme
import org.jellyfin.mobile.nflix.ui.Route
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/** Keeps the native navigation stack across configuration changes. */
class NflixNavViewModel : ViewModel() {
    val navigator = Navigator()
}

/** The fully native Nflix experience, shown once a session exists. */
class NflixAppFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private val navViewModel: NflixNavViewModel by viewModel()
    private val api: NflixApi by inject()
    private val jellyfin: JellyfinRepository by inject()
    private val plugins: PluginRepository by inject()
    private val horus: HorusRepository by inject()
    private val music: MusicController by inject()
    private val events: ActivityEventHandler by inject()
    private val session: NflixSession by inject()
    private val updater: NflixUpdater by inject()
    private val alerts: org.jellyfin.mobile.nflix.alerts.AlertsRepository by inject()
    private val alertPrefs: org.jellyfin.mobile.nflix.alerts.AlertPrefs by inject()
    private val cast: org.jellyfin.mobile.nflix.cast.CastManager by inject()
    private val downloadDao: DownloadDao by inject()
    private val storageManager: StorageManager by inject()

    /** Back is only handled natively while no player or settings screen sits on top. */
    private val onTop = mutableStateOf(true)
    private val backStackListener = FragmentManager.OnBackStackChangedListener {
        onTop.value = parentFragmentManager.backStackEntryCount == 0
    }

    fun playOfflineAudio(uri: String, title: String) = music.playLocal(Uri.parse(uri), title)

    fun openOfflineBook(id: String) = navViewModel.navigator.open(Route.Reader(id))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        horus.userName = session.userName
        val deps = AppDeps(
            api = api, jellyfin = jellyfin, plugins = plugins, horus = horus, music = music,
            events = events, session = session, updater = updater, alerts = alerts, alertPrefs = alertPrefs, cast = cast,
            downloadDao = downloadDao, storageManager = storageManager,
            onSignOut = {
                alerts.clear()
                lifecycleScope.launch { cast.disconnect() }
                lifecycleScope.launch {
                    runCatching { api.send("POST", "Sessions/Logout") }
                    music.stop()
                    mainViewModel.signOut()
                }
            },
            onOpenWeb = { mainViewModel.setWebMode(true) },
        )
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NflixTheme {
                    NflixRoot(deps, navViewModel.navigator, backEnabled = onTop.value)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        parentFragmentManager.addOnBackStackChangedListener(backStackListener)
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.openPlayerRequests.collectLatest { requested ->
                if (requested) {
                    mainViewModel.consumeOpenPlayer()
                    navViewModel.navigator.open(Route.NowPlaying)
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.openItemRequests.collectLatest { id ->
                if (id != null) {
                    mainViewModel.consumeOpenItem()
                    navViewModel.navigator.open(Route.Item(id))
                }
            }
        }
        // Signed in: the alerts worker runs every 15 min (Android minimum), like Mira Connect.
        org.jellyfin.mobile.nflix.alerts.NflixAlerts.createChannel(requireContext())
        org.jellyfin.mobile.nflix.alerts.NflixAlerts.schedule(requireContext())
    }

    override fun onDestroyView() {
        parentFragmentManager.removeOnBackStackChangedListener(backStackListener)
        super.onDestroyView()
    }
}
