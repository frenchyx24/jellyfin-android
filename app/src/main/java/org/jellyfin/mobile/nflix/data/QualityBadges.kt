package org.jellyfin.mobile.nflix.data

/**
 * Small quality badges shown on posters: 4K (video height), HDR/Dolby Vision (from the video
 * stream display title, e.g. "HEVC HDR"), VF (a French audio track) and VOSTFR (no French audio
 * but French subtitles). Pure logic so it is unit tested.
 */
object QualityBadges {

    private fun videoStreams(item: Item): List<MediaStream> =
        (item.mediaSources.firstOrNull()?.streams ?: item.mediaStreams).filter { it.type == "Video" }

    private fun languages(item: Item, type: String): Set<String> =
        (item.mediaSources.firstOrNull()?.streams ?: item.mediaStreams)
            .filter { it.type == type }
            .mapNotNull { it.language?.uppercase() }
            .toSet()

    /** Badges for a poster, in display order. */
    fun of(item: Item): List<String> {
        val badges = mutableListOf<String>()
        val video = videoStreams(item)
        if (video.any { (it.height ?: 0) >= 2160 }) badges += "4K"
        val videoTitles = video.mapNotNull { it.displayTitle?.uppercase() }.joinToString(" ")
        if ("DV" in videoTitles || "DOVI" in videoTitles || "DOLBY VISION" in videoTitles) badges += "DV"
        else if ("HDR" in videoTitles || "HLG" in videoTitles) badges += "HDR"
        val audio = languages(item, "Audio")
        val subs = languages(item, "Subtitle")
        val frenchAudio = audio.any { it == "FRE" || it == "FRA" || it == "FR" }
        val frenchSubs = subs.any { it == "FRE" || it == "FRA" || it == "FR" }
        if (frenchAudio) badges += "VF"
        else if (frenchSubs) badges += "VOSTFR"
        return badges
    }
}
