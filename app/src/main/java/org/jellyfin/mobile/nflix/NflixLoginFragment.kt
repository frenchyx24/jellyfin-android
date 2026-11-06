package org.jellyfin.mobile.nflix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.jellyfin.mobile.MainViewModel
import org.jellyfin.mobile.ServerState
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.ui.utils.AppTheme
import org.jellyfin.mobile.utils.Constants
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber

/**
 * Native Nflix sign-in: Mira Connect (Authentik OIDC, PKCE) or username/password.
 * On success the session is handed to the embedded web client.
 */
class NflixLoginFragment : Fragment() {
    private val mainViewModel: MainViewModel by activityViewModel()
    private val repository: NflixAuthRepository by inject()
    private val nflixSession: NflixSession by inject()
    private val miraBridge by lazy { MiraAccountBridge(requireContext().applicationContext) }
    private var silentMiraAttempted = false
    private val activityEventHandler: ActivityEventHandler by inject()
    private val state = NflixLoginUiState()
    private var authService: AuthorizationService? = null

    private val miraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onMiraResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authService = AuthorizationService(requireContext())
        state.connectionError = arguments?.getBoolean(Constants.FRAGMENT_CONNECT_EXTRA_ERROR) == true
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    NflixLoginScreen(
                        state = state,
                        miraConnectInstalled = isMiraConnectInstalled(),
                        onMiraLogin = ::startMiraLogin,
                        onPasswordLogin = ::passwordLogin,
                        onForgotPassword = { open(FORGOT_PASSWORD_URL) },
                        onOpenDownloads = { activityEventHandler.emit(ActivityEvent.OpenDownloads) },
                        onRetryConnection = {
                            state.connectionError = false
                            mainViewModel.retryConnection()
                        },
                    )
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (savedInstanceState == null && !state.connectionError) trySilentMiraLogin()
        // Mira Connect can open Nflix with a request to sign in right away
        viewLifecycleOwner.lifecycleScope.launch {
            mainViewModel.miraLoginRequests.collectLatest { requested ->
                if (requested && !state.connectionError && state.busy == LoginBusy.None) {
                    mainViewModel.consumeMiraLoginRequest()
                    startMiraLogin()
                }
            }
        }
    }

    override fun onDestroy() {
        authService?.dispose()
        authService = null
        super.onDestroy()
    }

    private fun trySilentMiraLogin() {
        if (silentMiraAttempted || !miraBridge.isMiraInstalled()) return
        silentMiraAttempted = true
        state.busy = LoginBusy.Mira
        viewLifecycleOwner.lifecycleScope.launch {
            attemptLogin {
                val account = withContext(Dispatchers.IO) { miraBridge.account() }
                if (!isAdded || account == null) {
                    state.busy = LoginBusy.None
                    return@attemptLogin
                }
                coroutineContext.ensureActive()
                val login = repository.signInWithMira(account.idToken)
                complete(login, METHOD_MIRA, accountProfile(account.idToken, account.name, account.email, account.avatar, account.groups))
            }
        }
    }

    private fun startMiraLogin() {
        if (state.busy != LoginBusy.None) return
        state.error = null
        state.busy = LoginBusy.Mira
        lifecycleScope.launch {
            val config = repository.miraConfig()
            AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(config.issuer)) { serviceConfig, error ->
                val service = authService
                if (!isAdded || service == null) return@fetchFromIssuer
                if (serviceConfig == null) {
                    Timber.e(error, "Mira discovery failed")
                    state.fail("Impossible de joindre Mira Connect. Vérifiez votre connexion.")
                    return@fetchFromIssuer
                }
                val request = AuthorizationRequest.Builder(
                    serviceConfig,
                    config.clientId,
                    ResponseTypeValues.CODE,
                    Uri.parse(REDIRECT_URI),
                ).setScope("openid profile email").build()
                runCatching { miraLauncher.launch(service.getAuthorizationRequestIntent(request)) }
                    .onFailure { state.fail("Installez un navigateur pour vous connecter avec Mira Connect.") }
            }
        }
    }

    private fun onMiraResult(data: Intent?) {
        val service = authService ?: return
        val response = data?.let(AuthorizationResponse::fromIntent)
        if (response == null) {
            val error = data?.let(AuthorizationException::fromIntent)
            Timber.w(error, "Mira authorization ended without a code")
            if (error?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code || data == null) {
                state.busy = LoginBusy.None
            } else {
                state.fail(error?.errorDescription ?: "Votre compte Mira n'a pas accès à Nflix.")
            }
            return
        }
        // AppAuth validates the ID token (issuer, audience, nonce, expiry) during the exchange
        service.performTokenRequest(response.createTokenExchangeRequest()) { token, error ->
            if (!isAdded) return@performTokenRequest
            val idToken = token?.idToken
            if (idToken == null) {
                Timber.e(error, "Mira token exchange failed")
                state.fail("Mira Connect a refusé la connexion. Réessayez.")
                return@performTokenRequest
            }
            lifecycleScope.launch {
                attemptLogin {
                    val login = repository.signInWithMira(idToken)
                    complete(login, METHOD_MIRA, MiraProfile.fromAcceptedToken(idToken))
                }
            }
        }
    }

    private fun passwordLogin(username: String, password: String) {
        state.error = null
        state.busy = LoginBusy.Password
        lifecycleScope.launch {
            attemptLogin {
                complete(repository.signInWithPassword(username, password), METHOD_PASSWORD)
            }
        }
    }

    private suspend fun attemptLogin(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            state.success = false
            state.busy = LoginBusy.None
            throw error
        } catch (error: Exception) {
            state.success = false
            state.fail(error.userMessage())
        }
    }

    private suspend fun complete(login: NflixLogin, method: String, profile: MiraProfile? = null) {
        val server = (mainViewModel.serverState.value as? ServerState.Available)?.server
        if (server == null) {
            state.fail("Serveur Nflix non configuré. Relancez l'application.")
            return
        }
        state.success = true
        // Let the success animation play before switching to the library
        delay(SUCCESS_DELAY)
        try {
            mainViewModel.completeNflixLogin(server.id, login, method)
            nflixSession.setMiraProfile(profile)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            nflixSession.clear()
            throw error
        }
    }

    private fun accountProfile(token: String, name: String, email: String, avatar: String?, groups: List<String>): MiraProfile {
        val claims = MiraProfile.fromAcceptedToken(token)
        return MiraProfile(
            name = name.takeIf(String::isNotBlank) ?: claims?.name,
            email = email.takeIf(String::isNotBlank) ?: claims?.email,
            avatar = MiraProfile.safeAvatar(avatar) ?: claims?.avatar,
            groups = groups.toSet().ifEmpty { claims?.groups.orEmpty() },
        )
    }

    private fun isMiraConnectInstalled(): Boolean = runCatching {
        requireContext().packageManager.getPackageInfo(MIRA_CONNECT_PACKAGE, 0)
        true
    }.getOrDefault(false)

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun Throwable.userMessage(): String = when (this) {
        is NflixAuthException -> message.orEmpty()
        else -> "Connexion impossible. Réessayez."
    }

    companion object {
        const val MIRA_CONNECT_PACKAGE = "fr.miracloud.connect"
        const val EXTRA_MIRA_LOGIN = "fr.miracloud.nflix.extra.MIRA_LOGIN"
        private const val REDIRECT_URI = "fr.miracloud.nflix:/oauth2redirect"
        private const val FORGOT_PASSWORD_URL = "https://auth.miracloud.fr/if/flow/recovery/?next=%2F"
        private const val METHOD_MIRA = "mira"
        private const val METHOD_PASSWORD = "password"
        private const val SUCCESS_DELAY = 480L
    }
}
