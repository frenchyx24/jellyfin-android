package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URLEncoder

/*
 * Every other feature of the Achievement Badges plugin (2.4.x): quests, leaderboards, comparison,
 * activity feed, shop, cosmetics, power-ups, unlock notifications and administration.
 * The plugin answers with anonymous C# objects whose fields vary between versions, so parsing is
 * lenient: keys are matched case-insensitively and every missing field has a default.
 */

/** Case-insensitive, null-safe reads on plugin JSON. */
internal object PJ {
    fun field(o: JsonObject?, key: String): JsonElement? = o?.get(key) ?: o?.entries?.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    fun obj(o: JsonObject?, key: String): JsonObject? = field(o, key) as? JsonObject
    fun arr(o: JsonObject?, key: String): List<JsonElement> = (field(o, key) as? JsonArray).orEmpty()
    fun objects(o: JsonObject?, key: String): List<JsonObject> = arr(o, key).filterIsInstance<JsonObject>()
    fun str(o: JsonObject?, key: String): String? = (field(o, key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf { it.isNotBlank() }
    fun long(o: JsonObject?, key: String): Long = (field(o, key) as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() } ?: 0L
    fun int(o: JsonObject?, key: String): Int = long(o, key).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    fun double(o: JsonObject?, key: String): Double = (field(o, key) as? JsonPrimitive)?.doubleOrNull ?: 0.0
    fun bool(o: JsonObject?, key: String, default: Boolean = false): Boolean = (field(o, key) as? JsonPrimitive)?.booleanOrNull ?: default
    fun strings(o: JsonObject?, key: String): List<String> = arr(o, key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull ?: (it as? JsonObject)?.let { obj -> str(obj, "Id") } }
}

@Immutable
data class AchievementPublicConfig(
    val leaderboard: Boolean = true,
    val compare: Boolean = true,
    val activityFeed: Boolean = true,
    val prestige: Boolean = true,
    val quests: Boolean = true,
    val privacy: Boolean = false,
    val hideShowcase: Boolean = false,
    val welcome: String = "",
) {
    companion object {
        fun parse(o: JsonObject?) = AchievementPublicConfig(
            leaderboard = PJ.bool(o, "LeaderboardEnabled", true),
            compare = PJ.bool(o, "CompareEnabled", true),
            activityFeed = PJ.bool(o, "ActivityFeedEnabled", true),
            prestige = PJ.bool(o, "PrestigeEnabled", true),
            quests = PJ.bool(o, "QuestsEnabled", true),
            privacy = PJ.bool(o, "ForcePrivacyMode"),
            hideShowcase = PJ.bool(o, "ForceHideEquippedShowcase"),
            welcome = PJ.str(o, "WelcomeMessage").orEmpty(),
        )
    }
}

@Immutable
data class AchievementBank(
    val scoreBank: Int = 0,
    val lifetimeScore: Int = 0,
    val prestigeLevel: Int = 0,
    val bought: Set<String> = emptySet(),
    val pinned: List<String> = emptyList(),
    val titleBadgeId: String? = null,
    val combo: Int = 0,
    val bestCombo: Int = 0,
) {
    companion object {
        fun parse(o: JsonObject?) = AchievementBank(
            PJ.int(o, "ScoreBank"), PJ.int(o, "LifetimeScore"), PJ.int(o, "PrestigeLevel"),
            PJ.strings(o, "BoughtBadgeIds").toSet(), PJ.strings(o, "PinnedBadgeIds"), PJ.str(o, "EquippedTitleBadgeId"),
            PJ.int(o, "ComboCount"), PJ.int(o, "BestComboCount"),
        )
    }
}

@Immutable
data class LeaderboardRow(
    val userId: String,
    val name: String,
    val score: Int,
    val unlocked: Int = 0,
    val total: Int = 0,
    val percentage: Double = 0.0,
    val bestStreak: Int = 0,
    val prestige: Int = 0,
    val customTitle: String? = null,
) {
    companion object {
        fun parse(o: JsonObject, prestige: Boolean = false) = LeaderboardRow(
            userId = PJ.str(o, "UserId").orEmpty(),
            name = PJ.str(o, "UserName") ?: "Membre",
            score = if (prestige) PJ.int(o, "CurrentScore") else PJ.int(o, "Score"),
            unlocked = PJ.int(o, "Unlocked"), total = PJ.int(o, "Total"), percentage = PJ.double(o, "Percentage"),
            bestStreak = PJ.int(o, "BestWatchStreak"), prestige = PJ.int(o, "PrestigeLevel"),
            customTitle = PJ.str(o, "CustomTitle"),
        )
    }
}

@Immutable
data class ActivityEntry(val at: String?, val userName: String, val badgeId: String?, val title: String, val rarity: String?, val icon: String?, val category: String?) {
    companion object {
        fun parse(o: JsonObject) = ActivityEntry(
            PJ.str(o, "At"), PJ.str(o, "UserName") ?: "Membre", PJ.str(o, "BadgeId"), PJ.str(o, "Title") ?: "Succès",
            PJ.str(o, "Rarity"), PJ.str(o, "Icon"), PJ.str(o, "Category"),
        )
    }
}

@Immutable
data class ActivityPage(val entries: List<ActivityEntry>, val page: Int, val totalPages: Int)

@Immutable
data class Member(val id: String, val name: String)

@Immutable
data class CompareSide(val userId: String, val name: String, val score: Int, val unlocked: Int, val total: Int, val prestige: Int)

@Immutable
data class Comparison(val a: CompareSide, val b: CompareSide, val onlyA: Int, val onlyB: Int, val both: Int)

@Immutable
data class Quest(
    val id: String,
    val kind: String,
    val title: String,
    val description: String,
    val icon: String?,
    val reward: Int,
    val target: Int,
    val current: Int,
    val completed: Boolean,
) {
    val progress: Float get() = if (target <= 0) (if (completed) 1f else 0f) else (current.toFloat() / target).coerceIn(0f, 1f)

    companion object {
        fun parse(o: JsonObject, kind: String) = Quest(
            PJ.str(o, "Id").orEmpty(), PJ.str(o, "Kind") ?: kind, PJ.str(o, "Title") ?: "Quête", PJ.str(o, "Description").orEmpty(),
            PJ.str(o, "Icon"), PJ.int(o, "Reward"), PJ.int(o, "Target"), PJ.int(o, "Current"), PJ.bool(o, "Completed"),
        )
    }
}

@Immutable
data class QuestBoard(val daily: List<Quest>, val weekly: List<Quest>, val dailyRerolls: Int, val weeklyRerolls: Int)

@Immutable
data class ShopItem(
    val id: String,
    /** PowerUp, or a cosmetic kind: ProfileTheme, BadgeFrame, RankTitle, Avatar, Background, ProfileBorder. */
    val kind: String,
    val name: String,
    val description: String,
    val price: Int,
    val milestone: Int? = null,
    val color: String? = null,
    val icon: String? = null,
    val bundle: Int = 1,
    val powerUpType: String? = null,
) {
    val isPowerUp: Boolean get() = kind == "PowerUp"
}

@Immutable
data class ShopCatalog(val powerUps: List<ShopItem>, val cosmetics: List<ShopItem>)

@Immutable
data class Cosmetics(val owned: Set<String>, val equipped: Map<String, String?>, val scoreBank: Int, val lifetimeScore: Int)

@Immutable
data class PowerUp(val type: String, val name: String, val description: String, val icon: String?, val count: Int, val active: Boolean, val activeUntil: String?)

@Immutable
data class PowerUps(val inventory: List<PowerUp>, val scoreBank: Int, val streakFreezes: Int, val doubleCreditPending: Boolean)

@Immutable
data class ServerAchievementStats(val users: Int, val unlocked: Int, val itemsWatched: Int, val movies: Int, val seriesCompleted: Int, val commonBadge: String, val totalScore: Int)

@Immutable
data class ActionResult(val success: Boolean, val message: String?)

/** Admin catalog row. */
@Immutable
data class CatalogBadge(val id: String, val title: String, val description: String, val category: String, val rarity: String?, val disabled: Boolean)

@Immutable
data class AuditEntry(val at: String?, val user: String, val type: String, val details: String)

@Immutable
data class AdminQuest(val id: String, val title: String, val description: String, val target: Int, val reward: Int, val weekly: Boolean, val builtIn: Boolean, val disabled: Boolean, val raw: JsonObject)

@Immutable
data class AdminQuests(val quests: List<AdminQuest>, val raw: JsonObject)

@Immutable
data class CustomBadgeItem(val id: String, val name: String, val description: String, val rarity: String, val enabled: Boolean)

/** Feature switches editable by the admin; the whole server object is written back untouched otherwise. */
@Immutable
data class FeatureConfig(val raw: JsonObject) {
    fun flag(key: String, default: Boolean = true) = PJ.bool(raw, key, default)
    fun number(key: String) = PJ.int(raw, key)
    fun text(key: String) = PJ.str(raw, key).orEmpty()

    companion object {
        /** Switches shown to admins, in display order. */
        val FLAGS = listOf(
            "LeaderboardEnabled" to "Classement",
            "CompareEnabled" to "Comparaison entre membres",
            "ActivityFeedEnabled" to "Fil d'activité",
            "QuestsEnabled" to "Quêtes",
            "PrestigeEnabled" to "Prestige",
            "FriendsEnabled" to "Amis",
            "ForcePrivacyMode" to "Mode confidentialité forcé",
            "ForceSpoilerMode" to "Masquer les succès secrets",
            "ForceHideEquippedShowcase" to "Masquer les vitrines",
        )
    }
}

@Immutable
data class WebhookConfig(val enabled: Boolean, val url: String?, val template: String?, val raw: JsonObject) {
    /** The webhook URL usually embeds a secret: only its host is ever shown. */
    val maskedUrl: String? get() = url?.let { runCatching { it.toHttpUrl().let { u -> "${u.scheme}://${u.host}/…" } }.getOrDefault("•••") }
}

class AchievementHub internal constructor(
    private val userId: () -> String,
    private val request: suspend (String, String, String?) -> String,
    private val json: Json,
) {
    constructor(api: NflixApi) : this(
        { api.userId },
        { method, path, body -> api.raw(method, achievementUrl(api.baseUrl.toHttpUrl(), path), body) },
        api.json,
    )

    private val me: String get() = userId()

    private suspend fun get(path: String): JsonElement = parse(call("GET", path))

    private suspend fun call(method: String, path: String, body: JsonElement? = null): String = try {
        request(method, "$ROOT/$path", body?.toString())
    } catch (error: NflixHttpException) {
        if (error.code == 404) throw AchievementPluginMissingException()
        throw error
    }

    private suspend fun parse(text: String): JsonElement = withContext(Dispatchers.Default) {
        if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
    }

    private fun safe(id: String): String {
        require(id.isNotBlank() && id.length <= 128 && id.none { it == '/' || it == '?' || it == '#' || it.isWhitespace() }) { "Identifiant invalide." }
        return URLEncoder.encode(id, "UTF-8")
    }

    private fun result(element: JsonElement, fallback: String): ActionResult {
        val o = element as? JsonObject
        val ok = PJ.field(o, "Success")?.let { PJ.bool(o, "Success") } ?: true
        return ActionResult(ok, PJ.str(o, "Message") ?: if (ok) null else fallback)
    }

    /** Errors from 400 answers carry {Message}; surface them instead of a bare HTTP code. */
    private suspend fun action(method: String, path: String, body: JsonElement? = null, fallback: String = "Action refusée par le serveur."): ActionResult = try {
        result(parse(call(method, path, body)), fallback)
    } catch (error: NflixHttpException) {
        if (error.code == 400 || error.code == 429) ActionResult(false, if (error.code == 429) "Trop de tentatives, réessayez plus tard." else fallback) else throw error
    }

    // --- Member features -----------------------------------------------------------------------

    suspend fun publicConfig(): AchievementPublicConfig = AchievementPublicConfig.parse(get("public-config") as? JsonObject)

    suspend fun bank(): AchievementBank = AchievementBank.parse(get("users/$me/bank") as? JsonObject)

    suspend fun tiers(): List<AchievementTier> =
        (get("ranks") as? JsonArray).orEmpty().filterIsInstance<JsonObject>().map { AchievementTier(PJ.str(it, "Name").orEmpty(), PJ.int(it, "MinScore"), PJ.str(it, "Color") ?: "#e50914", PJ.str(it, "Icon") ?: "military_tech") }

    suspend fun leaderboard(limit: Int = 50): List<LeaderboardRow> =
        (get("leaderboard?limit=${limit.coerceIn(1, 200)}") as? JsonArray).orEmpty().filterIsInstance<JsonObject>().map { LeaderboardRow.parse(it) }

    suspend fun prestigeLeaderboard(limit: Int = 50): List<LeaderboardRow> =
        (get("leaderboard-prestige?limit=${limit.coerceIn(1, 200)}") as? JsonArray).orEmpty().filterIsInstance<JsonObject>().map { LeaderboardRow.parse(it, prestige = true) }

    suspend fun activity(page: Int = 1, mineOnly: Boolean = false): ActivityPage {
        val o = get("activity-feed?page=${page.coerceAtLeast(1)}&pageSize=30" + if (mineOnly) "&userId=$me" else "") as? JsonObject
        return ActivityPage(PJ.objects(o, "Entries").map(ActivityEntry::parse), PJ.int(o, "Page").coerceAtLeast(1), PJ.int(o, "TotalPages"))
    }

    suspend fun directory(): List<Member> = (get("users/$me/directory") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        .mapNotNull { o -> PJ.str(o, "Id")?.let { Member(it, PJ.str(o, "Name") ?: "Membre") } }
        .filter { !it.id.replace("-", "").equals(me.replace("-", ""), ignoreCase = true) }

    /** Null with a message when the server refuses (feature off, privacy, user hidden). */
    suspend fun compare(other: String): Pair<Comparison?, String?> {
        val o = get("compare/$me/${safe(other)}") as? JsonObject
        PJ.str(o, "Error")?.let { return null to compareError(it) }
        fun side(s: JsonObject?) = CompareSide(PJ.str(s, "UserId").orEmpty(), PJ.str(s, "UserName") ?: "Membre", PJ.int(s, "Score"), PJ.int(s, "Unlocked"), PJ.int(s, "Total"), PJ.int(s, "PrestigeLevel"))
        return Comparison(side(PJ.obj(o, "UserA")), side(PJ.obj(o, "UserB")), PJ.int(o, "OnlyA"), PJ.int(o, "OnlyB"), PJ.int(o, "Both")) to null
    }

    suspend fun publicProfile(user: String): LeaderboardRow? = try {
        (get("profiles/${safe(user)}/summary") as? JsonObject)?.let { LeaderboardRow.parse(it) }
    } catch (missing: AchievementPluginMissingException) {
        null // 404 here means a private or unknown profile, not a missing plugin
    }

    suspend fun quests(): QuestBoard {
        val o = get("users/$me/quests") as? JsonObject
        fun list(key: String, kind: String) = when (val value = PJ.field(o, key)) {
            is JsonArray -> value.filterIsInstance<JsonObject>()
            is JsonObject -> listOf(value).filter { it.isNotEmpty() }
            else -> emptyList()
        }.map { Quest.parse(it, kind) }
        return QuestBoard(list("Daily", "daily"), list("Weekly", "weekly"), PJ.int(o, "DailyRerollsRemaining"), PJ.int(o, "WeeklyRerollsRemaining"))
    }

    suspend fun reroll(weekly: Boolean): ActionResult = action("POST", "users/$me/quests/${if (weekly) "weekly" else "daily"}/reroll", fallback = "Relance déjà utilisée.")

    suspend fun shop(): ShopCatalog {
        val o = get("shop/catalog") as? JsonObject
        val power = PJ.objects(o, "PowerUps").map {
            ShopItem(PJ.str(it, "Id").orEmpty(), "PowerUp", PJ.str(it, "DisplayName").orEmpty(), PJ.str(it, "Description").orEmpty(), PJ.int(it, "PriceScore"),
                bundle = PJ.int(it, "BundleSize").coerceAtLeast(1), powerUpType = PJ.str(it, "Type"))
        }
        val cosmetics = PJ.objects(o, "Cosmetics").map {
            ShopItem(PJ.str(it, "Id").orEmpty(), PJ.str(it, "Kind") ?: "ProfileTheme", PJ.str(it, "DisplayName").orEmpty(), PJ.str(it, "Description").orEmpty(),
                PJ.int(it, "PriceScore"), PJ.field(it, "MilestoneScore")?.let { _ -> PJ.int(it, "MilestoneScore") }?.takeIf { m -> m > 0 },
                PJ.str(it, "PreviewColor"), PJ.str(it, "PreviewIcon"))
        }
        return ShopCatalog(power.filter { it.id.isNotBlank() }, cosmetics.filter { it.id.isNotBlank() })
    }

    suspend fun purchase(itemId: String): ActionResult =
        action("POST", "users/$me/shop/purchase", buildJsonObject { put("ItemId", itemId) }, "Achat impossible (score insuffisant ?).")

    suspend fun buyBadge(badgeId: String): ActionResult = action("POST", "users/$me/buy-badge/${safe(badgeId)}", fallback = "Achat du badge impossible.")

    suspend fun cosmetics(): Cosmetics {
        val o = get("users/$me/cosmetics") as? JsonObject
        val equipped = listOf("ProfileTheme" to "EquippedThemeId", "BadgeFrame" to "EquippedBadgeFrameId", "RankTitle" to "EquippedCustomTitleId",
            "Avatar" to "EquippedAvatarId", "Background" to "EquippedBackgroundId", "ProfileBorder" to "EquippedProfileBorderId")
            .associate { (kind, key) -> kind to PJ.str(o, key) }
        return Cosmetics(PJ.strings(o, "Owned").toSet(), equipped, PJ.int(o, "ScoreBank"), PJ.int(o, "LifetimeScore"))
    }

    suspend fun equip(cosmeticId: String): ActionResult = action("POST", "users/$me/cosmetics/equip", buildJsonObject { put("CosmeticId", cosmeticId) })

    suspend fun unequip(kind: String): ActionResult {
        require(kind in COSMETIC_KINDS) { "Type de cosmétique inconnu." }
        return action("POST", "users/$me/cosmetics/unequip?kind=$kind")
    }

    suspend fun powerUps(): PowerUps {
        val o = get("users/$me/powerups") as? JsonObject
        return PowerUps(
            PJ.objects(o, "Inventory").map { PowerUp(PJ.str(it, "Type").orEmpty(), PJ.str(it, "DisplayName").orEmpty(), PJ.str(it, "Description").orEmpty(), PJ.str(it, "Icon"), PJ.int(it, "Count"), PJ.bool(it, "Active"), PJ.str(it, "ActiveUntil")) },
            PJ.int(o, "ScoreBank"), PJ.int(o, "StreakFreezesBanked"), PJ.bool(o, "DoubleCreditPending"),
        )
    }

    suspend fun usePowerUp(type: String): ActionResult = action("POST", "users/$me/powerups/use/${safe(type)}", fallback = "Ce bonus ne peut pas être utilisé maintenant.")

    suspend fun pin(badgeId: String, pinned: Boolean): ActionResult = action("POST", "users/$me/pin/${safe(badgeId)}", buildJsonObject { put("Pinned", pinned) })

    suspend fun setTitle(badgeId: String?): ActionResult = action("POST", "users/$me/title", buildJsonObject { put("BadgeId", badgeId) })

    suspend fun prestige(): ActionResult = action("POST", "users/$me/prestige", fallback = "Prestige impossible pour le moment.")

    suspend fun serverStats(): ServerAchievementStats {
        val o = get("server/stats") as? JsonObject
        return ServerAchievementStats(PJ.int(o, "TotalUsers"), PJ.int(o, "TotalBadgesUnlocked"), PJ.int(o, "TotalItemsWatched"), PJ.int(o, "TotalMoviesWatched"),
            PJ.int(o, "TotalSeriesCompleted"), PJ.str(o, "MostCommonBadge") ?: "—", PJ.int(o, "TotalAchievementScore"))
    }

    /** Badges unlocked after [since] (ISO-8601), plus the server clock to use as the next cutoff. */
    suspend fun unlocksSince(since: String?): Pair<String?, List<Pair<String, String>>> {
        val query = since?.let { "?since=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        val o = get("users/$me/unlocks-since$query") as? JsonObject
        return PJ.str(o, "Now") to PJ.objects(o, "Badges").mapNotNull { b -> PJ.str(b, "Id")?.let { it to (PJ.str(b, "Title") ?: "Succès") } }
    }

    // --- Administration (Jellyfin administrators; the server checks it again) ----------------------

    suspend fun adminCatalog(): List<CatalogBadge> {
        val o = get("admin/badge-catalog?lang=fr") as? JsonObject
        val disabled = PJ.strings(o, "DisabledBadgeIds").toSet()
        return PJ.objects(o, "Catalog").flatMap { group ->
            val category = PJ.str(group, "Category") ?: "General"
            PJ.objects(group, "Badges").mapNotNull { b ->
                val id = PJ.str(b, "Id") ?: return@mapNotNull null
                CatalogBadge(id, PJ.str(b, "Title") ?: id, PJ.str(b, "Description").orEmpty(), category, PJ.str(b, "Rarity"), PJ.bool(b, "Disabled") || id in disabled)
            }
        }
    }

    suspend fun toggleBadge(id: String, disabled: Boolean): ActionResult =
        action("POST", "admin/badge-catalog/toggle", buildJsonObject { put("BadgeId", id); put("Disabled", disabled) })

    suspend fun setDisabledBadges(ids: Collection<String>): ActionResult =
        action("POST", "admin/badge-catalog/bulk", buildJsonObject { putJsonArray("DisabledBadgeIds") { ids.forEach { add(JsonPrimitive(it)) } } })

    suspend fun featureConfig(): FeatureConfig = FeatureConfig(get("admin/feature-config") as? JsonObject ?: JsonObject(emptyMap()))

    /** Read-modify-write: every field the app does not edit (tokens included) goes back unchanged. */
    suspend fun saveFeatureConfig(current: FeatureConfig, changes: Map<String, JsonElement>): ActionResult {
        val merged = JsonObject(current.raw + changes.mapKeys { (key, _) -> current.raw.keys.firstOrNull { it.equals(key, true) } ?: key })
        return action("POST", "admin/feature-config", merged)
    }

    suspend fun auditLog(limit: Int = 200): List<AuditEntry> = (get("admin/audit-log?limit=${limit.coerceIn(1, 1000)}") as? JsonArray).orEmpty()
        .filterIsInstance<JsonObject>().map { AuditEntry(PJ.str(it, "At"), PJ.str(it, "UserName") ?: PJ.str(it, "UserId") ?: "—", PJ.str(it, "Type").orEmpty(), PJ.str(it, "Details").orEmpty()) }
        .sortedByDescending { it.at }

    suspend fun webhook(): WebhookConfig {
        val o = get("admin/webhook") as? JsonObject ?: JsonObject(emptyMap())
        return WebhookConfig(PJ.bool(o, "WebhookEnabled"), PJ.str(o, "WebhookUrl"), PJ.str(o, "WebhookMessageTemplate"), o)
    }

    /** [newUrl] null keeps the stored URL (never shown in full). */
    suspend fun saveWebhook(current: WebhookConfig, enabled: Boolean, newUrl: String?, template: String?): ActionResult {
        newUrl?.let { require(it.startsWith("https://")) { "L'adresse doit commencer par https://." } }
        return action("POST", "admin/webhook", buildJsonObject {
            put("WebhookEnabled", enabled)
            put("WebhookUrl", newUrl ?: current.url)
            put("WebhookMessageTemplate", template ?: current.template)
            put("WebhookHeaders", PJ.str(current.raw, "WebhookHeaders"))
        })
    }

    suspend fun adminQuests(): AdminQuests {
        val o = get("admin/quests") as? JsonObject ?: JsonObject(emptyMap())
        val disabled = PJ.strings(o, "DisabledQuestIds").toSet()
        fun read(key: String, weekly: Boolean, builtIn: Boolean) = PJ.objects(o, key).map {
            val id = PJ.str(it, "Id").orEmpty()
            AdminQuest(id, PJ.str(it, "Title") ?: id, PJ.str(it, "Description").orEmpty(), PJ.int(it, "Target"), PJ.int(it, "Reward"), weekly, builtIn, id in disabled, it)
        }
        return AdminQuests(read("BuiltInDaily", false, true) + read("CustomDailyQuests", false, false) + read("BuiltInWeekly", true, true) + read("CustomWeeklyQuests", true, false), o)
    }

    suspend fun saveQuests(current: AdminQuests, disabledIds: Set<String>, customDaily: List<JsonObject>? = null, customWeekly: List<JsonObject>? = null): ActionResult =
        action("POST", "admin/quests", buildJsonObject {
            put("CustomDailyQuests", JsonArray(customDaily ?: PJ.objects(current.raw, "CustomDailyQuests")))
            put("CustomWeeklyQuests", JsonArray(customWeekly ?: PJ.objects(current.raw, "CustomWeeklyQuests")))
            putJsonArray("DisabledQuestIds") { disabledIds.forEach { add(JsonPrimitive(it)) } }
        })

    suspend fun challenges(): List<JsonObject> = (get("admin/challenges") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    suspend fun saveChallenges(list: List<JsonObject>): ActionResult = action("POST", "admin/challenges", JsonArray(list))

    suspend fun customBadges(): List<CustomBadgeItem> = (get("custom-badges") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        .mapNotNull { o -> PJ.str(o, "Id")?.let { CustomBadgeItem(it, PJ.str(o, "Name") ?: it, PJ.str(o, "Description").orEmpty(), PJ.str(o, "Rarity") ?: "Common", PJ.bool(o, "Enabled", true)) } }

    suspend fun createCustomBadge(name: String, description: String, rarity: String, metric: String, threshold: Int): ActionResult {
        require(name.isNotBlank() && name.length <= 80) { "Nom requis (80 caractères maximum)." }
        require(threshold in 1..1_000_000) { "Seuil invalide." }
        return action("POST", "custom-badges", buildJsonObject {
            put("Name", name.trim()); put("Description", description.trim()); put("Rarity", rarity); put("Media", "Multi"); put("Enabled", true)
            put("Criteria", buildJsonObject { put("Metric", metric); put("Threshold", threshold) })
        }, "Badge refusé par le serveur.")
    }

    suspend fun deleteCustomBadge(id: String): ActionResult = action("DELETE", "custom-badges/${safe(id)}")

    suspend fun grantScore(user: String, amount: Int): ActionResult {
        require(amount in -100_000..100_000 && amount != 0) { "Montant invalide." }
        return action("POST", "admin/users/${safe(user)}/grant-score", buildJsonObject { put("Amount", amount) })
    }

    suspend fun resetUser(user: String): ActionResult = action("DELETE", "admin/users/${safe(user)}/reset")

    companion object {
        const val ROOT = "Plugins/AchievementBadges"
        val COSMETIC_KINDS = setOf("ProfileTheme", "BadgeFrame", "RankTitle", "Avatar", "Background", "ProfileBorder")
        val RARITIES = listOf("Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic")

        /** Metrics a custom badge can count, with their French labels. */
        val METRICS = listOf(
            "TotalItemsWatched" to "Titres regardés",
            "MoviesWatched" to "Films regardés",
            "SeriesCompleted" to "Séries terminées",
            "DaysWatched" to "Jours de visionnage",
            "BestWatchStreak" to "Jours d'affilée",
            "LateNightSessions" to "Séances nocturnes",
            "WeekendSessions" to "Séances le week-end",
            "UniqueGenresWatched" to "Genres différents",
        )

        fun compareError(raw: String): String = when {
            raw.contains("disabled", true) && raw.contains("Compare", true) -> "La comparaison est désactivée sur ce serveur."
            raw.contains("Privacy", true) -> "Le mode confidentialité est activé sur ce serveur."
            raw.contains("not found", true) -> "Profil introuvable."
            raw.contains("disabled profile comparison", true) -> "Ce membre a désactivé la comparaison."
            else -> "Comparaison impossible."
        }

        fun cosmeticKindName(kind: String) = when (kind) {
            "ProfileTheme" -> "Thèmes"
            "BadgeFrame" -> "Cadres de badge"
            "RankTitle" -> "Titres"
            "Avatar" -> "Avatars"
            "Background" -> "Fonds animés"
            "ProfileBorder" -> "Bordures"
            "PowerUp" -> "Bonus"
            else -> kind
        }
    }
}
