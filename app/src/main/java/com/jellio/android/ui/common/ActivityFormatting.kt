package com.jellio.android.ui.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import com.jellio.android.ui.theme.JellioRarityCommon
import com.jellio.android.ui.theme.JellioRarityEpic
import com.jellio.android.ui.theme.JellioRarityLegendary
import com.jellio.android.ui.theme.JellioRarityRare
import java.time.Instant
import java.time.temporal.ChronoUnit

fun formatRelativeTime(isoUtc: String?): String {
    if (isoUtc.isNullOrBlank()) return ""
    val then = com.jellio.android.data.JellyfinDates.parseInstant(isoUtc) ?: return ""
    val minutes = ChronoUnit.MINUTES.between(then, Instant.now())
    if (minutes < 1) return com.jellio.android.data.i18n.Strings.get("time_just_now")
    if (minutes < 60) return if (minutes == 1L) com.jellio.android.data.i18n.Strings.get("time_minute_ago") else com.jellio.android.data.i18n.Strings.format("time_minutes_ago", minutes)
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return if (hours == 1L) com.jellio.android.data.i18n.Strings.get("time_hour_ago") else com.jellio.android.data.i18n.Strings.format("time_hours_ago", hours)
    val days = Math.round(hours / 24.0)
    if (days < 30) return if (days == 1L) com.jellio.android.data.i18n.Strings.get("time_day_ago") else com.jellio.android.data.i18n.Strings.format("time_days_ago", days)
    val months = Math.round(days / 30.0)
    if (months < 12) return if (months == 1L) com.jellio.android.data.i18n.Strings.get("time_month_ago") else com.jellio.android.data.i18n.Strings.format("time_months_ago", months)
    val years = Math.round(months / 12.0)
    return if (years == 1L) com.jellio.android.data.i18n.Strings.get("time_year_ago") else com.jellio.android.data.i18n.Strings.format("time_years_ago", years)
}

fun rarityColor(rarity: String?): Color = when (rarity?.lowercase()) {
    "rare" -> JellioRarityRare
    "epic" -> JellioRarityEpic
    "legendary" -> JellioRarityLegendary
    else -> JellioRarityCommon
}

fun watchActivityText(
    itemType: String?,
    seriesName: String?,
    episodeCount: Int,
    seasonNumber: Int?,
    firstEpisodeNumber: Int?,
    lastEpisodeNumber: Int?,
    itemName: String?,
    rewatch: Boolean = false,
): AnnotatedString = buildAnnotatedString {
    val verb = com.jellio.android.data.i18n.Strings.get(if (rewatch) "activity_rewatched" else "activity_watched")
    val lang = com.jellio.android.data.prefs.DisplayPreferences.displayLanguage.code.lowercase()
    val isEpisode = itemType == "Episode" && !seriesName.isNullOrEmpty()
    val isGerman = lang == "de"

    if (isGerman) {
        if (isEpisode) {
            val season = if (seasonNumber != null) com.jellio.android.data.i18n.Strings.format("feed_season_single", seasonNumber) + ", " else ""
            if (episodeCount > 1) {
                val range = if (firstEpisodeNumber != null && lastEpisodeNumber != null) {
                    com.jellio.android.data.i18n.Strings.format("feed_episodes_range", firstEpisodeNumber, lastEpisodeNumber)
                } else {
                    com.jellio.android.data.i18n.Strings.format("feed_episodes_count", episodeCount)
                }
                append("$season$range ")
            } else if (firstEpisodeNumber != null) {
                append(season + com.jellio.android.data.i18n.Strings.format("feed_episode_single", firstEpisodeNumber) + " ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(seriesName) }
            append(" " + verb.lowercase())
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(itemName ?: "") }
            append(" " + verb.lowercase())
        }
    } else {
        append("$verb ")
        if (isEpisode) {
            val season = if (seasonNumber != null) com.jellio.android.data.i18n.Strings.format("feed_season_single", seasonNumber) + ", " else ""
            if (episodeCount > 1) {
                val range = if (firstEpisodeNumber != null && lastEpisodeNumber != null) {
                    com.jellio.android.data.i18n.Strings.format("feed_episodes_range", firstEpisodeNumber, lastEpisodeNumber)
                } else {
                    com.jellio.android.data.i18n.Strings.format("feed_episodes_count", episodeCount)
                }
                append("$season$range ")
            } else if (firstEpisodeNumber != null) {
                append(season + com.jellio.android.data.i18n.Strings.format("feed_episode_single", firstEpisodeNumber) + " ")
            }
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(seriesName) }
        } else {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(itemName ?: "") }
        }
    }
}

fun badgeActivityText(
    badgeName: String?,
    rarity: String?,
    badgeId: String? = null,
): AnnotatedString = buildAnnotatedString {
    val localizedBadge = com.jellio.android.data.i18n.badgeDisplayName(badgeId, badgeName)
    append(com.jellio.android.data.i18n.Strings.get("activity_unlocked") + " ")
    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = rarityColor(rarity))) {
        append(localizedBadge)
    }
}
