package org.jellyfin.mobile.nflix.data

/** Advanced library filters (decade, minimum rating, studio) mapped to Jellyfin /Items query parameters. */
data class AdvancedFilter(val decade: Int? = null, val minRating: Int? = null, val studioId: String? = null) {
    val active: Boolean get() = decade != null || minRating != null

    val count: Int get() = listOfNotNull(decade, minRating).size

    fun query(): Map<String, Any?> = buildMap {
        decade?.let { put("years", (it..it + 9).joinToString(",")) }
        minRating?.let { put("minCommunityRating", it) }
        studioId?.let { put("studioIds", it) }
    }

    companion object {
        val DECADES = listOf(2020, 2010, 2000, 1990, 1980, 1970, 1960)
        val RATINGS = listOf(6, 7, 8)

        fun decadeLabel(decade: Int): String = if (decade >= 2000) "Années $decade" else "Années ${decade % 100}"

        fun ratingLabel(rating: Int): String = "$rating+ ★"
    }
}
