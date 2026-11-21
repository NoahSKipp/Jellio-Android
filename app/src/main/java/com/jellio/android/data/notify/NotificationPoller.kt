package com.jellio.android.data.notify

import android.content.Context
import com.jellio.android.data.JellyfinDates
import com.jellio.android.data.catching
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.network.JellyfinApi
import com.jellio.android.data.prefs.DisplayPreferences
import com.jellio.android.data.prefs.NotificationPreferences
import com.jellio.android.data.session.SessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val appTr = Tr { key, args -> if (args.isEmpty()) Strings.get(key) else Strings.format(key, *args) }

// One check: reads the signed-in user's notifications and Group Watch
// invites, and posts local alerts for the new ones. Used by the periodic
// worker and when the app comes to the foreground. Reads only, so the
// server's read marks are left to the app's own notification list.
@Singleton
class NotificationPoller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: JellyfinApi,
    private val sessionManager: SessionManager,
    private val prefs: NotificationPreferences,
    private val notifier: JellioNotifier,
) {
    private val lock = Mutex()

    suspend fun check() {
        lock.withLock { run() }
    }

    private suspend fun run() {
        val settings = prefs.settings.value
        if (!settings.master) return
        val session = sessionManager.sessionFlow.first() ?: return
        // Blocked notifications consume nothing, so granting later seeds first.
        if (!notifier.canPost()) return

        val notifications = catching { api.getJellioNotifications() }.getOrNull() ?: return
        val invites = catching { api.getGroupWatchInvites(0L) }.getOrNull().orEmpty()

        val incoming = ArrayList<Incoming>()
        for (n in notifications) {
            val category = categoryOf(n.Kind, n.Id) ?: continue
            incoming.add(
                Incoming(
                    key = dedupeKey(n.Kind, n.Id, n.Detail),
                    serverId = n.Id,
                    category = category,
                    kind = n.Kind.orEmpty(),
                    name = n.Name,
                    detail = n.Detail,
                    date = n.Date,
                    itemId = n.ItemId,
                    fromUserId = n.FromUserId,
                    fromUserName = n.FromUserName,
                    createdMs = JellyfinDates.parseEpochMs(n.CreatedUtc),
                    read = n.Read,
                    readingKey = n.ReadingKey,
                    type = n.Type,
                ),
            )
        }
        for (invite in invites) {
            incoming.add(
                Incoming(
                    key = inviteKey(invite.Id),
                    serverId = null,
                    category = NotifyCategory.GROUP_WATCH,
                    kind = KIND_INVITE,
                    name = invite.GroupName,
                    fromUserName = invite.FromUserName,
                    inviteGroupId = invite.GroupId,
                ),
            )
        }

        val userId = session.userId
        val seeding = !prefs.isSeeded(userId)
        val plan = planDelivery(incoming, settings, prefs.delivered(userId).toHashSet(), seeding, System.currentTimeMillis())
        prefs.addDelivered(userId, plan.seen)
        prefs.setSeeded(userId, true)
        notifier.cancelStale(incoming.filter { !it.read }.map { it.key })
        if (plan.toNotify.isEmpty()) return

        DisplayPreferences.init(context)
        notifier.ensureChannels(appTr)
        groupForPosting(plan.toNotify).forEach { notifier.post(it, appTr, ::partyWhen) }
    }

    private fun partyWhen(date: String?): String {
        val at = JellyfinDates.parseEpochMs(date) ?: return ""
        return java.text.DateFormat.getDateTimeInstance(
            java.text.DateFormat.MEDIUM,
            java.text.DateFormat.SHORT,
            DisplayPreferences.displayLanguage.locale,
        ).format(java.util.Date(at))
    }
}
