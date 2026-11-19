package com.jellio.android.ui.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.session.Session
import com.jellio.android.ui.common.RegisterGridFocusItem
import com.jellio.android.ui.common.gridColumnFocus
import com.jellio.android.ui.common.rememberGridFocusController
import com.jellio.android.ui.common.rememberTvGridState
import com.jellio.android.ui.home.PosterCard
import com.jellio.android.ui.home.PosterWidth
import com.jellio.android.ui.home.SkeletonPoster
import com.jellio.android.ui.home.rememberCardOptionsHost
import com.jellio.android.ui.nav.LocalBottomBarPadding
import com.jellio.android.data.i18n.Strings
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.scaled
import org.json.JSONArray

private const val SEARCH_HISTORY_PREFS = "jellio_search_history"
private const val SEARCH_HISTORY_KEY = "recent_queries"
private const val MAX_RECENT_SEARCHES = 10

private val QUICK_BROWSE_CHIPS = listOf(
    "Trending",
    "Action",
    "Sci-Fi",
    "Anime",
    "Comedy",
    "Thriller",
    "Fantasy",
)

private fun loadSearchHistory(context: Context): List<String> {
    val raw = context.getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE)
        .getString(SEARCH_HISTORY_KEY, null)
        ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        buildList(arr.length()) {
            for (i in 0 until arr.length()) {
                val entry = arr.optString(i).trim()
                if (entry.isNotEmpty()) add(entry)
            }
        }
    }.getOrDefault(emptyList())
}

private fun saveSearchHistory(context: Context, entries: List<String>) {
    val arr = JSONArray()
    entries.take(MAX_RECENT_SEARCHES).forEach { arr.put(it) }
    context.getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(SEARCH_HISTORY_KEY, arr.toString())
        .apply()
}

