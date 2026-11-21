package org.jellyfin.mobile.nflix.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.nflix.data.Item
import org.jellyfin.mobile.nflix.family.ChallengeInfo
import org.jellyfin.mobile.nflix.family.FamilyMe
import org.jellyfin.mobile.nflix.family.GeneratedAvatar
import org.jellyfin.mobile.nflix.family.ListInfo
import org.jellyfin.mobile.nflix.family.NightInfo
import org.jellyfin.mobile.nflix.family.SocialLinks
import org.jellyfin.mobile.nflix.family.WallInfo
import org.jellyfin.mobile.nflix.ui.LocalDeps
import org.jellyfin.mobile.nflix.ui.ToastKind
import org.jellyfin.mobile.nflix.ui.Toasts

private suspend fun safe(block: suspend () -> Unit): Boolean = try { block(); true } catch (e: Exception) {
    Toasts.show(e.message ?: "Action impossible.", ToastKind.Warning); false
}

/** Authenticated chat image (photo or animated GIF shows its first frame). */
@Composable
fun ChatImage(imageId: String) {
    val deps = LocalDeps.current
    val bitmap by produceState<ImageBitmap?>(null, imageId) {
        value = runCatching {
            val bytes = deps.social15.image(imageId)
            withContext(Dispatchers.Default) {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1080) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
            }
        }.getOrNull()
    }
    val b = bitmap
    if (b == null) Text("📷 Chargement…", color = Color.White, fontSize = 13.sp)
    else Image(b, "Image", contentScale = ContentScale.Fit, modifier = Modifier.widthIn(max = 260.dp).heightIn(max = 320.dp).clip(RoundedCornerShape(12.dp)))
}

/** Original generated avatar: two colours, one shape, the initials. */
@Composable
fun GeneratedAvatarArt(seed: String, initials: String, size: Int) {
    val spec = remember(seed) { GeneratedAvatar.spec(seed) }
    Box(Modifier.size(size.dp).background(Color(spec.background)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp).rotate(spec.rotation.toFloat())) {
            val c = Color(spec.accent).copy(alpha = .75f)
            when (spec.shape) {
                0 -> drawCircle(c, radius = this.size.minDimension * .42f, center = Offset(this.size.width * .75f, this.size.height * .2f))
                1 -> drawRect(c, topLeft = Offset(this.size.width * .45f, 0f), size = Size(this.size.width * .6f, this.size.height))
                2 -> drawCircle(c, radius = this.size.minDimension * .6f, center = Offset(0f, this.size.height))
                else -> drawRect(c, topLeft = Offset(0f, this.size.height * .55f), size = Size(this.size.width, this.size.height * .2f))
            }
        }
        Text(initials, fontSize = (size / 3).sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

fun qrBitmap(text: String, px: Int = 512): android.graphics.Bitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, px, px)
    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.RGB_565)
    val pixels = IntArray(px * px)
    for (y in 0 until px) for (x in 0 until px) pixels[y * px + x] = if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    bmp.setPixels(pixels, 0, px, 0, 0, px, px)
    return bmp
}

