package com.jellio.android.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.jellio.android.data.notify.NotifyCategory
import com.jellio.android.data.notify.NotifySettings
import com.jellio.android.data.notify.decodeDelivered
import com.jellio.android.data.notify.encodeDelivered
import com.jellio.android.data.notify.mergeDelivered
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Per device: the phone notification switches, plus what was already
// delivered for each signed-in user. Nothing here is a token or a secret.
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<NotifySettings> = _settings.asStateFlow()

    private fun read() = NotifySettings(
        master = prefs.getBoolean(KEY_MASTER, true),
        categories = NotifyCategory.entries.associateWith { prefs.getBoolean(it.prefKey, true) },
    )

    fun setMaster(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MASTER, enabled).apply()
        _settings.value = read()
    }

    fun setCategory(category: NotifyCategory, enabled: Boolean) {
        prefs.edit().putBoolean(category.prefKey, enabled).apply()
        _settings.value = read()
    }

    var permissionPrompted: Boolean
        get() = prefs.getBoolean(KEY_PERMISSION_PROMPTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_PERMISSION_PROMPTED, value).apply()
        }

    @Synchronized
    fun delivered(userId: String): List<String> = decodeDelivered(prefs.getString(KEY_DELIVERED + userId, null))

    @Synchronized
    fun addDelivered(userId: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val merged = mergeDelivered(delivered(userId), keys)
        prefs.edit().putString(KEY_DELIVERED + userId, encodeDelivered(merged)).apply()
    }

    fun isSeeded(userId: String): Boolean = prefs.getBoolean(KEY_SEEDED + userId, false)

    fun setSeeded(userId: String, seeded: Boolean) {
        prefs.edit().putBoolean(KEY_SEEDED + userId, seeded).apply()
    }

    // After sign out or switching the feature off, the next run seeds again.
    @Synchronized
    fun forgetSeeding() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_SEEDED) }.forEach { editor.remove(it) }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "jellio_notifications"
        private const val KEY_MASTER = "master"
        private const val KEY_PERMISSION_PROMPTED = "permission_prompted"
        private const val KEY_DELIVERED = "delivered_"
        private const val KEY_SEEDED = "seeded_"
    }
}
