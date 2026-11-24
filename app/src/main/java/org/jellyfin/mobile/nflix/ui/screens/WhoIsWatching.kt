package org.jellyfin.mobile.nflix.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.nflix.data.HomeCache
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.data.LibrarySort
import org.jellyfin.mobile.nflix.data.NflixApi
import org.jellyfin.mobile.nflix.family.FamilyException
import org.jellyfin.mobile.nflix.family.FamilyMe
import org.jellyfin.mobile.nflix.family.FamilyMemberInfo
import org.jellyfin.mobile.nflix.family.ProfileSwitchLogic
import org.jellyfin.mobile.nflix.ui.AnimatedNflixLogo
import org.jellyfin.mobile.nflix.ui.AvatarImage
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.LocalNavigator
import org.jellyfin.mobile.nflix.ui.Nflix
import org.jellyfin.mobile.nflix.ui.Route
import org.jellyfin.mobile.nflix.ui.SeasonAmbience
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts

// "Qui regarde ?" and its PIN pad, drawn after Nflix Web (docs/qui-regarde/SPEC.md in nflix-web).

private val PickerBg = Color(0xFF050505)
private val SubtitleGrey = Color(0xFFB9B9C4)
private val NameGrey = Color(0xFFC9C9D2)
private val OutlineGrey = Color(0xFF8F8F9A)
private val PinErrorRed = Color(0xFFFF6B7A)
private val PopEasing = CubicBezierEasing(.2f, .9f, .25f, 1.2f)
private val SmoothEasing = CubicBezierEasing(.2f, .8f, .2f, 1f)

private fun profileColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex ?: "#E50914")) }.getOrDefault(Nflix.BrandRed)

