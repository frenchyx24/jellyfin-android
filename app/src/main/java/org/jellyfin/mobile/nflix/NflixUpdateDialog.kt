package org.jellyfin.mobile.nflix

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.R

/** Animated update prompt shown above the library when a newer Nflix is published. */
@Composable
fun NflixUpdateDialog(updater: NflixUpdater) {
    val state by updater.state.collectAsState()
    val release = when (val s = state) {
        NflixUpdateState.Idle -> return
        is NflixUpdateState.Available -> s.release
        is NflixUpdateState.Downloading -> s.release
        is NflixUpdateState.Installing -> s.release
        is NflixUpdateState.Failed -> s.release
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = state is NflixUpdateState.Downloading || state is NflixUpdateState.Installing
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }

    Dialog(
        onDismissRequest = { if (!busy) updater.postpone(release) },
        properties = DialogProperties(dismissOnClickOutside = !busy, dismissOnBackPress = !busy),
    ) {
        Surface(
            color = Color(0xFF121218),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    alpha = appear.value.coerceIn(0f, 1f)
                    scaleX = 0.86f + 0.14f * appear.value
                    scaleY = scaleX
                    translationY = (1f - appear.value) * 80f
                },
        ) {
            Column {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xFF5C0712), Color(0xFF121218))))
                        .padding(top = 28.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(painterResource(R.drawable.nflix_logo), contentDescription = "Nflix", modifier = Modifier.size(150.dp, 66.dp))
                }
                Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.SystemUpdate, null, tint = NflixRed)
                        Text("Nflix ${release.versionName} est disponible", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("Version installée : ${BuildConfig.VERSION_NAME}", color = Color(0xFFA9A9B8), fontSize = 13.sp)
                    if (release.changelog.isNotBlank()) {
                        Text(release.changelog, color = Color(0xFFE3E3EA), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    AnimatedContent(
                        targetState = state,
                        contentKey = { it::class },
                        transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 }) togetherWith fadeOut(tween(150)) },
                        label = "update-state",
                    ) { current ->
                        when (current) {
                            is NflixUpdateState.Downloading -> {
                                val progress by animateFloatAsState(current.progress, tween(250, easing = FastOutSlowInEasing), label = "progress")
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    LinearProgressIndicator(progress, color = NflixRed, backgroundColor = Color.White.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                                    Text("Téléchargement… ${(progress * 100).toInt()} %", color = Color(0xFFA9A9B8), fontSize = 12.sp)
                                }
                            }
                            is NflixUpdateState.Installing -> Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                LinearProgressIndicator(color = NflixRed, backgroundColor = Color.White.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                                Text("Installation… confirmez si Android le demande.", color = Color(0xFFA9A9B8), fontSize = 12.sp)
                            }
                            is NflixUpdateState.Failed -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = Color(0xFFFF8A96), modifier = Modifier.size(18.dp))
                                Text(current.message, color = Color(0xFFFFD6DB), fontSize = 13.sp)
                            }
                            else -> Spacer(Modifier.height(0.dp))
                        }
                    }
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            when {
                                updater.miraConnectInstalled && updater.updateWithMiraConnect() -> Unit
                                !updater.canInstallDirectly() -> runCatching { context.startActivity(updater.installPermissionIntent()) }
                                else -> scope.launch { updater.installDirectly(release) }
                            }
                        },
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = NflixRed, contentColor = Color.White, disabledBackgroundColor = NflixRed.copy(alpha = 0.4f)),
                        elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text(
                            when {
                                state is NflixUpdateState.Failed -> "Réessayer"
                                updater.miraConnectInstalled -> "Mettre à jour avec Mira Connect"
                                else -> "Mettre à jour maintenant"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(onClick = { updater.postpone(release) }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Plus tard", color = Color(0xFFA9A9B8))
                    }
                }
            }
        }
    }
}
