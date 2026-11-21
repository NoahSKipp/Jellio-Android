package com.jellio.android.ui.friends

import com.jellio.android.data.FriendFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsLogicTest {
    @Test
    fun statusMapsToButton() {
        assertEquals(FriendButtonState.Add, buttonStateOf(relationOf("none"), true))
        assertEquals(FriendButtonState.Hidden, buttonStateOf(relationOf("none"), false))
        assertEquals(FriendButtonState.RequestSent, buttonStateOf(relationOf("outgoing"), false))
        assertEquals(FriendButtonState.Respond, buttonStateOf(relationOf("incoming"), false))
        assertEquals(FriendButtonState.Friends, buttonStateOf(relationOf("friends"), false))
        for (s in listOf("self", "private", "closed", "weird", null)) {
            assertEquals(FriendButtonState.Hidden, buttonStateOf(relationOf(s), true))
        }
    }

    @Test
    fun resultsMapToKeys() {
        assertEquals("friends_result_added", addResultKey("added"))
        assertEquals("friends_result_already_on_list", addResultKey("already_on_list"))
        assertEquals("friends_result_not_visible", addResultKey("not_visible"))
        assertEquals("friends_result_failed", addResultKey("something_new"))
        assertEquals("friends_result_failed", addResultKey(null))
        assertTrue(isAddSuccess("added"))
        assertTrue(isAddSuccess("already_on_list"))
        assertFalse(isAddSuccess("muted"))
    }

    @Test
    fun readingResultsSayReadingList() {
        assertEquals("reading_result_added", addResultKey("added", reading = true))
        assertEquals("reading_result_already_on_list", addResultKey("already_on_list", reading = true))
        assertEquals("reading_result_not_visible", addResultKey("not_visible", reading = true))
        assertEquals("reading_result_unsupported", addResultKey("unsupported", reading = true))
        // The rest reads the same for both kinds of list.
        for (result in listOf("muted", "disabled", "limit", "not_friends", "something_new", null)) {
            assertEquals(addResultKey(result), addResultKey(result, reading = true))
        }
        assertEquals("friends_result_unsupported", addResultKey("unsupported"))
    }

    @Test
    fun failuresMapToKeys() {
        assertEquals("friends_err_cooldown", failureKey(FriendFailure.Cooldown))
        assertEquals("friends_err_gone", failureKey(FriendFailure.Gone))
        assertEquals(FriendFailure.entries.size, FriendFailure.entries.map { failureKey(it) }.toSet().size)
    }

    @Test
    fun watchlistNoticeCountAndTarget() {
        assertEquals(1, watchlistAddedCount(null))
        assertEquals(1, watchlistAddedCount("x"))
        assertEquals(1, watchlistAddedCount("0"))
        assertEquals(3, watchlistAddedCount(" 3 "))
        assertEquals(NoticeTarget.Item, watchlistNoticeTarget(1, true))
        assertEquals(NoticeTarget.None, watchlistNoticeTarget(1, false))
        assertEquals(NoticeTarget.Watchlist, watchlistNoticeTarget(3, true))
    }

    @Test
    fun readingNoticeTargets() {
        assertEquals(NoticeTarget.Item, watchlistNoticeTarget(1, true, "i:abc"))
        assertEquals(NoticeTarget.Reading, watchlistNoticeTarget(1, false, "i:abc"))
        assertEquals(NoticeTarget.Series, watchlistNoticeTarget(1, true, "s:one piece"))
        assertEquals(NoticeTarget.Series, watchlistNoticeTarget(1, false, "s:one piece"))
        assertEquals(NoticeTarget.Reading, watchlistNoticeTarget(1, false, "w:hc:1"))
        assertEquals(NoticeTarget.Watchlist, watchlistNoticeTarget(2, true, "i:abc"))
        // No ReadingKey: the watchlist behaviour as before.
        assertEquals(NoticeTarget.Item, watchlistNoticeTarget(1, true, null))
        assertEquals(NoticeTarget.None, watchlistNoticeTarget(1, false, ""))
    }

    @Test
    fun kindsAndTypes() {
        assertTrue(isFriendKind("friend_request"))
        assertTrue(isFriendKind("watchlist_added"))
        assertFalse(isFriendKind("episode"))
        assertTrue(canShareType("Movie"))
        assertTrue(canShareType("Series"))
        assertFalse(canShareType("Episode"))
        assertTrue(badgeVisible(1))
        assertFalse(badgeVisible(0))
        assertTrue(nextMuted(false))
    }
}
