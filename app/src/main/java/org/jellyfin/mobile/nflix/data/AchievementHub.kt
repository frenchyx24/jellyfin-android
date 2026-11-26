package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
/** In-memory cache of members' showcased badges; [version] bumps on invalidation so tiles refetch. */
internal object BadgeShowcaseCache {
    private val entries = java.util.concurrent.ConcurrentHashMap<String, List<AchievementBadge>>()
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    fun peek(userId: String): List<AchievementBadge>? = entries[userId]
    fun put(userId: String, badges: List<AchievementBadge>) { entries[userId] = badges }
    fun invalidate(userId: String? = null) {
        if (userId == null) entries.clear() else entries.remove(userId)
        version++
    }
}

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

@Immutable
data class SecretHint(val id: String, val hint: String, val level: Int, val percent: Double)

@Immutable
data class CollectionProgress(
    val id: String, val name: String, val total: Int, val watched: Int, val percent: Double,
    val posterItemId: String?, val completed: Boolean,
) {
    val progress: Float get() = if (total > 0) (watched.toFloat() / total).coerceIn(0f, 1f) else (percent / 100.0).toFloat().coerceIn(0f, 1f)
}

@Immutable
data class SeasonReward(val type: String, val id: String, val label: String)

@Immutable
data class SeasonTier(val tier: Int, val xpRequired: Long, val reward: SeasonReward?, val unlocked: Boolean)

@Immutable
data class SeasonPass(
    val id: String, val name: String, val endsAt: String?, val currentTier: Int,
    val seasonXp: Long, val xpPerTier: Int, val tiers: List<SeasonTier>,
) {
    val maxTier: Int get() = maxOf(30, tiers.maxOfOrNull { it.tier } ?: 0)
    val complete: Boolean get() = currentTier >= maxTier
    val xpIntoTier: Long get() = (seasonXp - currentTier.toLong() * xpPerTier).coerceIn(0L, xpPerTier.toLong())
    val tierProgress: Float get() = if (complete || xpPerTier <= 0) 1f else (xpIntoTier.toFloat() / xpPerTier).coerceIn(0f, 1f)
}

@Immutable
data class CosmeticOption(val id: String, val label: String, val unlocked: Boolean, val source: String?)

@Immutable
data class ProfileCosmetics(val frames: List<CosmeticOption>, val titles: List<CosmeticOption>, val equippedFrame: String?, val equippedTitle: String?) {
    val equippedTitleLabel: String? get() = equippedTitle?.let { id -> titles.find { it.id == id }?.label ?: id }
}

@Immutable
data class PublicCosmetics(val frame: String?, val title: String?, val titleLabel: String?)

@Immutable
data class DailyChallenge(
    val id: String, val title: String, val description: String, val icon: String?,
    val progress: Int, val target: Int, val rewardXp: Int, val completed: Boolean, val expiresAt: String?,
)

/** Equipped frame and title of other members, fetched once per user; equipping clears it. */
internal object ProfileCosmeticsCache {
    private val entries = java.util.concurrent.ConcurrentHashMap<String, PublicCosmetics>()
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    fun peek(userId: String): PublicCosmetics? = entries[userId]
    fun put(userId: String, value: PublicCosmetics) { entries[userId] = value }
    fun invalidate(userId: String? = null) {
        if (userId == null) entries.clear() else entries.remove(userId)
        version++
    }
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