private fun share(context: android.content.Context, text: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Family 1.5 block: challenges, movie nights, invite link/QR, lists, groups, friends wall. */
@Composable
fun FamilyMoreSection(me: FamilyMe, switched: Boolean) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    var challenges by remember { mutableStateOf<List<ChallengeInfo>>(emptyList()) }
    var nights by remember { mutableStateOf<List<NightInfo>>(emptyList()) }
    var lists by remember { mutableStateOf<List<ListInfo>>(emptyList()) }
    var wall by remember { mutableStateOf(WallInfo()) }
    var qr by remember { mutableStateOf<String?>(null) }
    var planOpen by remember { mutableStateOf(false) }
    var listOpen by remember { mutableStateOf(false) }
    var groupOpen by remember { mutableStateOf(false) }
    LaunchedEffect(me.userId, reload) {
        challenges = deps.social15.challenges()
        nights = deps.social15.nights()
        lists = deps.social15.lists()
        wall = deps.social15.wall()
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (challenges.isNotEmpty()) {
            Text("Défis de la famille", fontWeight = FontWeight.SemiBold)
            challenges.forEach { c ->
                Text((if (c.done) "🏆 " else "") + c.title + " · ${c.progress.coerceAtMost(c.goal)}/${c.goal}", fontSize = 13.sp)
                LinearProgressIndicator(progress = { (c.progress.toFloat() / c.goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Soirées cinéma prévues", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { planOpen = true }) { Text("Planifier") }
        }
        if (nights.isEmpty()) Text("Aucune soirée prévue.", fontSize = 13.sp)
        nights.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(n.title, fontWeight = FontWeight.Medium)
                    Text(FamilyNightFormat.label(n.startUtc) + " · " + n.going.size + " participant(s)", fontSize = 12.sp)
                }
                TextButton(onClick = { scope.launch { if (safe { deps.social15.toggleGoing(n.id) }) reload++ } }) { Text(if (n.iAmGoing) "Je ne viens plus" else "Je viens") }
                if (n.mine || me.isHead) TextButton(onClick = { scope.launch { if (safe { deps.social15.cancelNight(n.id) }) reload++ } }) { Text("Annuler") }
            }
        }
        if (me.isHead && !switched) {
            TextButton(onClick = {
                scope.launch {
                    runCatching { deps.social15.createInviteLink() }
                        .onSuccess { qr = SocialLinks.inviteLink(deps.api.baseUrl, it) }
                        .onFailure { Toasts.show(it.message ?: "Lien indisponible.", ToastKind.Warning) }
                }
            }) { Text("Inviter par lien ou QR code") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Listes partagées", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { listOpen = true }) { Text("Nouvelle liste") }
        }
        lists.forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${l.name} · ${l.items.size} titre(s) · " + when (l.visibility) { "family" -> "famille"; "public" -> "publique"; else -> "privée" }, modifier = Modifier.weight(1f), fontSize = 13.sp)
                l.publicToken?.let { t -> TextButton(onClick = { share(context, SocialLinks.publicListLink(deps.api.baseUrl, t)) }) { Text("Partager") } }
            }
        }
        TextButton(onClick = { groupOpen = true }) { Text("Créer un groupe d'amis") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Partager mon activité avec mes amis", modifier = Modifier.weight(1f))
            Switch(checked = wall.optedIn, onCheckedChange = { on -> scope.launch { if (safe { deps.social15.setWall(on) }) reload++ } })
        }
        if (wall.entries.isNotEmpty()) {
            Text("Activité de mes amis", fontWeight = FontWeight.SemiBold)
            wall.entries.take(15).forEach { w -> Text("${w.userName} a lancé « ${w.name} »", fontSize = 13.sp) }
        }
    }
    qr?.let { link ->
        val bmp = remember(link) { qrBitmap(link).asImageBitmap() }
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text("Rejoindre la famille") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(bmp, "QR code d'invitation", Modifier.size(220.dp))
                Text("Valable 7 jours, 5 personnes.", fontSize = 12.sp)
            } },
            confirmButton = { TextButton(onClick = { share(context, link) }) { Text("Partager le lien") } },
            dismissButton = { TextButton(onClick = { qr = null }) { Text("Fermer") } },
        )
    }
    if (planOpen) {
        var title by remember { mutableStateOf("Soirée cinéma") }
        var day by remember { mutableIntStateOf(0) }
        var hour by remember { mutableIntStateOf(20) }
        AlertDialog(
            onDismissRequest = { planOpen = false },
            title = { Text("Planifier une soirée") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Titre") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Ce soir", "Demain", "Samedi").forEachIndexed { i, l -> FilterChip(selected = day == i, onClick = { day = i }, label = { Text(l) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(18, 20, 21).forEach { h -> FilterChip(selected = hour == h, onClick = { hour = h }, label = { Text("$h h") }) }
                }
            } },
            confirmButton = { TextButton(onClick = {
                scope.launch { if (safe { deps.social15.planNight(title, FamilyNightFormat.startIso(day, hour)) }) { planOpen = false; reload++ } }
            }) { Text("Planifier") } },
            dismissButton = { TextButton(onClick = { planOpen = false }) { Text("Annuler") } },
        )
    }
    if (listOpen) {
        var name by remember { mutableStateOf("") }
        var vis by remember { mutableStateOf("family") }
        AlertDialog(
            onDismissRequest = { listOpen = false },
            title = { Text("Nouvelle liste") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nom") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("family" to "Famille", "public" to "Publique", "private" to "Privée").forEach { (k, l) -> FilterChip(selected = vis == k, onClick = { vis = k }, label = { Text(l) }) }
                }
                Text("Ajoutez des titres depuis leur page avec « Ajouter à une liste ».", fontSize = 12.sp)
            } },
            confirmButton = { TextButton(onClick = { scope.launch { if (name.isNotBlank() && safe { deps.social15.saveList(null, name.trim(), vis, emptyList()) }) { listOpen = false; reload++ } } }) { Text("Créer") } },
            dismissButton = { TextButton(onClick = { listOpen = false }) { Text("Annuler") } },
        )
    }
    if (groupOpen) GroupDialog { groupOpen = false }
}

