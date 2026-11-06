package org.jellyfin.mobile.nflix.data

import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

class AchievementParsingTest : FunSpec({
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    val badge = """{"Id":"first-contact","Title":"Premier contact","Description":"Regardez votre premier élément.","Category":"Getting Started","Unlocked":true,"UnlockedAt":"2026-11-03T09:00:00+00:00","CurrentValue":1,"TargetValue":1,"Rarity":"Common","EarnSource":2}"""
    val summary = """{"Unlocked":1,"Total":20,"Percentage":5.0,"EquippedCount":1,"Score":10,"CurrentWatchStreak":2,"BestWatchStreak":5}"""
    val rank = """{"Score":10,"Tier":{"Name":"Rookie","MinScore":0,"Color":"#9aa5b1","Icon":"sprout"},"NextTier":null,"ProgressToNext":100}"""
    val aggregate = """{"SchemaVersion":1,"Language":"fr","Badges":[$badge],"Summary":$summary,"Rank":$rank,"Equipped":[$badge],"RarityStats":{"first-contact":12.5},"FutureField":true}"""

    test("fallback query remains a query parameter on the server URL") {
        val url = achievementUrl("https://nflix.example/base/".toHttpUrl(), "Plugins/AchievementBadges/users/user?lang=fr")
        url.encodedPath shouldBe "/base/Plugins/AchievementBadges/users/user"
        url.queryParameter("lang") shouldBe "fr"
        url.encodedPath.contains("lang%3Dfr") shouldBe false
    }
    test("PascalCase aggregate includes full equipped badges and real rarity percentages") {
        val profile = json.decodeFromString(AchievementProfile.serializer(), aggregate)
        profile.badges.single().title shouldBe "Premier contact"
        profile.equipped.single().id shouldBe "first-contact"
        profile.rank.nextTier shouldBe null
        profile.rank.tier.frenchName shouldBe "Débutant"
        profile.summary.currentWatchStreak shouldBe 2
        profile.rarityStats["first-contact"] shouldBe 12.5
        profile.badges.single().progress shouldBe 1f
    }
    test("missing required aggregate field fails instead of inventing a profile") {
        shouldThrow<SerializationException> { json.decodeFromString(AchievementProfile.serializer(), """{"SchemaVersion":1,"Language":"fr"}""") }
    }
    test("locked progress handles zero targets and clamps server overshoot") {
        val item = json.decodeFromString(AchievementBadge.serializer(), badge)
        item.copy(unlocked = false, currentValue = 5, targetValue = 0).progress shouldBe 0f
        item.copy(unlocked = false, currentValue = 5, targetValue = 3).progress shouldBe 1f
        item.copy(unlocked = false, currentValue = -1, targetValue = 3).progress shouldBe 0f
    }
    test("aggregate uses exactly one HTTP request") {
        val calls = mutableListOf<String>()
        val repository = AchievementRepository({ "user" }, { _, path -> calls += path; aggregate }, json)
        repository.profile().badges.size shouldBe 1
        calls shouldBe listOf("Plugins/AchievementBadges/users/user/nflix")
    }
    test("only aggregate 404 enables upstream fallback") {
        val calls = mutableListOf<String>()
        val repository = AchievementRepository({ "user" }, { _, path ->
            calls += path
            when {
                path.endsWith("/nflix") -> throw NflixHttpException(404, "not found")
                path.endsWith("/summary") -> summary
                path.endsWith("/rank") -> rank
                path.endsWith("/rarity-stats") -> """{"first-contact":12.5}"""
                else -> "[$badge]"
            }
        }, json)
        repository.profile().equipped.single().title shouldBe "Premier contact"
        calls.size shouldBe 6
    }
    listOf(401, 403, 500).forEach { status ->
        test("HTTP $status does not enable fallback") {
            var calls = 0
            val repository = AchievementRepository({ "user" }, { _, _ -> calls++; throw NflixHttpException(status, "failure") }, json)
            shouldThrow<NflixHttpException> { repository.profile() }.code shouldBe status
            calls shouldBe 1
        }
    }
    test("missing plugin 404 is explicit") {
        val repository = AchievementRepository({ "user" }, { _, _ -> throw NflixHttpException(404, "not found") }, json)
        shouldThrow<AchievementPluginMissingException> { repository.profile() }
    }
    test("invalid JSON never enables fallback") {
        var calls = 0
        val repository = AchievementRepository({ "user" }, { _, _ -> calls++; "{}" }, json)
        shouldThrow<SerializationException> { repository.profile() }
        calls shouldBe 1
    }
    test("unsupported schema fails") {
        val repository = AchievementRepository({ "user" }, { _, _ -> aggregate.replace("\"SchemaVersion\":1", "\"SchemaVersion\":2") }, json)
        shouldThrow<IllegalStateException> { repository.profile() }
    }
})