/** False when the user turned system animations off (accessibility "remove animations"). */
@Composable
private fun rememberMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Full-screen profile picker for family heads ("Qui regarde ?"). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WhoIsWatchingScreen(me: FamilyMe, onDone: () -> Unit) {
    val deps = LocalDeps.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val motion = rememberMotion()
    var pinFor by remember { mutableStateOf<FamilyMemberInfo?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var hot by remember { mutableStateOf<String?>(null) }
    val members = me.family?.members.orEmpty()

    val title = remember { Animatable(if (motion) 0f else 1f) }
    val subtitle = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        launch { title.animateTo(1f, tween(700, easing = SmoothEasing)) }
        launch { subtitle.animateTo(1f, tween(700, delayMillis = 80, easing = SmoothEasing)) }
    }

    Box(Modifier.fillMaxSize().background(PickerBg).clickable(enabled = false) {}) {
        if (motion) PosterWall(Modifier.fillMaxSize())
        Veil(Modifier.fillMaxSize())
        SeasonAmbience(Modifier.fillMaxSize(), animated = motion, topGlow = false)

        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Box(
                    Modifier.height(40.dp).drawBehind {
                        drawCircle(
                            Brush.radialGradient(listOf(Nflix.BrandRed.copy(alpha = .35f), Color.Transparent), center = center, radius = size.width * .7f),
                            radius = size.width * .7f,
                        )
                    },
                    contentAlignment = Alignment.CenterStart,
                ) { AnimatedNflixLogo(deps.plugins.serverLogoUrl, compact = true) }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "Qui regarde ?",
                style = TextStyle(
                    fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = (-.01).em,
                    shadow = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 8f), 40f),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp).graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 30.dp.toPx()
                },
            )
            Text(
                "Choisissez votre profil pour retrouver vos titres, votre liste et vos reprises.",
                color = SubtitleGrey, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 28.dp, end = 28.dp, top = 10.dp, bottom = 32.dp).graphicsLayer {
                    alpha = subtitle.value
                    translationY = (1f - subtitle.value) * 30.dp.toPx()
                },
            )
            FlowRow(
                Modifier.widthIn(max = 1100.dp).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                members.forEachIndexed { index, member ->
                    val isMe = ProfileSwitchLogic.sameUser(member.userId, me.userId)
                    PickerTile(
                        key = member.userId, index = index, hot = hot, motion = motion, onHot = { hot = it },
                        onClick = {
                            when {
                                deps.switcher.busy -> Unit
                                isMe -> onDone()
                                member.hasPin -> { pinError = null; pinFor = member }
                                else -> scope.launch {
                                    try {
                                        deps.switcher.switchTo(member, null)
                                    } catch (e: FamilyException) {
                                        Toasts.show(e.message ?: "Erreur", ToastKind.Warning)
                                    }
                                }
                            }
                        },
                        label = member.displayName,
                        current = isMe,
                        glow = profileColor(member.avatarColor),
                        frame = { active -> ProfilePic(member, 120.dp, 18.dp, shine = active && motion) },
                        chip = {
                            when {
                                isMe -> CornerChip(Icons.Rounded.Check, "Profil actuel", Nflix.BrandRed)
                                member.hasPin -> CornerChip(Icons.Rounded.Lock, "Protégé par un code PIN", Color(0xFF111111))
                            }
                        },
                        badges = {
                            if (member.isKid) Pill("Enfant", Brush.horizontalGradient(listOf(Color(0xFFFFB347), Color(0xFFFF5FA2))), Color.White)
                            if (member.isHead) Pill("Principal", Brush.linearGradient(listOf(Color.White.copy(alpha = .1f), Color.White.copy(alpha = .1f))), Color(0xFFDDDDDD))
                            if (isMe) Pill("En cours", Brush.linearGradient(listOf(Nflix.BrandRed, Nflix.BrandRed)), Color.White)
                        },
                    )
                }
                if (me.isHead) {
                    PickerTile(
                        key = "+add", index = members.size, hot = hot, motion = motion, onHot = { hot = it },
                        onClick = { onDone(); navigator.open(Route.Family) },
                        label = "Ajouter un profil", current = false, glow = null,
                        frame = { active -> AddFrame(active) }, chip = {}, badges = {},
                    )
                }
            }
            if (deps.switcher.busy) {
                Spacer(Modifier.height(20.dp))
                CircularProgressIndicator(color = Nflix.BrandRed, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(56.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (me.family != null) OutlineButton("Gérer les profils") { onDone(); navigator.open(Route.Family) }
                OutlineButton("Paramètres") { onDone(); navigator.open(Route.Settings) }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Ne plus demander au démarrage", color = SubtitleGrey.copy(alpha = .7f), fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { deps.switcher.store.pickerEnabled = false; onDone() }.padding(8.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    pinFor?.let { member ->
        PinDialog(member, error = pinError, busy = deps.switcher.busy, onDismiss = { pinFor = null }) { pin ->
            scope.launch {
                pinError = null
                try {
                    deps.switcher.switchTo(member, pin)
                    pinFor = null
                } catch (e: FamilyException) {
                    pinError = e.message ?: "Code PIN incorrect."
                }
            }
        }
    }
}

// ------------------------------------------------------------------ backdrop

/** Three tilted, blurred rows of artwork drifting sideways, as on Nflix Web. */
@Composable
private fun PosterWall(modifier: Modifier) {
    val urls = rememberWallArt()
    if (urls.isEmpty()) return
    val blur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val loop = rememberInfiniteTransition(label = "wall")
    val rows = listOf(90_000, 110_000, 130_000).map { ms ->
        loop.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "wall-row")
    }
    BoxWithConstraints(modifier.graphicsLayer { alpha = .35f; clip = true }, contentAlignment = Alignment.Center) {
        val side = maxOf(maxWidth, maxHeight) * 1.5f
        Column(
            Modifier.requiredSize(side).graphicsLayer { rotationZ = -8f }.then(if (blur) Modifier.blur(3.dp) else Modifier),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        ) {
            rows.forEachIndexed { r, phase ->
                val slice = List(8) { i -> urls[(r * 8 + i) % urls.size] }
                WallRow(slice, phase, reverse = r == 1)
            }
        }
    }
}

@Composable
private fun WallRow(urls: List<String>, phase: State<Float>, reverse: Boolean) {
    val tileWidth = 240.dp
    val gap = 14.dp
    val density = LocalDensity.current
    val span = with(density) { ((tileWidth + gap) * urls.size).toPx() }
    Row(
        Modifier.fillMaxWidth().wrapUnbounded().graphicsLayer {
            val p = if (reverse) 1f - phase.value else phase.value
            translationX = -p * span
        },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        (urls + urls + urls).forEach { url ->
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.width(tileWidth).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF15151A)),
            )
        }
    }
}

private fun Modifier.wrapUnbounded(): Modifier =
    this.then(Modifier.wrapContentWidthStart())

private fun Modifier.wrapContentWidthStart(): Modifier =
    this.wrapContentWidth(Alignment.Start, unbounded = true)

