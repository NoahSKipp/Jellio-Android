package com.jellio.android.data

import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.model.UserItemDataDto
import com.jellio.android.ui.home.withoutStaleEpisodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRulesTest {
    private val runtime = 36_000_000_000L // one hour

    private fun episode(id: String, season: Int, number: Int, played: Boolean, position: Long, series: String = "show") =
        BaseItemDto(
            Id = id,
            Type = "Episode",
            SeriesId = series,
            ParentIndexNumber = season,
            IndexNumber = number,
            RunTimeTicks = runtime,
            UserData = UserItemDataDto(Played = played, PlaybackPositionTicks = position),
        )

    @Test
    fun playedWithAPartPositionCountsAsInProgress() {
        val item = episode("e3", 1, 3, played = true, position = runtime / 3)
        assertTrue(isPartlyWatched(item))
        assertFalse(isFinished(item))
    }

    @Test
    fun playedNearTheEndStaysFinished() {
        val item = episode("e3", 1, 3, played = true, position = runtime * 99 / 100)
        assertFalse(isPartlyWatched(item))
        assertTrue(isFinished(item))
    }

    @Test
    fun playedWithoutAPositionIsFinished() {
        assertTrue(isFinished(episode("e1", 1, 1, played = true, position = 0L)))
    }

    @Test
    fun unknownRuntimeTrustsTheSavedPosition() {
        val item = episode("e3", 1, 3, played = true, position = 1_000L).copy(RunTimeTicks = null)
        assertTrue(isPartlyWatched(item))
    }

    @Test
    fun continueWatchingKeepsAPlayedEpisodeThatIsPartWatched() {
        val kept = listOf(episode("e3", 1, 3, played = true, position = runtime / 3)).withoutStaleEpisodes(emptyList())
        assertEquals(listOf("e3"), kept.map { it.Id })
    }

    @Test
    fun continueWatchingStillDropsAnEpisodeBehindNextUp() {
        val finished = episode("e3", 1, 3, played = true, position = runtime * 99 / 100)
        val leftover = episode("e2", 1, 2, played = false, position = runtime / 2)
        val next = episode("e4", 1, 4, played = false, position = 0L)
        val kept = listOf(finished, leftover).withoutStaleEpisodes(listOf(next))
        assertTrue(kept.isEmpty())
    }
}
