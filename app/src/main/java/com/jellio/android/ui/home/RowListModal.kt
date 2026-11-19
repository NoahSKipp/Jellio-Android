package com.jellio.android.ui.home

import com.jellio.android.ui.common.dismissOnBack
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.ui.common.RegisterGridFocusItem
import com.jellio.android.ui.common.gridColumnFocus
import com.jellio.android.ui.common.isCompactScreen
import com.jellio.android.ui.common.rememberGridFocusController
import com.jellio.android.ui.common.rememberTvGridState
import com.jellio.android.ui.nav.LocalBottomBarPadding
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.scaled

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RowListModal(
    title: String,
    items: List<BaseItemDto>,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    onDismiss: () -> Unit,
    fetchAll: (suspend () -> List<BaseItemDto>)? = null,
    fetchPage: (suspend (Int, Int) -> List<BaseItemDto>)? = null,
) {
    BackHandler(onBack = onDismiss)
    val isCompact = isCompactScreen()
    val bottomBarPadding = if (isCompact) LocalBottomBarPadding.current else 0.dp
    var displayedItems by remember(items) { mutableStateOf(items) }
    var isLoadingRest by remember(items) { mutableStateOf(fetchAll != null || fetchPage != null) }
    // Paged rows load ROW_PAGE_SIZE titles at a time, the next page once the
    // focus nears the end, until the server runs out.
    var rawNextStart by remember(items) { mutableStateOf(items.size) }
    var consecutiveEmptyPages by remember(items) { mutableStateOf(0) }
    var exhausted by remember(items) { mutableStateOf(fetchPage == null) }
    var columns by remember { mutableStateOf(1) }
    var initialFocusRequested by remember(items) { mutableStateOf(false) }
    val listFocusRequester = remember { FocusRequester() }
    val gridState = rememberTvGridState()
    val focusController = rememberGridFocusController()
    suspend fun loadNextPage() {
        val page = fetchPage ?: return
        if (exhausted) return
        isLoadingRest = true
        while (!exhausted) {
            val start = rawNextStart
            val result = runCatching { page(start, ROW_PAGE_SIZE) }.getOrNull()
            if (result == null) {
                break
            }
            rawNextStart = start + ROW_PAGE_SIZE
            if (result.isEmpty()) {
                exhausted = true
                break
            }
            val existingIds = displayedItems.mapTo(hashSetOf()) { it.Id }
            val existingKeys = displayedItems.mapTo(hashSetOf()) { com.jellio.android.data.recommend.titleKey(it) }
            val fresh = result.filter { item ->
                existingIds.add(item.Id) && existingKeys.add(com.jellio.android.data.recommend.titleKey(item))
            }
            if (fresh.isNotEmpty()) {
                consecutiveEmptyPages = 0
                displayedItems = displayedItems + fresh
                break
            } else {
                consecutiveEmptyPages += 1
                if (consecutiveEmptyPages >= 2) {
                    exhausted = true
                    break
                }
            }
        }
        isLoadingRest = false
    }
    LaunchedEffect(items, fetchPage) {
        if (fetchPage == null) return@LaunchedEffect
        if (displayedItems.isEmpty()) {
            loadNextPage()
        }
        // Keep loading while the focus sits within two rows of the end.
        androidx.compose.runtime.snapshotFlow {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= displayedItems.size - columns * 2 && !exhausted && !isLoadingRest
        }.collect { wantMore -> if (wantMore) loadNextPage() }
    }
    LaunchedEffect(items, fetchAll) {
        if (fetchAll == null || fetchPage != null) return@LaunchedEffect
        val fullItems = runCatching { fetchAll() }.getOrNull()
        isLoadingRest = false
        if (fullItems != null) {
            val existingIds = items.mapTo(hashSetOf()) { it.Id }
            val merged = items + fullItems.filterNot { it.Id in existingIds }
            if (merged.size > items.size) {
                displayedItems = merged
            }
        }
    }
    LaunchedEffect(displayedItems.isNotEmpty(), isCompact) {
        if (!isCompact && !initialFocusRequested && displayedItems.isNotEmpty()) {
            initialFocusRequested = true
            runCatching { listFocusRequester.requestFocus() }
        }
    }
    // A page of its own: the row's title over a poster grid, like a
    // library page, the rest filling in when fetchAll lands.
    val firstCard = focusController.requesterFor(0)
    val horizontalPad = if (isCompact) 16.dp else 48.dp
    val topPad = if (isCompact) 12.dp else 32.dp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .background(JellioBg)
            .focusProperties { onExit = { FocusRequester.Cancel } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(start = horizontalPad, end = horizontalPad, top = topPad),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (isCompact) 14.dp else 20.dp),
            ) {
                if (isCompact) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = JellioText,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = JellioText,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(
                        text = when {
                            isLoadingRest -> com.jellio.android.data.i18n.Strings.format("row_titles_loading_more", displayedItems.size)
                            !exhausted -> com.jellio.android.data.i18n.Strings.format("row_titles_plus", displayedItems.size)
                            else -> com.jellio.android.data.i18n.Strings.format("row_titles_count", displayedItems.size)
                        },
                        color = JellioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val scaledPosterWidth = PosterWidth.scaled()
                val spacing = if (isCompact) 10.dp else 16.dp
                val columnCount = if (isCompact) {
                    3
                } else {
                    maxOf(1, ((maxWidth + spacing) / (scaledPosterWidth + spacing)).toInt())
                }
                columns = columnCount
                val shownItems = displayedItems
                val totalCount = shownItems.size
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 14.dp else 20.dp),
                    contentPadding = PaddingValues(
                        top = 4.dp,
                        bottom = if (isCompact) bottomBarPadding + 24.dp else 48.dp,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isCompact) {
                                Modifier
                            } else {
                                Modifier
                                    .focusRequester(listFocusRequester)
                                    .focusRestorer(firstCard)
                            },
                        ),
                ) {
                    items(totalCount, key = { "${shownItems[it].Id}-$it" }, contentType = { "poster_card" }) { index ->
                        val item = shownItems[index]
                        RegisterGridFocusItem(focusController, index)
                        PosterCard(
                            item = item,
                            imageUrl = imageUrl,
                            onClick = {
                                onDismiss()
                                onItemClick(item)
                            },
                            canExpand = false,
                            fillWidth = isCompact,
                            modifier = Modifier.gridColumnFocus(
                                controller = focusController,
                                index = index,
                                totalCount = totalCount,
                                columnCount = columnCount,
                                allowExitUp = false,
                                allowExitLeft = false,
                            ),
                        )
                    }
                    if (isLoadingRest) {
                        items(6, key = { "placeholder-$it" }, contentType = { "placeholder" }) {
                            SkeletonPoster()
                        }
                    }
                }
            }
        }
    }
}

internal const val ROW_PAGE_SIZE = 60
