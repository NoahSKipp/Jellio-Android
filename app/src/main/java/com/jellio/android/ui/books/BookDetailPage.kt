package com.jellio.android.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jellio.android.data.books.RESUME_FAB_SETTLE_MS
import com.jellio.android.data.books.ResumeKind
import com.jellio.android.data.books.topBarFraction
import com.jellio.android.data.i18n.Strings
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioText
import kotlinx.coroutines.delay

// What the page of a book or audiobook shows.
internal data class BookDetailModel(
    val title: String,
    val author: String?,
    val infoLine: String?,
    val coverUrl: String?,
    val description: String?,
    val genres: List<String>,
    val audiobook: Boolean,
    val resumeKind: ResumeKind,
    val downloaded: Boolean,
    val downloading: Boolean,
    // Null for an audiobook, which has no read flag here.
    val finished: Boolean?,
)

// The page for a book or audiobook, built from the same parts as the manga page.
@Composable
internal fun BookDetailPage(
    model: BookDetailModel,
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDownload: () -> Unit,
    onCategories: () -> Unit,
    onMarkRead: ((Boolean) -> Unit)?,
    onRemove: () -> Unit,
    reading: ReadingMenuActions?,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val listState = rememberLazyListState()
        val fadeDistancePx = with(LocalDensity.current) { 120.dp.roundToPx() }
        val barFraction by remember(listState, fadeDistancePx) {
            derivedStateOf {
                topBarFraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, fadeDistancePx)
            }
        }
        val scrolling = listState.isScrollInProgress
        var extended by remember { mutableStateOf(true) }
        LaunchedEffect(scrolling) {
            if (scrolling) {
                extended = false
            } else {
                delay(RESUME_FAB_SETTLE_MS)
                extended = true
            }
        }
        var showOverflow by remember { mutableStateOf(false) }

        val lines = buildList {
            if (!model.author.isNullOrBlank()) add(DetailHeaderLine(Icons.Filled.Person, model.author))
            if (!model.infoLine.isNullOrBlank()) {
                add(DetailHeaderLine(if (model.audiobook) Icons.Filled.Headphones else Icons.Filled.MenuBook, model.infoLine))
            }
        }
        val actions = buildList {
            if (reading != null) {
                add(
                    DetailAction(
                        icon = if (reading.onList) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        label = Strings.get("reading_list"),
                        active = reading.onList,
                        onClick = reading.onToggle,
                    ),
                )
                reading.onFriend?.let { add(DetailAction(Icons.Filled.PersonAdd, Strings.get("series_friend"), false, it)) }
            }
            add(DetailAction(Icons.Filled.Category, Strings.get("reader_menu_categories"), false, onCategories))
            add(
                DetailAction(
                    icon = if (model.downloaded) Icons.Filled.CheckCircle else Icons.Filled.Download,
                    label = Strings.get(if (model.downloaded) "series_downloaded" else "menu_download"),
                    active = model.downloaded || model.downloading,
                    onClick = { if (!model.downloaded && !model.downloading) onDownload() },
                ),
            )
        }
        val resumeLabel = when (model.resumeKind) {
            ResumeKind.START -> Strings.get("series_start")
            ResumeKind.READ_AGAIN -> Strings.get("series_read_again")
            ResumeKind.RESUME -> Strings.get("player_resume")
        }

        Box(modifier = Modifier.fillMaxSize().background(JellioBg)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                item(key = "header") {
                    DetailHeader(coverUrl = model.coverUrl, title = model.title, lines = lines)
                }
                item(key = "actions") {
                    DetailActionRow(actions = actions, modifier = Modifier.padding(top = 4.dp))
                }
                if (!model.description.isNullOrBlank()) {
                    item(key = "description") { DetailDescription(text = model.description) }
                }
                if (model.genres.isNotEmpty()) {
                    item(key = "genres") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            items(model.genres) { genre -> DetailChip(genre) }
                        }
                    }
                }
            }

            DetailTopBar(
                title = model.title,
                fraction = barFraction,
                backLabel = Strings.get("action_back"),
                onBack = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Box {
                    IconButton(onClick = { showOverflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = Strings.get("series_more"), tint = JellioText)
                    }
                    DropdownMenu(
                        expanded = showOverflow,
                        onDismissRequest = { showOverflow = false },
                        modifier = Modifier.background(JellioBgElevated),
                    ) {
                        if (onMarkRead != null && model.finished != null) {
                            val finished = model.finished
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        Strings.get(if (finished) "reader_menu_mark_unread" else "reader_menu_mark_read"),
                                        color = JellioText,
                                        fontSize = 14.sp,
                                    )
                                },
                                onClick = {
                                    showOverflow = false
                                    onMarkRead(!finished)
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(Strings.get("reader_menu_remove_library"), color = JellioDanger, fontSize = 14.sp) },
                            onClick = {
                                showOverflow = false
                                onRemove()
                            },
                        )
                    }
                }
            }

            DetailResumeButton(
                label = resumeLabel,
                extended = extended,
                onClick = onResume,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp),
            )
        }
    }
}
