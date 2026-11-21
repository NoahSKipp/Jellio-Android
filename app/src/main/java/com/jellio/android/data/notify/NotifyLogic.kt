package com.jellio.android.data.notify

// Pure rules for the local phone notifications: which server notifications
// are worth an alert, dedupe, grouping, wording and where a tap leads.

enum class NotifyCategory(val prefKey: String, val channelId: String, val labelKey: String, val summaryKey: String) {
    RELEASES("cat_releases", "jellio_releases", "notif_cat_releases", "notif_summary_releases"),
    GROUP_WATCH("cat_groupwatch", "jellio_groupwatch", "notif_cat_groupwatch", "notif_summary_groupwatch"),
    FRIENDS("cat_friends", "jellio_friends", "notif_cat_friends", "notif_summary_friends"),
    ANNOUNCEMENTS("cat_announcements", "jellio_announcements", "notif_cat_announcements", "notif_summary_announcements"),
    BOOKS("cat_books", "jellio_books", "notif_cat_books", "notif_summary_books"),
}

data class NotifySettings(
    val master: Boolean = true,
    val categories: Map<NotifyCategory, Boolean> = NotifyCategory.entries.associateWith { true },
) {
    fun isOn(category: NotifyCategory): Boolean = master && categories[category] != false
}

// What the poller needs from one server notification or Group Watch invite.
data class Incoming(
    val key: String,
    val serverId: String?,
    val category: NotifyCategory,
    val kind: String,
    val name: String? = null,
    val detail: String? = null,
    val date: String? = null,
    val itemId: String? = null,
    val fromUserId: String? = null,
    val fromUserName: String? = null,
    val createdMs: Long? = null,
    val read: Boolean = false,
    val inviteGroupId: String? = null,
)

const val KIND_INVITE = "invite"
const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
const val SUMMARY_THRESHOLD = 3
const val MAX_DELIVERED = 500

private val FRIEND_KINDS = setOf("friend_request", "friend_accepted", "watchlist_added")

// Null for kinds that never become a phone alert (Live TV link health,
// changelog entries, what's new).
fun categoryOf(kind: String?, id: String?): NotifyCategory? {
    val k = kind.orEmpty()
    if (id.orEmpty().startsWith("changelog:")) return null
    return when {
        k == "streamlink" || k == "streamlink_removed" || k == "whatsnew" || k.startsWith("changelog") -> null
        k == "party_scheduled" || k == "party_starting" -> NotifyCategory.GROUP_WATCH
        k in FRIEND_KINDS -> NotifyCategory.FRIENDS
        k == "announcement" -> NotifyCategory.ANNOUNCEMENTS
        k == "manga" -> NotifyCategory.BOOKS
        else -> NotifyCategory.RELEASES
    }
}

fun watchlistCount(detail: String?): Int = detail?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1

// A watchlist_added notice grows in place while friends keep adding, so each
// new count is its own alert; everything else is one alert per Id.
fun dedupeKey(kind: String?, id: String, detail: String?): String =
    if (kind == "watchlist_added") "n:$id#${watchlistCount(detail)}" else "n:$id"

fun inviteKey(inviteId: Long): String = "i:$inviteId"

fun hasRealId(id: String?): Boolean = !id.isNullOrBlank() && id.replace("-", "").trim('0').isNotEmpty()

class Plan(val toNotify: List<Incoming>, val seen: List<String>)

// Everything unread, recent and not delivered before counts as seen, so a
// switched off category or the seeding run never floods later. Only enabled
// categories outside a seeding run produce alerts.
fun planDelivery(
    items: List<Incoming>,
    settings: NotifySettings,
    delivered: Set<String>,
    seeding: Boolean,
    nowMs: Long,
    maxAgeMs: Long = MAX_AGE_MS,
): Plan {
    val fresh = items
        .filter { !it.read }
        .filter { it.createdMs == null || nowMs - it.createdMs <= maxAgeMs }
        .filter { it.key !in delivered }
        .distinctBy { it.key }
        .sortedByDescending { it.createdMs ?: 0L }
    val notify = if (seeding) emptyList() else fresh.filter { settings.isOn(it.category) }
    return Plan(notify, fresh.map { it.key })
}

// Oldest first, newest last, bounded.
fun mergeDelivered(existing: List<String>, added: Collection<String>, max: Int = MAX_DELIVERED): List<String> {
    val have = existing.toHashSet()
    val out = ArrayList<String>(existing)
    for (key in added) if (have.add(key)) out.add(key)
    return if (out.size > max) out.subList(out.size - max, out.size).toList() else out
}

fun encodeDelivered(keys: List<String>): String = keys.joinToString("\n") { it.replace('\n', ' ') }

fun decodeDelivered(raw: String?): List<String> =
    raw.orEmpty().split('\n').filter { it.isNotBlank() }

sealed interface Group {
    val category: NotifyCategory

    data class Single(val item: Incoming) : Group {
        override val category: NotifyCategory get() = item.category
    }

    data class Summary(override val category: NotifyCategory, val items: List<Incoming>) : Group
}

