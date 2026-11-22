package com.jellio.android.ui.changelog

import com.jellio.android.ui.common.dismissOnBack
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioRarityEpic
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import kotlinx.coroutines.delay

data class ChangelogFeature(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

data class ChangelogRelease(
    val version: String,
    val title: String,
    val subtitle: String,
    val features: List<ChangelogFeature>,
)

// Helper to determine whether a release version is a big release (e.g. 1.4X.0, 0.42.0)
// as opposed to a small bugfix patch release (e.g. 1.4X.1, 0.42.1).
fun isBigRelease(version: String): Boolean {
    val clean = version.trim().removePrefix("v").removePrefix("V")
    val parts = clean.split(".")
    if (parts.size >= 3) {
        val patch = parts[2].toIntOrNull() ?: return false
        val build = if (parts.size > 3) parts[3].toIntOrNull() ?: 0 else 0
        return patch == 0 && build == 0
    }
    return parts.size == 2
}

val CHANGELOG_RELEASES = listOf(
    ChangelogRelease(
        version = "0.3.6",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.6",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "Removed Books Stay Removed", "A book you remove from your library that other readers still have no longer comes back on your Books and Audiobooks shelves."),
        ),
    ),
    ChangelogRelease(
        version = "0.3.5",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.5",
        features = listOf(
            ChangelogFeature(Icons.Filled.Tune, "Clearer Book Removal", "Removing a book from the library now tells you when only the library entry could be removed because the server could not delete its files."),
        ),
    ),
    ChangelogRelease(
        version = "0.3.4",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.4",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Reading List", "Add books, audiobooks and manga to a reading list from their pages, card menus and Discover. Reading and Listening rows sit under Continue on the Reader tab, and you can add titles to a friend's list too. Needs the Jellio plugin 1.113.0 on your server."),
        ),
    ),
    ChangelogRelease(
        version = "0.3.3",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.3",
        features = listOf(
            ChangelogFeature(Icons.Filled.Person, "Actor Pages Made for Phones", "A compact header with a readable name, a biography you can expand, and the filmography as a poster grid instead of one long list. The Back button now clears the status bar."),
        ),
    ),
    ChangelogRelease(
        version = "0.3.2",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.2",
        features = listOf(
            ChangelogFeature(Icons.Filled.Sync, "Updates Install Themselves", "After an update is downloaded and checked, the installer now opens right away. If Jellio is in the background you get a notification to finish the install, and an Install button stays in the update prompt."),
        ),
    ),
    ChangelogRelease(
        version = "0.3.1",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.1",
        features = listOf(
            ChangelogFeature(Icons.Filled.Notifications, "Phone Notifications", "Jellio now notifies you about new episodes and releases for your watchlist and shows you are watching, Group Watch invites, friend activity, announcements and book and manga updates. Each category can be switched off in Settings under Notifications."),
            ChangelogFeature(Icons.Filled.Tune, "Manga Removals Stay Removed", "Removing a manga series from Continue reading now stays removed on this device even when the server is older."),
        ),
    ),
    ChangelogRelease(
        version = "0.3.0",
        title = "What's New in Jellio",
        subtitle = "Version 0.3.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.Speed, "Faster Books, Manga & Audiobooks", "Shelves show a skeleton at once and fill in as results arrive, reopen instantly from the last shelf, and start on your Continue row. Manga chapters open without downloading first and prefetch the next pages."),
            ChangelogFeature(Icons.Filled.Tune, "Long-Press Menus Everywhere", "Cards now offer Share link, Download, Grouplist and admin skip scans like the web, and library and continue cards in the reader get Remove from continue reading, Mark as read and Remove from library. Removals from Continue reading follow you across devices."),
            ChangelogFeature(Icons.Filled.AutoAwesome, "Genre Picker & Chaptarr Fix", "Books and Audiobooks Discover get the Manga genre picker, and the Chaptarr request button no longer says it is not configured before the server answers."),
            ChangelogFeature(Icons.Filled.GraphicEq, "Back and Camera Cutout Fixes", "Back from Settings and the other tabs returns to Home first, and the reader bars, notifications and player overlays stay clear of the camera and status bar."),
        ),
    ),
    ChangelogRelease(
        version = "0.2.0",
        title = "What's New in Jellio",
        subtitle = "Version 0.2.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Featured Hero Carousel & Landscape Continue Watching", "Swipeable full-bleed featured carousel on Home with auto-advance and quick Play/Details actions, plus 16:9 landscape Continue Watching cards with remaining time badges and inset progress bars."),
            ChangelogFeature(Icons.Filled.GraphicEq, "Android Media Drawer for Audiobooks, Video & Live TV", "Active audiobooks, movies, shows, and Live TV now appear in Android's Quick Settings media drawer, lock screen, and connected wearables with artwork, scrub bar, and playback controls."),
            ChangelogFeature(Icons.Filled.Tune, "Streamlined Navigation, Detail & Search Overhaul", "5-tab floating pill with Browse centered and Offline moved inside Browse, edge-to-edge scrolling behind the pill, vertical episode lists and expandable synopses on Detail, a polished Stream Picker sheet, and a unified Search pill with recent searches."),
            ChangelogFeature(Icons.Filled.Speed, "2x Long-Press Speed & Smooth 60fps Performance", "Hold anywhere in the video player for 2x speed, cutout-safe player controls, swipe-to-dismiss audiobook mini-player, isolated 400ms position tickers, smooth Coil crossfades, and animated route transitions."),
        ),
    ),
    ChangelogRelease(
        version = "1.0.0",
        title = "What's New in Jellio",
        subtitle = "Version 1.0.0",
        features = listOf(
            ChangelogFeature(Icons.Filled.AutoAwesome, "Jellio for Android Phones & Tablets", "Adaptive touch interface with a floating glassmorphic pill bottom bar on phones, sidebar navigation on tablets and landscape screens, Studio Hubs, Top Picks, seasonal effects, and Picture-in-Picture."),
            ChangelogFeature(Icons.Filled.PlayCircle, "Touch Video Player, Stream Warmup & Watch Party", "Swipe gestures for brightness and volume, double-tap seek, pinch-to-zoom, touch lock, rotation toggle, Ready stream warmup, automatic failover, Skip Intro/Credits, Live TV, and Watch Party sync."),
            ChangelogFeature(Icons.Filled.DynamicFeed, "Books, Manga, Audiobooks & Language Study", "Reflowable EPUB reader with inline images, highlights, notes, and TextToSpeech read-aloud; native PDF reader; CBZ and Suwayomi manga reader; Wiktionary/DeepL flashcards; and an M4B/multi-track audiobook player with background lock-screen controls."),
            ChangelogFeature(Icons.Filled.Sync, "Offline Downloads, Friends & Ani-Sync", "Download movies, episodes, books, manga chapters, and audiobooks for offline use with automatic progress sync, plus shared friend watchlists and two-way Ani-Sync for AniList, MyAnimeList, Shikimori, and Simkl."),
        ),
    ),
)

