package com.jellio.android.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioText

// A titled panel over a dimmed screen: bottom sheet on phones, right drawer on tablets.
// Focus stays inside; Back or a tap on the scrim closes it.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePanel(
    title: String,
    width: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val isCompact = isCompactScreen()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .dismissOnBack(onDismiss)
            .focusProperties { onExit = { FocusRequester.Cancel } }
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
            ),
        contentAlignment = if (isCompact) Alignment.BottomCenter else Alignment.CenterEnd,
    ) {
        val sheetShape = if (isCompact) {
            RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
        }
        Column(
            modifier = Modifier
                .then(
                    if (isCompact) {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight * 0.85f)
                    } else {
                        Modifier
                            .width(width)
                            .fillMaxHeight()
                    },
                )
                .clip(sheetShape)
                .background(JellioBgElevated)
                .then(
                    if (isCompact) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier.statusBarsPadding().navigationBarsPadding()
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(
                    top = if (isCompact) 12.dp else 24.dp,
                    bottom = if (isCompact) 16.dp else 24.dp,
                ),
        ) {
            if (isCompact) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.25f)),
                )
            }
            Text(
                text = title,
                color = JellioText,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            content()
        }
    }
}