@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    session: Session,
    imageUrl: (BaseItemDto, String, Int) -> String,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberTvGridState()
    val focusController = rememberGridFocusController()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val bottomBarPadding = LocalBottomBarPadding.current

    var recentSearches by remember(context) { mutableStateOf(loadSearchHistory(context)) }
    fun recordSearchTerm(raw: String) {
        val term = raw.trim()
        if (term.isEmpty()) return
        val updated = (listOf(term) + recentSearches.filterNot { it.equals(term, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
        recentSearches = updated
        saveSearchHistory(context, updated)
    }
    fun removeSearchTerm(term: String) {
        val updated = recentSearches.filterNot { it == term }
        recentSearches = updated
        saveSearchHistory(context, updated)
    }
    fun clearSearchHistory() {
        recentSearches = emptyList()
        saveSearchHistory(context, emptyList())
    }

    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, Strings.get("search_voice_prompt"))
        }
    }

    val isAmazonDevice = remember {
        android.os.Build.MANUFACTURER.equals("Amazon", ignoreCase = true) ||
            android.os.Build.BRAND.equals("Amazon", ignoreCase = true)
    }

    val isVoiceSupported = remember(context, isAmazonDevice) {
        if (isAmazonDevice) {
            false
        } else {
            runCatching {
                android.speech.SpeechRecognizer.isRecognitionAvailable(context) &&
                    context.packageManager.queryIntentActivities(voiceIntent, 0).isNotEmpty()
            }.getOrDefault(false)
        }
    }

    var isEditingQuery by remember { mutableStateOf(false) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                recordSearchTerm(spoken)
                viewModel.onQueryChange(session, spoken)
            }
        }
    }

    val openItemOptions = rememberCardOptionsHost(
        onToggleWatchlist = { viewModel.toggleWatchlist(session, it) },
        onToggleWatched = { viewModel.toggleWatched(session, it) },
    )

    val isCompact = com.jellio.android.ui.common.isCompactScreen()
    val horizontalPad = if (isCompact) 16.dp else 52.dp
    val topPad = if (isCompact) 16.dp else 32.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = topPad, start = horizontalPad, end = horizontalPad),
    ) {
        Text(text = com.jellio.android.ui.i18n.string("nav_search"), style = MaterialTheme.typography.titleLarge)

        if (isCompact) {
            MobileSearchPill(
                value = uiState.query,
                onValueChange = { viewModel.onQueryChange(session, it) },
                onSubmit = {
                    recordSearchTerm(uiState.query)
                    focusManager.clearFocus()
                },
                onClear = { viewModel.onQueryChange(session, "") },
                showMic = isVoiceSupported,
                onMicClick = {
                    try {
                        speechLauncher.launch(voiceIntent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            context,
                            Strings.get("search_voice_not_supported"),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                placeholder = com.jellio.android.ui.i18n.string("search_placeholder"),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp),
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 20.dp),
            ) {
                // 1. Microphone Voice Search button positioned first (accessible before text field)
                Surface(
                    onClick = {
                        if (isVoiceSupported) {
                            try {
                                speechLauncher.launch(voiceIntent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    Strings.get("search_voice_not_supported"),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        } else {
                            // On Firestick / devices without Google Speech Services:
                            // Open the search field keyboard and guide the user to hold the remote mic button
                            isEditingQuery = true
                            Toast.makeText(
                                context,
                                Strings.get("search_voice_firestick_hint"),
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = JellioBgElevated,
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = JellioText,
                    ),
                    border = ClickableSurfaceDefaults.border(
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = com.jellio.android.ui.i18n.string("search_voice_btn"),
                            tint = if (isVoiceSupported) JellioText else JellioTextSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                // 2. TV-friendly Search Field that activates keyboard only on click/select
                TvSearchField(
                    value = uiState.query,
                    onValueChange = { viewModel.onQueryChange(session, it) },
                    label = com.jellio.android.ui.i18n.string("search_placeholder"),
                    isEditing = isEditingQuery,
                    onEditingChange = { isEditingQuery = it },
                    modifier = Modifier.width(480.dp),
                )

                // 3. One-click Clear button when query is populated
                if (uiState.query.isNotEmpty()) {
                    Surface(
                        onClick = { viewModel.onQueryChange(session, "") },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioBgElevated,
                            focusedContainerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = JellioTextSecondary,
                            focusedContentColor = JellioText,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(12.dp),
                            ),
                        ),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = com.jellio.android.ui.i18n.string("search_clear"),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }

        if (!uiState.correctedQuery.isNullOrBlank()) {
            Text(
                text = "Showing results for \"${uiState.correctedQuery}\"",
                color = JellioTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        when {
            uiState.results.isNotEmpty() || uiState.isSearching || uiState.similarResults.isNotEmpty() -> {
                val placeholders = when {
                    !uiState.isSearching -> 0
                    uiState.results.isEmpty() -> 12
                    else -> 4
                }
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isCompactPortrait = com.jellio.android.ui.common.rememberWindowLayoutMode() == com.jellio.android.ui.common.WindowLayoutMode.COMPACT_PORTRAIT
                    val scaledPosterWidth = PosterWidth.scaled()
                    val spacing = if (isCompactPortrait) 10.dp else 16.dp
                    val columnCount = if (isCompactPortrait) 3 else maxOf(1, ((maxWidth + spacing) / (scaledPosterWidth + spacing)).toInt())
                    val results = remember(uiState.results) { uiState.results.distinctBy { it.Id } }
                    val similar = remember(uiState.similarResults) { uiState.similarResults.distinctBy { it.Id } }
                    val primaryCount = results.size
                    val similarCount = similar.size
                    val totalFocusCount = primaryCount + similarCount

                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(columnCount),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactPortrait) 12.dp else 16.dp),
                        contentPadding = PaddingValues(bottom = 48.dp + if (isCompact) bottomBarPadding else 0.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!isCompact) Modifier.focusRestorer(focusController.requesterFor(0)) else Modifier),
                    ) {
                        items(primaryCount, key = { results[it].Id }, contentType = { "poster_card" }) { index ->
                            val item = results[index]
                            RegisterGridFocusItem(focusController, index)
                            PosterCard(
                                item = item,
                                imageUrl = imageUrl,
                                onClick = {
                                    if (isCompact) recordSearchTerm(uiState.query)
                                    onItemClick(item)
                                },
                                onOptionsClick = { openItemOptions(item) },
                                canExpand = false,
                                fillWidth = isCompactPortrait,
                                modifier = Modifier
                                    .gridColumnFocus(
                                        controller = focusController,
                                        index = index,
                                        totalCount = totalFocusCount,
                                        columnCount = columnCount,
                                        allowExitUp = true,
                                        allowExitLeft = true,
                                    )
                                    .onFocusChanged { if (it.isFocused) viewModel.onResultFocused(item.Id) },
                            )
                        }
                        items(placeholders, key = { "placeholder-$it" }, contentType = { "placeholder" }) {
                            SkeletonPoster()
                        }
                        if (similarCount > 0) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "similar_header") {
                                Text(
                                    text = uiState.suggestions?.MatchedTitle?.let {
                                        com.jellio.android.ui.i18n.formatString("search_suggest_similar", it)
                                    } ?: com.jellio.android.ui.i18n.string("search_suggest_similar_generic"),
                                    color = JellioText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                                )
                            }
                            items(similarCount, key = { "similar_${similar[it].Id}" }, contentType = { "poster_card" }) { sIndex ->
                                val item = similar[sIndex]
                                val focusIdx = primaryCount + sIndex
                                RegisterGridFocusItem(focusController, focusIdx)
                                PosterCard(
                                    item = item,
                                    imageUrl = imageUrl,
                                    onClick = {
                                        if (isCompact) recordSearchTerm(uiState.query)
                                        onItemClick(item)
                                    },
                                    onOptionsClick = { openItemOptions(item) },
                                    canExpand = false,
                                    fillWidth = isCompactPortrait,
                                    modifier = Modifier
                                        .gridColumnFocus(
                                            controller = focusController,
                                            index = focusIdx,
                                            totalCount = totalFocusCount,
                                            columnCount = columnCount,
                                            allowExitUp = true,
                                            allowExitLeft = true,
                                        )
                                        .onFocusChanged { if (it.isFocused) viewModel.onResultFocused(item.Id) },
                                )
                            }
                        }
                    }
                }
            }
            uiState.error != null -> com.jellio.android.ui.common.ErrorState(
                message = com.jellio.android.ui.i18n.string(
                    if (uiState.error == com.jellio.android.ui.common.LoadFailure.Unreachable) "search_network_error" else "search_load_error",
                ),
                onRetry = { viewModel.retry(session) },
                // The search field keeps focus while typing.
                takeFocus = false,
                modifier = Modifier.fillMaxSize(),
            )
            uiState.hasSearched && uiState.suggestions != null -> {
                val answer = uiState.suggestions!!
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 48.dp + if (isCompact) bottomBarPadding else 0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(key = "intro", contentType = "intro") {
                        Text(
                            text = com.jellio.android.ui.i18n.formatString("search_suggest_intro", answer.MatchedTitle ?: uiState.query),
                            color = JellioTextSecondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                        )
                    }
                    items(answer.Rows.size, key = { "suggest:$it" }, contentType = { "suggest_row" }) { index ->
                        val row = answer.Rows[index]
                        val title = when (row.Kind) {
                            "similar" -> com.jellio.android.ui.i18n.formatString("search_suggest_similar", answer.MatchedTitle ?: uiState.query)
                            "person" -> com.jellio.android.ui.i18n.formatString("search_suggest_person", row.Name ?: "")
                            else -> com.jellio.android.ui.i18n.formatString("search_suggest_genre", com.jellio.android.data.i18n.genreDisplayName(row.Name ?: ""))
                        }
                        com.jellio.android.ui.home.PosterRow(
                            section = com.jellio.android.data.model.HomeSection(title = title, items = row.Items),
                            imageUrl = imageUrl,
                            onItemClick = onItemClick,
                        )
                    }
                }
            }
            uiState.hasSearched -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = com.jellio.android.ui.i18n.formatString("search_no_results_for", uiState.query), color = JellioTextSecondary)
            }
            isCompact && uiState.query.isBlank() -> {
                MobileSearchIdleState(
                    recentSearches = recentSearches,
                    bottomBarPadding = bottomBarPadding,
                    onSelectQuery = { term ->
                        recordSearchTerm(term)
                        viewModel.onQueryChange(session, term)
                        focusManager.clearFocus()
                    },
                    onRemoveRecent = { removeSearchTerm(it) },
                    onClearAllRecent = { clearSearchHistory() },
                )
            }
        }
    }
}

