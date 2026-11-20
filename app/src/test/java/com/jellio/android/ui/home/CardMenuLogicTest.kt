package com.jellio.android.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CardMenuLogicTest {
    @Test
    fun linkPointsAtTheWebPageOfTheItem() {
        assertEquals("https://jf.example/web/#/item?id=abc123", shareUrl("https://jf.example/", "abc123"))
        assertEquals("http://10.0.0.2:8096/web/#/item?id=abc", shareUrl("http://10.0.0.2:8096", "abc"))
    }

    @Test
    fun episodeLabelNamesSeasonAndEpisode() {
        assertEquals("Show - Season 2 Episode 5", shareLabel("Episode", "Pilot", "Show", 2, 5))
        assertEquals("Show - Specials Episode 1", shareLabel("Episode", "Pilot", "Show", 0, 1))
        assertEquals("Show - Episode 5", shareLabel("Episode", "Pilot", "Show", null, 5))
        assertEquals("Show - Pilot", shareLabel("Episode", "Pilot", "Show", null, null))
        assertEquals("Season 2 Episode 5", shareLabel("Episode", "Pilot", null, 2, 5))
        assertEquals("Pilot", shareLabel("Episode", "Pilot", null, null, null))
        assertEquals("Episode", shareLabel("Episode", null, null, null, null))
    }

    @Test
    fun seasonAndOtherLabels() {
        assertEquals("Show - Season 3", shareLabel("Season", "Season 3", "Show", null, 3))
        assertEquals("Show - Specials", shareLabel("Season", "Specials", "Show", null, 0))
        assertEquals("Season 3", shareLabel("Season", "Season 3", null, null, null))
        assertEquals("Show Season", shareLabel("Season", null, "Show", null, null))
        assertEquals("Heat", shareLabel("Movie", "Heat", null, null, null))
        assertEquals("", shareLabel("Movie", null, null, null, null))
    }

    @Test
    fun textCarriesTheLabelAndTheLink() {
        assertEquals("Look at Heat on Jellyfin - https://x", shareText("Heat", "https://x"))
        assertEquals("https://x", shareText("", "https://x"))
    }

    @Test
    fun onlyVideoAndShowsCanBeDownloaded() {
        assertEquals(DownloadKind.VIDEO, downloadKindFor("Movie"))
        assertEquals(DownloadKind.VIDEO, downloadKindFor("Episode"))
        assertEquals(DownloadKind.EPISODES, downloadKindFor("Series"))
        assertEquals(DownloadKind.EPISODES, downloadKindFor("Season"))
        assertEquals(null, downloadKindFor("BoxSet"))
        assertEquals(null, downloadKindFor("Book"))
        assertEquals(null, downloadKindFor(null))
    }

    @Test
    fun episodeScopesPickFromTheUnwatched() {
        // 1..12, the first three watched.
        val episodes = (1..12).toList()
        val played = { n: Int -> n <= 3 }
        assertEquals(listOf(4), pickEpisodes(EpisodeScope.NEXT_1, episodes, played))
        assertEquals(listOf(4, 5, 6, 7, 8), pickEpisodes(EpisodeScope.NEXT_5, episodes, played))
        assertEquals((4..12).toList(), pickEpisodes(EpisodeScope.NEXT_10, episodes, played))
        assertEquals((4..12).toList(), pickEpisodes(EpisodeScope.UNWATCHED, episodes, played))
        assertEquals(episodes, pickEpisodes(EpisodeScope.ALL, episodes, played))
        assertEquals(emptyList<Int>(), pickEpisodes(EpisodeScope.NEXT_1, episodes) { true })
    }

    @Test
    fun wholeLabelFollowsSeasonOrSeries() {
        assertEquals("downloads_scope_season", EpisodeScope.ALL.labelKeyFor(true))
        assertEquals("downloads_scope_series", EpisodeScope.ALL.labelKeyFor(false))
        assertEquals("downloads_scope_next_5", EpisodeScope.NEXT_5.labelKeyFor(true))
    }

    @Test
    fun qualitiesMatchTheDownloadManager() {
        assertEquals(listOf("low", "high", "higher", "original"), DownloadQuality.values().map { it.value })
    }
}
