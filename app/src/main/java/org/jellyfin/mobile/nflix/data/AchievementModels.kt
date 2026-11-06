package org.jellyfin.mobile.nflix.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Immutable
@Serializable
data class AchievementBadge(
    @SerialName("Id") val id: String,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("Key") val key: String? = null,
    @SerialName("Title") val title: String,
    @SerialName("Description") val description: String,
    @SerialName("Icon") val icon: String? = null,
    @SerialName("Category") val category: String,
    @SerialName("Unlocked") val unlocked: Boolean,
    @SerialName("UnlockedAt") val unlockedAt: String? = null,
    @SerialName("UnlockDeviceId") val unlockDeviceId: String? = null,
    @SerialName("CurrentValue") val currentValue: Long,
    @SerialName("TargetValue") val targetValue: Long,
    @SerialName("Rarity") val rarity: String,
    @SerialName("EarnSource") val earnSource: JsonElement? = null,
) {
    val progress: Float get() = if (unlocked) 1f else if (targetValue > 0) (currentValue.toDouble() / targetValue).toFloat().coerceIn(0f, 1f) else 0f
}

@Immutable
@Serializable
data class AchievementSummary(
    @SerialName("Unlocked") val unlocked: Int,
    @SerialName("Total") val total: Int,
    @SerialName("Percentage") val percentage: Double,
    @SerialName("EquippedCount") val equippedCount: Int,
    @SerialName("Score") val score: Int,
    @SerialName("CurrentWatchStreak") val currentWatchStreak: Int,
    @SerialName("BestWatchStreak") val bestWatchStreak: Int,
)

@Immutable
@Serializable
data class AchievementTier(
    @SerialName("Name") val name: String,
    @SerialName("MinScore") val minScore: Int,
    @SerialName("Color") val color: String,
    @SerialName("Icon") val icon: String,
) {
    val frenchName: String get() = when (name) {
        "Rookie" -> "Débutant"
        "Novice" -> "Novice"
        "Viewer" -> "Spectateur"
        "Regular" -> "Habitué"
        "Enthusiast" -> "Passionné"
        "Binger" -> "Marathonien"
        "Connoisseur" -> "Connaisseur"
        "Maestro" -> "Maestro"
        "Legend" -> "Légende"
        "Immortal" -> "Immortel"
        else -> name
    }
}

@Immutable
@Serializable
data class AchievementRank(
    @SerialName("Score") val score: Int,
    @SerialName("Tier") val tier: AchievementTier,
    @SerialName("NextTier") val nextTier: AchievementTier? = null,
    @SerialName("ProgressToNext") val progressToNext: Int,
)

@Immutable
@Serializable
data class AchievementProfile(
    @SerialName("SchemaVersion") val schemaVersion: Int,
    @SerialName("Language") val language: String,
    @SerialName("Badges") val badges: List<AchievementBadge>,
    @SerialName("Summary") val summary: AchievementSummary,
    @SerialName("Rank") val rank: AchievementRank,
    @SerialName("Equipped") val equipped: List<AchievementBadge>,
    @SerialName("RarityStats") val rarityStats: Map<String, Double>,
)
