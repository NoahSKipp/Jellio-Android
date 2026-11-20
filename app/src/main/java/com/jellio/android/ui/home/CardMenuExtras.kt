package com.jellio.android.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.normalizeGuid
import com.jellio.android.data.session.Session

// What the options menu can do for one title beyond the basics. A null
// action hides its entry, so each screen offers only what applies.
data class ItemMenuExtras(
    val onShareLink: (() -> Unit)? = null,
    val isOnGrouplist: Boolean = false,
    val onToggleGrouplist: (() -> Unit)? = null,
    val onDownload: (() -> Unit)? = null,
    val onFindSkip: (() -> Unit)? = null,
    val onQuickSkip: (() -> Unit)? = null,
    val onDeepSkip: (() -> Unit)? = null,
)

// The extra entries of a season's episode menu: the episode itself, and its
// season for the downloads and scans that cover all of it.
data class EpisodeMenuExtras(
    val onDownloadEpisode: (() -> Unit)? = null,
    val onDownloadSeason: (() -> Unit)? = null,
    val onQuickSkipEpisode: (() -> Unit)? = null,
    val onDeepSkipEpisode: (() -> Unit)? = null,
    val onQuickSkipSeason: (() -> Unit)? = null,
    val onDeepSkipSeason: (() -> Unit)? = null,
)

@Stable
class CardMenuExtras internal constructor(
    private val session: Session,
    private val context: Context,
    private val viewModel: CardMenuViewModel,
    private val askDownload: (BaseItemDto) -> Unit,
) {
    // Composed only while a menu is open, which is when the flags get read.
    @Composable
    fun forItem(item: BaseItemDto): ItemMenuExtras {
        LaunchedEffect(session.userId) { viewModel.load(session) }
        val state by viewModel.state.collectAsState()
        val skip = skipActionsFor(item.Type, state.isAdmin, state.skipMenuEnabled)
        return ItemMenuExtras(
            onShareLink = { copyShareLink(item) },
            isOnGrouplist = normalizeGuid(item.Id) in state.grouplistIds,
            onToggleGrouplist = if (state.grouplistEnabled) {
                { viewModel.toggleGrouplist(item) { toast("toast_grouplist_error") } }
            } else {
                null
            },
            onDownload = if (downloadKindFor(item.Type) != null) { { askDownload(item) } } else null,
            onFindSkip = skipAction(item, skip, SkipAction.FIND),
            onQuickSkip = skipAction(item, skip, SkipAction.QUICK),
            onDeepSkip = skipAction(item, skip, SkipAction.DEEP),
        )
    }

    @Composable
    fun forEpisode(episode: BaseItemDto, season: BaseItemDto?): EpisodeMenuExtras {
        LaunchedEffect(session.userId) { viewModel.load(session) }
        val state by viewModel.state.collectAsState()
        val scans = canScanSkips(state.isAdmin, state.skipMenuEnabled)
        val seasonId = episode.SeasonId
        val seriesId = episode.SeriesId
        val seasonItem = if (seasonId != null && seriesId != null) {
            BaseItemDto(Id = seasonId, Name = season?.Name, Type = "Season", SeriesId = seriesId)
        } else {
            null
        }
        val seasonScan = season?.takeIf { scans }
        return EpisodeMenuExtras(
            onDownloadEpisode = { askDownload(episode) },
            onDownloadSeason = seasonItem?.let { target -> { askDownload(target) } },
            onQuickSkipEpisode = if (scans) { { scanSkipTimestamps(episode, SkipAction.QUICK) } } else null,
            onDeepSkipEpisode = if (scans) { { scanSkipTimestamps(episode, SkipAction.DEEP) } } else null,
            onQuickSkipSeason = seasonScan?.let { target -> { scanSkipTimestamps(target, SkipAction.QUICK) } },
            onDeepSkipSeason = seasonScan?.let { target -> { scanSkipTimestamps(target, SkipAction.DEEP) } },
        )
    }

    private fun skipAction(item: BaseItemDto, offered: List<SkipAction>, action: SkipAction): (() -> Unit)? =
        if (action in offered) {
            { scanSkipTimestamps(item, action) }
        } else {
            null
        }

    internal fun scanSkipTimestamps(item: BaseItemDto, action: SkipAction) {
        viewModel.scanSkipTimestamps(item, action) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    internal fun download(item: BaseItemDto, scope: EpisodeScope?, quality: DownloadQuality) {
        viewModel.download(session, item, scope, quality) { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun toast(key: String) {
        Toast.makeText(context, Strings.get(key), Toast.LENGTH_SHORT).show()
    }

    private fun copyShareLink(item: BaseItemDto) {
        val label = shareLabel(item.Type, item.Name, item.SeriesName, item.ParentIndexNumber, item.IndexNumber)
        val text = shareText(label, shareUrl(session.serverAddress, item.Id))
        val copied = runCatching {
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("Jellio", text))
        }.isSuccess
        toast(if (copied) "toast_link_copied" else "toast_copy_link_error")
    }
}

@Composable
fun rememberCardMenuExtras(
    session: Session,
    viewModel: CardMenuViewModel = hiltViewModel(),
): CardMenuExtras {
    val context = LocalContext.current
    var downloadTarget by remember { mutableStateOf<BaseItemDto?>(null) }
    val extras = remember(session, context, viewModel) {
        CardMenuExtras(session, context, viewModel) { downloadTarget = it }
    }
    // The menu has closed by the time a choice is made, so the picker lives here.
    downloadTarget?.let { target ->
        DownloadChoiceDialog(
            item = target,
            onChoose = { scope, quality -> extras.download(target, scope, quality) },
            onDismiss = { downloadTarget = null },
        )
    }
    return extras
}
