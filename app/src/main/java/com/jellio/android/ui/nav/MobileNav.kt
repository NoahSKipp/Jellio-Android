package com.jellio.android.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary

val MobileBottomNavHeight = 82.dp
val LocalBottomBarPadding = compositionLocalOf { 0.dp }
private val FloatingPillShape = RoundedCornerShape(999.dp)

/**
 * Floating glassmorphic pill bottom navigation bar for Android phones.
 * Displays Home, Search, Books/Manga/Audiobooks, Media Libraries, Downloads/More, and Profile.
 */
@Composable
fun MobileBottomNav(
    items: List<JellioRoute>,
    selected: JellioRoute,
    onSelect: (JellioRoute) -> Unit,
    enabled: Boolean = true,
    profileAvatarUrl: String? = null,
    profileName: String? = null,
    profileBadge: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val home = items.firstOrNull { it is JellioRoute.Home } ?: JellioRoute.Home()
    val search = items.firstOrNull { it is JellioRoute.Search } ?: JellioRoute.Search
    val bookshelf = items.firstOrNull { it is JellioRoute.Bookshelf } ?: JellioRoute.Bookshelf("ebook")
    val downloads = items.firstOrNull { it is JellioRoute.Downloads } ?: JellioRoute.Downloads
    val profile = items.firstOrNull { it is JellioRoute.Profile && it.userId == null } ?: JellioRoute.Profile()

    val mediaLibraries = remember(items) {
        buildList {
            items.filter { it is JellioRoute.LibraryTab || it is JellioRoute.LiveTv }.forEach { add(it) }
            items.firstOrNull { it is JellioRoute.Feed }?.let { add(it) }
            add(downloads)
        }.distinct()
    }

    var librariesMenuOpen by remember { mutableStateOf(false) }
    val isLibraryOrMoreSelected = selected is JellioRoute.LibraryTab ||
        selected is JellioRoute.LiveTv ||
        selected is JellioRoute.Feed ||
        selected is JellioRoute.Downloads

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Surface(
            color = Color(0xF2121216),
            shape = FloatingPillShape,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(62.dp)
                .border(
                    width = 0.75.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.16f),
                            JellioBorder.copy(alpha = 0.55f),
                        ),
                    ),
                    shape = FloatingPillShape,
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                BottomNavPillTab(
                    route = home,
                    isSelected = selected is JellioRoute.Home,
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(home)
                    },
                    modifier = Modifier.weight(1f),
                )
                BottomNavPillTab(
                    route = search,
                    isSelected = selected is JellioRoute.Search,
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(search)
                    },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val activeLibLabel = when (selected) {
                        is JellioRoute.LibraryTab -> selected.label()
                        is JellioRoute.LiveTv -> selected.label()
                        is JellioRoute.Feed -> selected.label()
                        is JellioRoute.Downloads -> "Offline"
                        else -> "Browse"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FloatingPillShape)
                            .then(
                                if (isLibraryOrMoreSelected) {
                                    Modifier.background(Color.White.copy(alpha = 0.10f))
                                } else {
                                    Modifier
                                },
                            )
                            .clickable(enabled = enabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                librariesMenuOpen = true
                            }
                            .padding(vertical = 5.dp, horizontal = 2.dp),
                    ) {
                        Icon(
                            imageVector = if (isLibraryOrMoreSelected) selected.icon() else Icons.Filled.Apps,
                            contentDescription = "Browse libraries",
                            tint = if (isLibraryOrMoreSelected) JellioSecondary else JellioTextSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = activeLibLabel,
                            color = if (isLibraryOrMoreSelected) JellioSecondary else JellioTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isLibraryOrMoreSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                BottomNavPillTab(
                    route = bookshelf,
                    labelOverride = "Reader",
                    isSelected = selected is JellioRoute.Bookshelf || selected is JellioRoute.Vocab,
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(bookshelf)
                    },
                    modifier = Modifier.weight(1f),
                )
                BottomNavPillTab(
                    route = profile,
                    labelOverride = profileName,
                    avatarUrl = profileAvatarUrl,
                    badge = profileBadge,
                    isSelected = selected is JellioRoute.Profile && selected.userId == null,
                    enabled = enabled,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(profile)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (librariesMenuOpen) {
        BrowseLibrariesSheet(
            libraries = mediaLibraries,
            selected = selected,
            onSelect = { libRoute ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                librariesMenuOpen = false
                onSelect(libRoute)
            },
            onDismiss = { librariesMenuOpen = false },
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun BrowseLibrariesSheet(
    libraries: List<JellioRoute>,
    selected: JellioRoute,
    onSelect: (JellioRoute) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        contentColor = JellioText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Browse Libraries",
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            val rows = remember(libraries) { libraries.chunked(2) }
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { libRoute ->
                        val active = libRoute == selected
                        val label = if (libRoute is JellioRoute.Downloads) "Offline" else libRoute.label()
                        Surface(
                            onClick = { onSelect(libRoute) },
                            color = if (active) Color.White.copy(alpha = 0.14f) else JellioBg,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    width = 1.dp,
                                    color = if (active) JellioSecondary else JellioBorder,
                                    shape = RoundedCornerShape(14.dp),
                                ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            ) {
                                Icon(
                                    imageVector = libRoute.icon(),
                                    contentDescription = null,
                                    tint = if (active) JellioSecondary else JellioTextSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = label,
                                    color = if (active) JellioSecondary else JellioText,
                                    fontSize = 14.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNavPillTab(
    route: JellioRoute,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
    avatarUrl: String? = null,
    badge: Boolean = false,
) {
    val label = if (!labelOverride.isNullOrBlank()) labelOverride else route.label()
    val tint = if (isSelected) JellioSecondary else JellioTextSecondary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(FloatingPillShape)
            .then(
                if (isSelected) {
                    Modifier.background(Color.White.copy(alpha = 0.10f))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, JellioSecondary, CircleShape) else Modifier
                        ),
                )
            } else {
                Icon(
                    imageVector = route.icon(),
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(21.dp * route.iconScale().coerceAtMost(1.1f)),
                )
            }
            if (badge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                        .size(8.dp)
                        .background(JellioDanger, CircleShape)
                        .border(1.dp, JellioBg, CircleShape),
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
