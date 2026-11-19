package com.jellio.android.ui.profile

import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import com.jellio.android.ui.common.isCompactScreen
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Border
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.android.data.model.BadgeDto
import com.jellio.android.data.model.GroupedActivityEntryDto
import com.jellio.android.data.session.Session
import com.jellio.android.ui.friends.FriendProfileButton
import com.jellio.android.ui.friends.FriendsPanel
import com.jellio.android.ui.friends.FriendsSection
import com.jellio.android.ui.friends.FriendsViewModel
import kotlinx.coroutines.delay
import com.jellio.android.ui.common.ErrorState
import com.jellio.android.ui.common.JellioTextField
import com.jellio.android.ui.common.formatRelativeTime
import com.jellio.android.ui.common.rarityColor
import com.jellio.android.ui.common.watchActivityText
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioRarityRare
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary

@Composable
fun ProfileScreen(
    session: Session,
    userId: String?,
    userImageUrl: (userId: String, tag: String?, maxWidth: Int) -> String,
    bannerUrl: (userId: String) -> String,
    itemImageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onNavigateToDetail: (String) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenProfile: (String) -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
    friendsViewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val targetUserId = userId ?: session.userId
    val contentFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var focusedBadge by remember { mutableStateOf<BadgeDto?>(null) }
    LaunchedEffect(uiState.isLoading, uiState.error) {
        if (!uiState.isLoading && uiState.error == null) runCatching { contentFocusRequester.requestFocus() }
    }

    LaunchedEffect(targetUserId) { viewModel.load(session, userId) }

    var showFriends by remember { mutableStateOf(false) }
    val friendsFocus = remember { FocusRequester() }
    // Back from the panel lands on the section that opened it.
    var friendsWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showFriends) {
        if (showFriends) {
            friendsWasOpen = true
        } else if (friendsWasOpen) {
            friendsWasOpen = false
            delay(120)
            runCatching { friendsFocus.requestFocus() }
        }
    }

    val isCompact = isCompactScreen()
    val horizontalPad = if (isCompact) 16.dp else 48.dp
    val badgeColumns = if (isCompact) 3 else BADGE_COLUMNS

    Box(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = com.jellio.android.data.i18n.Strings.get("status_loading"), color = JellioTextSecondary)
            }
            uiState.error != null -> ErrorState(
                message = com.jellio.android.ui.i18n.string("profile_load_error"),
                failure = uiState.error,
                onRetry = { viewModel.retry(session, userId) },
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                val user = uiState.user
                val profile = uiState.profile
                val achievements = uiState.achievements
                val bannerImageUrl = remember(targetUserId) { bannerUrl(targetUserId) }

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().focusRequester(contentFocusRequester).focusRestorer()) {
                    item {
                        ProfileHeader(
                            bannerUrl = bannerImageUrl,
                            avatarUrl = userImageUrl(targetUserId, user?.PrimaryImageTag, 240),
                            name = user?.Name ?: "",
                            onFocused = { scope.launch { listState.animateScrollToItem(0) } },
                        ) {
                            if (!uiState.isOwner) {
                                FriendProfileButton(userId = targetUserId, viewModel = friendsViewModel, modifier = Modifier.padding(top = 6.dp))
                            }
                            if (uiState.isOwner && profile?.IsPrivate == true) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Color.White.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                ) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(14.dp))
                                    Text(text = com.jellio.android.data.i18n.Strings.get("settings_private_profile"), color = JellioTextSecondary, modifier = Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    item {
                        Column(modifier = Modifier.padding(horizontal = horizontalPad, vertical = 4.dp)) {
                            BioSection(
                                bio = profile?.Bio,
                                isOwner = uiState.isOwner,
                                isEditing = uiState.isEditingBio,
                                draft = uiState.bioDraft,
                                isSaving = uiState.isSavingBio,
                                onEdit = { viewModel.startEditingBio() },
                                onDraftChange = { viewModel.updateBioDraft(it) },
                                onSave = { viewModel.saveBio() },
                                onCancel = { viewModel.cancelEditingBio() },
                            )
                        }
                    }

                    if (uiState.isOwner) {
                        item {
                            FriendsSection(
                                session = session,
                                viewModel = friendsViewModel,
                                onOpen = { showFriends = true },
                                focusRequester = friendsFocus,
                            )
                        }
                    }

                    if (achievements?.IsPrivate == true) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(horizontal = horizontalPad, vertical = 32.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(32.dp))
                                    Text(text = com.jellio.android.data.i18n.Strings.get("profile_is_private"), color = JellioTextSecondary, modifier = Modifier.padding(top = 12.dp))
                                }
                            }
                        }
                    } else if (achievements != null) {
                        item { StatsRow(achievements.MoviesCompleted, achievements.EpisodesCompleted, achievements.TotalCompleted, achievements.BestBingeStreak) }
                        val shownBadges = if (uiState.isOwner) achievements.Badges else achievements.Badges.filter { it.Unlocked }
                        item {
                            val unlocked = achievements.Badges.count { it.Unlocked }
                            Text(
                                text = if (uiState.isOwner) {
                                    com.jellio.android.data.i18n.Strings.format("tv_badges_count", unlocked, achievements.Badges.size)
                                } else {
                                    com.jellio.android.data.i18n.Strings.get("feed_filter_badges") + " ($unlocked)"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = horizontalPad, end = horizontalPad, top = 28.dp, bottom = 4.dp),
                            )
                            // What the focused badge is, and how to earn it.
                            val info = focusedBadge
                            Text(
                                text = info?.let { com.jellio.android.data.i18n.badgeDisplayDescription(it.Id, it.Description) }.orEmpty(),
                                color = JellioTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = horizontalPad).padding(bottom = 8.dp).heightIn(min = 20.dp),
                            )
                        }
                        if (shownBadges.isEmpty()) {
                            item {
                                Text(text = com.jellio.android.data.i18n.Strings.get("feed_nothing_here"), color = JellioTextSecondary, modifier = Modifier.padding(horizontal = horizontalPad))
                            }
                        }
                        items(shownBadges.chunked(badgeColumns)) { rowBadges ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = if (isCompact) 11.dp else 42.dp)) {
                                rowBadges.forEach { badge ->
                                    BadgeTile(badge, onFocused = { focusedBadge = badge }, modifier = Modifier.weight(1f))
                                }
                                repeat(badgeColumns - rowBadges.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                        item {
                            Text(
                                text = com.jellio.android.data.i18n.Strings.get("tv_recent_activity"),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(start = horizontalPad, end = horizontalPad, top = 24.dp, bottom = 12.dp),
                            )
                        }
                        if (achievements.RecentActivity.isEmpty()) {
                            item {
                                Text(text = com.jellio.android.data.i18n.Strings.get("feed_nothing_here"), color = JellioTextSecondary, modifier = Modifier.padding(horizontal = horizontalPad))
                            }
                        } else {
                            items(achievements.RecentActivity) { entry ->
                                ActivityRow(
                                    entry = entry,
                                    imageUrl = itemImageUrl,
                                    onClick = { onNavigateToDetail(entry.SeriesId ?: entry.ItemId) },
                                )
                            }
                        }
                    }

                    if (uiState.isOwner) {
                        item {
                            Surface(
                                onClick = onLogout,
                                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                                colors = com.jellio.android.ui.common.dangerButtonColors(),
                                modifier = Modifier.padding(start = horizontalPad, top = 32.dp, bottom = 48.dp),
                            ) {
                                Text(text = com.jellio.android.data.i18n.Strings.get("nav_log_out"), modifier = Modifier.padding(horizontal = 32.dp, vertical = 14.dp))
                            }
                        }
                    } else {
                        item { Spacer(modifier = Modifier.height(48.dp)) }
                    }
                }
            }
        }
        if (showFriends) {
            FriendsPanel(
                session = session,
                viewModel = friendsViewModel,
                onDismiss = { showFriends = false },
                onOpenProfile = onOpenProfile,
            )
        }
    }
}

