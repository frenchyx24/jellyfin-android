package org.jellyfin.mobile.nflix

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.jellyfin.mobile.R

internal val NflixRed = Color(0xFFE50914)
private val NflixRedDeep = Color(0xFF8C0A1C)
private val NflixNight = Color(0xFF07070A)
private val NflixCard = Color(0xFF15151C)
private val NflixText = Color(0xFFF5F5F7)
private val NflixMuted = Color(0xFFA9A9B8)

enum class LoginMode { Choice, Password }
enum class LoginBusy { None, Mira, Password }

/** Observable state of the native login screen. */
class NflixLoginUiState {
    var mode by mutableStateOf(LoginMode.Choice)
    var busy by mutableStateOf(LoginBusy.None)
    var error by mutableStateOf<String?>(null)
    var errorTick by mutableStateOf(0)
    var success by mutableStateOf(false)
    var connectionError by mutableStateOf(false)
    var userName by mutableStateOf("")

    fun fail(message: String) {
        busy = LoginBusy.None
        error = message
        errorTick++
    }
}

@Composable
fun NflixLoginScreen(
    state: NflixLoginUiState,
    miraConnectInstalled: Boolean,
    onMiraLogin: () -> Unit,
    onPasswordLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
    onOpenDownloads: () -> Unit,
    onRetryConnection: () -> Unit,
) {
    val exit by animateFloatAsState(if (state.success) 1f else 0f, tween(520, easing = FastOutSlowInEasing), label = "exit")
    Box(Modifier.fillMaxSize().background(NflixNight)) {
        AnimatedBackdrop(Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .graphicsLayer { alpha = 1f - exit * 0.9f },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            AnimatedLogo(success = state.success)
            Spacer(Modifier.height(28.dp))

            Staggered(index = 0) {
                Text(
                    text = if (state.connectionError) "Nflix est injoignable" else "Bienvenue sur Nflix",
                    color = NflixText,
                    fontSize = 30.sp,
                    lineHeight = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            Staggered(index = 1) {
                Text(
                    text = if (state.connectionError) {
                        "Le serveur nflix.miracloud.fr ne répond pas. Vos téléchargements restent disponibles hors ligne."
                    } else {
                        "Films, séries et musique de votre espace Mira, en illimité."
                    },
                    color = NflixMuted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
            Spacer(Modifier.height(30.dp))

            ErrorBanner(state.error, state.errorTick)

            Box(Modifier.widthIn(max = 460.dp).fillMaxWidth()) {
                if (state.connectionError) {
                    Staggered(index = 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrimaryButton("Réessayer", Icons.Rounded.Refresh, busy = false, onClick = onRetryConnection)
                            SecondaryButton("Mes téléchargements", Icons.Rounded.DownloadForOffline, onClick = onOpenDownloads)
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = state.mode,
                        transitionSpec = {
                            val forward = targetState == LoginMode.Password
                            (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(300))) togetherWith
                                (slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                        },
                        label = "login-mode",
                    ) { mode ->
                        when (mode) {
                            LoginMode.Choice -> ChoicePanel(state, miraConnectInstalled, onMiraLogin, onOpenDownloads)
                            LoginMode.Password -> PasswordPanel(state, onPasswordLogin, onForgotPassword)
                        }
                    }
                }
            }

            Spacer(Modifier.height(36.dp))
            Staggered(index = 5) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Shield, null, tint = NflixMuted, modifier = Modifier.size(14.dp))
                    Text("Connexion sécurisée · nflix.miracloud.fr", color = NflixMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ChoicePanel(
    state: NflixLoginUiState,
    miraConnectInstalled: Boolean,
    onMiraLogin: () -> Unit,
    onOpenDownloads: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Staggered(index = 2) {
            PrimaryButton(
                text = if (state.busy == LoginBusy.Mira) "Connexion avec Mira…" else "Continuer avec Mira Connect",
                icon = Icons.Rounded.Key,
                busy = state.busy == LoginBusy.Mira,
                enabled = state.busy == LoginBusy.None,
                onClick = onMiraLogin,
            )
        }
        Staggered(index = 3) {
            SecondaryButton(
                text = "Identifiant et mot de passe",
                icon = Icons.Rounded.Person,
                enabled = state.busy == LoginBusy.None,
                onClick = { state.error = null; state.mode = LoginMode.Password },
            )
        }
        Staggered(index = 4) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                MiraHint(miraConnectInstalled)
                TextButton(onClick = onOpenDownloads) {
                    Icon(Icons.Rounded.DownloadForOffline, null, tint = NflixMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Mes téléchargements hors ligne", color = NflixMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun MiraHint(installed: Boolean) {
    Surface(color = Color.White.copy(alpha = 0.06f), shape = CircleShape) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                if (installed) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                null,
                tint = if (installed) Color(0xFF7CE0B4) else NflixMuted,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (installed) "Mira Connect détecté : connexion en un geste" else "Un seul compte Mira pour tous vos services",
                color = NflixText.copy(alpha = 0.85f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun PasswordPanel(
    state: NflixLoginUiState,
    onPasswordLogin: (String, String) -> Unit,
    onForgotPassword: () -> Unit,
) {
    var username by rememberSaveable { mutableStateOf(state.userName) }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val passwordFocus = remember { FocusRequester() }
    val busy = state.busy == LoginBusy.Password
    val submit = {
        if (username.isNotBlank() && password.isNotEmpty() && state.busy == LoginBusy.None) {
            focus.clearFocus()
            state.userName = username.trim()
            onPasswordLogin(username.trim(), password)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.error = null; state.mode = LoginMode.Choice }, enabled = !busy) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour", tint = NflixText)
            }
            Text("Identifiants Mira", color = NflixText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Identifiant") },
            leadingIcon = { Icon(Icons.Rounded.Person, null) },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            colors = fieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Mot de passe") },
            leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            trailingIcon = {
                IconButton(onClick = { reveal = !reveal }) {
                    Crossfade(reveal, label = "reveal") { shown ->
                        Icon(if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Afficher le mot de passe")
                    }
                }
            },
            singleLine = true,
            enabled = !busy,
            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            colors = fieldColors(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
        )
        Spacer(Modifier.height(2.dp))
        PrimaryButton(
            text = if (busy) "Connexion…" else "Se connecter",
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            busy = busy,
            enabled = username.isNotBlank() && password.isNotEmpty() && state.busy == LoginBusy.None,
            onClick = submit,
        )
        TextButton(onClick = onForgotPassword, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Mot de passe oublié ?", color = NflixMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.outlinedTextFieldColors(
    textColor = NflixText,
    cursorColor = NflixRed,
    focusedBorderColor = NflixRed,
    unfocusedBorderColor = Color.White.copy(alpha = 0.16f),
    focusedLabelColor = NflixRed,
    unfocusedLabelColor = NflixMuted,
    leadingIconColor = NflixMuted,
    trailingIconColor = NflixMuted,
    backgroundColor = NflixCard.copy(alpha = 0.72f),
)

@Composable
private fun PrimaryButton(text: String, icon: ImageVector, busy: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "button-pulse")
    val shine by pulse.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(2600, delayMillis = 900, easing = LinearEasing)),
        label = "shine",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            backgroundColor = NflixRed,
            contentColor = Color.White,
            disabledBackgroundColor = NflixRed.copy(alpha = if (busy) 0.75f else 0.35f),
            disabledContentColor = Color.White.copy(alpha = 0.85f),
        ),
        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().height(58.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (enabled) {
                Canvas(Modifier.fillMaxSize()) {
                    val x = size.width * shine
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                            start = Offset(x - size.width * 0.25f, 0f),
                            end = Offset(x + size.width * 0.25f, size.height),
                        ),
                    )
                }
            }
            Crossfade(busy, animationSpec = tween(220), label = "button-busy") { loading ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(icon, null, modifier = Modifier.size(20.dp))
                    }
                    Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SecondaryButton(text: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
        colors = ButtonDefaults.outlinedButtonColors(backgroundColor = Color.White.copy(alpha = 0.04f), contentColor = NflixText),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorBanner(error: String?, tick: Int) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        if (tick > 0) {
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 420
                    -14f at 60
                    12f at 130
                    -8f at 200
                    6f at 270
                    -3f at 340
                },
            )
        }
    }
    AnimatedVisibility(
        visible = error != null,
        enter = expandVertically(tween(260)) + fadeIn(tween(260)),
        exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
    ) {
        Surface(
            color = Color(0xFF3A1016),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(bottom = 18.dp)
                .graphicsLayer { translationX = shake.value * density },
        ) {
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (error?.contains("joindre") == true) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
                    null,
                    tint = Color(0xFFFF8A96),
                )
                Text(error.orEmpty(), color = Color(0xFFFFD6DB), fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun AnimatedLogo(success: Boolean) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow))
    }
    val breathing = rememberInfiniteTransition(label = "logo-breath")
    val glow by breathing.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val successScale by animateFloatAsState(if (success) 1.18f else 1f, spring(dampingRatio = 0.6f), label = "success-scale")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().height(150.dp)) {
        Canvas(Modifier.size(260.dp, 150.dp)) {
            drawCircle(
                Brush.radialGradient(
                    listOf(NflixRed.copy(alpha = glow * 0.45f * entrance.value.coerceIn(0f, 1f)), Color.Transparent),
                    center = center,
                    radius = size.width / 2,
                ),
                radius = size.width / 2,
            )
        }
        Image(
            painter = painterResource(R.drawable.nflix_logo),
            contentDescription = "Nflix",
            modifier = Modifier
                .size(240.dp, 105.dp)
                .graphicsLayer {
                    val e = entrance.value
                    alpha = e.coerceIn(0f, 1f)
                    scaleX = (0.72f + 0.28f * e) * successScale
                    scaleY = scaleX
                    translationY = (1f - e) * 40f
                    rotationX = (1f - e.coerceIn(0f, 1f)) * 18f
                },
        )
        AnimatedVisibility(success, enter = scaleIn(spring(dampingRatio = 0.5f)) + fadeIn(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF7CE0B4), modifier = Modifier.size(30.dp))
        }
    }
}

/** Entrance animation with a per-item delay to build a staggered reveal. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(180L + index * 90L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + slideInVertically(tween(480, easing = FastOutSlowInEasing)) { it / 2 },
    ) {
        content()
    }
}

@Composable
private fun AnimatedBackdrop(modifier: Modifier) {
    val motion = rememberInfiniteTransition(label = "backdrop")
    val drift by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val sway by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway",
    )
    Canvas(modifier.clip(RoundedCornerShape(0.dp))) {
        val w = size.width
        val h = size.height
        drawCircle(
            Brush.radialGradient(
                listOf(NflixRed.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * (0.15f + 0.5f * drift), h * (0.08f + 0.12f * sway)),
                radius = w * 0.85f,
            ),
            radius = w * 0.85f,
            center = Offset(w * (0.15f + 0.5f * drift), h * (0.08f + 0.12f * sway)),
        )
        drawCircle(
            Brush.radialGradient(
                listOf(NflixRedDeep.copy(alpha = 0.38f), Color.Transparent),
                center = Offset(w * (0.95f - 0.4f * sway), h * (0.62f + 0.18f * drift)),
                radius = w * 0.9f,
            ),
            radius = w * 0.9f,
            center = Offset(w * (0.95f - 0.4f * sway), h * (0.62f + 0.18f * drift)),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, NflixNight.copy(alpha = 0.55f), NflixNight.copy(alpha = 0.92f)),
                startY = h * 0.25f,
                endY = h,
            ),
        )
    }
}
