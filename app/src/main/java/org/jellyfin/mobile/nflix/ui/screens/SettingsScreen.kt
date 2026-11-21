package org.jellyfin.mobile.nflix.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DataSaverOn
import androidx.compose.material.icons.rounded.FormatSize
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.SwipeRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.BuildConfig
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.NflixSeason
import org.jellyfin.mobile.nflix.ui.NflixSeasonTheme
import org.jellyfin.mobile.nflix.ui.SeasonBadge
import org.jellyfin.mobile.nflix.ui.SeasonGlyphIcon
import org.jellyfin.mobile.nflix.ui.SeasonBirthday
import org.jellyfin.mobile.nflix.ui.SeasonIntensity
import org.jellyfin.mobile.nflix.ui.SeasonScene
import org.jellyfin.mobile.utils.Constants

// ---- Building blocks (same language as Mira Connect) ----------------------------------------

internal object SettingTints {
    val Rose = Color(0xFFFF6B9A)
    val Mint = Color(0xFF46D39A)
    val Amber = Color(0xFFFFC05C)
    val Sky = Color(0xFF5AB4FF)
    val Violet = Color(0xFFB07CFF)
    val Coral = Color(0xFFFF7A59)
}

/** Fade + rise + slight scale, staggered by [index]. */
internal fun Modifier.settleIn(index: Int, step: Long = 55L): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(step * index.coerceAtMost(12)); shown = true }
    val p by animateFloatAsState(if (shown) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 220f), label = "appear")
    graphicsLayer { alpha = p.coerceIn(0f, 1f); translationY = (1f - p) * 48f; val s = 0.97f + 0.03f * p; scaleX = s; scaleY = s }
}

@Composable
internal fun GlassCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "card-press")
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)
    var base = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clip(shape)
        .background(Brush.verticalGradient(listOf(Color(0xFF1C1C22), Color(0xFF131317))))
        .border(1.dp, Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x08FFFFFF))), shape)
    if (onClick != null) base = base.clickable(interactionSource = interaction, indication = null) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick()
    }
    Column(base, content = content)
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = Nflix.Muted, fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp))
}

@Composable
internal fun IconTile(icon: ImageVector, tint: Color, alpha: Float = 1f) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = .14f * alpha)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(21.dp))
    }
}

@Composable
internal fun PrefSwitch(icon: ImageVector, tint: Color, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else .45f, label = "setting-alpha")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tint, alpha)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Nflix.Text.copy(alpha = alpha))
            Text(subtitle, fontSize = 12.sp, color = Nflix.Muted.copy(alpha = alpha), lineHeight = 17.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = Nflix.Red, checkedThumbColor = Color.White, uncheckedTrackColor = Nflix.Night, uncheckedBorderColor = Nflix.Outline),
        )
    }
}

@Composable
internal fun SettingLink(icon: ImageVector, tint: Color, title: String, subtitle: String, external: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Nflix.Text)
            Text(subtitle, fontSize = 12.sp, color = Nflix.Muted, lineHeight = 17.sp)
        }
        Icon(if (external) Icons.AutoMirrored.Rounded.OpenInNew else Icons.Rounded.ChevronRight, null, tint = Nflix.Muted, modifier = Modifier.size(if (external) 18.dp else 24.dp))
    }
}

/** Segmented choice: the selection pill slides with a spring. */
@Composable
internal fun <T> SettingChoice(icon: ImageVector, tint: Color, title: String, subtitle: String, options: List<Pair<T, String>>, selected: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    val alpha by animateFloatAsState(if (enabled) 1f else .45f, label = "choice-alpha")
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, tint, alpha)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = Nflix.Text.copy(alpha = alpha))
                Text(subtitle, fontSize = 12.sp, color = Nflix.Muted.copy(alpha = alpha), lineHeight = 17.sp)
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Nflix.Night.copy(alpha = 0.6f)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                val on = value == selected
                val bg by animateColorAsState(if (on) Nflix.Red.copy(alpha = alpha) else Color.Transparent, label = "choice-bg")
                val fg by animateColorAsState(if (on) Color.White else Nflix.Muted.copy(alpha = alpha), label = "choice-fg")
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(bg).clickable(enabled = enabled) { onSelect(value) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun SettingsHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.statusBarsPadding().padding(start = 4.dp, top = 52.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp, color = Nflix.Text)
        Text(subtitle, color = Nflix.Muted, fontSize = 14.sp, lineHeight = 22.sp)
    }
}

