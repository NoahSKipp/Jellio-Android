package com.jellio.android.ui.person

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Border
import com.jellio.android.ui.common.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.jellio.android.ui.common.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.jellio.android.data.model.DepartmentTab
import com.jellio.android.data.model.PersonCreditItem
import com.jellio.android.data.model.RichPersonDetails
import com.jellio.android.data.session.Session
import com.jellio.android.ui.common.ErrorState
import com.jellio.android.ui.common.NoOpBringIntoViewSpec
import com.jellio.android.ui.common.readerHorizontalInsets
import com.jellio.android.ui.common.rememberTvHorizontalBringIntoViewSpec
import com.jellio.android.ui.common.topBarInsets
import com.jellio.android.ui.home.RatingChip
import com.jellio.android.ui.home.TmdbBadge
import com.jellio.android.ui.i18n.Strings
import com.jellio.android.ui.nav.LocalBottomBarPadding
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.scaled

private fun renderMarkdownBiography(raw: String): AnnotatedString {
    val cleanLinks = raw.replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1")
    val builder = AnnotatedString.Builder()
    val tokenRegex = Regex("(\\*\\*|__)(.*?)\\1|(\\*|_)(.*?)\\3")
    var lastIndex = 0

    for (match in tokenRegex.findAll(cleanLinks)) {
        val range = match.range
        if (range.first > lastIndex) {
            builder.append(cleanLinks.substring(lastIndex, range.first))
        }
        val boldContent = match.groups[2]?.value
        val italicContent = match.groups[4]?.value
        if (boldContent != null) {
            builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            builder.append(boldContent)
            builder.pop()
        } else if (italicContent != null) {
            builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            builder.append(italicContent)
            builder.pop()
        }
        lastIndex = range.last + 1
    }

    if (lastIndex < cleanLinks.length) {
        builder.append(cleanLinks.substring(lastIndex))
    }

    return builder.toAnnotatedString()
}


private const val NameWideSp = 34f
private val FullSpan: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