    /** For background work: explicit user and token, the in-memory session is left alone. */
    constructor(api: NflixApi, userId: String, token: String) : this(
        { userId },
        { method, path, body -> api.rawWithToken(method, achievementUrl(api.baseUrl.toHttpUrl(), path), token, body) },
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

    /** Refusals carry {Message} or {error}: show them (translated when known) instead of a bare HTTP code. */
    private suspend fun action(method: String, path: String, body: JsonElement? = null, fallback: String = "Action refusée par le serveur."): ActionResult = try {
        result(parse(call(method, path, body)), fallback).let { if (it.success) it else it.copy(message = translate(it.message) ?: fallback) }
    } catch (missing: AchievementPluginMissingException) {
        // 404 on an action: no achievements profile yet for this member, not a missing plugin.
        ActionResult(false, "Profil de succès introuvable : regardez un premier titre pour le créer.")
    } catch (error: NflixHttpException) {
        if (error.code != 400 && error.code != 409 && error.code != 429) throw error
        val server = error.body?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?.let { PJ.str(it, "Message") ?: PJ.str(it, "error") }
        ActionResult(false, translate(server) ?: if (error.code == 429) "Trop de tentatives, réessayez plus tard." else fallback)
    }

    // --- Member features -----------------------------------------------------------------------

    /**
     * Aggregated Nflix view (level, XP, streak, next and recent badges, seasonal event).
     * Null when the server is too old (404, or no Level block), so the classic screen still works.
     */
    suspend fun nflix(): NflixAchievements? {
        val o = try {
            get("users/$me/nflix") as? JsonObject
        } catch (missing: AchievementPluginMissingException) {
            null
        } ?: return null
        return NflixAchievements.parse(o)
    }


    suspend fun publicConfig(): AchievementPublicConfig = AchievementPublicConfig.parse(get("public-config") as? JsonObject)

    // --- Daily challenge, season pass, collections, secret hints, profile cosmetics -------------

    suspend fun secretHints(): List<SecretHint> {
        val element = get("users/$me/secret-hints")
        val list = (element as? JsonArray) ?: (PJ.field(element as? JsonObject, "Hints") as? JsonArray) ?: JsonArray(emptyList())
        return list.filterIsInstance<JsonObject>().mapNotNull { o ->
            val id = PJ.str(o, "Id") ?: return@mapNotNull null
            SecretHint(id, PJ.str(o, "Hint").orEmpty(), PJ.int(o, "HintLevel").coerceIn(0, 3), PJ.double(o, "Percent"))
        }.filter { it.hint.isNotBlank() }
    }

    suspend fun collections(): List<CollectionProgress> {
        val o = get("users/$me/collections") as? JsonObject
        return PJ.objects(o, "Collections").mapNotNull { c ->
            val id = PJ.str(c, "Id") ?: return@mapNotNull null
            CollectionProgress(id, PJ.str(c, "Name") ?: "Collection", PJ.int(c, "Total"), PJ.int(c, "Watched"), PJ.double(c, "Percent"), PJ.str(c, "PosterItemId"), PJ.bool(c, "Completed"))
        }
    }

    /** Null when no season is running (404). */
    suspend fun seasonPass(): SeasonPass? {
        val o = try {
            get("users/$me/season-pass") as? JsonObject
        } catch (missing: AchievementPluginMissingException) {
            null
        } ?: return null
        val season = PJ.obj(o, "Season")
        fun date(value: String?) = value?.takeIf { runCatching { java.time.OffsetDateTime.parse(it) }.isSuccess }
        val tiers = PJ.objects(o, "Tiers").map { t ->
            val r = PJ.obj(t, "Reward")
            val reward = PJ.str(r, "Id")?.let { id -> SeasonReward(PJ.str(r, "Type").orEmpty().lowercase(), id, PJ.str(r, "Label") ?: id) }
            SeasonTier(PJ.int(t, "Tier"), PJ.long(t, "XpRequired"), reward, PJ.bool(t, "Unlocked"))
        }
        return SeasonPass(
            PJ.str(o, "Id") ?: PJ.str(season, "Id").orEmpty(),
            PJ.str(o, "Name") ?: PJ.str(season, "Name") ?: "Saison",
            date(PJ.str(o, "EndsAt")) ?: date(PJ.str(season, "EndsAt")),
            PJ.int(o, "CurrentTier"), PJ.long(o, "SeasonXp"), PJ.int(o, "XpPerTier").takeIf { it > 0 } ?: 200,
            tiers.sortedBy { it.tier },
        )
    }

    suspend fun profileCosmetics(): ProfileCosmetics {
        val o = get("users/$me/profile-cosmetics") as? JsonObject
        fun options(key: String) = PJ.objects(o, key).mapNotNull { f ->
            PJ.str(f, "Id")?.let { CosmeticOption(it, PJ.str(f, "Label") ?: it, PJ.bool(f, "Unlocked"), PJ.str(f, "Source")) }
        }
        return ProfileCosmetics(options("Frames"), options("Titles"), cosmeticId(o, "EquippedFrame"), cosmeticId(o, "EquippedTitle"))
    }

    private fun cosmeticId(o: JsonObject?, key: String): String? = PJ.str(o, key) ?: PJ.str(PJ.obj(o, key), "Id")

    /** Null clears the slot. A locked cosmetic is refused with a 400 {Message}. */
    suspend fun equipCosmetics(frame: String?, title: String?): ActionResult =
        action("POST", "users/$me/profile-cosmetics/equip", buildJsonObject { put("Frame", frame); put("Title", title) }, "Cosmétique verrouillé.")
            .also { if (it.success) ProfileCosmeticsCache.invalidate() }

    /** Equipped frame and title of any member; cached, errors give an empty result. */
    suspend fun publicCosmetics(targetUserId: String): PublicCosmetics {
        ProfileCosmeticsCache.peek(targetUserId)?.let { return it }
        val value = try {
            val o = get("profiles/${safe(targetUserId)}/cosmetics") as? JsonObject
            val title = cosmeticId(o, "EquippedTitle")
            val self = targetUserId.replace("-", "").equals(me.replace("-", ""), ignoreCase = true)
            val label = PJ.str(o, "EquippedTitleLabel") ?: PJ.str(o, "TitleLabel") ?: PJ.str(PJ.obj(o, "EquippedTitle"), "Label")
                ?: (if (title != null && self) runCatching { profileCosmetics().titles.find { it.id == title }?.label }.getOrNull() else null)
            PublicCosmetics(cosmeticId(o, "EquippedFrame"), title, label)
        } catch (cancel: kotlin.coroutines.cancellation.CancellationException) {
            throw cancel
        } catch (error: Exception) {
            PublicCosmetics(null, null, null)
        }
        ProfileCosmeticsCache.put(targetUserId, value)
        return value
    }

    /** Null when there is no challenge today (404). */
    suspend fun dailyChallenge(): DailyChallenge? {
        val o = try {
            get("users/$me/daily-challenge") as? JsonObject
        } catch (missing: AchievementPluginMissingException) {
            null
        } ?: return null
        val id = PJ.str(o, "Id") ?: return null
        return DailyChallenge(
            id, PJ.str(o, "Title") ?: "Défi du jour", PJ.str(o, "Description").orEmpty(), PJ.str(o, "Icon"),
            PJ.int(o, "Progress"), PJ.int(o, "Target").coerceAtLeast(1), PJ.int(o, "RewardXp"), PJ.bool(o, "Completed"), PJ.str(o, "ExpiresAt"),
        )
    }

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

    // "Mes stats" (Achievement Badges 3.0). Lenient reads: unknown or missing fields give empty values.

    suspend fun streakCalendar(): StreakCalendar = StreakCalendar.parse(get("users/$me/streak-calendar") as? JsonObject)

    suspend fun records(): StatRecords = StatRecords.parse(get("users/$me/records") as? JsonObject)

    /** [period] is week, month or year; servers that ignore it answer with their default period. */
    suspend fun recap(period: String): StatRecap = StatRecap.parse(get("users/$me/recap?period=${safe(period)}") as? JsonObject)

    suspend fun smartGoals(): List<SmartGoal> {
        val element = get("users/$me/smart-goals")
        val list = (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: PJ.objects(element as? JsonObject, "Goals")
        return list.mapNotNull(SmartGoal::parse)
    }

    suspend fun badgeEta(): List<BadgeEta> {
        val element = get("users/$me/badge-eta")
        val list = (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: PJ.objects(element as? JsonObject, "Etas")
        return list.mapNotNull(BadgeEta::parse)
    }

    suspend fun categoryProgress(): List<CategoryProgress> {
        val element = get("users/$me/category-progress")
        val list = (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: PJ.objects(element as? JsonObject, "Categories")
        return list.mapNotNull(CategoryProgress::parse)
    }

    suspend fun libraryCompletion(): List<LibraryCompletion> {
        val o = get("users/$me/library-completion") as? JsonObject
        val map = PJ.obj(o, "LibraryCompletionPercents") ?: return emptyList()
        return map.entries.mapNotNull { (name, value) ->
            val percent = (value as? JsonPrimitive)?.let { it.doubleOrNull } ?: (value as? JsonObject)?.let { PJ.double(it, "Percent") }
            percent?.let { LibraryCompletion(name, it.toInt().coerceIn(0, 100)) }
        }
    }

    suspend fun recommendations(): List<BadgeRecommendation> {
        val element = get("users/$me/recommendations")
        val list = (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: PJ.objects(element as? JsonObject, "BadgeTargets")
        return list.mapNotNull(BadgeRecommendation::parse)
    }

    suspend fun compareHistory(): List<CompareHistoryEntry> {
        val element = get("users/$me/compare-history")
        val list = (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: PJ.objects(element as? JsonObject, "History")
        return list.mapNotNull(CompareHistoryEntry::parse)
    }

    // ---------------------------------------------------------------- Lot 4 (plugin 3.2): social

    /** Weekly XP board of the given accounts (usually the family), best first. */
    suspend fun weeklyBoard(ids: Collection<String>): List<WeeklyRow> {
        val q = java.net.URLEncoder.encode(idsParam(ids), "UTF-8").replace("+", "%20")
        return (get("users/$me/weekly-board?ids=$q") as? JsonArray).objects().mapNotNull { o ->
            val id = PJ.str(o, "UserId") ?: return@mapNotNull null
            WeeklyRow(id, PJ.str(o, "Name") ?: "Membre", PJ.long(o, "WeekXp"), PJ.int(o, "Level"), PJ.str(o, "Title") ?: "", PJ.int(o, "Rank"))
        }
    }

    suspend fun duels(): List<Duel> = (get("users/$me/duels") as? JsonArray).objects().mapNotNull(Duel::parse)

    suspend fun createDuel(opponentId: String, kind: String, target: Int, days: Int): ActionResult =
        action("POST", "users/$me/duels", duelBody(opponentId, kind, target, days), fallback = "Défi refusé par le serveur.")

    suspend fun respondDuel(id: String, accept: Boolean): ActionResult =
        action("POST", "users/$me/duels/$id/${if (accept) "accept" else "decline"}", fallback = "Défi introuvable.")

    suspend fun unlockFeed(ids: Collection<String>): List<UnlockItem> {
        val q = java.net.URLEncoder.encode(idsParam(ids), "UTF-8").replace("+", "%20")
        return (get("users/$me/unlock-feed?ids=$q") as? JsonArray).objects().mapNotNull(UnlockItem::parse)
    }

    suspend fun reactToUnlock(key: String, emoji: String): ActionResult =
        action("POST", "users/$me/unlock-feed/react", reactBody(key, emoji), fallback = "Réaction impossible.")

    suspend fun progressAlerts(): List<ProgressAlert> {
        val element = get("users/$me/progress-alerts")
        val list = (element as? JsonArray)?.filterIsInstance<JsonObject>() ?: PJ.objects(element as? JsonObject, "Alerts")
        return list.mapNotNull(ProgressAlert::parse)
    }

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

    /** Showcased badges of any member (own route for me, public route otherwise), cached in memory; errors give an empty list. */
    suspend fun showcase(targetUserId: String): List<AchievementBadge> {
        BadgeShowcaseCache.peek(targetUserId)?.let { return it }
        val list = try {
            val id = safe(targetUserId)
            val root = get(if (targetUserId.equals(me, ignoreCase = true)) "users/$id/equipped?lang=fr" else "profiles/$id/equipped?lang=fr")
            val items = (root as? JsonArray)?.filterIsInstance<JsonObject>()
                ?: listOf("Equipped", "Badges", "Items").firstNotNullOfOrNull { k -> PJ.objects(root as? JsonObject, k).takeIf { it.isNotEmpty() } }.orEmpty()
            items.mapNotNull { o ->
                val badgeId = PJ.str(o, "Id") ?: PJ.str(o, "BadgeId") ?: return@mapNotNull null
                AchievementBadge(
                    id = badgeId, title = PJ.str(o, "Title") ?: PJ.str(o, "Name") ?: badgeId, description = PJ.str(o, "Description").orEmpty(),
                    icon = PJ.str(o, "Icon"), category = PJ.str(o, "Category").orEmpty(), unlocked = true,
                    currentValue = 0, targetValue = 0, rarity = PJ.str(o, "Rarity") ?: "Common",
                )
            }.take(3)
        } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
        BadgeShowcaseCache.put(targetUserId, list)
        return list
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
    suspend fun unlocksSince(since: String?, deviceId: String? = null): Pair<String?, List<Pair<String, String>>> {
        val params = listOfNotNull(since?.let { "since=" + URLEncoder.encode(it, "UTF-8") }, deviceId?.let { "deviceId=" + URLEncoder.encode(it.take(256), "UTF-8") })
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
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

        /** Known plugin messages in French; others are shown as sent. */
        fun translate(message: String?): String? {
            val text = message?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            Regex("Not enough score bank\\. Need (\\d+), have (\\d+)").find(text)?.let { return "Score insuffisant : il faut ${it.groupValues[1]} points, vous en avez ${it.groupValues[2]}." }
            return when {
                text.contains("reroll already used", true) -> "Relance déjà utilisée pour cette période."
                text.contains("Already unlocked", true) -> "Ce badge est déjà débloqué."
                text.contains("Badge not found", true) -> "Badge introuvable."
                text.contains("Not enough", true) -> "Score insuffisant."
                text.contains("Unknown power-up", true) -> "Bonus inconnu."
                else -> text
            }
        }

        /** Score-bank price of a locked badge, by rarity (plugin GetPurchaseCost). */
        fun badgePrice(rarity: String?): Int = when (rarity?.lowercase()) {
            "common" -> 150; "uncommon" -> 300; "rare" -> 600; "epic" -> 1200; "legendary" -> 2500; "mythic" -> 5000; else -> 400
        }
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


@Immutable
data class NflixLevel(val level: Int, val xp: Int, val xpIntoLevel: Int, val xpForNextLevel: Int, val title: String) {
    val progress: Float get() = if (xpForNextLevel > 0) (xpIntoLevel.toFloat() / xpForNextLevel).coerceIn(0f, 1f) else 0f
}

@Immutable
data class NflixStreak(val current: Int, val best: Int)

@Immutable
data class NflixSeasonal(val active: Boolean, val name: String?, val endsAt: String?)

@Immutable
data class NflixAchievements(
    val level: NflixLevel,
    val streak: NflixStreak,
    val recent: List<AchievementBadge>,
    val next: List<AchievementBadge>,
    val seasonal: NflixSeasonal,
    val tierLabel: String?,
    val unlocked: Int,
    val total: Int,
) {
    companion object {
        internal fun parse(o: JsonObject): NflixAchievements? {
            val level = PJ.obj(o, "Level") ?: return null
            val summary = PJ.obj(o, "Summary")
            val streak = PJ.obj(o, "Streak")
            val seasonal = PJ.obj(o, "Seasonal")
            val badges = PJ.objects(o, "Badges").mapNotNull(::parseNflixBadge)
            val lvl = PJ.int(level, "Level").coerceAtLeast(1)
            return NflixAchievements(
                level = NflixLevel(
                    level = lvl,
                    xp = PJ.int(level, "Xp"),
                    xpIntoLevel = PJ.int(level, "XpIntoLevel").coerceAtLeast(0),
                    xpForNextLevel = PJ.int(level, "XpForNextLevel").takeIf { it > 0 } ?: (100 * lvl),
                    title = PJ.str(level, "Title") ?: "Spectateur",
                ),
                streak = NflixStreak(
                    current = if (streak != null) PJ.int(streak, "Current") else PJ.int(summary, "CurrentWatchStreak"),
                    best = if (streak != null) PJ.int(streak, "Best") else PJ.int(summary, "BestWatchStreak"),
                ),
                recent = PJ.objects(o, "Recent").mapNotNull(::parseNflixBadge).take(10),
                next = PJ.objects(o, "Next").mapNotNull(::parseNflixBadge).take(6),
                seasonal = NflixSeasonal(PJ.bool(seasonal, "Active"), PJ.str(seasonal, "Name"), PJ.str(seasonal, "EndsAt")),
                tierLabel = PJ.str(PJ.obj(PJ.obj(o, "Rank"), "Tier"), "Label"),
                unlocked = PJ.field(summary, "Unlocked")?.let { PJ.int(summary, "Unlocked") } ?: badges.count { it.unlocked },
                total = PJ.field(summary, "Total")?.let { PJ.int(summary, "Total") } ?: badges.size,
            )
        }
    }
}

/** Lenient badge read; entries may also wrap the badge as {Badge: {...}}. */
internal fun parseNflixBadge(element: JsonObject): AchievementBadge? {
    val o = PJ.obj(element, "Badge") ?: element
    val id = PJ.str(o, "Id") ?: return null
    return AchievementBadge(
        id = id,
        key = PJ.str(o, "Key"),
        title = PJ.str(o, "Title") ?: id,
        description = PJ.str(o, "Description").orEmpty(),
        icon = PJ.str(o, "Icon"),
        category = PJ.str(o, "Category") ?: "General",
        unlocked = PJ.bool(o, "Unlocked"),
        unlockedAt = PJ.str(o, "UnlockedAt"),
        currentValue = PJ.long(o, "CurrentValue"),
        targetValue = PJ.long(o, "TargetValue"),
        rarity = PJ.str(o, "Rarity") ?: "Common",
        rarityLabel = PJ.str(o, "RarityLabel"),
    )
}

// ---- "Mes stats" models (Achievement Badges 3.0) ----

/** One day of the streak calendar; [count] is 1 for a plain "watched" flag. */
@Immutable
data class StreakDay(val date: String, val watched: Boolean, val count: Int)

@Immutable
data class StreakCalendar(val weeks: Int, val days: List<StreakDay>) {
    companion object {
        fun parse(o: JsonObject?): StreakCalendar = StreakCalendar(
            weeks = PJ.int(o, "Weeks").takeIf { it > 0 } ?: 53,
            days = PJ.objects(o, "Days").mapNotNull { d ->
                val date = PJ.str(d, "D") ?: PJ.str(d, "Date") ?: return@mapNotNull null
                val raw = PJ.field(d, "W") ?: PJ.field(d, "Watched")
                val primitive = raw as? JsonPrimitive
                val flag = primitive?.booleanOrNull
                val count = PJ.int(d, "C").takeIf { it > 0 } ?: PJ.int(d, "Count").takeIf { it > 0 } ?: primitive?.longOrNull?.toInt() ?: if (flag == true) 1 else 0
                StreakDay(date.take(10), flag ?: (count > 0), if (flag == true) count.coerceAtLeast(1) else count)
            },
        )
    }
}

/** Every numeric field of the records object, read case-insensitively. */
@Immutable
data class StatRecords(val values: Map<String, Long>) {
    operator fun get(key: String): Long? = values[key] ?: values.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value

    companion object {
        fun parse(o: JsonObject?): StatRecords = StatRecords(
            o?.entries.orEmpty().mapNotNull { (key, value) ->
                val p = value as? JsonPrimitive ?: return@mapNotNull null
                if (p is JsonNull || p.isString) return@mapNotNull null
                (p.longOrNull ?: p.doubleOrNull?.toLong())?.let { key to it }
            }.toMap(),
        )
    }
}

@Immutable
data class RankedName(val name: String, val count: Int)

@Immutable
data class RecapBadge(val id: String, val title: String, val rarity: String?, val unlockedAt: String?)

@Immutable
data class StatRecap(
    val period: String?,
    val days: Int,
    val movies: Int,
    val episodes: Int,
    val total: Int,
    val daysWatched: Int,
    val badgesUnlocked: Int,
    val badges: List<RecapBadge>,
    val topGenres: List<RankedName>,
    val topDirectors: List<RankedName>,
    val topActors: List<RankedName>,
) {
    val isEmpty: Boolean get() = total == 0 && movies == 0 && episodes == 0 && badges.isEmpty() && topGenres.isEmpty() && topDirectors.isEmpty() && topActors.isEmpty()

    companion object {
        private fun ranked(o: JsonObject?, key: String) = PJ.objects(o, key).mapNotNull { r -> PJ.str(r, "Name")?.let { RankedName(it, PJ.int(r, "Count")) } }

        fun parse(o: JsonObject?): StatRecap {
            val badges = PJ.objects(o, "BadgesList").mapNotNull { b ->
                val id = PJ.str(b, "Id") ?: PJ.str(b, "BadgeId") ?: return@mapNotNull null
                RecapBadge(id, PJ.str(b, "Title") ?: id, PJ.str(b, "Rarity"), PJ.str(b, "UnlockedAt"))
            }
            return StatRecap(
                period = PJ.str(o, "Period"),
                days = PJ.int(o, "Days"),
                movies = PJ.int(o, "MoviesWatched"),
                episodes = PJ.int(o, "EpisodesWatched"),
                total = PJ.int(o, "TotalItems"),
                daysWatched = PJ.int(o, "DaysWatched"),
                badgesUnlocked = PJ.int(o, "BadgesUnlocked").takeIf { it > 0 } ?: badges.size,
                badges = badges,
                topGenres = ranked(o, "TopGenres"),
                topDirectors = ranked(o, "TopDirectors"),
                topActors = ranked(o, "TopActors"),
            )
        }
    }
}

@Immutable
data class SmartGoal(val badgeId: String, val title: String, val action: String?, val current: Long, val target: Long, val rarity: String?) {
    val progress: Float get() = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f

    companion object {
        fun parse(o: JsonObject): SmartGoal? {
            val id = PJ.str(o, "BadgeId") ?: PJ.str(o, "Id") ?: return null
            return SmartGoal(id, PJ.str(o, "Title") ?: id, PJ.str(o, "Action"), PJ.long(o, "Current"), PJ.long(o, "Target"), PJ.str(o, "Rarity"))
        }
    }
}

@Immutable
data class BadgeEta(val badgeId: String, val title: String, val current: Long, val target: Long, val daysRemaining: Int?, val velocity: Double?) {
    val progress: Float get() = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f

    companion object {
        fun parse(o: JsonObject): BadgeEta? {
            val id = PJ.str(o, "BadgeId") ?: PJ.str(o, "Id") ?: return null
            val days = (PJ.field(o, "DaysRemaining") as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }?.toInt()
            val velocity = (PJ.field(o, "Velocity") as? JsonPrimitive)?.doubleOrNull
            return BadgeEta(id, PJ.str(o, "Title") ?: id, PJ.long(o, "Current"), PJ.long(o, "Target"), days?.takeIf { it >= 0 }, velocity)
        }
    }
}

@Immutable
data class CategoryProgress(val category: String, val label: String, val total: Int, val unlocked: Int, val percent: Int) {
    companion object {
        fun parse(o: JsonObject): CategoryProgress? {
            val category = PJ.str(o, "Category") ?: return null
            val total = PJ.int(o, "Total")
            val unlocked = PJ.int(o, "Unlocked")
            val percent = (PJ.field(o, "Percent") as? JsonPrimitive)?.doubleOrNull?.toInt() ?: if (total > 0) unlocked * 100 / total else 0
            return CategoryProgress(category, PJ.str(o, "CategoryLabel") ?: category, total, unlocked, percent.coerceIn(0, 100))
        }
    }
}

@Immutable
data class LibraryCompletion(val name: String, val percent: Int)

@Immutable
data class RecommendedItem(val id: String, val name: String, val type: String?, val year: Int, val runTimeMinutes: Int)

@Immutable
data class BadgeRecommendation(val badgeId: String, val title: String, val current: Long, val target: Long, val items: List<RecommendedItem>) {
    val progress: Float get() = if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f

    companion object {
        /** Null for entries without items: nothing to suggest. */
        fun parse(o: JsonObject): BadgeRecommendation? {
            val items = PJ.objects(o, "Items").mapNotNull { i ->
                val id = PJ.str(i, "Id") ?: return@mapNotNull null
                RecommendedItem(id, PJ.str(i, "Name") ?: "", PJ.str(i, "Type"), PJ.int(i, "Year"), PJ.int(i, "RunTimeMinutes"))
            }
            if (items.isEmpty()) return null
            val id = PJ.str(o, "BadgeId") ?: ""
            val progress = PJ.obj(o, "Progress")
            return BadgeRecommendation(
                badgeId = id,
                title = PJ.str(o, "BadgeTitle") ?: PJ.str(o, "Title") ?: id.ifBlank { "Suggestions" },
                current = if (progress != null) PJ.long(progress, "Current") else PJ.long(o, "Current"),
                target = if (progress != null) PJ.long(progress, "Target") else PJ.long(o, "Target"),
                items = items,
            )
        }
    }
}

@Immutable
data class CompareHistoryEntry(val otherUserId: String, val otherUserName: String, val at: String?) {
    companion object {
        fun parse(o: JsonObject): CompareHistoryEntry? {
            val id = PJ.str(o, "OtherUserId") ?: PJ.str(o, "UserId") ?: return null
            return CompareHistoryEntry(id, PJ.str(o, "OtherUserName") ?: PJ.str(o, "UserName") ?: "Membre", PJ.str(o, "At"))
        }
    }
}