// ---- Settings ---------------------------------------------------------------------------------

/** App settings: appearance, video player, music, downloads, about. Same keys as the old screen. */
@Composable
fun SettingsScreen() {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("${context.packageName}_preferences", Context.MODE_PRIVATE) }
    fun bool(key: String, default: Boolean) = prefs.getBoolean(key, default)
    var landscape by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_START_LANDSCAPE_VIDEO_IN_LANDSCAPE, false)) }
    var swipe by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES, true)) }
    var brightness by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_REMEMBER_BRIGHTNESS, false)) }
    var speedUp by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, true)) }
    var background by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO, false)) }
    var horizontal by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_ALLOW_HORIZONTAL_GESTURE, true)) }
    var ass by remember { mutableStateOf(bool(Constants.PREF_EXOPLAYER_DIRECT_PLAY_ASS, true)) }
    var buffer by remember { mutableStateOf(prefs.getString(Constants.PREF_EXOPLAYER_NETWORK_BUFFER, Constants.NETWORK_BUFFER_AUTO) ?: Constants.NETWORK_BUFFER_AUTO) }
    var dismissible by remember { mutableStateOf(bool(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE, false)) }
    var download by remember { mutableStateOf(prefs.getInt(Constants.PREF_DOWNLOAD_METHOD, 0).coerceIn(0, 2)) }
    fun put(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }

    val seasonalSubtitle = NflixSeason.summary

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SettingsHeader("Réglages", "Apparence, lecture et téléchargements de Nflix.", Modifier.settleIn(0)) }

            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(1), onClick = { navigator.open(Route.Wrapped) }) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                        Text("Mes stats et Nflix Wrapped", fontWeight = FontWeight.SemiBold)
                        Text("Heures regardées, genres et séries préférés, série de jours.", fontSize = 12.sp, color = Nflix.Muted)
                    }
                }
            }
            item { SectionLabel("Apparence", Modifier.settleIn(1)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(1), onClick = { navigator.open(Route.SeasonalSettings) }) {
                    // Live miniature of the active season theme (or the automatic one when off).
                    val shown = NflixSeason.theme.takeIf { it.isSeasonal } ?: NflixSeason.automatic
                    SeasonPreview(shown, Modifier.fillMaxWidth().aspectRatio(2.6f), animated = true)
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.Celebration, shown.accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Thème saisonnier", fontWeight = FontWeight.SemiBold)
                            Text(seasonalSubtitle, fontSize = 12.sp, color = Nflix.Muted, lineHeight = 17.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Nflix.Muted)
                    }
                }
            }

            item { SectionLabel("Appareils", Modifier.settleIn(2)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(2)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        SettingLink(Icons.Rounded.Tv, SettingTints.Coral, "Connecter une TV", "Recherchez les TV Nflix sur votre Wi-Fi, ou saisissez leur code.") {
                            org.jellyfin.mobile.nflix.tv.TvLink.openDiscovery()
                        }
                    }
                }
            }

            item { SectionLabel("Lecteur vidéo", Modifier.settleIn(2)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(2)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        PrefSwitch(Icons.Rounded.ScreenRotation, SettingTints.Sky, "Démarrer en paysage", "Les vidéos horizontales s'ouvrent directement en plein écran paysage.", landscape) { landscape = it; put(Constants.PREF_EXOPLAYER_START_LANDSCAPE_VIDEO_IN_LANDSCAPE, it) }
                        PrefSwitch(Icons.Rounded.SwipeVertical, SettingTints.Violet, "Gestes luminosité et volume", "Glissez à gauche pour la luminosité, à droite pour le volume.", swipe) { swipe = it; put(Constants.PREF_EXOPLAYER_ALLOW_SWIPE_GESTURES, it) }
                        PrefSwitch(Icons.Rounded.Brightness6, SettingTints.Amber, "Retenir la luminosité", "Garde la luminosité choisie d'une vidéo à l'autre.", brightness, enabled = swipe) {
                            brightness = it; put(Constants.PREF_EXOPLAYER_REMEMBER_BRIGHTNESS, it)
                            if (!it) prefs.edit { putFloat(Constants.PREF_EXOPLAYER_BRIGHTNESS, -1f) }
                        }
                        PrefSwitch(Icons.Rounded.SwipeRight, SettingTints.Mint, "Glisser pour avancer", "Un glissement horizontal déplace la lecture.", horizontal) { horizontal = it; put(Constants.PREF_EXOPLAYER_ALLOW_HORIZONTAL_GESTURE, it) }
                        PrefSwitch(Icons.Rounded.FastForward, SettingTints.Coral, "Appui long : vitesse x2", "Maintenez l'écran pour accélérer temporairement.", speedUp) { speedUp = it; put(Constants.PREF_EXOPLAYER_ALLOW_PRESS_SPEED_UP, it) }
                        PrefSwitch(Icons.Rounded.Headphones, SettingTints.Rose, "Son en arrière-plan", "La lecture continue quand vous quittez l'application.", background) { background = it; put(Constants.PREF_EXOPLAYER_ALLOW_BACKGROUND_AUDIO, it) }
                        PrefSwitch(Icons.Rounded.Subtitles, SettingTints.Sky, "Sous-titres stylés natifs", "Affiche les sous-titres ASS/SSA avec leur mise en forme d'origine.", ass) { ass = it; put(Constants.PREF_EXOPLAYER_DIRECT_PLAY_ASS, it) }
                        SettingChoice(
                            Icons.Rounded.Memory, SettingTints.Violet, "Mémoire tampon", "Plus grand = moins de coupures sur une connexion instable.",
                            listOf(Constants.NETWORK_BUFFER_AUTO to "Auto", Constants.NETWORK_BUFFER_LARGE to "Grande", Constants.NETWORK_BUFFER_EXTRA_LARGE to "Très grande"),
                            buffer,
                        ) { buffer = it; prefs.edit { putString(Constants.PREF_EXOPLAYER_NETWORK_BUFFER, it) } }
                        SettingLink(Icons.Rounded.ClosedCaption, SettingTints.Amber, "Style des sous-titres", "Taille, couleur et fond, dans les réglages d'Android.", external = true) {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_CAPTIONING_SETTINGS)) }
                        }
                    }
                }
            }

            item { SectionLabel("Lecture 5.7", Modifier.settleIn(2)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(2)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        var ask by remember { mutableStateOf(org.jellyfin.mobile.nflix.playback.PlaybackPrefs.alwaysAsk) }
                        PrefSwitch(Icons.Rounded.VideoLibrary, SettingTints.Coral, "« Êtes-vous toujours là ? »", "Après 3 épisodes enchaînés, la lecture se met en pause et demande confirmation.", ask) { ask = it; org.jellyfin.mobile.nflix.playback.PlaybackPrefs.alwaysAsk = it }
                        var dts by remember { mutableStateOf(org.jellyfin.mobile.nflix.playback.PlaybackPrefs.doubleTapSeek) }
                        SettingChoice(
                            Icons.Rounded.FastForward, SettingTints.Mint, "Double appui : avance de",
                            "Un double appui sur la gauche ou la droite de la vidéo avance ou recule la lecture.",
                            org.jellyfin.mobile.nflix.playback.PlaybackMemory.DoubleTapSeek.entries.map { it to it.label }, dts,
                        ) { dts = it; org.jellyfin.mobile.nflix.playback.PlaybackPrefs.doubleTapSeek = it }
                        Text("Piste audio et sous-titres mémorisées par série, et reprise exacte après un crash : activés.", fontSize = 12.sp, color = Nflix.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }

            item { SectionLabel("Affichage", Modifier.settleIn(2)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(2)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        var amoled by remember { mutableStateOf(org.jellyfin.mobile.nflix.playback.PlaybackPrefs.amoledBlack) }
                        PrefSwitch(Icons.Rounded.DarkMode, SettingTints.Violet, "Noir AMOLED", "Fonds vraiment noirs, économes sur les écrans OLED.", amoled) { amoled = it; org.jellyfin.mobile.nflix.playback.PlaybackPrefs.amoledBlack = it }
                        var ts by remember { mutableStateOf(org.jellyfin.mobile.nflix.playback.PlaybackPrefs.textScale) }
                        SettingChoice(
                            Icons.Rounded.FormatSize, SettingTints.Sky, "Taille du texte",
                            "${(ts * 100).toInt()} % de la taille normale.",
                            listOf(0.85f to "Petit", 1f to "Normal", 1.15f to "Grand", 1.35f to "Très grand"), ts,
                        ) { ts = it; org.jellyfin.mobile.nflix.playback.PlaybackPrefs.textScale = it }
                    }
                }
            }

            item { SectionLabel("Données", Modifier.settleIn(2)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(2)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        var ds by remember { mutableStateOf(org.jellyfin.mobile.nflix.playback.PlaybackPrefs.dataSaver) }
                        PrefSwitch(Icons.Rounded.DataSaverOn, SettingTints.Mint, "Économiseur de données", "Les images sont téléchargées en plus petit, les aperçus et vidéos en fond sont coupés.", ds) { ds = it; org.jellyfin.mobile.nflix.playback.PlaybackPrefs.dataSaver = it }
                        var boost by remember { mutableStateOf(org.jellyfin.mobile.nflix.playback.PlaybackPrefs.dialogueBoost) }
                        PrefSwitch(Icons.Rounded.MusicNote, SettingTints.Rose, "Dialogues renforcés", "Voix plus audibles sur les films mixés trop bas.", boost) { boost = it; org.jellyfin.mobile.nflix.playback.PlaybackPrefs.dialogueBoost = it }
                        SettingsBackupRow()
                    }
                }
            }

            item { SectionLabel("Musique", Modifier.settleIn(3)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(3)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        PrefSwitch(Icons.Rounded.MusicNote, SettingTints.Rose, "Notification toujours effaçable", "Balayez la notification de lecture même pendant la musique.", dismissible) { dismissible = it; put(Constants.PREF_MUSIC_NOTIFICATION_ALWAYS_DISMISSIBLE, it) }
                    }
                }
            }

            item { SectionLabel("Téléchargements", Modifier.settleIn(4)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(4)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        SettingChoice(
                            Icons.Rounded.Download, SettingTints.Mint, "Réseau autorisé",
                            when (download) { 0 -> "Wi-Fi uniquement : aucune donnée mobile utilisée."; 1 -> "Wi-Fi et données mobiles."; else -> "Wi-Fi, données mobiles et itinérance." },
                            listOf(0 to "Wi-Fi", 1 to "Mobile", 2 to "Itinérance"), download,
                        ) { download = it; prefs.edit { putInt(Constants.PREF_DOWNLOAD_METHOD, it) } }
                    }
                }
            }

            item { SectionLabel("Application", Modifier.settleIn(5)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(5)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        SettingLink(Icons.Rounded.NotificationsActive, SettingTints.Coral, "Notifications", "Demandes, séries suivies, succès") { navigator.open(Route.AlertSettings) }
                        if (BuildConfig.STORE_UPDATES) SettingLink(Icons.Rounded.SystemUpdate, SettingTints.Mint, "Rechercher une mise à jour", "Version ${BuildConfig.VERSION_NAME}") { scope.launch { deps.updater.check(force = true) } }
                        SettingLink(Icons.Rounded.School, SettingTints.Amber, "Didacticiel", "Revoir la présentation de Nflix") { org.jellyfin.mobile.nflix.ui.tutorial.Tutorial.open() }
                        var changelog by remember { mutableStateOf(false) }
                        SettingLink(Icons.Rounded.NewReleases, SettingTints.Sky, "Nouveautés de la version", "Ce qui change dans cette version de Nflix") { changelog = true }
                        if (changelog) {
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { changelog = false },
                                title = { Text("Nouveautés — Nflix ${BuildConfig.VERSION_NAME}") },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        org.jellyfin.mobile.nflix.Changelog.entries.forEach { e ->
                                            Text(e.title, fontWeight = FontWeight.Bold)
                                            e.lines.forEach { Text("• $it", fontSize = 13.sp, color = Nflix.Muted) }
                                        }
                                    }
                                },
                                confirmButton = { TextButton(onClick = { changelog = false }) { Text("Fermer") } },
                            )
                        }
                        SettingLink(Icons.AutoMirrored.Rounded.OpenInNew, SettingTints.Sky, "Réglages Android de l'app", "Autorisations, stockage, notifications", external = true) {
                            runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        }
                    }
                }
            }
            item {
                Text("Nflix ${BuildConfig.VERSION_NAME}", color = Nflix.Muted.copy(alpha = .6f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).settleIn(6))
            }
        }
        BackButton()
    }
}

