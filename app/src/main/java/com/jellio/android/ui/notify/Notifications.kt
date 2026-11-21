package com.jellio.android.ui.notify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import com.jellio.android.data.catching
import com.jellio.android.data.model.JellioNotificationDto
import com.jellio.android.data.network.JellyfinApi
import com.jellio.android.ui.groupwatch.GroupWatchViewModel
import com.jellio.android.ui.groupwatch.InviteRow
import com.jellio.android.ui.groupwatch.SidePanelOverlay
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val api: JellyfinApi,
    private val notifyController: com.jellio.android.data.notify.NotifyController,
) : ViewModel() {
    private val _items = MutableStateFlow<List<JellioNotificationDto>?>(null)
    val items: StateFlow<List<JellioNotificationDto>?> = _items.asStateFlow()

    // Manga lives on the web and desktop only, so its alerts stay there.
    fun load() {
        viewModelScope.launch {
            _items.value = catching { api.getJellioNotifications() }
                .getOrDefault(emptyList())
                .filter { it.Kind != "manga" && !it.Id.orEmpty().startsWith("changelog:") }
            catching { api.markJellioNotificationsRead() }
            // Read here, so the matching phone notifications go too.
            notifyController.onNotificationListOpened()
        }
    }

    fun delete(id: String) {
        val current = _items.value ?: return
        _items.value = current.filterNot { it.Id == id }
        viewModelScope.launch {
            catching { api.deleteJellioNotification(id) }
        }
    }

    fun clearAll() {
        _items.value = emptyList()
        viewModelScope.launch {
            catching { api.clearJellioNotifications() }
        }
    }
}

// The same lines components/notifications.js's messageFor/subtitleFor show.
internal fun messageFor(n: JellioNotificationDto): String = when (n.Kind) {
    "streamlink" -> com.jellio.android.data.i18n.Strings.format("live_link_dead_notice", n.Name.orEmpty())
    "streamlink_removed" -> com.jellio.android.data.i18n.Strings.format("live_link_removed_notice", n.Name.orEmpty(), n.Detail.orEmpty())
    "announcement" -> n.Name.orEmpty()
    "party_scheduled" -> com.jellio.android.data.i18n.Strings.format("party_notice_scheduled", n.Detail ?: com.jellio.android.data.i18n.Strings.get("tv_someone"), n.Name.orEmpty(), partyNoticeWhen(n.Date))
    "party_starting" -> com.jellio.android.data.i18n.Strings.format("party_notice_starting", n.Name.orEmpty())
    "episode" -> com.jellio.android.data.i18n.Strings.format("notify_out_now", (n.Name ?: com.jellio.android.data.i18n.Strings.get("notify_new_item")) + (n.Detail?.let { " $it" } ?: ""))
    "friend_request" -> com.jellio.android.data.i18n.Strings.format("friends_notice_request", friendSender(n))
    "friend_accepted" -> com.jellio.android.data.i18n.Strings.format("friends_notice_accepted", friendSender(n))
    "watchlist_added" -> {
        val count = com.jellio.android.ui.friends.watchlistAddedCount(n.Detail)
        val key = com.jellio.android.data.notify.watchlistNoticeTextKey(count, n.ReadingKey)
        if (count > 1) {
            com.jellio.android.data.i18n.Strings.format(key, friendSender(n), count)
        } else {
            com.jellio.android.data.i18n.Strings.format(key, friendSender(n), n.Name.orEmpty())
        }
    }
    else -> com.jellio.android.data.i18n.Strings.format("notify_available_to_watch", n.Name ?: com.jellio.android.data.i18n.Strings.get("notify_new_item"))
}

// A friend request carries the sender's name in Name too.
private fun friendSender(n: JellioNotificationDto): String =
    n.FromUserName?.takeIf { it.isNotBlank() }
        ?: (if (n.Kind == "watchlist_added") null else n.Name?.takeIf { it.isNotBlank() })
        ?: com.jellio.android.data.i18n.Strings.get("tv_someone")

private fun partyNoticeWhen(date: String?): String {
    val at = com.jellio.android.data.syncplay.PartyManager.parseTime(date) ?: return ""
    return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, com.jellio.android.data.i18n.displayLocale()).format(java.util.Date(at))
}

internal fun subtitleFor(n: JellioNotificationDto): String = when (n.Kind) {
    "streamlink" -> com.jellio.android.data.i18n.Strings.get("live_link_dead_sub")
    "streamlink_removed" -> com.jellio.android.data.i18n.Strings.get("live_link_section")
    "announcement" -> com.jellio.android.data.i18n.Strings.get("notify_from_server")
    "party_scheduled", "party_starting" -> com.jellio.android.data.i18n.Strings.get("party_notice_sub")
    "episode" -> com.jellio.android.data.i18n.Strings.get("calendar_new_episode")
    "friend_request", "friend_accepted" -> com.jellio.android.data.i18n.Strings.get("friends_notice_sub")
    "watchlist_added" -> com.jellio.android.data.i18n.Strings.get(
        com.jellio.android.data.notify.watchlistNoticeSubKey(com.jellio.android.ui.friends.watchlistAddedCount(n.Detail), n.ReadingKey, n.Type),
    )
    else -> com.jellio.android.data.i18n.Strings.get("notify_now_streaming")
}

// The notification drawer from Jellio's sidebar, for the TV: Group Watch
// invites first, then releases from the watchlist and server announcements.
@Composable
fun NotificationsOverlay(
    onDismiss: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenLiveTv: () -> Unit = {},
    onOpenGroupWatch: () -> Unit = {},
    viewModel: NotificationsViewModel = hiltViewModel(),
    groupWatch: GroupWatchViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val items by viewModel.items.collectAsState()
    val invites by groupWatch.invites.collectAsState()
    SidePanelOverlay(title = com.jellio.android.ui.i18n.string("notifications_title"), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            invites.forEach { invite ->
                InviteRow(
                    invite = invite,
                    onJoin = {
                        groupWatch.joinInvite(invite)
                        onDismiss()
                    },
                    onDismiss = { groupWatch.dismissInvite(invite) },
                )
            }
            val list = items
            when {
                list == null -> Text(text = com.jellio.android.ui.i18n.string("status_loading"), color = JellioTextSecondary)
                list.isEmpty() && invites.isEmpty() -> Text(text = com.jellio.android.ui.i18n.string("notifications_nothing_new"), color = JellioTextSecondary)
                else -> list.forEach { n ->
                    val itemId = n.ItemId?.takeIf { n.Kind != "announcement" && it.replace("-", "").trim('0').isNotEmpty() }
                    Surface(
                        onClick = {
                            if (n.Kind == "streamlink" || n.Kind == "streamlink_removed") {
                                onDismiss()
                                onOpenLiveTv()
                            } else if (n.Kind == "party_scheduled" || n.Kind == "party_starting") {
                                onDismiss()
                                onOpenGroupWatch()
                            } else if (itemId != null) {
                                onDismiss()
                                onOpenItem(itemId)
                            }
                        },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = if (n.Read) 0.04f else 0.1f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.25f),
                            focusedContentColor = JellioText,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(text = messageFor(n), color = JellioText)
                            Text(text = subtitleFor(n), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