@Composable
private fun GroupDialog(onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<String>()) }
    val friends by produceState(emptyList<org.jellyfin.mobile.nflix.family.FriendInfo>()) { value = runCatching { deps.family.friends().friends }.getOrDefault(emptyList()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Groupe d'amis") },
        text = { Column {
            OutlinedTextField(name, { name = it.take(60) }, label = { Text("Nom du groupe") }, singleLine = true)
            if (friends.isEmpty()) Text("Ajoutez d'abord des amis.", fontSize = 12.sp)
            friends.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { picked = if (f.userId in picked) picked - f.userId else picked + f.userId }) {
                    Checkbox(f.userId in picked, null); Text(f.userName)
                }
            }
        } },
        confirmButton = { TextButton(onClick = { scope.launch { if (name.isNotBlank() && safe { deps.social15.saveGroup(null, name.trim(), picked.toList()) }) onDismiss() } }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Join a family with an invite link or code (shown when the user has no family). */
@Composable
fun JoinByLinkCard(onJoined: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(text, { text = it.take(200) }, label = { Text("Lien ou code d'invitation") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = {
            val token = SocialLinks.parseInvite(text)
            if (token == null) Toasts.show("Lien d'invitation invalide.", ToastKind.Warning)
            else scope.launch { runCatching { deps.social15.joinByLink(token) }.onSuccess { Toasts.show("Bienvenue dans « $it » !", ToastKind.Success); onJoined() }.onFailure { Toasts.show(it.message ?: "Échec.", ToastKind.Warning) } }
        }) { Text("Rejoindre avec le lien") }
    }
}

/** "Recommander à un ami" and "Ajouter à une liste" from a title page. */
@Composable
fun ShareTitleDialog(item: Item, onDismiss: () -> Unit) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val friends by produceState(emptyList<org.jellyfin.mobile.nflix.family.FriendInfo>()) { value = runCatching { deps.family.friends().friends }.getOrDefault(emptyList()) }
    val lists by produceState(emptyList<ListInfo>()) { value = deps.social15.lists() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Partager « ${item.name} »") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Recommander à un ami", fontWeight = FontWeight.SemiBold)
            if (friends.isEmpty()) Text("Aucun ami pour l'instant.", fontSize = 12.sp)
            friends.forEach { f ->
                Text(f.userName, modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        if (safe {
                                val c = deps.family.openConversation(f.userId)
                                deps.family.send(c.id, SocialLinks.recommendation(item.name, item.id, deps.api.baseUrl))
                            }) { Toasts.show("Recommandé à ${f.userName}.", ToastKind.Success); onDismiss() }
                    }
                }.padding(vertical = 6.dp))
            }
            Text("Ajouter à une liste", fontWeight = FontWeight.SemiBold)
            if (lists.isEmpty()) Text("Créez une liste dans Famille.", fontSize = 12.sp)
            lists.forEach { l ->
                Text(l.name, modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        if (safe { deps.social15.saveList(l.id, l.name, l.visibility, (l.items + item.id).distinct()) }) { Toasts.show("Ajouté à « ${l.name} ».", ToastKind.Success); onDismiss() }
                    }
                }.padding(vertical = 6.dp))
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}

/** Dates of movie nights (unit-tested). */
object FamilyNightFormat {
    /** [day]: 0 tonight, 1 tomorrow, 2 next Saturday; local [hour] converted to UTC ISO-8601. */
    fun startIso(day: Int, hour: Int, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): String {
        var date = now.toLocalDate()
        date = when (day) {
            1 -> date.plusDays(1)
            2 -> date.with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SATURDAY))
            else -> date
        }
        var start = date.atTime(hour, 0).atZone(now.zone)
        if (start.isBefore(now)) start = if (day == 2) start.plusWeeks(1) else start.plusDays(1)
        return start.withZoneSameInstant(java.time.ZoneOffset.UTC).toOffsetDateTime().toString()
    }

    fun label(utc: String): String = runCatching {
        val t = java.time.OffsetDateTime.parse(if (utc.endsWith("Z") || utc.contains('+')) utc else utc + "Z").atZoneSameInstant(java.time.ZoneId.systemDefault())
        java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM 'à' H'h'mm", java.util.Locale.FRANCE).format(t)
    }.getOrDefault(utc)
}
