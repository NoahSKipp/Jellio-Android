package com.jellio.android.data.notify

import android.content.Context
import com.jellio.android.data.catching
import com.jellio.android.data.prefs.NotificationPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// What the app tells the notification machinery: signed in or out, in the
// foreground, a switch changed, the notification list opened.
@Singleton
class NotifyController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: NotificationPreferences,
    private val notifier: JellioNotifier,
    private val poller: NotificationPoller,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Signed in: keep the periodic check scheduled while notifications are on.
    fun onSignedIn() {
        if (prefs.settings.value.master) NotifyScheduler.schedule(context) else NotifyScheduler.cancel(context)
    }

    // Signed out: no work, no leftover alerts, and the next sign-in seeds again.
    fun onSignedOut() {
        NotifyScheduler.cancel(context)
        notifier.cancelAll()
        prefs.forgetSeeding()
    }

    fun onForeground() {
        checkNow()
    }

    fun checkNow() {
        scope.launch { catching { poller.check() } }
    }

    fun setMaster(enabled: Boolean) {
        prefs.setMaster(enabled)
        if (enabled) {
            NotifyScheduler.schedule(context)
            checkNow()
        } else {
            NotifyScheduler.cancel(context)
            notifier.cancelAll()
            prefs.forgetSeeding()
        }
    }

    fun setCategory(category: NotifyCategory, enabled: Boolean) {
        prefs.setCategory(category, enabled)
    }

    fun onNotificationListOpened() {
        notifier.cancelForListOpen()
    }

    // An invite answered or dismissed in the app needs no alert any more.
    fun onInviteHandled(userId: String?, inviteId: Long) {
        notifier.cancelInvite(inviteId)
        if (!userId.isNullOrBlank()) prefs.addDelivered(userId, listOf(inviteKey(inviteId)))
    }
}
