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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    val home = items.firstOrNull { it is JellioRoute.Home } ?: JellioRoute.Home()
    val search = items.firstOrNull { it is JellioRoute.Search } ?: JellioRoute.Search
    val bookshelf = items.firstOrNull { it is JellioRoute.Bookshelf } ?: JellioRoute.Bookshelf("ebook")
    val downloads = items.firstOrNull { it is JellioRoute.Downloads } ?: JellioRoute.Downloads
    val profile = items.firstOrNull { it is JellioRoute.Profile && it.userId == null } ?: JellioRoute.Profile()

    val mediaLibraries = remember(items) {
        buildList {
            items.filter { it is JellioRoute.LibraryTab || it is JellioRoute.LiveTv }.forEach { add(it) }
            items.firstOrNull { it is JellioRoute.Feed }?.let { add(it) }
        }.distinct()
    }

    var librariesMenuOpen by remember { mutableStateOf(false) }
    val isLibraryOrMoreSelected = selected is JellioRoute.LibraryTab ||
        selected is JellioRoute.LiveTv ||
        selected is JellioRoute.Feed

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Surface(
            color = Color.Transparent,
            shape = FloatingPillShape,
            shadowElevation = 18.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .height(62.dp)
                .clip(FloatingPillShape)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            JellioBgElevated.copy(alpha = 0.95f),
                            Color(0xFF0E0E10).copy(alpha = 0.97f),
                        ),
                    ),
                )
                .border(1.dp, JellioBorder.copy(alpha = 0.85f), FloatingPillShape),
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
                    onClick = { onSelect(home) },
                    modifier = Modifier.weight(1f),
                )
                BottomNavPillTab(
                    route = search,
                    isSelected = selected is JellioRoute.Search,
                    enabled = enabled,
                    onClick = { onSelect(search) },
                    modifier = Modifier.weight(1f),
                )
                BottomNavPillTab(
                    route = bookshelf,
                    labelOverride = "Reader",
                    isSelected = selected is JellioRoute.Bookshelf || selected is JellioRoute.Vocab,
                    enabled = enabled,
                    onClick = { onSelect(bookshelf) },
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
                            .clickable(enabled = enabled) { librariesMenuOpen = true }
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
                    DropdownMenu(
                        expanded = librariesMenuOpen,
                        onDismissRequest = { librariesMenuOpen = false },
                        modifier = Modifier
                            .background(JellioBgElevated)
                            .border(1.dp, JellioBorder, RoundedCornerShape(14.dp)),
                    ) {
                        mediaLibraries.forEach { libRoute ->
                            val active = libRoute == selected
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = libRoute.label(),
                                        color = if (active) JellioSecondary else JellioText,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = libRoute.icon(),
                                        contentDescription = null,
                                        tint = if (active) JellioSecondary else JellioTextSecondary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                onClick = {
                                    librariesMenuOpen = false
                                    onSelect(libRoute)
                                },
                            )
                        }
                    }
                }
                BottomNavPillTab(
                    route = downloads,
                    labelOverride = "Offline",
                    isSelected = selected is JellioRoute.Downloads,
                    enabled = enabled,
                    onClick = { onSelect(downloads) },
                    modifier = Modifier.weight(1f),
                )
                BottomNavPillTab(
                    route = profile,
                    labelOverride = profileName,
                    avatarUrl = profileAvatarUrl,
                    badge = profileBadge,
                    isSelected = selected is JellioRoute.Profile && selected.userId == null,
                    enabled = enabled,
                    onClick = { onSelect(profile) },
                    modifier = Modifier.weight(1f),
                )
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