// More than three at once in one category collapse into a single summary.
fun groupForPosting(items: List<Incoming>): List<Group> {
    val out = ArrayList<Group>()
    for (category in NotifyCategory.entries) {
        val of = items.filter { it.category == category }
        if (of.isEmpty()) continue
        if (of.size > SUMMARY_THRESHOLD) out.add(Group.Summary(category, of)) else of.forEach { out.add(Group.Single(it)) }
    }
    return out
}

// Translation hook, so the wording is testable without the app's string tables.
fun interface Tr {
    fun get(key: String, vararg args: Any): String
}

class NoticeText(val title: String, val text: String)

fun textFor(n: Incoming, tr: Tr, whenText: (String?) -> String = { it.orEmpty() }): NoticeText {
    val name = n.name.orEmpty()
    val someone = tr.get("tv_someone")
    val sender = n.fromUserName?.takeIf { it.isNotBlank() }
        ?: (if (n.kind == "watchlist_added") null else n.name?.takeIf { it.isNotBlank() })
        ?: someone
    return when (n.kind) {
        KIND_INVITE -> NoticeText(
            tr.get("groupwatch_invite_title"),
            tr.get("party_invite_toast", n.fromUserName?.takeIf { it.isNotBlank() } ?: someone, name.ifBlank { tr.get("tv_group_watch") }),
        )
        "party_scheduled" -> NoticeText(tr.get("party_notice_sub"), tr.get("party_notice_scheduled", n.detail?.takeIf { it.isNotBlank() } ?: someone, name, whenText(n.date)))
        "party_starting" -> NoticeText(tr.get("party_notice_sub"), tr.get("party_notice_starting", name))
        "friend_request" -> NoticeText(tr.get("friends_notice_sub"), tr.get("friends_notice_request", sender))
        "friend_accepted" -> NoticeText(tr.get("friends_notice_sub"), tr.get("friends_notice_accepted", sender))
        "watchlist_added" -> {
            val count = watchlistCount(n.detail)
            val text = if (count > 1) tr.get("friends_notice_added_many", sender, count) else tr.get("friends_notice_added_one", sender, name)
            NoticeText(tr.get("friends_notice_sub_watchlist"), text)
        }
        "announcement" -> NoticeText(tr.get("notify_from_server"), name.ifBlank { tr.get("notify_from_server") })
        "manga" -> NoticeText(tr.get("notif_cat_books"), name + ": " + (n.detail?.takeIf { it.isNotBlank() } ?: tr.get("notif_new_chapter")))
        "episode" -> NoticeText(
            tr.get("calendar_new_episode"),
            tr.get("notify_out_now", (n.name ?: tr.get("notify_new_item")) + (n.detail?.let { " $it" } ?: "")),
        )
        else -> NoticeText(tr.get("notify_now_streaming"), tr.get("notify_available_to_watch", n.name ?: tr.get("notify_new_item")))
    }
}

fun summaryText(category: NotifyCategory, count: Int, tr: Tr): NoticeText =
    NoticeText(tr.get(category.labelKey), tr.get(category.summaryKey, count))

// Where a tap leads, as the string MainActivity hands to JellioApp:
// "detail:<id>" opens a title, "notify:<where>" the rest.
const val TARGET_PREFIX = "notify:"
const val TARGET_WATCHLIST = "notify:watchlist"
const val TARGET_FRIENDS = "notify:friends"
const val TARGET_GROUP_WATCH = "notify:groupwatch"
const val TARGET_LIST = "notify:list"
const val TARGET_PROFILE_PREFIX = "notify:profile:"

fun targetFor(n: Incoming): String {
    val item = n.itemId?.takeIf { hasRealId(it) }
    return when (n.kind) {
        KIND_INVITE, "party_scheduled", "party_starting" -> TARGET_GROUP_WATCH
        "friend_request" -> TARGET_FRIENDS
        "friend_accepted" -> n.fromUserId?.takeIf { it.isNotBlank() }?.let { TARGET_PROFILE_PREFIX + it } ?: TARGET_FRIENDS
        "watchlist_added" -> if (watchlistCount(n.detail) > 1 || item == null) TARGET_WATCHLIST else "detail:$item"
        "announcement" -> TARGET_LIST
        "manga" -> TARGET_LIST
        else -> if (item != null) "detail:$item" else TARGET_LIST
    }
}

fun summaryTarget(category: NotifyCategory): String = when (category) {
    NotifyCategory.GROUP_WATCH -> TARGET_GROUP_WATCH
    NotifyCategory.FRIENDS -> TARGET_FRIENDS
    else -> TARGET_LIST
}

// The Notification tag and the integer id behind one alert, so a later
// check updates it in place and opening the list can cancel it.
const val TAG_PREFIX = "jellio:"
const val TAG_INVITE_PREFIX = "jellio:i:"

fun tagFor(key: String): String = TAG_PREFIX + key

const val TAG_SUMMARY_PREFIX = "jellio:summary:"

fun summaryTag(category: NotifyCategory): String = TAG_SUMMARY_PREFIX + category.channelId

// Opening the notification list reads the server notifications; invites are
// answered elsewhere and stay.
fun shouldCancelOnListOpen(tag: String?): Boolean =
    tag != null && tag.startsWith(TAG_PREFIX) && !tag.startsWith(TAG_INVITE_PREFIX)
