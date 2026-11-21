package com.jellio.android.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifyLogicTest {
    private val now = 10L * 24 * 60 * 60 * 1000
    private val hour = 60L * 60 * 1000
    private val tr = Tr { key, args -> if (args.isEmpty()) key else key + args.joinToString(prefix = "(", postfix = ")", separator = "|") }

    private fun item(
        id: String,
        kind: String = "episode",
        category: NotifyCategory = categoryOf(kind, id)!!,
        detail: String? = null,
        created: Long? = now - hour,
        read: Boolean = false,
        name: String? = "Show",
    ) = Incoming(dedupeKey(kind, id, detail), id, category, kind, name = name, detail = detail, createdMs = created, read = read)

    @Test fun categoriesFollowTheKind() {
        assertEquals(NotifyCategory.RELEASES, categoryOf("episode", "a:1"))
        assertEquals(NotifyCategory.RELEASES, categoryOf("digital-release", "a:1"))
        assertEquals(NotifyCategory.RELEASES, categoryOf(null, "a:1"))
        assertEquals(NotifyCategory.GROUP_WATCH, categoryOf("party_scheduled", "p"))
        assertEquals(NotifyCategory.GROUP_WATCH, categoryOf("party_starting", "p"))
        assertEquals(NotifyCategory.FRIENDS, categoryOf("friend_request", "f"))
        assertEquals(NotifyCategory.FRIENDS, categoryOf("friend_accepted", "f"))
        assertEquals(NotifyCategory.FRIENDS, categoryOf("watchlist_added", "w"))
        assertEquals(NotifyCategory.ANNOUNCEMENTS, categoryOf("announcement", "x"))
        assertEquals(NotifyCategory.BOOKS, categoryOf("manga", "manga:1:2"))
    }

    @Test fun someKindsNeverNotify() {
        assertNull(categoryOf("streamlink", "s"))
        assertNull(categoryOf("streamlink_removed", "s"))
        assertNull(categoryOf("whatsnew", "w"))
        assertNull(categoryOf("announcement", "changelog:1.2.3"))
        assertNull(categoryOf("episode", "changelog:1"))
    }

    @Test fun everyCategoryHasItsOwnChannel() {
        assertEquals(NotifyCategory.entries.size, NotifyCategory.entries.map { it.channelId }.toSet().size)
        assertEquals(NotifyCategory.entries.size, NotifyCategory.entries.map { it.prefKey }.toSet().size)
    }

    @Test fun watchlistAddedGrowsIntoANewKey() {
        assertEquals("n:w1#1", dedupeKey("watchlist_added", "w1", null))
        assertEquals("n:w1#3", dedupeKey("watchlist_added", "w1", "3"))
        assertEquals("n:e1", dedupeKey("episode", "e1", "S1E2"))
    }

    @Test fun seedingMarksEverythingSeenAndNotifiesNothing() {
        val items = listOf(item("a"), item("b", kind = "announcement"))
        val plan = planDelivery(items, NotifySettings(), emptySet(), seeding = true, nowMs = now)
        assertTrue(plan.toNotify.isEmpty())
        assertEquals(setOf("n:a", "n:b"), plan.seen.toSet())
    }

    @Test fun skipsDeliveredReadAndOldItems() {
        val items = listOf(
            item("new"),
            item("done"),
            item("read", read = true),
            item("old", created = now - 4L * 24 * hour),
            item("edge", created = now - 3L * 24 * hour),
        )
        val plan = planDelivery(items, NotifySettings(), setOf("n:done"), seeding = false, nowMs = now)
        assertEquals(listOf("n:new", "n:edge"), plan.toNotify.map { it.key }.sortedByDescending { it == "n:new" })
        assertFalse("n:done" in plan.seen)
        assertFalse("n:read" in plan.seen)
        assertFalse("n:old" in plan.seen)
    }

    @Test fun disabledCategoriesAreSeenButSilent() {
        val settings = NotifySettings(categories = NotifyCategory.entries.associateWith { it != NotifyCategory.FRIENDS })
        val items = listOf(item("a"), item("f", kind = "friend_request"))
        val plan = planDelivery(items, settings, emptySet(), seeding = false, nowMs = now)
        assertEquals(listOf("n:a"), plan.toNotify.map { it.key })
        assertEquals(setOf("n:a", "n:f"), plan.seen.toSet())
    }

    @Test fun masterOffSilencesEverything() {
        val plan = planDelivery(listOf(item("a")), NotifySettings(master = false), emptySet(), seeding = false, nowMs = now)
        assertTrue(plan.toNotify.isEmpty())
    }

    @Test fun duplicateKeysCollapseAndNewestComesFirst() {
        val items = listOf(item("a", created = now - 3 * hour), item("a", created = now - 3 * hour), item("b", created = now - hour))
        val plan = planDelivery(items, NotifySettings(), emptySet(), seeding = false, nowMs = now)
        assertEquals(listOf("n:b", "n:a"), plan.toNotify.map { it.key })
    }

    @Test fun deliveredSetIsBoundedAndKeepsTheNewest() {
        val existing = (1..498).map { "k$it" }
        val merged = mergeDelivered(existing, listOf("k1", "x1", "x2", "x3"), max = 500)
        assertEquals(500, merged.size)
        assertEquals("k2", merged.first())
        assertEquals("x3", merged.last())
        assertEquals(1, merged.count { it == "x1" })
    }

    @Test fun deliveredSurvivesEncoding() {
        val keys = listOf("n:a:1", "i:5", "n:w#2")
        assertEquals(keys, decodeDelivered(encodeDelivered(keys)))
        assertEquals(emptyList<String>(), decodeDelivered(null))
        assertEquals(listOf("n:a b"), decodeDelivered(encodeDelivered(listOf("n:a\nb"))))
    }

    @Test fun threeStaySeparateMoreThanThreeCollapse() {
        val three = (1..3).map { item("e$it") }
        assertTrue(groupForPosting(three).all { it is Group.Single })
        val four = (1..4).map { item("e$it") }
        val groups = groupForPosting(four)
        assertEquals(1, groups.size)
        assertEquals(4, (groups[0] as Group.Summary).items.size)
    }

    @Test fun groupingIsPerCategory() {
        val items = (1..4).map { item("e$it") } + item("a", kind = "announcement") + item("f", kind = "friend_request")
        val groups = groupForPosting(items)
        assertEquals(3, groups.size)
        assertTrue(groups[0] is Group.Summary)
        assertEquals(NotifyCategory.RELEASES, groups[0].category)
        assertEquals(NotifyCategory.FRIENDS, groups[1].category)
        assertEquals(NotifyCategory.ANNOUNCEMENTS, groups[2].category)
    }

    @Test fun releaseTextMirrorsTheNotificationList() {
        val ep = textFor(item("e", detail = "S1E2"), tr)
        assertEquals("calendar_new_episode", ep.title)
        assertEquals("notify_out_now(Show S1E2)", ep.text)
        val movie = textFor(item("m", kind = "digital-release"), tr)
        assertEquals("notify_now_streaming", movie.title)
        assertEquals("notify_available_to_watch(Show)", movie.text)
    }

    @Test fun friendTexts() {
        val req = Incoming("n:f", "f", NotifyCategory.FRIENDS, "friend_request", name = "Sam", fromUserName = null)
        assertEquals("friends_notice_request(Sam)", textFor(req, tr).text)
        val one = Incoming("n:w", "w", NotifyCategory.FRIENDS, "watchlist_added", name = "Dune", detail = "1", fromUserName = "Ann")
        assertEquals("friends_notice_added_one(Ann|Dune)", textFor(one, tr).text)
        val many = one.copy(detail = "4")
        assertEquals("friends_notice_added_many(Ann|4)", textFor(many, tr).text)
        val anon = many.copy(fromUserName = null)
        assertEquals("friends_notice_added_many(tv_someone|4)", textFor(anon, tr).text)
    }

    private fun reading(key: String?, type: String?, detail: String? = "1", itemId: String? = null) = Incoming(
        "n:r", "r", NotifyCategory.FRIENDS, "watchlist_added",
        name = "Dune", detail = detail, fromUserName = "Ann", itemId = itemId, readingKey = key, type = type,
    )

    @Test fun readingNoticeSaysReadingListAndShowsTheType() {
        val book = textFor(reading("i:abc", "Book"), tr)
        assertEquals("friends_notice_added_reading_one(Ann|Dune)", book.text)
        assertEquals("reading_kind_ebook", book.title)
        assertEquals("reading_kind_audiobook", textFor(reading("w:hc:1", "AudioBook"), tr).title)
        assertEquals("reading_kind_manga", textFor(reading("s:one piece", "Manga"), tr).title)
        // Several titles keep the watchlist wording, and so does an old notice without ReadingKey.
        val many = textFor(reading("i:abc", "Book", detail = "3"), tr)
        assertEquals("friends_notice_added_many(Ann|3)", many.text)
        assertEquals("friends_notice_sub_watchlist", many.title)
        val old = textFor(reading(null, null), tr)
        assertEquals("friends_notice_added_one(Ann|Dune)", old.text)
        assertEquals("friends_notice_sub_watchlist", old.title)
        assertEquals("friends_notice_added_one(Ann|Dune)", textFor(reading(" ", "Book"), tr).text)
    }

    @Test fun readingNoticeTapOpensWhereTheItemLives() {
        val id = "0a1b2c3d4e5f60718293a4b5c6d7e8f9"
        assertEquals("detail:$id", targetFor(reading("i:$id", "Book", itemId = id)))
        // An i: key without a usable item id falls back to the reading list.
        assertEquals(TARGET_READING, targetFor(reading("i:$id", "Book", itemId = "00000000-0000-0000-0000-000000000000")))
        assertEquals(TARGET_SERIES_PREFIX + "one piece", targetFor(reading("s:one piece", "Manga")))
        // A series entry may carry a cover chapter file; the series still wins.
        assertEquals(TARGET_SERIES_PREFIX + "one piece", targetFor(reading("s:one piece", "Manga", itemId = id)))
        assertEquals(TARGET_READING, targetFor(reading("w:hc:12345", "Book")))
        assertEquals(TARGET_WATCHLIST, targetFor(reading("i:$id", "Book", detail = "2", itemId = id)))
        assertEquals(TARGET_WATCHLIST, targetFor(reading("s:x", "Manga", detail = "5")))
        // Old notices still open the item or the watchlist.
        assertEquals("detail:$id", targetFor(reading(null, null, itemId = id)))
        assertEquals(TARGET_WATCHLIST, targetFor(reading(null, null)))
    }

    @Test fun readingNoticeHelpers() {
        assertTrue(isReadingNotice("w:hc:1"))
        assertFalse(isReadingNotice(null))
        assertFalse(isReadingNotice("  "))
        assertEquals("one piece", readingNoticeSeriesKey("s:one piece"))
        assertNull(readingNoticeSeriesKey("s:"))
        assertNull(readingNoticeSeriesKey("i:abc"))
        assertNull(readingNoticeSeriesKey(null))
        assertEquals("reading_kind_ebook", readingNoticeTypeKey("Book"))
        assertEquals("reading_kind_ebook", readingNoticeTypeKey(null))
    }

    @Test fun inviteMangaAndAnnouncementTexts() {
        val invite = Incoming(inviteKey(7), null, NotifyCategory.GROUP_WATCH, KIND_INVITE, name = "Movie night", fromUserName = "Ann")
        assertEquals("party_invite_toast(Ann|Movie night)", textFor(invite, tr).text)
        val manga = item("m", kind = "manga", name = "Berserk", detail = "Chapter 5")
        assertEquals("Berserk: Chapter 5", textFor(manga, tr).text)
        assertEquals("Berserk: notif_new_chapter", textFor(manga.copy(detail = null), tr).text)
        val note = item("n", kind = "announcement", name = "Maintenance at 10")
        assertEquals("Maintenance at 10", textFor(note, tr).text)
    }

    @Test fun summaryText() {
        val s = summaryText(NotifyCategory.RELEASES, 5, tr)
        assertEquals("notif_cat_releases", s.title)
        assertEquals("notif_summary_releases(5)", s.text)
    }

    @Test fun tapsLeadToTheRightPlace() {
        val id = "0a1b2c3d-0000-0000-0000-000000000001"
        assertEquals("detail:$id", targetFor(item("e").copy(itemId = id)))
        assertEquals(TARGET_LIST, targetFor(item("e")))
        assertEquals(TARGET_LIST, targetFor(item("e").copy(itemId = "00000000-0000-0000-0000-000000000000")))
        val single = item("w", kind = "watchlist_added", detail = "1").copy(itemId = id)
        assertEquals("detail:$id", targetFor(single))
        assertEquals(TARGET_WATCHLIST, targetFor(single.copy(detail = "3")))
        assertEquals(TARGET_FRIENDS, targetFor(item("f", kind = "friend_request")))
        assertEquals(TARGET_PROFILE_PREFIX + "abc", targetFor(item("f", kind = "friend_accepted").copy(fromUserId = "abc")))
        assertEquals(TARGET_FRIENDS, targetFor(item("f", kind = "friend_accepted")))
        assertEquals(TARGET_GROUP_WATCH, targetFor(item("p", kind = "party_starting")))
        assertEquals(TARGET_GROUP_WATCH, targetFor(Incoming(inviteKey(1), null, NotifyCategory.GROUP_WATCH, KIND_INVITE)))
        assertEquals(TARGET_LIST, targetFor(item("a", kind = "announcement")))
        assertEquals(TARGET_LIST, targetFor(item("m", kind = "manga")))
        assertEquals(TARGET_GROUP_WATCH, summaryTarget(NotifyCategory.GROUP_WATCH))
        assertEquals(TARGET_FRIENDS, summaryTarget(NotifyCategory.FRIENDS))
        assertEquals(TARGET_LIST, summaryTarget(NotifyCategory.RELEASES))
    }

    @Test fun openingTheListKeepsInvitesAndForeignTags() {
        assertTrue(shouldCancelOnListOpen(tagFor("n:abc")))
        assertTrue(shouldCancelOnListOpen(summaryTag(NotifyCategory.FRIENDS)))
        assertFalse(shouldCancelOnListOpen(tagFor(inviteKey(3))))
        assertFalse(shouldCancelOnListOpen("other"))
        assertFalse(shouldCancelOnListOpen(null))
    }
}
