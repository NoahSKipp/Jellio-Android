package com.jellio.android.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.session.Session
import com.jellio.android.ui.friends.FriendWatchlistPickerDialog
import com.jellio.android.ui.friends.FriendsViewModel
import com.jellio.android.ui.friends.canShareType

@Composable
fun rememberCardOptionsHost(
    session: Session,
    onToggleWatchlist: (BaseItemDto) -> Unit,
    onToggleWatched: (BaseItemDto) -> Unit,
    friendsViewModel: FriendsViewModel = hiltViewModel(),
): (BaseItemDto) -> Unit {
    var menuTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    var shareTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    val friends by friendsViewModel.state.collectAsState()
    val extras = rememberCardMenuExtras(session)

    menuTarget?.let { item ->
        CardOptionsMenu(
            item = item,
            extras = extras.forItem(item),
            onToggleWatchlist = { onToggleWatchlist(item) },
            onToggleWatched = { onToggleWatched(item) },
            onAddToFriendWatchlist = if (canShareType(item.Type) && friends.available && friends.friends.isNotEmpty()) {
                { shareTarget = item }
            } else {
                null
            },
            onDismiss = { menuTarget = null },
        )
    }
    shareTarget?.let { item ->
        FriendWatchlistPickerDialog(item = item, viewModel = friendsViewModel, onDismiss = { shareTarget = null })
    }

    return { item -> menuTarget = item }
}
