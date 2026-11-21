package com.jellio.android.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.model.JellioNotificationDto
import com.jellio.android.data.notify.readingNoticeSeriesKey
import com.jellio.android.data.session.Session
import com.jellio.android.ui.books.ReadingListViewModel
import com.jellio.android.ui.common.formatRelativeTime
import com.jellio.android.ui.notify.messageFor
import com.jellio.android.ui.notify.subtitleFor
import com.jellio.android.ui.nowplaying.DrawerActionButton
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary

// Quick Hub cards for the friend kinds: a request to answer in place, an
// accepted request, and titles a friend added to the watchlist.
@Composable
fun FriendNotificationCard(
    notification: JellioNotificationDto,
    session: Session,
    viewModel: FriendsViewModel,
    rawImageUrl: (String, String?, String, Int) -> String,
    onOpenItem: (String) -> Unit,
    onOpenWatchlist: () -> Unit,
    onDismiss: () -> Unit,
    leadFocus: Modifier = Modifier,
    onOpenSeries: (key: String, title: String) -> Unit = { _, _ -> },
    onOpenReading: () -> Unit = {},
    readingListViewModel: ReadingListViewModel = hiltViewModel(),
) {
    val kind = notification.Kind
    val sender = notification.FromUserName ?: notification.Name.orEmpty()
    val senderAvatar = notification.FromUserId?.let { viewModel.avatarUrl(session, it, notification.FromImageTag) }
    val itemId = notification.ItemId?.takeIf { it.replace("-", "").trim('0').isNotEmpty() }
    val count = watchlistAddedCount(notification.Detail)
    val readingKey = notification.ReadingKey?.takeIf { it.isNotBlank() }
    val isReading = kind == KIND_WATCHLIST_ADDED && readingKey != null
    val target = watchlistNoticeTarget(count, itemId != null, readingKey)
    var posterFailed by remember(notification.Id) { mutableStateOf(false) }
    // A library book shows its own image; other reading items the cover on
    // the reading list entry, else an icon.
    val itemPoster = isReading && readingKey != null && readingKey.startsWith("i:") && itemId != null
    val listCover by produceState<String?>(null, notification.Id) {
        if (isReading && !itemPoster && readingKey != null) value = readingListViewModel.coverUrlFor(readingKey)
    }
    val posterModel: String? = when {
        kind != KIND_WATCHLIST_ADDED -> null
        isReading -> if (itemPoster && itemId != null) rawImageUrl(itemId, null, "Primary", 200) else listCover
        else -> itemId?.let { rawImageUrl(it, null, "Primary", 200) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = if (notification.Read) 0.04f else 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (kind == KIND_WATCHLIST_ADDED && ((posterModel != null && !posterFailed) || isReading)) {
                    Box {
                        if (posterModel != null && !posterFailed) {
                            AsyncImage(
                                model = posterModel,
                                contentDescription = notification.Name,
                                contentScale = ContentScale.Crop,
                                onError = { posterFailed = true },
                                modifier = Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 48.dp, height = 68.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                            ) {
                                Icon(
                                    imageVector = when (notification.Type) {
                                        "AudioBook" -> Icons.Filled.Headphones
                                        "Manga" -> Icons.Filled.AutoStories
                                        else -> Icons.Filled.MenuBook
                                    },
                                    contentDescription = null,
                                    tint = JellioTextSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        FriendAvatar(
                            url = senderAvatar,
                            name = sender,
                            size = 24.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .border(1.5.dp, JellioBg, CircleShape),
                        )
                    }
                } else {
                    FriendAvatar(url = senderAvatar, name = sender, size = 48.dp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                            if (!notification.Read) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = subtitleFor(notification),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = JellioTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        val time = remember(notification.CreatedUtc, notification.Date) {
                            formatRelativeTime(notification.CreatedUtc ?: notification.Date)
                        }
                        if (time.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelSmall,
                                color = JellioTextSecondary.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = messageFor(notification),
                        color = JellioText,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                when (kind) {
                    KIND_FRIEND_REQUEST -> {
                        // The request id rides in Detail; the notification id is friendreq:{id}.
                        val requestId = notification.Detail?.takeIf { it.isNotBlank() } ?: notification.Id.removePrefix("friendreq:")
                        DrawerActionButton(
                            label = Strings.get("friends_accept"),
                            icon = Icons.Filled.Info,
                            primary = true,
                            onClick = {
                                viewModel.accept(requestId)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).then(leadFocus),
                        )
                        DrawerActionButton(
                            label = Strings.get("friends_decline"),
                            icon = Icons.Filled.Close,
                            primary = false,
                            onClick = {
                                viewModel.decline(requestId)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    KIND_WATCHLIST_ADDED -> {
                        if (target != NoticeTarget.None) {
                            DrawerActionButton(
                                label = Strings.get(
                                    when (target) {
                                        NoticeTarget.Watchlist -> "friends_open_watchlist"
                                        NoticeTarget.Series, NoticeTarget.Reading -> "reading_open_list"
                                        else -> "details_title"
                                    },
                                ),
                                icon = Icons.Filled.Info,
                                primary = true,
                                onClick = {
                                    when (target) {
                                        NoticeTarget.Watchlist -> onOpenWatchlist()
                                        NoticeTarget.Series -> readingNoticeSeriesKey(readingKey)?.let { onOpenSeries(it, notification.Name.orEmpty()) }
                                        NoticeTarget.Reading -> onOpenReading()
                                        else -> itemId?.let(onOpenItem)
                                    }
                                },
                                modifier = Modifier.weight(1f).then(leadFocus),
                            )
                        }
                        if (count == 1 && (isReading || itemId != null)) {
                            DrawerActionButton(
                                label = Strings.get("friends_undo"),
                                icon = Icons.Filled.Close,
                                primary = false,
                                onClick = {
                                    // A reading item was never on the watchlist: undo takes it off the reading list.
                                    if (isReading && readingKey != null) {
                                        readingListViewModel.remove(readingKey)
                                        onDismiss()
                                    } else if (itemId != null) {
                                        viewModel.undoWatchlistAdd(session, itemId) { onDismiss() }
                                    }
                                },
                                modifier = Modifier.weight(1f).then(if (target == NoticeTarget.None) leadFocus else Modifier),
                            )
                        }
                        DrawerActionButton(
                            label = Strings.get("notifications_dismiss"),
                            icon = Icons.Filled.Close,
                            primary = target == NoticeTarget.None,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).then(
                                if (target == NoticeTarget.None && !(count == 1 && (isReading || itemId != null))) leadFocus else Modifier,
                            ),
                        )
                    }
                    else -> DrawerActionButton(
                        label = Strings.get("notifications_dismiss"),
                        icon = Icons.Filled.Close,
                        primary = true,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).then(leadFocus),
                    )
                }
            }
        }
    }
}
