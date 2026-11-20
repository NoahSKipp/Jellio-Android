package com.jellio.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.jellio.android.data.i18n.Strings
import com.jellio.android.data.model.BaseItemDto
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioText

// Which episodes of a show or season (when it is one), then the quality.
@Composable
internal fun DownloadChoiceDialog(
    item: BaseItemDto,
    onChoose: (EpisodeScope?, DownloadQuality) -> Unit,
    onDismiss: () -> Unit,
) {
    val needsScope = downloadKindFor(item.Type) == DownloadKind.EPISODES
    var scope by remember { mutableStateOf<EpisodeScope?>(null) }
    val pickingScope = needsScope && scope == null
    CardDialog(onDismiss = onDismiss) { firstFocus ->
        Column(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                .background(JellioBgElevated, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = item.Name.orEmpty(),
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            Text(
                text = Strings.get(if (pickingScope) "downloads_choose_episodes" else "downloads_choose_quality"),
                color = JellioText.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            if (pickingScope) {
                EpisodeScope.values().forEachIndexed { index, option ->
                    CardOptionsMenuRow(
                        label = Strings.get(option.labelKeyFor(item.Type == "Season")),
                        onClick = { scope = option },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            } else {
                DownloadQuality.values().forEachIndexed { index, option ->
                    CardOptionsMenuRow(
                        label = Strings.get(option.labelKey),
                        onClick = {
                            onChoose(scope, option)
                            onDismiss()
                        },
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}
