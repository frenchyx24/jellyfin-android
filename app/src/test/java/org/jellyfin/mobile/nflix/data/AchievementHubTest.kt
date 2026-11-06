package org.jellyfin.mobile.nflix.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jellyfin.mobile.nflix.alerts.AchievementAlertSource
import org.jellyfin.mobile.nflix.ui.screens.HubTab
import org.jellyfin.mobile.nflix.ui.screens.achievementAdmin
import org.jellyfin.mobile.nflix.ui.screens.visibleHubTabs

class AchievementHubTest : FunSpec({
    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    class Fake(val answers: Map<String, String>) {
        val calls = mutableListOf<Triple<String, String, String?>>()
        val hub = AchievementHub({ "u1" }, { method, path, body ->
            calls += Triple(method, path, body)
            answers[path.removePrefix("Plugins/AchievementBadges/")] ?: throw NflixHttpException(404, "missing")
        }, json)
    }

    test("public config defaults and switches") {
        val hub = Fake(mapOf("public-config" to """{"LeaderboardEnabled":false,"questsEnabled":true,"ForcePrivacyMode":true,"WelcomeMessage":"Salut"}""")).hub
        val config = runBlocking { hub.publicConfig() }
        config.leaderboard shouldBe false
        config.quests shouldBe true
        config.compare shouldBe true
        config.welcome shouldBe "Salut"
        visibleHubTabs(config, isAdmin = false) shouldContainExactly listOf(HubTab.Quests, HubTab.Shop)
        visibleHubTabs(AchievementPublicConfig(), isAdmin = true).last() shouldBe HubTab.Admin
    }

    test("missing plugin surfaces a dedicated error") {
        shouldThrow<AchievementPluginMissingException> { runBlocking { Fake(emptyMap()).hub.quests() } }
    }

    test("quests accept lists, single objects and partial answers") {
        val hub = Fake(mapOf("users/u1/quests" to """{"Daily":[{"Id":"d1","Title":"Trois films","Target":3,"Current":5,"Reward":20}],"Weekly":{"Id":"w1","Title":"Semaine","Target":0,"Completed":true},"DailyRerollsRemaining":1}""")).hub
        val board = runBlocking { hub.quests() }
        board.daily.single().progress shouldBe 1f
        board.weekly.single().progress shouldBe 1f
        board.dailyRerolls shouldBe 1
        board.weeklyRerolls shouldBe 0
    }

    test("comparison errors are translated, results parsed") {
        val refused = Fake(mapOf("compare/u1/u2" to """{"Error":"Compare feature is disabled."}""")).hub
        runBlocking { refused.compare("u2") }.second shouldBe "La comparaison est désactivée sur ce serveur."
        val ok = Fake(mapOf("compare/u1/u2" to """{"UserA":{"UserName":"Moi","Score":120},"UserB":{"UserName":"Toi","Score":80},"OnlyA":3,"OnlyB":1,"Both":7}""")).hub
        val (comparison, _) = runBlocking { ok.compare("u2") }
        comparison!!.a.score shouldBe 120
        comparison.both shouldBe 7
        shouldThrow<IllegalArgumentException> { runBlocking { ok.compare("../admin") } }
    }

    test("shop, cosmetics and leaderboards parse") {
        val hub = Fake(mapOf(
            "shop/catalog" to """{"PowerUps":[{"Type":"XpBoost","Id":"powerup-xp-boost","DisplayName":"Boost","PriceScore":50,"BundleSize":3}],"Cosmetics":[{"Id":"theme-red","Kind":"ProfileTheme","DisplayName":"Rouge","PriceScore":0,"MilestoneScore":500}]}""",
            "users/u1/cosmetics" to """{"Owned":["theme-red"],"EquippedThemeId":"theme-red","ScoreBank":42}""",
            "leaderboard?limit=50" to """[{"UserId":"a","UserName":"Ana","Score":300,"Unlocked":10,"Total":90}]""",
            "leaderboard-prestige?limit=50" to """[{"UserId":"b","UserName":"Bob","PrestigeLevel":2,"CurrentScore":900}]""",
        )).hub
        val shop = runBlocking { hub.shop() }
        shop.powerUps.single().bundle shouldBe 3
        shop.cosmetics.single().milestone shouldBe 500
        val cosmetics = runBlocking { hub.cosmetics() }
        cosmetics.equipped["ProfileTheme"] shouldBe "theme-red"
        cosmetics.scoreBank shouldBe 42
        runBlocking { hub.leaderboard() }.single().name shouldBe "Ana"
        runBlocking { hub.prestigeLeaderboard() }.single().score shouldBe 900
    }

    test("a refused purchase keeps the server message") {
        val fake = Fake(mapOf("users/u1/buy-badge/b1" to """{"Success":false,"Message":"Not enough score bank."}"""))
        val result = runBlocking { fake.hub.buyBadge("b1") }
        result.success shouldBe false
        result.message shouldBe "Score insuffisant."
        AchievementHub.translate("Not enough score bank. Need 600, have 42.") shouldBe "Score insuffisant : il faut 600 points, vous en avez 42."
        AchievementHub.badgePrice("Epic") shouldBe 1200
    }

    test("a 404 on an action means no profile yet, not a missing plugin") {
        val result = runBlocking { Fake(emptyMap()).hub.reroll(weekly = false) }
        result.success shouldBe false
        result.message!! shouldContain "Profil"
    }

    test("refusal bodies are surfaced") {
        val hub = AchievementHub({ "u1" }, { _, _, _ -> throw NflixHttpException(429, "x", "{\"Message\":\"Daily quest reroll already used today.\"}") }, json)
        runBlocking { hub.reroll(weekly = false) }.message shouldBe "Relance déjà utilisée pour cette période."
    }

    test("feature config is written back whole, tokens included") {
        val fake = Fake(mapOf("admin/feature-config" to """{"LeaderboardEnabled":true,"TracearrApiToken":"keep-me","MaxEquippedBadges":5}"""))
        runBlocking {
            val config = fake.hub.featureConfig()
            fake.answers.toMutableMap()
            fake.hub.saveFeatureConfig(config, mapOf("leaderboardEnabled" to JsonPrimitive(false)))
        }
        val (method, _, body) = fake.calls.last()
        method shouldBe "POST"
        val sent = json.parseToJsonElement(body!!).jsonObject
        sent["TracearrApiToken"] shouldBe JsonPrimitive("keep-me")
        sent["LeaderboardEnabled"] shouldBe JsonPrimitive(false)
        sent.containsKey("leaderboardEnabled") shouldBe false
    }

    test("webhook URL is masked and kept when unchanged") {
        val fake = Fake(mapOf("admin/webhook" to """{"WebhookUrl":"https://discord.com/api/webhooks/123/secret","WebhookEnabled":true}"""))
        val config = runBlocking { fake.hub.webhook() }
        config.maskedUrl!! shouldNotContain "secret"
        config.maskedUrl!! shouldContain "discord.com"
        runBlocking { fake.hub.saveWebhook(config, false, null, null) }
        fake.calls.last().third!! shouldContain "secret"
        shouldThrow<IllegalArgumentException> { runBlocking { fake.hub.saveWebhook(config, true, "http://insecure", null) } }
    }

    test("badge unlock notifications get stable negative ids") {
        val alerts = AchievementAlertSource.planBadgeAlerts(listOf("b1" to "Cinéphile", "b1" to "Cinéphile", "b2" to "Marathon"))
        alerts.size shouldBe 2
        alerts.all { it.id < 0 } shouldBe true
        alerts.first().id shouldBe AchievementAlertSource.planBadgeAlerts(listOf("b1" to "x")).first().id
        alerts.first().title shouldContain "Cinéphile"
    }

    test("admin needs Jellyfin rights and, through Mira, the admin group") {
        achievementAdmin(true, "password", emptySet()) shouldBe true
        achievementAdmin(true, "mira", setOf("membres")) shouldBe false
        achievementAdmin(true, "mira", setOf("authentik Admins")) shouldBe true
        achievementAdmin(false, "mira", setOf("authentik Admins")) shouldBe false
    }
})
