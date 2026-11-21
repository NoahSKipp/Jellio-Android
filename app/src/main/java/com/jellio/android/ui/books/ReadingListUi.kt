package com.jellio.android.ui.books

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.jellio.android.data.books.READING_KIND_AUDIOBOOK
import com.jellio.android.data.books.READING_KIND_MANGA
import com.jellio.android.data.books.readingKindLabelKey
import com.jellio.android.data.i18n.Strings
import com.jellio.android.ui.home.AddedByBadge
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary

// Shows the toasts the reading list asks for. Placed once at the app's root,
// so a message is never shown twice.
@Composable
fun ReadingListToasts(viewModel: ReadingListViewModel) {
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { key ->
            Toast.makeText(context, Strings.get(key), Toast.LENGTH_SHORT).show()
        }
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    READING_KIND_AUDIOBOOK -> Icons.Filled.Headphones
    READING_KIND_MANGA -> Icons.Filled.AutoStories
    else -> Icons.Filled.MenuBook
}

// A horizontal row of the reading list ("Reading" or "Listening") under the
// continue row of the Reader tab.
@Composable
internal fun ReadingListSection(
    title: String,
    cards: List<ReadingCardModel>,
    userImageUrl: ((String, String?) -> String)?,
    onClick: (ReadingCardModel) -> Unit,
    onLongClick: (ReadingCardModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = JellioSecondary, modifier = Modifier.size(16.dp))
            Text(text = title, color = JellioText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards, key = { it.key }, contentType = { "reading_card" }) { card ->
                ReadingListCard(
                    card = card,
                    userImageUrl = userImageUrl,
                    onClick = { onClick(card) },
                    onLongClick = { onLongClick(card) },
                )
            }
        }
    }
}

@Composable
private fun ReadingListCard(
    card: ReadingCardModel,
    userImageUrl: ((String, String?) -> String)?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, JellioBorder),
        modifier = Modifier
            .width(120.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.68f)
                    .background(JellioBg),
            ) {
                val context = LocalContext.current
                var coverFailed by remember(card.coverUrl) { mutableStateOf(false) }
                if (!card.coverUrl.isNullOrBlank() && !coverFailed) {
                    val request = remember(card.coverUrl) {
                        ImageRequest.Builder(context)
                            .data(card.coverUrl)
                            .size(360, 540)
                            .build()
                    }
                    AsyncImage(
                        model = request,
                        contentDescription = card.title,
                        contentScale = ContentScale.Crop,
                        onError = { coverFailed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(kindIcon(card.kind), contentDescription = null, tint = JellioTextTertiary, modifier = Modifier.size(32.dp))
                    }
                }
                if (card.addedBy.isNotEmpty()) {
                    AddedByBadge(
                        people = card.addedBy,
                        userImageUrl = userImageUrl,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(JellioBg.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = Strings.get(readingKindLabelKey(card.kind)).uppercase(),
                        color = JellioSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = card.title,
                    color = JellioText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!card.author.isNullOrBlank()) {
                    Text(
                        text = card.author,
                        color = JellioTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
