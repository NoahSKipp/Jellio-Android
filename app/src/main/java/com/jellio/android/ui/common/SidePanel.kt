package com.jellio.android.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioText

// A titled panel on the right over a dimmed screen. Focus stays inside;
// Back or a tap on the scrim closes it.
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidePanel(
    title: String,
    width: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onDismiss)
    Box(
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
        contentAlignment = Alignment.CenterEnd,
    ) {
        val isCompact = isCompactScreen()
        Column(
            modifier = Modifier
                .then(if (isCompact) Modifier.fillMaxWidth(0.90f) else Modifier.width(width))
                .fillMaxSize()
                .background(JellioBgElevated)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(top = if (isCompact) 24.dp else 48.dp, bottom = if (isCompact) 24.dp else 48.dp),
        ) {
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