/** Artwork from the home cache, topped up with random movies and series. */
@Composable
private fun rememberWallArt(): List<String> {
    val deps = LocalDeps.current
    val context = LocalContext.current
    var urls by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        val cached = runCatching { HomeCache.read(context, deps.api.userId) }.getOrNull()
        var pool: List<Item> = cached?.let { it.hero + it.rows.flatMap { row -> row.items } }.orEmpty()
        if (pool.size < 16) {
            pool = pool + runCatching { deps.jellyfin.items(includeTypes = "Movie,Series", sort = LibrarySort.Random, limit = 40).items }.getOrDefault(emptyList())
        }
        urls = pool.asSequence()
            .filter { !it.id.startsWith("yt:") }
            .mapNotNull { wallUrl(deps.api, it) }
            .distinct().toList().shuffled().take(24)
    }
    return urls
}

private fun wallUrl(api: NflixApi, item: Item): String? = when {
    item.backdropTags.isNotEmpty() -> api.imageUrl(item.id, "Backdrop", item.backdropTags.first(), 480)
    item.parentBackdropItemId != null && item.parentBackdropTags.isNotEmpty() ->
        api.imageUrl(item.parentBackdropItemId, "Backdrop", item.parentBackdropTags.first(), 480)
    item.imageTags["Primary"] != null -> api.imageUrl(item.id, "Primary", item.imageTags["Primary"], 360)
    else -> null
}

/** Radial vignette plus top and bottom fades over the wall. Without blur (before Android 12) it is darker. */
@Composable
private fun Veil(modifier: Modifier) {
    val boost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0f else .2f
    Box(
        modifier.drawBehind {
            val radius = maxOf(size.width, size.height) * .75f
            drawRect(
                Brush.radialGradient(
                    0f to PickerBg.copy(alpha = (.55f + boost).coerceAtMost(1f)),
                    .7f to PickerBg.copy(alpha = (.92f + boost).coerceAtMost(1f)),
                    1f to PickerBg,
                    center = center, radius = radius,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    0f to PickerBg.copy(alpha = .9f), .18f to Color.Transparent,
                    .82f to Color.Transparent, 1f to PickerBg,
                ),
            )
        },
    )
}

// ------------------------------------------------------------------ tiles

/** One tile of the picker: entrance pop, lift and ring on focus or press, others dimmed. */
@Composable
private fun PickerTile(
    key: String,
    index: Int,
    hot: String?,
    motion: Boolean,
    onHot: (String?) -> Unit,
    onClick: () -> Unit,
    label: String,
    current: Boolean,
    glow: Color?,
    frame: @Composable (active: Boolean) -> Unit,
    chip: @Composable () -> Unit,
    badges: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = pressed || focused || hovered
    LaunchedEffect(active) {
        if (active) onHot(key) else if (hot == key) onHot(null)
    }
    val lift by animateFloatAsState(if (active) 1f else 0f, tween(if (motion) 300 else 0, easing = SmoothEasing), label = "tile-lift")
    val dim by animateFloatAsState(if (hot != null && hot != key) .5f else 1f, tween(if (motion) 250 else 0), label = "tile-dim")
    val entrance = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        delay(120L + 70L * index)
        entrance.animateTo(1f, tween(600, easing = PopEasing))
    }
    val density = LocalDensity.current
    Column(
        Modifier.width(132.dp)
            .graphicsLayer {
                val e = entrance.value
                alpha = e.coerceIn(0f, 1f) * dim
                translationY = (1f - e) * 30.dp.toPx()
                val s = .85f + .15f * e
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.padding(top = 10.dp).size(120.dp)
                .graphicsLayer {
                    translationY = -8.dp.toPx() * lift
                    val s = 1f + .06f * lift
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    val radius = with(density) { 18.dp.toPx() }
                    // Resting shadow, then the profile-colour glow and white ring when active.
                    drawRoundRect(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = .5f), Color.Transparent), center = center + Offset(0f, 10.dp.toPx()), radius = size.width * .8f),
                        topLeft = Offset(-30.dp.toPx(), -20.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width + 60.dp.toPx(), size.height + 60.dp.toPx()),
                    )
                    if (lift > 0f) {
                        if (glow != null) {
                            val r = size.width * .5f + 60.dp.toPx()
                            drawCircle(
                                Brush.radialGradient(listOf(glow.copy(alpha = .55f * lift), Color.Transparent), center = center + Offset(0f, 22.dp.toPx()), radius = r),
                                radius = r, center = center + Offset(0f, 22.dp.toPx()),
                            )
                        }
                        val ring = 3.dp.toPx()
                        drawRoundRect(
                            Color.White.copy(alpha = lift),
                            topLeft = Offset(-ring, -ring),
                            size = androidx.compose.ui.geometry.Size(size.width + ring * 2, size.height + ring * 2),
                            cornerRadius = CornerRadius(radius + ring),
                        )
                    }
                },
        ) {
            frame(active)
            Box(Modifier.align(Alignment.BottomEnd).offset(8.dp, 8.dp)) { chip() }
        }
        Spacer(Modifier.height(14.dp))
        val nameColor by animateColorAsState(if (active || current) Color.White else NameGrey, tween(200), label = "tile-name")
        Text(label, color = nameColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) { badges() }
    }
}

