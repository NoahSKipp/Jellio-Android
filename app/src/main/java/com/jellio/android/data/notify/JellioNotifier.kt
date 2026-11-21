package com.jellio.android.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jellio.android.MainActivity
import com.jellio.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// Posts and cancels the local Android notifications. Each category has its
// own channel, so the system settings offer the same switches as the app.
@Singleton
class JellioNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager get() = NotificationManagerCompat.from(context)

    // False while the user has switched Jellio off in the system or, on
    // Android 13 and later, has not granted the permission.
    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    // Safe to repeat: it also refreshes the channel names after a language change.
    fun ensureChannels(tr: Tr) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val system = context.getSystemService(NotificationManager::class.java) ?: return
        NotifyCategory.entries.forEach { category ->
            val importance = when (category) {
                NotifyCategory.GROUP_WATCH -> NotificationManager.IMPORTANCE_HIGH
                NotifyCategory.BOOKS -> NotificationManager.IMPORTANCE_LOW
                else -> NotificationManager.IMPORTANCE_DEFAULT
            }
            system.createNotificationChannel(NotificationChannel(category.channelId, tr.get(category.labelKey), importance))
        }
    }

    fun post(group: Group, tr: Tr, whenText: (String?) -> String) {
        if (!canPost()) return
        when (group) {
            is Group.Single -> {
                val item = group.item
                val text = textFor(item, tr, whenText)
                show(tagFor(item.key), group.category, text.title, text.text, null, targetFor(item))
            }
            is Group.Summary -> {
                val summary = summaryText(group.category, group.items.size, tr)
                val lines = group.items.take(MAX_LINES).map { textFor(it, tr, whenText).text }
                show(summaryTag(group.category), group.category, summary.title, summary.text, lines, summaryTarget(group.category))
            }
        }
    }

    private fun show(tag: String, category: NotifyCategory, title: String, text: String, lines: List<String>?, target: String) {
        val builder = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_stat_jellio)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent(tag, target))
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(if (category == NotifyCategory.GROUP_WATCH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
        if (lines != null) {
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            lines.forEach { style.addLine(it) }
            builder.setStyle(style)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        }
        runCatching { manager.notify(tag, NOTIFICATION_ID, builder.build()) }
    }

    private fun openIntent(tag: String, target: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_NOTIFICATION)
            .putExtra(EXTRA_NOTIFICATION_TARGET, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, tag.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // The app's own notification list marks the server notifications read.
    fun cancelForListOpen() = cancelWhere { shouldCancelOnListOpen(it) }

    fun cancelAll() = cancelWhere { it != null && it.startsWith(TAG_PREFIX) }

    // An alert whose notification was read or removed elsewhere goes away too.
    fun cancelStale(unreadKeys: Collection<String>) {
        val live = unreadKeys.mapTo(HashSet()) { tagFor(it) }
        cancelWhere { tag ->
            tag != null && shouldCancelOnListOpen(tag) && !tag.startsWith(TAG_SUMMARY_PREFIX) && tag !in live
        }
    }

    fun cancelInvite(inviteId: Long) {
        runCatching { manager.cancel(tagFor(inviteKey(inviteId)), NOTIFICATION_ID) }
    }

    private fun cancelWhere(match: (String?) -> Boolean) {
        runCatching {
            val system = context.getSystemService(NotificationManager::class.java) ?: return
            system.activeNotifications.filter { match(it.tag) }.forEach { system.cancel(it.tag, it.id) }
        }
    }

    companion object {
        const val ACTION_OPEN_NOTIFICATION = "com.jellio.android.ACTION_OPEN_NOTIFICATION"
        const val EXTRA_NOTIFICATION_TARGET = "com.jellio.android.EXTRA_NOTIFICATION_TARGET"
        private const val NOTIFICATION_ID = 1
        private const val MAX_LINES = 5
    }
}
