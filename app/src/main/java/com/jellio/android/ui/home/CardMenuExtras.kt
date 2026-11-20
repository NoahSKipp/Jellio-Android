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
import androidx.compose.runtime.remember
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
)

@Stable
class CardMenuExtras internal constructor(
    private val session: Session,
    private val context: Context,
    private val viewModel: CardMenuViewModel,
) {
    // Composed only while a menu is open, which is when the flags get read.
    @Composable
    fun forItem(item: BaseItemDto): ItemMenuExtras {
        LaunchedEffect(session.userId) { viewModel.load(session) }
        val state by viewModel.state.collectAsState()
        return ItemMenuExtras(
            onShareLink = { copyShareLink(item) },
            isOnGrouplist = normalizeGuid(item.Id) in state.grouplistIds,
            onToggleGrouplist = if (state.grouplistEnabled) {
                { viewModel.toggleGrouplist(item) { toast("toast_grouplist_error") } }
            } else {
                null
            },
        )
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
    return remember(session, context, viewModel) { CardMenuExtras(session, context, viewModel) }
}