/** Square profile picture: user photo, or a lit gradient of the profile colour with the initial. */
@Composable
internal fun ProfilePic(member: FamilyMemberInfo, size: Dp, corner: Dp, shine: Boolean = false) {
    val deps = LocalDeps.current
    // Mira Connect photo first (also for other members, once they signed in through Mira), then the
    // Jellyfin photo, then the colour and initials.
    val image = if (member.hasImage) deps.api.userImageUrl(member.userId, null) else null
    val c = profileColor(member.avatarColor)
    val light = lerp(c, Color.White, .3f)
    val dark = lerp(c, Color.Black, .45f)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(shine) {
        if (shine) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(800, easing = SmoothEasing))
        } else {
            sweep.snapTo(0f)
        }
    }
    val initial = member.displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val fontSize = with(LocalDensity.current) { (size * .46f).toSp() }
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner))
            .drawBehind {
                val spot = Offset(size.toPx() * .3f, size.toPx() * .25f)
                drawRect(Brush.radialGradient(0f to light, .45f to c, 1f to dark, center = spot, radius = size.toPx() * 1.03f))
            }
            .drawWithContent {
                drawContent()
                val p = sweep.value
                if (p > 0f && p < 1f) {
                    val w = this.size.width
                    val x = -w + p * w * 2.5f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = .28f), Color.Transparent),
                            start = Offset(x, 0f), end = Offset(x + w * .6f, this.size.height),
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        org.jellyfin.mobile.nflix.ui.ProfilePicture(member.userId, image, Modifier.fillMaxSize()) {
            Text(initial, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Black, lineHeight = fontSize)
        }
    }
}