val CURRENT_CHANGELOG = CHANGELOG_RELEASES.first()
val LATEST_BIG_CHANGELOG = CHANGELOG_RELEASES.firstOrNull { isBigRelease(it.version) } ?: CURRENT_CHANGELOG

object ChangelogPrefs {
    private const val PREFS = "jellio_changelog"
    private const val KEY_LAST_SEEN = "last_seen_version"

    fun shouldShowChangelog(context: Context): Boolean {
        // What's new popup dialog is ONLY triggered by big releases (1.4X.0, where patch is 0)
        val targetRelease = LATEST_BIG_CHANGELOG
        if (!isBigRelease(targetRelease.version)) {
            return false
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastSeen = prefs.getString(KEY_LAST_SEEN, null)
        // If an older release was stored, clear it so the new big release is displayed
        if (lastSeen != null && lastSeen != targetRelease.version) {
            prefs.edit().remove(KEY_LAST_SEEN).apply()
            return true
        }
        return lastSeen == null
    }

    fun markSeen(context: Context, version: String = LATEST_BIG_CHANGELOG.version) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_SEEN, version).apply()
    }
}

// Centered modal popup shown on initial open following a major update,
// or accessed on demand from Settings -> About.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChangelogPopup(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    release: ChangelogRelease = CURRENT_CHANGELOG,
    allReleases: List<ChangelogRelease> = CHANGELOG_RELEASES,
) {
    val buttonFocusRequester = remember { FocusRequester() }
    val firstFeatureFocusRequester = remember { FocusRequester() }
    var showPreviousVersions by remember { mutableStateOf(false) }
    var expandedPreviousVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        repeat(10) {
            delay(50)
            if (runCatching { buttonFocusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    // Its own window: the screen behind claims focus while it loads, which
    // used to pull focus off Continue and behind this popup.
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .focusProperties { onExit = { FocusRequester.Cancel } }
            .background(Color.Black.copy(alpha = 0.8f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(JellioBgElevated)
                .border(1.dp, JellioBorder, RoundedCornerShape(20.dp))
                .padding(28.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(JellioRarityEpic.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = JellioRarityEpic,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = release.title,
                        color = JellioText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                    )
                    Text(
                        text = release.subtitle,
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Scrollable feature highlights
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .focusRestorer { firstFeatureFocusRequester }
                    .padding(end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                release.features.forEachIndexed { index, feature ->
                    val isFirst = index == 0
                    Surface(
                        onClick = {},
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.14f),
                            focusedContentColor = JellioText,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isFirst) Modifier.focusRequester(firstFeatureFocusRequester) else Modifier)
                            .then(if (isFirst) Modifier.focusProperties { up = FocusRequester.Cancel } else Modifier),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = feature.icon,
                                    contentDescription = null,
                                    tint = JellioText,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = feature.title,
                                    color = JellioText,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = feature.description,
                                    color = JellioTextSecondary,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                )
                            }
                        }
                    }
                }

                val previousReleases = allReleases.filter { it.version != release.version }
                if (previousReleases.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        onClick = { showPreviousVersions = !showPreviousVersions },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Color.White.copy(alpha = 0.05f),
                            contentColor = JellioTextSecondary,
                            focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            focusedContentColor = JellioText,
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(12.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = com.jellio.android.data.i18n.Strings.format("changelog_previous_versions", previousReleases.size),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            )
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp).rotate(if (showPreviousVersions) 90f else 0f),
                            )
                        }
                    }

                    if (showPreviousVersions) {
                        previousReleases.forEach { prev ->
                            val isExpanded = expandedPreviousVersion == prev.version
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
                            ) {
                                Surface(
                                    onClick = {
                                        expandedPreviousVersion = if (isExpanded) null else prev.version
                                    },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                                    colors = ClickableSurfaceDefaults.colors(
                                        containerColor = Color.Transparent,
                                        contentColor = JellioText,
                                        focusedContainerColor = Color.White.copy(alpha = 0.12f),
                                        focusedContentColor = JellioText,
                                    ),
                                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                                    modifier = Modifier.fillMaxWidth().height(40.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = prev.subtitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.ChevronRight,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp).rotate(if (isExpanded) 90f else 0f),
                                        )
                                    }
                                }

                                if (isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        prev.features.forEach { prevFeature ->
                                            Surface(
                                                onClick = {},
                                                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                                                colors = ClickableSurfaceDefaults.colors(
                                                    containerColor = Color.Transparent,
                                                    contentColor = JellioText,
                                                    focusedContainerColor = Color.White.copy(alpha = 0.10f),
                                                    focusedContentColor = JellioText,
                                                ),
                                                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
                                                border = ClickableSurfaceDefaults.border(
                                                    border = Border.None,
                                                    focusedBorder = Border(
                                                        border = BorderStroke(1.5.dp, Color.White),
                                                        shape = RoundedCornerShape(8.dp),
                                                    ),
                                                ),
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.Top,
                                                ) {
                                                    Icon(
                                                        imageVector = prevFeature.icon,
                                                        contentDescription = null,
                                                        tint = JellioTextSecondary,
                                                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = prevFeature.title,
                                                            color = JellioText,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                        )
                                                        Text(
                                                            text = prevFeature.description,
                                                            color = JellioTextSecondary,
                                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dismiss Button
            Surface(
                onClick = onDismiss,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = JellioRarityEpic,
                    contentColor = Color.White,
                    focusedContainerColor = Color.White,
                    focusedContentColor = Color.Black,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .focusRequester(buttonFocusRequester)
                    .focusProperties {
                        up = firstFeatureFocusRequester
                    },
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = com.jellio.android.data.i18n.Strings.get("changelog_continue"),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
    }
}
