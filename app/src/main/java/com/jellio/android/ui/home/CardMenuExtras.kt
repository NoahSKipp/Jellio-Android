package com.jellio.android.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.data.session.Session

// What the options menu can do for one title beyond the basics. A null
// action hides its entry, so each screen offers only what applies.
data class ItemMenuExtras(
    val onShareLink: (() -> Unit)? = null,
)

class CardMenuExtras internal constructor(private val build: (BaseItemDto) -> ItemMenuExtras) {
    fun forItem(item: BaseItemDto): ItemMenuExtras = build(item)
}

@Composable
fun rememberCardMenuExtras(session: Session): CardMenuExtras {
    val context = LocalContext.current
    return remember(session.serverAddress, context) {
        CardMenuExtras { item ->
            ItemMenuExtras(onShareLink = { copyShareLink(context, session.serverAddress, item) })
        }
    }
}

private fun copyShareLink(context: Context, serverAddress: String, item: BaseItemDto) {
    val label = shareLabel(item.Type, item.Name, item.SeriesName, item.ParentIndexNumber, item.IndexNumber)
    val text = shareText(label, shareUrl(serverAddress, item.Id))
    val copied = runCatching {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Jellio", text))
    }.isSuccess
    Toast.makeText(context, Strings.get(if (copied) "toast_link_copied" else "toast_copy_link_error"), Toast.LENGTH_SHORT).show()
}