// ---- Seasonal themes --------------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeasonalSettingsScreen() {
    val navigator = LocalNavigator.current
    val theme = NflixSeason.theme
    val shown = theme.takeIf { it.isSeasonal } ?: NflixSeason.automatic
    val mode = NflixSeason.mode
    val year = NflixSeason.today.year
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { SettingsHeader("Thème saisonnier", "Saisons et fêtes françaises : décor animé, couleurs et icônes sur toute l'application.", Modifier.settleIn(0)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(1)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
                        SeasonScene(shown, Modifier.fillMaxSize(), animated = true)
                        Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 14.dp, bottom = 46.dp)) {
                            SeasonBadge(shown)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                when (mode) {
                                    NflixSeason.OFF -> "Désactivé · aperçu du thème automatique"
                                    NflixSeason.AUTO -> "Automatique · ${shown.periodIn(year)}"
                                    else -> "Choisi · ${shown.periodIn(year)}"
                                },
                                color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
            item { SectionLabel("Mode", Modifier.settleIn(2)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(2)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                        SeasonModeRow("Automatique", "Suit la date : ${NflixSeason.automatic.label} aujourd'hui. Les fêtes passent avant la saison.", mode == NflixSeason.AUTO) { NflixSeason.updateMode(NflixSeason.AUTO) }
                        SeasonModeRow("Choisir un thème", if (mode != NflixSeason.AUTO && mode != NflixSeason.OFF) "${theme.label}, quelle que soit la date." else "Touchez un thème ci-dessous.", mode != NflixSeason.AUTO && mode != NflixSeason.OFF) {
                            if (mode == NflixSeason.AUTO || mode == NflixSeason.OFF) NflixSeason.updateMode(NflixSeason.automatic.key)
                        }
                        SeasonModeRow("Désactivé", "Toujours l'apparence Nflix d'origine.", mode == NflixSeason.OFF) { NflixSeason.updateMode(NflixSeason.OFF) }
                    }
                }
            }
            item { SectionLabel("Saisons", Modifier.settleIn(3)) }
            NflixSeasonTheme.seasons.chunked(2).forEachIndexed { row, pair ->
                item(key = "season-$row") { ThemeRow(pair, mode, year, Modifier.settleIn(3 + row)) }
            }
            item { SectionLabel("Fêtes", Modifier.settleIn(5)) }
            NflixSeasonTheme.holidays.chunked(2).forEachIndexed { row, pair ->
                item(key = "holiday-$row") { ThemeRow(pair, mode, year, Modifier.settleIn(5 + row.coerceAtMost(4))) }
            }
            item { SectionLabel("Calendrier $year", Modifier.settleIn(8)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(8), onClick = { navigator.open(Route.SeasonalCalendar) }) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.CalendarMonth, shown.accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("12 prochains mois", fontWeight = FontWeight.SemiBold)
                            Text("Toutes les fêtes et saisons à venir, avec aperçu animé.", fontSize = 12.sp, color = Nflix.Muted, lineHeight = 17.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = Nflix.Muted)
                    }
                }
            }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(8)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                        org.jellyfin.mobile.nflix.ui.FrenchCalendar.year(year).forEach { (event, _) ->
                            val option = NflixSeasonTheme.forEvent(event)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { NflixSeason.updateMode(option.key) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SeasonGlyphIcon(option.decor.badgeGlyph, option, Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(option.label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(option.periodIn(year), fontSize = 12.sp, color = Nflix.Muted)
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Options", Modifier.settleIn(9)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(9)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                        PrefSwitch(Icons.Rounded.Celebration, shown.accent, "Fêtes", "Noël, 14 Juillet, Pâques… remplacent la saison en mode automatique.", NflixSeason.events, onChange = NflixSeason::updateEvents)
                        PrefSwitch(Icons.Rounded.Celebration, SettingTints.Sky, "Décor", "Ciel, coins décorés, bouton Lecture et silhouette au-dessus de la barre. Désactivé : seulement les couleurs.", NflixSeason.decorations, onChange = NflixSeason::updateDecorations)
                        PrefSwitch(Icons.Rounded.Celebration, SettingTints.Sky, "Animations", "Chauves-souris, neige, feux d'artifice… Figées seulement si « Supprimer les animations » est activé dans Android ; allégées en économie de batterie.", NflixSeason.ambient, onChange = NflixSeason::updateAmbient)
                        SettingChoice(
                            Icons.Rounded.Speed, SettingTints.Violet, "Intensité des effets",
                            "Nombre de particules et amplitude des mouvements.", 
                            SeasonIntensity.entries.map { it to it.label }, NflixSeason.intensity,
                            enabled = NflixSeason.ambient,
                        ) { NflixSeason.updateIntensity(it) }
                        PrefSwitch(
                            Icons.Rounded.VolumeUp, SettingTints.Sky, "Sons discrets",
                            "Petit carillon sur le bouton Lecture et au changement de thème. Jamais pendant la lecture, muet en silencieux.",
                            NflixSeason.sounds, enabled = NflixSeason.ambient, onChange = NflixSeason::updateSounds,
                        )
                    }
                }
            }
            item { SectionLabel("Anniversaire", Modifier.settleIn(10)) }
            item {
                GlassCard(Modifier.fillMaxWidth().settleIn(10)) {
                    var picking by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { picking = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconTile(Icons.Rounded.Cake, NflixSeasonTheme.Birthday.accent)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Date d'anniversaire", fontWeight = FontWeight.SemiBold)
                            Text(
                                NflixSeason.birthday?.let { day ->
                                    val d = SeasonBirthday.inYear(day, year)
                                    "${DateTimeFormatter.ofPattern("d MMMM", Locale.FRANCE).format(d)} · thème « Joyeux anniversaire » ce jour-là, prioritaire sur les autres."
                                } ?: "Jour et mois seulement, gardés sur cet appareil. Ballons et confettis ce jour-là.",
                                fontSize = 12.sp, color = Nflix.Muted, lineHeight = 17.sp,
                            )
                        }
                        if (NflixSeason.birthday != null) {
                            Text("Retirer", fontSize = 12.sp, color = Nflix.Muted, modifier = Modifier.clickable { NflixSeason.updateBirthday(null) })
                        }
                    }
                    if (picking) {
                        val picker = androidx.compose.material3.rememberDatePickerState(
                            initialSelectedDateMillis = NflixSeason.birthday?.let { SeasonBirthday.inYear(it, 2000).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() },
                        )
                        androidx.compose.material3.DatePickerDialog(
                            onDismissRequest = { picking = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    picker.selectedDateMillis?.let { millis ->
                                        val date = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                                        NflixSeason.updateBirthday(java.time.MonthDay.of(date.month, date.dayOfMonth))
                                    }
                                    picking = false
                                }) { Text("Enregistrer") }
                            },
                            dismissButton = { TextButton(onClick = { picking = false }) { Text("Annuler", color = Nflix.Muted) } },
                        ) {
                            androidx.compose.material3.DatePicker(
                                state = picker,
                                title = { Text("Votre anniversaire", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                                showModeToggle = false,
                            )
                        }
                    }
                }
            }
        }
        BackButton()
    }
}