// Lets a full-bleed item (the hero) escape the grid's horizontal content padding.
private fun Modifier.bleedHorizontal(amount: Dp): Modifier = layout { measurable, constraints ->
    val bleed = amount.roundToPx()
    val width = constraints.maxWidth + bleed * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

@Composable
fun PersonScreen(
    session: Session,
    personId: String,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val heroFocusRequester = remember { FocusRequester() }
    val bottomBarPadding = LocalBottomBarPadding.current

    BackHandler(onBack = onBack)

    LaunchedEffect(personId) {
        viewModel.load(session, personId)
    }

    LaunchedEffect(uiState.details) {
        if (uiState.details != null) {
            gridState.scrollToItem(0)
            runCatching { heroFocusRequester.requestFocus() }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(JellioBg)) {
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = Strings.get("status_loading"), color = JellioTextSecondary)
                }
            }
            uiState.error != null -> {
                ErrorState(
                    message = Strings.get("person_load_error"),
                    failure = uiState.error,
                    onRetry = { viewModel.retry(session, personId) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            uiState.details != null -> {
                val details = uiState.details!!
                val widthDp = maxWidth.value.toInt()
                val compact = isCompactWidth(widthDp)
                val horizontalPadding = if (compact) 16.dp else 48.dp
                val gap = if (compact) 10.dp else 16.dp
                val columns = creditGridColumns(widthDp, horizontalPadding.value.toInt(), gap.value.toInt())
                val credits = uiState.filmography
                var shown by remember(credits) { mutableIntStateOf(initialCreditCount(credits.size)) }
                val hasKnownFor = details.knownFor.isNotEmpty()
                val hasCredits = details.allCredits.isNotEmpty()
                // Header, Known For, then the Filmography title and filter row.
                val leadingItems = 1 + (if (hasKnownFor) 1 else 0) + (if (hasCredits) 2 else 0)

                LaunchedEffect(gridState, credits, leadingItems) {
                    snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                        .collect { last ->
                            if (shouldLoadMoreCredits(last, leadingItems, shown, credits.size)) {
                                shown = nextCreditCount(shown, credits.size)
                            }
                        }
                }

                val cardWidth = if (compact) 116.dp else 140.dp

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columns),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalArrangement = Arrangement.spacedBy(gap + 2.dp),
                    contentPadding = PaddingValues(
                        start = horizontalPadding,
                        end = horizontalPadding,
                        bottom = 48.dp + bottomBarPadding,
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .readerHorizontalInsets()
                        .focusRestorer(),
                ) {
                    item(key = "person_header", span = FullSpan, contentType = "header") {
                        Box(modifier = Modifier.bleedHorizontal(horizontalPadding)) {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides NoOpBringIntoViewSpec) {
                                PersonHeroHeader(
                                    details = details,
                                    fallbackImageUrl = imageUrl(details.id, null, "Primary", 400),
                                    compact = compact,
                                    horizontalPadding = horizontalPadding,
                                    onBack = onBack,
                                    backFocusRequester = heroFocusRequester,
                                )
                            }
                        }
                    }

                    if (hasKnownFor) {
                        item(key = "person_known_for", span = FullSpan, contentType = "known_for") {
                            Column(modifier = Modifier.padding(top = 20.dp)) {
                                SectionTitle(Strings.get("person_known_for"))
                                CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvHorizontalBringIntoViewSpec()) {
                                    LazyRow(
                                        modifier = Modifier.bleedHorizontal(horizontalPadding),
                                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                                        horizontalArrangement = Arrangement.spacedBy(gap),
                                    ) {
                                        items(details.knownFor, key = { "known_" + it.id + "_" + it.department }) { credit ->
                                            PersonCreditCard(
                                                credit = credit,
                                                imageUrl = imageUrl,
                                                onClick = { onItemClick(credit.id) },
                                                modifier = Modifier.width(cardWidth),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (hasCredits) {
                        item(key = "person_filmography_title", span = FullSpan, contentType = "section_title") {
                            SectionTitle(Strings.get("tv_filmography"), modifier = Modifier.padding(top = 12.dp))
                        }
                        item(key = "person_filmography_filters", span = FullSpan, contentType = "filters") {
                            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberTvHorizontalBringIntoViewSpec()) {
                                LazyRow(
                                    modifier = Modifier.bleedHorizontal(horizontalPadding),
                                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(details.departmentTabs, key = { it.key }) { tab ->
                                        DepartmentTabPill(
                                            tab = tab,
                                            selected = uiState.selectedDepartment.equals(tab.key, ignoreCase = true),
                                            onSelect = { viewModel.selectDepartment(tab.key) },
                                        )
                                    }
                                }
                            }
                        }

                        val department = uiState.selectedDepartment
                        items(
                            count = minOf(shown, credits.size),
                            key = { index -> "credit_${department}_$index" },
                            contentType = { "credit" },
                        ) { index ->
                            val credit = credits[index]
                            PersonCreditCard(
                                credit = credit,
                                imageUrl = imageUrl,
                                onClick = { onItemClick(credit.id) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = JellioText,
        modifier = modifier
            .padding(bottom = 10.dp)
            .semantics { heading() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonHeroHeader(
    details: RichPersonDetails,
    fallbackImageUrl: String,
    compact: Boolean,
    horizontalPadding: Dp,
    onBack: () -> Unit,
    backFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.heightIn(min = 380.dp.scaled())),
    ) {
        // The backdrop is someone else's title art, so it only sets the mood:
        // blurred and dimmed behind the text, a little clearer on the far right.
        if (!details.backdropUrl.isNullOrBlank()) {
            val canBlur = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            AsyncImage(
                model = details.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().then(if (canBlur) Modifier.blur(24.dp) else Modifier),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(JellioBg.copy(alpha = if (canBlur) 0.45f else 0.62f)),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        0f to JellioBg.copy(alpha = 0.92f),
                        0.55f to JellioBg.copy(alpha = 0.72f),
                        1f to JellioBg.copy(alpha = 0.25f),
                    ),
                ),
        )

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to JellioBg.copy(alpha = 0.35f),
                        0.45f to Color.Transparent,
                        0.8f to JellioBg.copy(alpha = 0.75f),
                        1f to JellioBg,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .topBarInsets()
                .padding(horizontal = horizontalPadding)
                .padding(top = if (compact) 8.dp else 20.dp, bottom = 20.dp),
        ) {
            BackPill(onBack = onBack, focusRequester = backFocusRequester)

            val photoModel = details.profileUrl ?: fallbackImageUrl
            val photoWidth = if (compact) 120.dp else 170.dp

            if (compact) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PersonPhoto(photoModel = photoModel, name = details.name, width = photoWidth)
                    PersonName(
                        name = details.name,
                        baseSp = NameBaseSp,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                }
                PersonChips(details = details, modifier = Modifier.padding(top = 14.dp))
                if (!details.biography.isNullOrBlank()) {
                    PersonBiography(text = details.biography, modifier = Modifier.padding(top = 12.dp))
                }
            } else {
                Row(
                    modifier = Modifier.padding(top = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    PersonPhoto(photoModel = photoModel, name = details.name, width = photoWidth)
                    Column(
                        modifier = Modifier
                            .padding(start = 28.dp)
                            .widthIn(max = 760.dp),
                    ) {
                        PersonName(name = details.name, baseSp = NameWideSp, modifier = Modifier.fillMaxWidth())
                        PersonChips(details = details, modifier = Modifier.padding(top = 10.dp))
                        if (!details.biography.isNullOrBlank()) {
                            PersonBiography(text = details.biography, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackPill(onBack: () -> Unit, focusRequester: FocusRequester) {
    Surface(
        onClick = onBack,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = JellioText,
            focusedContainerColor = Color.White.copy(alpha = 0.22f),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.focusRequester(focusRequester),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = Strings.get("nav_back"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PersonPhoto(photoModel: String, name: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(14.dp))
            .background(JellioBgElevated)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (photoModel.isNotBlank()) {
            AsyncImage(
                model = photoModel,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = JellioTextSecondary.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

// Wraps between words only. A word that does not fit makes the text shrink
// step by step instead of being split.
@Composable
private fun PersonName(name: String, baseSp: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val widthDp = maxWidth.value
        var sizeSp by remember(name, widthDp, baseSp) {
            mutableFloatStateOf(initialNameSize(name, widthDp, baseSp))
        }
        Text(
            text = name,
            color = JellioText,
            fontWeight = FontWeight.Bold,
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.18f).sp,
            maxLines = NameMaxLines,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineSmall.copy(shadow = HeroTextShadow),
            onTextLayout = { result ->
                val lineEnds = (0 until result.lineCount).map { result.getLineEnd(it) }
                if (result.hasVisualOverflow || breaksMidWord(name, lineEnds)) {
                    shrunkNameSize(sizeSp)?.let { sizeSp = it }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonChips(details: RichPersonDetails, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetadataBadge(text = details.knownForDepartment ?: "Acting", accent = true)

        if (!details.birthday.isNullOrBlank()) {
            val ageText = if (details.age != null) " (${Strings.format("person_age", details.age)})" else ""
            MetadataBadge(text = "${Strings.get("person_born")} ${details.birthday}$ageText")
        }

        if (!details.placeOfBirth.isNullOrBlank()) {
            MetadataBadge(text = details.placeOfBirth)
        }

        MetadataBadge(text = Strings.format("person_credits_count", details.totalCreditsCount))

        if (!details.imdbId.isNullOrBlank()) {
            ImdbBadge()
        }

        TmdbBadge(width = 28.dp, height = 12.dp)
    }
}

// Collapsed to a few lines; the toggle only exists when the text is longer.
@Composable
private fun PersonBiography(text: String, modifier: Modifier = Modifier) {
    val annotated = remember(text) { renderMarkdownBiography(text) }
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    var overflows by remember(text) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Text(
            text = annotated,
            color = JellioText.copy(alpha = 0.86f),
            style = MaterialTheme.typography.bodyMedium.copy(shadow = HeroTextShadow),
            maxLines = if (expanded) Int.MAX_VALUE else BioCollapsedLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { overflows = bioOverflowMemo(expanded, overflows, it.hasVisualOverflow) },
            // Tapping the text is a touch shortcut; the button below is the accessible control.
            modifier = Modifier.fillMaxWidth().pointerInput(overflows) {
                detectTapGestures { if (overflows) expanded = !expanded }
            },
        )

        if (overflows) {
            Surface(
                onClick = { expanded = !expanded },
                shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.08f),
                    contentColor = JellioTextSecondary,
                    focusedContainerColor = Color.White.copy(alpha = 0.22f),
                    focusedContentColor = Color.White,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = Border(BorderStroke(2.dp, Color.White)),
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text = Strings.get(if (expanded) "person_read_less" else "person_read_more"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MetadataBadge(text: String, accent: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(
                if (accent) Color.White.copy(alpha = 0.22f) else JellioBg.copy(alpha = 0.55f),
                RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (accent) Color.White else JellioText.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ImdbBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFF5C518), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = "IMDb",
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun DepartmentTabPill(
    tab: DepartmentTab,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onSelect,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(999.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f, pressedScale = 0.97f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) Color.White else JellioTextSecondary,
            focusedContainerColor = Color.White.copy(alpha = 0.30f),
            focusedContentColor = Color.White,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White)),
        ),
        modifier = modifier
            .semantics { this.selected = selected }
            .onFocusChanged {
                if (it.isFocused) {
                    onSelect()
                }
            },
    ) {
        Text(
            text = "${tab.label} (${tab.count})",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

private val CreditTitleLineHeight = 15.sp

@Composable
private fun PersonCreditCard(
    credit: PersonCreditItem,
    imageUrl: (itemId: String, tag: String?, imageType: String, maxWidth: Int) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageModel = credit.posterUrl
        ?: if (credit.inLibrary) imageUrl(credit.id, credit.baseItem.PrimaryImageTag, "Primary", 300) else null
    // Two title lines are always reserved so cards in a row stay the same height.
    val titleMinHeight = with(LocalDensity.current) { (CreditTitleLineHeight * 2).toDp() }

    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = JellioBgElevated,
            contentColor = JellioText,
            focusedContainerColor = JellioBgElevated,
            focusedContentColor = JellioText,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(10.dp),
            ),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = modifier,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.White.copy(alpha = 0.04f)),
            ) {
                if (!imageModel.isNullOrBlank()) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (credit.rating != null && credit.rating > 0.0) {
                    RatingChip(
                        rating = credit.rating,
                        fontSize = 10.sp,
                        badgeWidth = 20.dp,
                        badgeHeight = 9.dp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }
            }

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                Text(
                    text = credit.title,
                    color = JellioText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    lineHeight = CreditTitleLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.heightIn(min = titleMinHeight),
                )

                val yearStr = credit.year?.toString()
                val roleStr = credit.role?.takeIf { it.isNotBlank() }
                val caption = when {
                    roleStr != null && yearStr != null -> "$yearStr · $roleStr"
                    yearStr != null -> yearStr
                    roleStr != null -> roleStr
                    else -> credit.mediaType
                }

                Text(
                    text = caption,
                    color = JellioTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private val HeroTextShadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 8f)
