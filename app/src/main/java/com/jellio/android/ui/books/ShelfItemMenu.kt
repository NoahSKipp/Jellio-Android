package com.jellio.android.ui.books

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellio.android.data.i18n.Strings
import com.jellio.android.ui.home.CardDialog
import com.jellio.android.ui.home.CardOptionsMenuRow
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary

private data class ShelfMenuAction(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

// The long-press menu on shelf cards, in the same dialog the home rows use.
// An action whose callback is null is not offered for this card.
@Composable
internal fun ShelfItemMenu(
    target: ShelfMenuTarget,
    finished: Boolean?,
    onMarkRead: ((Boolean) -> Unit)?,
    onOpenSeries: (() -> Unit)?,
    onCategories: (() -> Unit)?,
    onRemoveContinue: (() -> Unit)?,
    onRemoveLibrary: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val actions = buildList {
        if (finished != null && onMarkRead != null) {
            add(
                if (finished) {
                    ShelfMenuAction(Strings.get("reader_menu_mark_unread")) { onMarkRead(false) }
                } else {
                    ShelfMenuAction(Strings.get("reader_menu_mark_read")) { onMarkRead(true) }
                },
            )
        }
        onOpenSeries?.let { add(ShelfMenuAction(Strings.get("menu_go_to_series"), onClick = it)) }
        onCategories?.let { add(ShelfMenuAction(Strings.get("reader_menu_categories"), onClick = it)) }
        onRemoveContinue?.let {
            val label = if (target.tab == BookshelfTab.AUDIOBOOK) "reader_menu_remove_continue_listening" else "reader_menu_remove_continue_reading"
            add(ShelfMenuAction(Strings.get(label), danger = true, onClick = it))
        }
        onRemoveLibrary?.let { add(ShelfMenuAction(Strings.get("reader_menu_remove_library"), danger = true, onClick = it)) }
    }
    if (actions.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
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
                text = target.title,
                color = JellioText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            )
            actions.forEachIndexed { index, action ->
                CardOptionsMenuRow(
                    label = action.label,
                    danger = action.danger,
                    onClick = {
                        action.onClick()
                        onDismiss()
                    },
                    modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
        }
    }
}

@Composable
internal fun ConfirmRemoveFromLibraryDialog(
    target: ShelfMenuTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val body = if (target.tab == BookshelfTab.MANGA) "reader_remove_confirm_manga" else "reader_remove_confirm_book"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        titleContentColor = JellioText,
        textContentColor = JellioTextSecondary,
        title = { Text(Strings.get("reader_menu_remove_library"), fontWeight = FontWeight.Bold) },
        text = { Text(Strings.format(body, target.title)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = JellioDanger, contentColor = JellioBg),
            ) {
                Text(Strings.get("reader_menu_remove"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(Strings.get("action_cancel"), color = JellioSecondary)
            }
        },
    )
}