private const val BADGE_COLUMNS = 6

// Banner fading into the page, with the avatar and name sitting on it. The
// avatar takes focus so the d-pad can always climb back to the top.
@Composable
private fun ProfileHeader(
    bannerUrl: String,
    avatarUrl: String,
    name: String,
    onFocused: () -> Unit,
    extras: @Composable () -> Unit,
) {
    val isCompact = isCompactScreen()
    var showFallback by remember(bannerUrl) { mutableStateOf(false) }
    val headerModifier = if (isCompact) {
        Modifier.fillMaxWidth().heightIn(min = 180.dp)
    } else {
        Modifier.fillMaxWidth().aspectRatio(16f / 6.2f)
    }
    Box(modifier = headerModifier) {
        if (showFallback) {
            Box(modifier = Modifier.matchParentSize().background(JellioBgElevated))
        } else {
            AsyncImage(
                model = bannerUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { showFallback = true },
                modifier = Modifier.matchParentSize(),
            )
        }
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to JellioBg.copy(alpha = 0.35f),
                    1f to JellioBg,
                ),
            ),
        )
        val avatarSize = if (isCompact) 84.dp else 104.dp
        val horizontalPad = if (isCompact) 16.dp else 48.dp
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .statusBarsPadding()
                .padding(start = horizontalPad, end = horizontalPad, top = if (isCompact) 36.dp else 16.dp, bottom = 8.dp),
        ) {
            Surface(
                onClick = {},
                shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
                colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, focusedContainerColor = JellioBgElevated),
                border = ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(3.dp, Color.White.copy(alpha = 0.25f)), shape = CircleShape),
                    focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() },
            ) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(avatarSize).clip(CircleShape),
                )
            }
            Column(modifier = Modifier.padding(start = if (isCompact) 16.dp else 20.dp, bottom = 6.dp)) {
                Text(
                    text = name,
                    style = if (isCompact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                extras()
            }
        }
    }
}

