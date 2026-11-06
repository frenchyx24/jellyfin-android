package org.jellyfin.mobile.nflix.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.nflix.ui.Nflix

/** Accent colours of the profile rows: one hue per entry so the list scans at a glance. */
internal object ProfileAccents {
    val Admin = Color(0xFFFF8A65)
    val Badges = Color(0xFFFFC05C)
    val Downloads = Color(0xFF46D39A)
    val Horus = Color(0xFFB388FF)
    val Releases = Color(0xFF6CB6FF)
    val Alerts = Color(0xFFFF6B9A)
    val Notices = Color(0xFFFFA94D)
    val Playback = Color(0xFF4DD0E1)
    val Updates = Color(0xFF8BD17C)
    val Mira = Color(0xFF8C9EFF)
    val Web = Color(0xFF64B5F6)
    val Tutorial = Color(0xFFFFD54F)
}

/** One tappable entry of a profile section. */
@Immutable
internal class ProfileEntry(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val accent: Color,
    val onClick: () -> Unit,
)

private const val EntranceTotalMs = 1200f
private const val EntranceStepMs = 80f
private const val EntranceItemMs = 440f

/**
 * Screen-wide entrance clock. It runs once per visit, so items scrolled back into view
 * do not replay their animation.
 */
@Composable
internal fun rememberProfileEntrance(): Animatable<Float, AnimationVector1D> {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(1f, tween(EntranceTotalMs.toInt(), easing = LinearEasing)) }
    return clock
}

/** Staggered fade + rise driven by [clock]; read in the draw phase only (no recomposition). */
internal fun Modifier.staggerIn(clock: Animatable<Float, AnimationVector1D>, index: Int): Modifier = graphicsLayer {
    val elapsed = clock.value * EntranceTotalMs - index * EntranceStepMs
    val t = FastOutSlowInEasing.transform((elapsed / EntranceItemMs).coerceIn(0f, 1f))
    alpha = t
    translationY = (1f - t) * 28.dp.toPx()
}

@Composable
internal fun ProfileHeader(
    displayName: String?,
    email: String?,
    avatar: String?,
    plan: MiraPlan,
    viaMira: Boolean,
    serverHost: String,
    version: String,
    modifier: Modifier = Modifier,
) {
    val ringColor = if (plan != MiraPlan.NONE) plan.color else Nflix.Red
    val ring = remember(ringColor) {
        Brush.sweepGradient(listOf(ringColor, ringColor.copy(alpha = 0.25f), Color.White.copy(alpha = 0.7f), ringColor.copy(alpha = 0.25f), ringColor))
    }
    val glow = remember(ringColor) { Brush.verticalGradient(listOf(ringColor.copy(alpha = 0.22f), Color.Transparent)) }
    val initials = remember(displayName) { org.jellyfin.mobile.nflix.MiraProfile.initials(displayName) }
    Box(modifier.fillMaxWidth().background(glow)) {
        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, bottom = 22.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(116.dp).border(3.dp, ring, CircleShape).padding(6.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
                    // Same order as Authentik's avatar setting: attributes.avatar, then initials.
                    org.jellyfin.mobile.nflix.ui.AvatarImage(avatar, Modifier.size(104.dp).clip(CircleShape)) {
                        Text(initials, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ringColor)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(displayName ?: "", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (email != null) {
                Text(email, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(12.dp))
            if (plan != MiraPlan.NONE) {
                Surface(color = plan.color.copy(alpha = 0.16f), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, plan.color.copy(alpha = 0.5f))) {
                    Text(
                        "Abonnement ${plan.label}",
                        style = MaterialTheme.typography.labelLarge,
                        color = plan.color,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatChip(if (viaMira) "Mira Connect" else "Identifiants", if (viaMira) ProfileAccents.Mira else Nflix.Muted)
                StatChip(serverHost, Nflix.Info)
                StatChip("v$version", Nflix.Success)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (viaMira) "Connecté avec Mira Connect" else "Connecté avec vos identifiants",
                style = MaterialTheme.typography.bodySmall,
                color = Nflix.Muted,
            )
        }
    }
}

@Composable
private fun StatChip(text: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Nflix.Text.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Titled, rounded group of rows separated by hairlines. */
@Composable
internal fun ProfileSection(title: String, entries: List<ProfileEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Nflix.Muted,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Nflix.Surface)
                .border(1.dp, Nflix.Outline.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        ) {
            entries.forEachIndexed { i, entry ->
                androidx.compose.runtime.key(entry.key) {
                    ProfileEntryRow(entry)
                }
                if (i < entries.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 66.dp), thickness = 0.5.dp, color = Nflix.Outline)
                }
            }
        }
    }
}

@Composable
private fun ProfileEntryRow(entry: ProfileEntry) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) Nflix.SurfaceHigh else Color.Transparent, tween(140), label = "profile-row-bg")
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "profile-row-scale")
    val nudge by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "profile-row-chevron")
    val tile = remember(entry.accent) { Brush.linearGradient(listOf(entry.accent.copy(alpha = 0.32f), entry.accent.copy(alpha = 0.12f))) }
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = entry.onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(tile), contentAlignment = Alignment.Center) {
            Icon(entry.icon, null, tint = entry.accent, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp)) {
            Text(entry.title, style = MaterialTheme.typography.titleSmall, color = Nflix.Text)
            if (entry.subtitle != null) {
                Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            null,
            tint = Nflix.Muted,
            modifier = Modifier.size(22.dp).graphicsLayer {
                translationX = nudge * 6.dp.toPx()
                alpha = 0.6f + 0.4f * nudge
            },
        )
    }
}

/** Destructive action kept apart at the bottom of the page. */
@Composable
internal fun ProfileDangerRow(icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(Nflix.BrandRed.copy(alpha = if (pressed) 0.22f else 0.10f), tween(140), label = "profile-danger-bg")
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f, stiffness = 700f), label = "profile-danger-scale")
    Row(
        modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, Nflix.BrandRed.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Nflix.BrandRed, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Nflix.BrandRed)
    }
}