@Composable
private fun ThemeRow(pair: List<NflixSeasonTheme>, mode: String, year: Int, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pair.forEach { option -> SeasonThemeCard(option, mode == option.key, year, Modifier.weight(1f)) }
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

/** Live miniature of [theme]: sky, corners, silhouette and particles. */
@Composable
private fun SeasonPreview(theme: NflixSeasonTheme, modifier: Modifier, animated: Boolean) {
    SeasonScene(theme, modifier, animated = animated)
}

@Composable
private fun SeasonModeRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 12.sp, color = Nflix.Muted, lineHeight = 17.sp)
        }
        androidx.compose.material3.RadioButton(
            selected = selected, onClick = onClick,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Nflix.Red, unselectedColor = Nflix.Muted),
        )
    }
}

@Composable
private fun SeasonThemeCard(theme: NflixSeasonTheme, selected: Boolean, year: Int, modifier: Modifier) {
    val border by animateColorAsState(if (selected) theme.accent else Color(0x18FFFFFF), label = "season-border")
    val shape = RoundedCornerShape(theme.cardRadius + 8.dp)
    Column(
        modifier.clip(shape).border(BorderStroke(if (selected) 2.dp else 1.dp, border), shape)
            .background(theme.surface).clickable { NflixSeason.updateMode(if (selected) NflixSeason.AUTO else theme.key) },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.3f)) {
            // Only the chosen card animates; the others stay still to keep the list light.
            SeasonPreview(theme, Modifier.fillMaxSize(), animated = selected)
            if (theme.key == NflixSeason.automatic.key) Text(
                "AUJOURD'HUI", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(theme.chipShape).background(theme.accent).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SeasonGlyphIcon(theme.decor.badgeGlyph, theme, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(theme.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                Text(if (selected) "Choisi · toucher pour l'auto" else theme.periodIn(year), fontSize = 11.sp, color = if (selected) theme.accentSoft else theme.muted, maxLines = 1)
            }
        }
    }
}