@Composable
private fun BioSection(
    bio: String?,
    isOwner: Boolean,
    isEditing: Boolean,
    draft: String,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        if (isEditing) {
            JellioTextField(
                value = draft,
                onValueChange = onDraftChange,
                label = com.jellio.android.ui.i18n.string("profile_bio_prompt"),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                Surface(
                    onClick = onSave,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = com.jellio.android.ui.common.primaryButtonColors(),
                ) {
                    Text(text = com.jellio.android.data.i18n.Strings.get(if (isSaving) "profile_saving" else "profile_save"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
                Surface(
                    onClick = onCancel,
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(containerColor = JellioBgElevated, contentColor = JellioText, focusedContainerColor = Color.White.copy(alpha = 0.18f), focusedContentColor = JellioText),
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(text = com.jellio.android.data.i18n.Strings.get("action_cancel"), modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                }
            }
        } else if (!bio.isNullOrEmpty()) {
            Text(text = bio, color = JellioTextSecondary, modifier = Modifier.clickableIfOwner(isOwner, onEdit))
        } else if (isOwner) {
            Text(
                text = com.jellio.android.data.i18n.Strings.get("tv_add_bio"),
                color = JellioTextSecondary,
                modifier = Modifier.clickableIfOwner(isOwner, onEdit),
            )
        }
    }
}

private fun Modifier.clickableIfOwner(isOwner: Boolean, onClick: () -> Unit): Modifier =
    if (isOwner) this.clickable(onClick = onClick) else this

@Composable
private fun StatsRow(movies: Int, episodes: Int, total: Int, bestBinge: Int) {
    val isCompact = isCompactScreen()
    if (isCompact) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(Icons.Filled.Movie, com.jellio.android.data.i18n.Strings.get("profile_stat_movies"), movies.toString(), Modifier.weight(1f))
                StatTile(Icons.Filled.Tv, com.jellio.android.data.i18n.Strings.get("profile_stat_episodes"), episodes.toString(), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(Icons.Filled.CheckCircle, com.jellio.android.data.i18n.Strings.get("profile_stat_total_watched"), total.toString(), Modifier.weight(1f))
                StatTile(Icons.Filled.LocalFireDepartment, com.jellio.android.data.i18n.Strings.get("profile_stat_best_binge"), bestBinge.toString(), Modifier.weight(1f))
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 44.dp, vertical = 8.dp)) {
            StatTile(Icons.Filled.Movie, com.jellio.android.data.i18n.Strings.get("profile_stat_movies"), movies.toString(), Modifier.weight(1f))
            StatTile(Icons.Filled.Tv, com.jellio.android.data.i18n.Strings.get("profile_stat_episodes"), episodes.toString(), Modifier.weight(1f))
            StatTile(Icons.Filled.CheckCircle, com.jellio.android.data.i18n.Strings.get("profile_stat_total_watched"), total.toString(), Modifier.weight(1f))
            StatTile(Icons.Filled.LocalFireDepartment, com.jellio.android.data.i18n.Strings.get("profile_stat_best_binge"), bestBinge.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(icon: ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        onClick = {},
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(16.dp)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier.padding(4.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = JellioRarityRare, modifier = Modifier.size(22.dp))
            Text(text = value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            Text(text = label, color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BadgeTile(badge: BadgeDto, onFocused: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (badge.Unlocked) rarityColor(badge.Rarity) else JellioBorder
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = { onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (badge.Unlocked) color.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
            contentColor = if (badge.Unlocked) JellioText else JellioTextSecondary.copy(alpha = 0.7f),
            focusedContainerColor = if (badge.Unlocked) color.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.14f),
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, color.copy(alpha = if (badge.Unlocked) 0.55f else 0.25f)), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.padding(5.dp).onFocusChanged { if (it.hasFocus) onFocused() },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = if (badge.Unlocked) 0.28f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (badge.Unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                    contentDescription = com.jellio.android.data.i18n.badgeDisplayDescription(badge.Id, badge.Description),
                    tint = if (badge.Unlocked) color else JellioTextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = com.jellio.android.data.i18n.badgeDisplayName(badge.Id, badge.Name),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ActivityRow(
    entry: GroupedActivityEntryDto,
    imageUrl: (String, String?, String, Int) -> String,
    onClick: () -> Unit,
) {
    val horizontalPad = if (isCompactScreen()) 8.dp else 40.dp
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.18f),
            focusedContentColor = JellioText,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPad, vertical = 2.dp),
    ) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var showFallback by remember(entry.ItemId) { mutableStateOf(false) }
        val posterId = entry.SeriesId ?: entry.ItemId
        if (showFallback) {
            Box(
                modifier = Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)).background(JellioBgElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = JellioTextSecondary)
            }
        } else {
            AsyncImage(
                model = imageUrl(posterId, null, "Primary", 160),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { showFallback = true },
                modifier = Modifier.size(width = 48.dp, height = 72.dp).clip(RoundedCornerShape(6.dp)),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = watchActivityText(
                    itemType = entry.ItemType,
                    seriesName = entry.SeriesName,
                    episodeCount = entry.EpisodeCount,
                    seasonNumber = entry.SeasonNumber,
                    firstEpisodeNumber = entry.FirstEpisodeNumber,
                    lastEpisodeNumber = entry.LastEpisodeNumber,
                    itemName = entry.ItemName,
                ),
                color = JellioText,
            )
            Text(text = formatRelativeTime(entry.CompletedAtUtc), color = JellioTextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
    }
}