@Composable
private fun CornerChip(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, background: Color) {
    Box(
        Modifier.size(38.dp).clip(CircleShape).background(background).border(2.dp, Color.White.copy(alpha = .85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, description, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun Pill(text: String, background: Brush, color: Color) {
    Text(
        text.uppercase(), color = color, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .04.em, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(background).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Dashed "Ajouter un profil" frame. */
@Composable
private fun AddFrame(active: Boolean) {
    val tint by animateColorAsState(if (active) Color.White else Color.White.copy(alpha = .25f), tween(200), label = "add-border")
    val icon by animateColorAsState(if (active) Color.White else Color(0xFF9A9AA4), tween(200), label = "add-icon")
    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .03f)).drawBehind {
            val stroke = 2.dp.toPx()
            drawRoundRect(
                tint,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
            )
        },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Ajouter un profil", tint = icon, modifier = Modifier.size(64.dp)) }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val active = pressed || focused || hovered
    val border by animateColorAsState(if (active) Color.White else OutlineGrey, tween(200), label = "outline-border")
    val color by animateColorAsState(if (active) Color.White else SubtitleGrey, tween(200), label = "outline-text")
    Text(
        text.uppercase(), color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .12.em,
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).border(1.5.dp, border, RoundedCornerShape(4.dp))
            .background(if (active) Color.White.copy(alpha = .06f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 12.dp),
    )
}

// ------------------------------------------------------------------ PIN pad

/** Numeric PIN pad (4 to 8 digits): frosted card, profile picture, dots that shake on a wrong code. */
@Composable
fun PinDialog(member: FamilyMemberInfo, error: String?, busy: Boolean, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val motion = rememberMotion()
    var pin by remember { mutableStateOf("") }
    val shake = remember { Animatable(0f) }
    val pop = remember { Animatable(if (motion) 0f else 1f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        if (motion) pop.animateTo(1f, tween(350, easing = PopEasing))
    }
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
            if (motion) {
                repeat(3) {
                    shake.animateTo(10f, tween(45))
                    shake.animateTo(-10f, tween(45))
                }
                shake.animateTo(0f, tween(45))
            }
        }
    }
    fun type(key: String) {
        if (busy) return
        when (key) {
            "<" -> pin = pin.dropLast(1)
            "ok" -> if (pin.length >= 4) onSubmit(pin)
            else -> if (pin.length < 8) pin += key
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        val density = LocalDensity.current
        LaunchedEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = with(density) { 14.dp.roundToPx() } }
                window.setDimAmount(.6f)
            } else {
                window.setDimAmount(.8f)
            }
        }
        Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.Center) {
            Box(
                Modifier.padding(16.dp).widthIn(max = 400.dp).fillMaxWidth()
                    .graphicsLayer {
                        val p = pop.value
                        alpha = p.coerceIn(0f, 1f)
                        val s = .85f + .15f * p
                        scaleX = s
                        scaleY = s
                        translationY = (1f - p) * 30.dp.toPx()
                    }
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.linearGradient(listOf(Color(40, 40, 46).copy(alpha = .9f), Color(16, 16, 20).copy(alpha = .95f)), start = Offset.Zero, end = Offset(400f, 1400f)))
                    .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(26.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .focusRequester(focus)
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val digit = digitOf(e.key)
                        when {
                            digit != null -> { type(digit); true }
                            e.key == Key.Backspace || e.key == Key.Delete -> { type("<"); true }
                            e.key == Key.Enter || e.key == Key.NumPadEnter -> { type("ok"); true }
                            e.key == Key.Escape -> { onDismiss(); true }
                            else -> false
                        }
                    }
                    .focusable(),
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Icon(Icons.Rounded.Close, "Fermer", tint = Color.White)
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 34.dp, bottom = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ProfilePic(member, 96.dp, 16.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(member.displayName, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text("Saisissez le code PIN du profil", color = SubtitleGrey, fontSize = 15.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Row(Modifier.graphicsLayer { translationX = shake.value.dp.toPx() }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        repeat(maxOf(4, pin.length)) { i -> PinDot(filled = i < pin.length, motion = motion) }
                    }
                    Text(
                        error ?: " ", color = PinErrorRed, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp).height(20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("<", "0", "ok")).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { key -> PinKey(key, enabled = !busy && (key != "ok" || pin.length >= 4), busy = busy && key == "ok") { type(key) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun digitOf(key: Key): String? = when (key) {
    Key.Zero, Key.NumPad0 -> "0"
    Key.One, Key.NumPad1 -> "1"
    Key.Two, Key.NumPad2 -> "2"
    Key.Three, Key.NumPad3 -> "3"
    Key.Four, Key.NumPad4 -> "4"
    Key.Five, Key.NumPad5 -> "5"
    Key.Six, Key.NumPad6 -> "6"
    Key.Seven, Key.NumPad7 -> "7"
    Key.Eight, Key.NumPad8 -> "8"
    Key.Nine, Key.NumPad9 -> "9"
    else -> null
}

@Composable
private fun PinDot(filled: Boolean, motion: Boolean) {
    val grow by animateFloatAsState(if (filled) 1.15f else 1f, tween(if (motion) 150 else 0), label = "pin-dot")
    Box(
        Modifier.size(16.dp).graphicsLayer { scaleX = grow; scaleY = grow }.clip(CircleShape)
            .background(if (filled) Color.White else Color.Transparent)
            .border(2.dp, if (filled) Color.White else Color.White.copy(alpha = .5f), CircleShape),
    )
}

@Composable
private fun PinKey(key: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val active = (pressed || focused) && enabled
    val scale by animateFloatAsState(if (pressed) .94f else 1f, tween(100), label = "pin-key")
    val background = when (key) {
        "ok" -> Nflix.BrandRed.copy(alpha = if (enabled || busy) 1f else .35f)
        "<" -> if (active) Color.White.copy(alpha = .14f) else Color.Transparent
        else -> Color.White.copy(alpha = if (active) .2f else .08f)
    }
    Box(
        Modifier.size(64.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(background)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (key) {
            "<" -> Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Effacer", tint = Color.White, modifier = Modifier.size(28.dp))
            "ok" -> if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Icon(Icons.Rounded.Check, "Valider", tint = Color.White.copy(alpha = if (enabled) 1f else .45f), modifier = Modifier.size(30.dp))
            else -> Text(key, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}