@Composable
private fun MobileSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    showMic: Boolean,
    onMicClick: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(999.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = JellioText, fontSize = 15.sp),
        cursorBrush = SolidColor(JellioSecondary),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSubmit() },
            onDone = { onSubmit() },
        ),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(pillShape)
            .background(JellioBgElevated, pillShape)
            .border(
                width = if (isFocused) 1.5.dp else 1.dp,
                color = if (isFocused) JellioSecondary else Color.White.copy(alpha = 0.12f),
                shape = pillShape,
            )
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (isFocused) JellioSecondary else JellioTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = JellioTextSecondary,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClear),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = com.jellio.android.ui.i18n.string("search_clear"),
                            tint = JellioTextSecondary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (showMic) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onMicClick),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = com.jellio.android.ui.i18n.string("search_voice_btn"),
                            tint = JellioTextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MobileSearchIdleState(
    recentSearches: List<String>,
    bottomBarPadding: androidx.compose.ui.unit.Dp,
    onSelectQuery: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearAllRecent: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp + bottomBarPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (recentSearches.isNotEmpty()) {
            item(key = "recent_header", contentType = "idle_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    Text(
                        text = "Recent searches",
                        color = JellioText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Clear all",
                        color = JellioSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onClearAllRecent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            items(
                items = recentSearches,
                key = { "recent:$it" },
                contentType = { "recent_row" },
            ) { term ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectQuery(term) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = JellioTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = term,
                        color = JellioText,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { onRemoveRecent(term) },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = JellioTextSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            item(key = "recent_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        item(key = "browse_header", contentType = "idle_header") {
            Text(
                text = "Browse",
                color = JellioText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item(key = "browse_chips", contentType = "browse_chips") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                QUICK_BROWSE_CHIPS.forEach { chip ->
                    Surface(
                        onClick = { onSelectQuery(chip) },
                        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = JellioBgElevated,
                            contentColor = JellioText,
                            focusedContainerColor = Color.White.copy(alpha = 0.18f),
                            focusedContentColor = Color.White,
                        ),
                        border = ClickableSurfaceDefaults.border(
                            border = Border(
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
                                shape = RoundedCornerShape(999.dp),
                            ),
                            focusedBorder = Border(
                                border = BorderStroke(2.dp, Color.White),
                                shape = RoundedCornerShape(999.dp),
                            ),
                        ),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                    ) {
                        Text(
                            text = chip,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldSurfaceFocusRequester = remember { FocusRequester() }
    val inputFocusRequester = remember { FocusRequester() }

    BackHandler(enabled = isEditing) {
        onEditingChange(false)
        runCatching { fieldSurfaceFocusRequester.requestFocus() }
    }

    LaunchedEffect(isEditing) {
        if (isEditing) {
            runCatching { inputFocusRequester.requestFocus() }
        }
    }

    if (isEditing) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = JellioText, fontSize = 16.sp),
            cursorBrush = SolidColor(JellioText),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search,
                autoCorrectEnabled = false,
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onEditingChange(false)
                    runCatching { fieldSurfaceFocusRequester.requestFocus() }
                },
                onDone = {
                    onEditingChange(false)
                    runCatching { fieldSurfaceFocusRequester.requestFocus() }
                },
            ),
            modifier = modifier
                .focusRequester(inputFocusRequester)
                .clip(RoundedCornerShape(12.dp))
                .background(JellioBgElevated)
                .border(2.dp, JellioSecondary, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = JellioSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (value.isEmpty()) {
                            Text(text = label, color = JellioTextSecondary, fontSize = 16.sp)
                        }
                        innerTextField()
                    }
                }
            },
        )
    } else {
        Surface(
            onClick = { onEditingChange(true) },
            shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(
                focusedScale = 1.02f,
                pressedScale = 0.98f,
            ),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = JellioBgElevated,
                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                contentColor = JellioText,
                focusedContentColor = JellioText,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(1.dp, Color(0xFF2B314B)),
                    shape = RoundedCornerShape(12.dp),
                ),
                focusedBorder = Border(
                    border = BorderStroke(2.dp, Color.White),
                    shape = RoundedCornerShape(12.dp),
                ),
            ),
            modifier = modifier.focusRequester(fieldSurfaceFocusRequester),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = JellioTextSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        color = JellioTextSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                    )
                } else {
                    Text(
                        text = value,
                        color = JellioText,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
