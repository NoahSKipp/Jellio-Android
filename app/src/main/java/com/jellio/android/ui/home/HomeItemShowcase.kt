package com.jellio.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jellio.android.R
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.scaled
import java.util.Locale

// The focused card's full details, and its show when it is an episode.
data class ShowcaseInfo(val item: BaseItemDto, val series: BaseItemDto?)

private const val TICKS_PER_MINUTE = 10_000_000L * 60L
private const val SHOWCASE_SETTLE_MS = 350L

// Top of Home: the focused card's backdrop on the right, fading into the
// background towards the left where its logo, details and overview sit.
@Composable
fun HomeItemShowcase(
    item: BaseItemDto?,
    info: ShowcaseInfo?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier = Modifier,
    onClick: ((BaseItemDto) -> Unit)? = null,
    onPlay: ((BaseItemDto) -> Unit)? = null,
) {
    val isCompactPortrait = com.jellio.android.ui.common.rememberWindowLayoutMode() == com.jellio.android.ui.common.WindowLayoutMode.COMPACT_PORTRAIT
    val clickModifier = if (onClick != null && item != null) {
        Modifier.clickable { onClick(item) }
    } else {
        Modifier
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .background(
                if (isCompactPortrait) {
                    Brush.verticalGradient(0f to JellioBg, 1f to JellioBg)
                } else {
                    Brush.verticalGradient(0f to JellioBg, 0.62f to JellioBg, 0.86f to JellioBg.copy(alpha = 0.45f), 1f to Color.Transparent)
                },
            ),
    ) {
        if (item == null) return@BoxWithConstraints
        val details = info?.item?.takeIf { it.Id == item.Id } ?: item
        val series = info?.series?.takeIf { info.item.Id == item.Id }
        val isEpisode = details.Type.equals("Episode", ignoreCase = true) || details.Type.equals("Season", ignoreCase = true)

        ShowcaseBackdrop(
            item = details,
            series = series,
            imageUrl = imageUrl,
            applySideMask = !isCompactPortrait,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .fillMaxWidth(if (isCompactPortrait) 1f else 0.72f),
        )

        if (isCompactPortrait) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to JellioBg.copy(alpha = 0.22f),
                            0.35f to JellioBg.copy(alpha = 0.30f),
                            0.68f to JellioBg.copy(alpha = 0.84f),
                            1f to JellioBg,
                        ),
                    ),
            )
        }

        // The text crossfades from one title to the next; more details for
        // the same title fill in place.
        androidx.compose.animation.AnimatedContent(
            targetState = Triple(details, series, isEpisode),
            contentKey = { it.first.Id },
            transitionSpec = {
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260)) togetherWith
                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180))
            },
            modifier = Modifier
                // Sits Nuvio's 16dp above the rows, which start HeaderOverlap above this box's bottom.
                // A long title or logo shortens the description rather than pushing into them.
                .align(Alignment.BottomStart)
                .fillMaxHeight()
                .then(if (isCompactPortrait) Modifier.statusBarsPadding() else Modifier)
                .padding(
                    start = if (isCompactPortrait) 16.dp else RowStartPadding,
                    top = if (isCompactPortrait) 12.dp else 24.dp,
                    end = if (isCompactPortrait) 16.dp else 24.dp,
                    bottom = if (isCompactPortrait) 14.dp else HeaderOverlap + 16.dp,
                )
                .width(if (isCompactPortrait) maxWidth else maxWidth * 0.42f),
            label = "showcaseText",
        ) { (details, series, isEpisode) ->
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isCompactPortrait) 6.dp else 8.dp, Alignment.Bottom),
            modifier = Modifier.fillMaxHeight(),
        ) {
            ShowcaseLogo(
                item = details,
                series = series,
                title = if (isEpisode) details.SeriesName ?: series?.Name ?: details.Name.orEmpty() else details.Name.orEmpty(),
                imageUrl = imageUrl,
                isCompactPortrait = isCompactPortrait,
            )
            ShowcaseMetadata(
                details = details,
                series = series,
                isEpisode = isEpisode,
                isCompactPortrait = isCompactPortrait,
            )
            val status = (series ?: details).Status
            if (!isCompactPortrait && status.equals("Continuing", ignoreCase = true)) {
                Text(
                    text = com.jellio.android.data.i18n.Strings.get("home_editorial_ongoing").uppercase(),
                    color = JellioText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            val overview = details.Overview?.takeIf { it.isNotBlank() } ?: series?.Overview?.takeIf { it.isNotBlank() }
            if (overview != null) {
                Text(
                    text = overview,
                    color = JellioText.copy(alpha = 0.84f),
                    fontSize = if (isCompactPortrait) 13.sp else 14.sp,
                    lineHeight = if (isCompactPortrait) 18.sp else 19.sp,
                    maxLines = if (isCompactPortrait) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (isCompactPortrait) Modifier else Modifier.weight(1f, fill = false),
                )
            }
            if (isCompactPortrait && (onPlay != null || onClick != null)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (onPlay != null) {
                        androidx.compose.material3.Button(
                            onClick = { onPlay(details) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF0D0D0D),
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                            Text(
                                text = com.jellio.android.data.i18n.Strings.get("action_play"),
                                color = Color(0xFF0D0D0D),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (onClick != null) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { onClick(details) },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.10f),
                                contentColor = JellioText,
                            ),
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                tint = JellioText,
                                modifier = Modifier.size(16.dp),
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                            Text(
                                text = com.jellio.android.data.i18n.Strings.get("details_title"),
                                color = JellioText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ShowcaseBackdrop(
    item: BaseItemDto,
    series: BaseItemDto?,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier,
    applySideMask: Boolean = true,
) {
    val candidates = remember(item.Id, series?.Id) { posterBackdropCandidates(item, imageUrl, 1280, series = series) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(index) ?: return
    val context = LocalContext.current
    BoxWithConstraints(
        // The image fades out by transparency (a mask), not by painting the
        // background colour over it, so it blends into whatever is beneath.
        modifier = modifier
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithCache {
                if (size.width <= 0f || size.height <= 0f) {
                    return@drawWithCache onDrawWithContent { drawContent() }
                }
                val sideMask = if (applySideMask) {
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.18f to Color.Black.copy(alpha = 0.1f),
                        0.42f to Color.Black.copy(alpha = 0.45f),
                        0.68f to Color.Black.copy(alpha = 0.85f),
                        1f to Color.Black,
                        endX = size.width * 0.7f,
                    )
                } else {
                    null
                }
                val bottomMask = Brush.verticalGradient(
                    0f to Color.Black,
                    0.55f to Color.Black,
                    0.8f to Color.Black.copy(alpha = 0.3f),
                    1f to Color.Transparent,
                )
                onDrawWithContent {
                    drawContent()
                    if (sideMask != null) {
                        drawRect(sideMask, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                    }
                    drawRect(bottomMask, blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
                }
            },
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val request = remember(url, widthPx, heightPx) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey("${url}_${widthPx}x$heightPx")
                .size(widthPx, heightPx)
                .crossfade(250)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            onError = { index += 1 },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun ShowcaseLogo(
    item: BaseItemDto,
    series: BaseItemDto?,
    title: String,
    imageUrl: (BaseItemDto, String, Int) -> String,
    isCompactPortrait: Boolean = false,
) {
    val candidates = remember(item.Id, series?.Id) { posterLogoCandidates(item, imageUrl, 1200, series = series) }
    var index by remember(candidates) { mutableIntStateOf(0) }
    val url = candidates.getOrNull(index)
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxLogoHeight = if (isCompactPortrait) 64.dp else 72.dp.scaled()
    val maxLogoWidth = if (isCompactPortrait) 260.dp else 340.dp.scaled()
    val logoWidthPx = maxOf(with(density) { maxLogoWidth.roundToPx() }, 1000)
    val logoHeightPx = maxOf(with(density) { maxLogoHeight.roundToPx() } * 3, 300)
    if (url != null) {
        Box(contentAlignment = Alignment.BottomStart, modifier = Modifier.height(maxLogoHeight)) {
            AsyncImage(
                model = remember(url, logoWidthPx, logoHeightPx) { sizedImageRequest(context, url, logoWidthPx, logoHeightPx) },
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { index += 1 },
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = maxLogoWidth),
            )
        }
    } else {
        Text(
            text = title,
            color = JellioText,
            fontSize = if (isCompactPortrait) 24.sp else 30.sp,
            lineHeight = if (isCompactPortrait) 28.sp else 34.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// "Movie • Action • 2h 25m • 2026 • TMDB 8.0" on one line; an episode shows
// "S1 E3 • Episode name" in place of the type.
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShowcaseMetadata(
    details: BaseItemDto,
    series: BaseItemDto?,
    isEpisode: Boolean,
    isCompactPortrait: Boolean = false,
) {
    val episodeHeading = remember(details, isEpisode) {
        if (!isEpisode) return@remember null
        val label = episodeLabel(details)
        val epName = details.Name?.takeIf { it.isNotBlank() && label.isNotEmpty() }
            ?.let { com.jellio.android.data.i18n.episodeDisplayName(it, details.IndexNumber) }
        when {
            label.isNotEmpty() && epName != null -> "$label · $epName"
            label.isNotEmpty() -> label
            else -> null
        }
    }
    val parts = remember(details, series, isCompactPortrait, com.jellio.android.data.prefs.DisplayPreferences.displayLanguage) {
        buildList {
            if (!isCompactPortrait && isEpisode) {
                val label = episodeLabel(details)
                if (label.isNotEmpty()) add(label)
                details.Name?.takeIf { it.isNotBlank() && label.isNotEmpty() }?.let { add(com.jellio.android.data.i18n.episodeDisplayName(it, details.IndexNumber)) }
            } else if (!isEpisode) {
                when (details.Type?.lowercase(Locale.US)) {
                    "movie" -> add(com.jellio.android.data.i18n.Strings.get("search_filter_movies"))
                    "series" -> add(com.jellio.android.data.i18n.Strings.get("nav_shows"))
                    "boxset" -> add(com.jellio.android.data.i18n.Strings.get("detail_collection"))
                }
            }
            (details.Genres?.firstOrNull() ?: series?.Genres?.firstOrNull())?.takeIf { it.isNotBlank() }?.let { add(com.jellio.android.data.i18n.genreDisplayName(it)) }
            details.RunTimeTicks?.let { ticks ->
                val minutes = (ticks / TICKS_PER_MINUTE).toInt()
                if (minutes > 0) add(com.jellio.android.data.i18n.formatDurationMinutes(minutes))
            }
            val year = details.ProductionYear?.toString() ?: details.PremiereDate?.take(4)
            if (!year.isNullOrBlank()) {
                val ongoing = !isEpisode && details.Status.equals("Continuing", ignoreCase = true)
                add(if (ongoing) "$year-" else year)
            }
        }
    }
    val rating = details.CommunityRating ?: series?.CommunityRating?.takeIf { isEpisode }
    if (isCompactPortrait) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (episodeHeading != null) {
                Text(
                    text = episodeHeading,
                    color = JellioText.copy(alpha = 0.92f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                parts.forEach { part ->
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = part,
                            color = JellioTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (rating != null && rating > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        TmdbBadge(width = 24.dp, height = 10.dp)
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            color = JellioText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            parts.forEachIndexed { index, part ->
                if (index > 0) MetaDot(modifier = Modifier.align(Alignment.CenterVertically))
                Text(
                    text = part,
                    color = JellioTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            if (rating != null && rating > 0) {
                if (parts.isNotEmpty()) MetaDot(modifier = Modifier.align(Alignment.CenterVertically))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.align(Alignment.CenterVertically),
                ) {
                    TmdbBadge(width = 28.dp, height = 12.dp)
                    Text(
                        text = String.format(Locale.US, "%.1f", rating),
                        color = JellioTextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaDot(modifier: Modifier = Modifier) {
    Text(text = "•", color = JellioTextSecondary.copy(alpha = 0.6f), fontSize = 13.sp, softWrap = false, modifier = modifier)
}

// Ratings here are TMDB's (Gelato imports them), same badge Jellio Web shows.
@Composable
internal fun TmdbBadge(
    modifier: Modifier = Modifier,
    width: Dp = 26.dp,
    height: Dp = 11.dp,
    size: Dp? = null,
    tint: Color? = null,
) {
    val finalWidth = size?.let { it * 2.33f } ?: width
    val finalHeight = size ?: height
    Image(
        painter = painterResource(R.drawable.ic_tmdb_logo),
        contentDescription = "TMDB",
        colorFilter = tint?.let { ColorFilter.tint(it) },
        modifier = modifier.size(width = finalWidth, height = finalHeight),
    )
}

// The showcase follows focus once it settles, so moving quickly along a row
// doesn't start a backdrop load for every card passed.
@Composable
fun ShowcaseHost(
    highlighted: androidx.compose.runtime.State<BaseItemDto?>,
    fallback: BaseItemDto?,
    loadInfo: suspend (BaseItemDto) -> ShowcaseInfo,
    imageUrl: (BaseItemDto, String, Int) -> String,
    modifier: Modifier = Modifier,
    onClick: ((BaseItemDto) -> Unit)? = null,
    onPlay: ((BaseItemDto) -> Unit)? = null,
) {
    val active = highlighted.value ?: fallback
    var shownItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var showcaseInfo by remember { mutableStateOf<ShowcaseInfo?>(null) }
    LaunchedEffect(active?.Id) {
        val target = active ?: return@LaunchedEffect
        if (shownItem != null) kotlinx.coroutines.delay(SHOWCASE_SETTLE_MS)
        shownItem = target
        showcaseInfo = ShowcaseInfo(target, null)
        showcaseInfo = loadInfo(target)
    }
    HomeItemShowcase(
        item = shownItem,
        info = showcaseInfo,
        imageUrl = imageUrl,
        modifier = modifier,
        onClick = onClick,
        onPlay = onPlay,
    )
}

suspend fun loadShowcaseInfo(repository: com.jellio.android.data.JellioRepository, userId: String, item: BaseItemDto): ShowcaseInfo {
    val isEpisode = item.Type.equals("Episode", ignoreCase = true) || item.Type.equals("Season", ignoreCase = true)
    val seriesId = item.SeriesId?.takeIf { isEpisode }
    val ids = buildList {
        if (item.Overview.isNullOrBlank()) add(item.Id)
        seriesId?.let(::add)
    }
    if (ids.isEmpty()) return ShowcaseInfo(item, null)
    val fetched = runCatching { repository.getShowcaseItems(userId, ids) }.getOrDefault(emptyMap())
    return ShowcaseInfo(fetched[item.Id] ?: item, seriesId?.let { fetched[it] })
}

// How far the header art runs under the first row: its title line plus 14dp, as in Nuvio.
internal val HeaderOverlap = 38.dp
