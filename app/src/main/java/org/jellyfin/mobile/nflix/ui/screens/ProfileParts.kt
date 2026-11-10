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
import androidx.compose.ui.unit.sp
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
    val initials = remember(displayName) { org.jellyfin.mobile.nflix.MiraProfile.initials(displayName) }
    Column(modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 18.dp, top = 56.dp, bottom = 4.dp)) {
        Text("Profil", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.8).sp, color = Nflix.Text)
        Spacer(Modifier.height(16.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).border(2.dp, ringColor.copy(alpha = 0.8f), CircleShape).padding(3.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(58.dp).clip(CircleShape).background(Nflix.SurfaceHigh), contentAlignment = Alignment.Center) {
                        org.jellyfin.mobile.nflix.ui.AvatarImage(avatar, Modifier.size(58.dp).clip(CircleShape)) {
                            Text(initials, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ringColor)
                        }
                    }
                }
                Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(displayName ?: "", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Nflix.Text)
                    if (email != null) Text(email, fontSize = 12.sp, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (plan != MiraPlan.NONE) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            plan.label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = plan.color,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(plan.color.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Three big shortcuts under the header. */
@Composable
internal fun ProfileShortcuts(entries: List<ProfileEntry>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { entry ->
            GlassCard(Modifier.weight(1f), onClick = entry.onClick) {
                Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconTile(entry.icon, entry.accent)
                    Text(entry.title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Nflix.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Titled glass card of rows, no dividers. */
@Composable
internal fun ProfileSection(title: String, entries: List<ProfileEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
        SectionLabel(title)
        Spacer(Modifier.height(6.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                entries.forEach { entry -> androidx.compose.runtime.key(entry.key) { ProfileEntryRow(entry) } }
            }
        }
    }
}

@Composable
private fun ProfileEntryRow(entry: ProfileEntry) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) Color.White.copy(alpha = 0.05f) else Color.Transparent, tween(140), label = "profile-row-bg")
    val nudge by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "profile-row-chevron")
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = entry.onClick)
            .padding(horizontal = 4.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(entry.icon, entry.accent)
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, fontWeight = FontWeight.SemiBold, color = Nflix.Text, fontSize = 15.sp)
            if (entry.subtitle != null) Text(entry.subtitle, fontSize = 12.sp, color = Nflix.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Nflix.Muted,
            modifier = Modifier.size(22.dp).graphicsLayer { translationX = nudge * 6.dp.toPx(); alpha = 0.55f + 0.45f * nudge },
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
