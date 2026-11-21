package com.jellio.android.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.ViewModel
import com.jellio.android.data.notify.JellioNotifier
import com.jellio.android.data.notify.NotifyCategory
import com.jellio.android.data.notify.NotifyController
import com.jellio.android.data.notify.NotifySettings
import com.jellio.android.data.prefs.NotificationPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val prefs: NotificationPreferences,
    private val controller: NotifyController,
    private val notifier: JellioNotifier,
) : ViewModel() {
    val settings: StateFlow<NotifySettings> = prefs.settings

    fun setMaster(enabled: Boolean) = controller.setMaster(enabled)

    fun setCategory(category: NotifyCategory, enabled: Boolean) = controller.setCategory(category, enabled)

    fun notificationsAllowed(): Boolean = notifier.canPost()

    fun markPermissionPrompted() {
        prefs.permissionPrompted = true
    }

    fun checkNow() = controller.checkNow()
}

// The system page for Jellio's notifications, with a switch per channel.
fun openSystemNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
