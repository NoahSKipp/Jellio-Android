package com.jellio.android.ui.books

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jellio.android.data.books.BooksApi
import com.jellio.android.data.books.BooksRepository
import com.jellio.android.data.books.CreateVocabRequest
import com.jellio.android.data.books.VocabEntryDto
import com.jellio.android.ui.theme.JellioBg
import com.jellio.android.ui.theme.JellioBgElevated
import com.jellio.android.ui.theme.JellioBorder
import com.jellio.android.ui.theme.JellioDanger
import com.jellio.android.ui.theme.JellioSecondary
import com.jellio.android.ui.theme.JellioText
import com.jellio.android.ui.theme.JellioTextSecondary
import com.jellio.android.ui.theme.JellioTextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class VocabUiState(
    val isLoading: Boolean = true,
    val cards: List<VocabEntryDto> = emptyList(),
    val searchQuery: String = "",
    val inReviewMode: Boolean = false,
    val reviewQueue: List<VocabEntryDto> = emptyList(),
    val reviewIndex: Int = 0,
    val isCardFlipped: Boolean = false,
    val isSubmittingReview: Boolean = false,
    val isExporting: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null,
) {
    val dueCards: List<VocabEntryDto>
        get() {
            val nowMs = System.currentTimeMillis()
            return cards.filter { isVocabDue(it, nowMs) }
        }
}

private fun isVocabDue(entry: VocabEntryDto, nowMs: Long): Boolean {
    val dueStr = entry.DueAt
    if (dueStr.isNullOrBlank()) return true
    val parsed = parseIsoTimestampMs(dueStr) ?: return true
    return parsed <= nowMs
}

private fun parseIsoTimestampMs(raw: String): Long? {
    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd",
    )
    for (pattern in formats) {
        val parsed = runCatching {
            SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }.parse(raw)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

@HiltViewModel
class VocabViewModel @Inject constructor(
    private val booksRepository: BooksRepository,
    private val booksApi: BooksApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabUiState())
    val uiState: StateFlow<VocabUiState> = _uiState.asStateFlow()

    init {
        loadVocabulary()
    }

    fun loadVocabulary() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            booksRepository.getVocabulary()
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoading = false, cards = list) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, error = err.message ?: "Failed to load vocabulary deck")
                    }
                }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun startReview(includeAllIfNoneDue: Boolean = false) {
        val state = _uiState.value
        val queue = state.dueCards.ifEmpty {
            if (includeAllIfNoneDue) state.cards else emptyList()
        }
        if (queue.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No cards due for review right now.") }
            return
        }
        _uiState.update {
            it.copy(
                inReviewMode = true,
                reviewQueue = queue,
                reviewIndex = 0,
                isCardFlipped = false,
            )
        }
    }

    fun exitReview() {
        _uiState.update { it.copy(inReviewMode = false, isCardFlipped = false) }
    }

    fun flipCard() {
        _uiState.update { it.copy(isCardFlipped = !it.isCardFlipped) }
    }

    fun gradeCurrentCard(grade: Int) {
        val state = _uiState.value
        val card = state.reviewQueue.getOrNull(state.reviewIndex) ?: return
        _uiState.update { it.copy(isSubmittingReview = true) }
        viewModelScope.launch {
            booksRepository.reviewVocabulary(card.Id, grade)
                .onSuccess { updated ->
                    _uiState.update { cur ->
                        val nextCards = cur.cards.map { if (it.Id == updated.Id) updated else it }
                        val nextQueue = if (grade == 0) {
                            // Re-queue "Again" at the end of this review session
                            cur.reviewQueue + updated
                        } else {
                            cur.reviewQueue
                        }
                        val nextIdx = cur.reviewIndex + 1
                        val finished = nextIdx >= nextQueue.size
                        cur.copy(
                            cards = nextCards,
                            reviewQueue = nextQueue,
                            reviewIndex = nextIdx,
                            isCardFlipped = false,
                            isSubmittingReview = false,
                            inReviewMode = !finished,
                            statusMessage = if (finished) "Review session complete!" else null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isSubmittingReview = false, statusMessage = err.message ?: "Review failed")
                    }
                }
        }
    }

    fun addCard(word: String, sentence: String?, definition: String?, translation: String?) {
        val cleanWord = word.trim()
        if (cleanWord.isEmpty()) return
        viewModelScope.launch {
            booksRepository.addVocabulary(
                CreateVocabRequest(
                    Word = cleanWord,
                    Sentence = sentence?.trim()?.ifBlank { null },
                    Definition = definition?.trim()?.ifBlank { null },
                    Translation = translation?.trim()?.ifBlank { null },
                ),
            )
                .onSuccess { created ->
                    _uiState.update {
                        it.copy(cards = listOf(created) + it.cards, statusMessage = "Added \"$cleanWord\"")
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Failed to add card") }
                }
        }
    }

    fun updateCard(id: String, sentence: String?, definition: String?, translation: String?) {
        viewModelScope.launch {
            booksRepository.updateVocabulary(
                id = id,
                translation = translation?.trim()?.ifBlank { null },
                definition = definition?.trim()?.ifBlank { null },
                sentence = sentence?.trim()?.ifBlank { null },
            )
                .onSuccess { updated ->
                    _uiState.update { cur ->
                        cur.copy(
                            cards = cur.cards.map { if (it.Id == updated.Id) updated else it },
                            statusMessage = "Card updated",
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(statusMessage = err.message ?: "Update failed") }
                }
        }
    }

    fun deleteCard(id: String) {
        viewModelScope.launch {
            booksRepository.deleteVocabulary(id)
                .onSuccess {
                    _uiState.update { cur ->
                        cur.copy(
                            cards = cur.cards.filterNot { it.Id == id },
                            statusMessage = "Card deleted",
                        )
                    }
                }
        }
    }

    fun exportAndShare(context: Context, format: String) {
        _uiState.update { it.copy(isExporting = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    booksApi.exportVocabulary(format).string()
                }
            }
                .onSuccess { exportedText ->
                    _uiState.update { it.copy(isExporting = false) }
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Jellio Vocabulary Export ($format)")
                        putExtra(Intent.EXTRA_TEXT, exportedText)
                    }
                    val chooser = Intent.createChooser(sendIntent, "Export Vocabulary ($format)")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isExporting = false, statusMessage = err.message ?: "Export failed")
                    }
                }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

@Composable
fun VocabScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VocabViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<VocabEntryDto?>(null) }
    var exportMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JellioBg),
    ) {
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (state.inReviewMode) viewModel.exitReview() else onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = JellioText)
                }
                Text(
                    text = if (state.inReviewMode) "Review Session" else "Vocabulary SRS Deck",
                    color = JellioText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (!state.inReviewMode) {
                    Box {
                        IconButton(onClick = { exportMenuOpen = true }, enabled = !state.isExporting) {
                            Icon(Icons.Filled.Share, contentDescription = "Export", tint = JellioText)
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false },
                            modifier = Modifier.background(JellioBgElevated),
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export CSV", color = JellioText) },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.exportAndShare(context, "csv")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export Anki (TSV)", color = JellioText) },
                                onClick = {
                                    exportMenuOpen = false
                                    viewModel.exportAndShare(context, "anki")
                                },
                            )
                        }
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Card", tint = JellioSecondary)
                    }
                    IconButton(onClick = viewModel::loadVocabulary) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = JellioTextSecondary)
                    }
                }
            }

            state.statusMessage?.let { msg ->
                Surface(
                    color = JellioBgElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, JellioBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(msg, color = JellioText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearStatusMessage, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = JellioTextSecondary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            when {
                state.isLoading && state.cards.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = JellioSecondary)
                    }
                }

                state.error != null && state.cards.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(state.error.orEmpty(), color = JellioDanger, fontSize = 14.sp)
                            Button(
                                onClick = viewModel::loadVocabulary,
                                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                state.inReviewMode -> {
                    ReviewSessionView(
                        state = state,
                        onFlip = viewModel::flipCard,
                        onGrade = viewModel::gradeCurrentCard,
                    )
                }

                else -> {
                    DeckBrowserView(
                        state = state,
                        onSearchChange = viewModel::setSearchQuery,
                        onStartDueReview = { viewModel.startReview(includeAllIfNoneDue = false) },
                        onPracticeAll = { viewModel.startReview(includeAllIfNoneDue = true) },
                        onEditCard = { editingCard = it },
                        onDeleteCard = viewModel::deleteCard,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        VocabCardEditorDialog(
            title = "Add Vocabulary Card",
            wordEditable = true,
            initialWord = "",
            initialSentence = "",
            initialDefinition = "",
            initialTranslation = "",
            onDismiss = { showAddDialog = false },
            onSave = { w, s, d, t ->
                viewModel.addCard(w, s, d, t)
                showAddDialog = false
            },
        )
    }

    editingCard?.let { card ->
        VocabCardEditorDialog(
            title = "Edit \"${card.Word}\"",
            wordEditable = false,
            initialWord = card.Word,
            initialSentence = card.Sentence.orEmpty(),
            initialDefinition = card.Definition.orEmpty(),
            initialTranslation = card.Translation.orEmpty(),
            onDismiss = { editingCard = null },
            onSave = { _, s, d, t ->
                viewModel.updateCard(card.Id, s, d, t)
                editingCard = null
            },
        )
    }
}

@Composable
private fun DeckBrowserView(
    state: VocabUiState,
    onSearchChange: (String) -> Unit,
    onStartDueReview: () -> Unit,
    onPracticeAll: () -> Unit,
    onEditCard: (VocabEntryDto) -> Unit,
    onDeleteCard: (String) -> Unit,
) {
    val dueCount = state.dueCards.size
    val totalCount = state.cards.size
    val query = state.searchQuery.trim().lowercase()
    val filtered = remember(state.cards, query) {
        if (query.isEmpty()) state.cards
        else state.cards.filter {
            it.Word.lowercase().contains(query) ||
                it.Translation.orEmpty().lowercase().contains(query) ||
                it.Definition.orEmpty().lowercase().contains(query) ||
                it.Sentence.orEmpty().lowercase().contains(query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stats + Start Review banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StatPill(label = "Due Today", value = dueCount.toString(), highlight = dueCount > 0, modifier = Modifier.weight(1f))
            StatPill(label = "Total Cards", value = totalCount.toString(), highlight = false, modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onStartDueReview,
                enabled = dueCount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = JellioSecondary,
                    contentColor = JellioBg,
                    disabledContainerColor = JellioBorder,
                    disabledContentColor = JellioTextSecondary,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Start Review ($dueCount)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            if (dueCount == 0 && totalCount > 0) {
                OutlinedButton(
                    onClick = onPracticeAll,
                    border = BorderStroke(1.dp, JellioBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = JellioText),
                ) {
                    Text("Practice All", fontSize = 12.sp)
                }
            }
        }

        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Filter cards...", color = JellioTextTertiary, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = JellioTextSecondary) },
            singleLine = true,
            colors = jellioTextFieldColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (totalCount == 0) "Your Vocabulary deck is empty. Save words while reading!" else "No matching cards.",
                    color = JellioTextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.Id }) { card ->
                    Surface(
                        color = JellioBgElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, JellioBorder),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(card.Word, color = JellioText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                val meaning = card.Translation ?: card.Definition
                                if (!meaning.isNullOrBlank()) {
                                    Text(meaning, color = JellioSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                card.Sentence?.takeIf { it.isNotBlank() }?.let { sent ->
                                    Text(sent, color = JellioTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                val meta = buildList {
                                    card.ItemName?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    add("Reps: ${card.Reps}")
                                    add("Interval: ${String.format("%.1f", card.IntervalDays)}d")
                                }.joinToString(" | ")
                                Text(meta, color = JellioTextTertiary, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onEditCard(card) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = JellioTextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDeleteCard(card.Id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = JellioDanger, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: String, highlight: Boolean, modifier: Modifier = Modifier) {
    Surface(
        color = JellioBgElevated,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (highlight) JellioSecondary else JellioBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, color = if (highlight) JellioSecondary else JellioText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(label, color = JellioTextSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ReviewSessionView(
    state: VocabUiState,
    onFlip: () -> Unit,
    onGrade: (Int) -> Unit,
) {
    val card = state.reviewQueue.getOrNull(state.reviewIndex) ?: return
    val total = state.reviewQueue.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Card ${state.reviewIndex + 1} of $total",
            color = JellioTextSecondary,
            fontSize = 13.sp,
        )

        Surface(
            onClick = onFlip,
            color = JellioBgElevated,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, JellioBorder),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = card.Word,
                    color = JellioText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                card.Sentence?.takeIf { it.isNotBlank() }?.let { sentence ->
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = sentence,
                        color = JellioTextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                if (state.isCardFlipped) {
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = JellioBorder)
                    Spacer(Modifier.height(20.dp))
                    card.Translation?.takeIf { it.isNotBlank() }?.let { trans ->
                        Text(
                            text = trans,
                            color = JellioSecondary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    card.Definition?.takeIf { it.isNotBlank() }?.let { def ->
                        Text(
                            text = def,
                            color = JellioText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    card.ItemName?.takeIf { it.isNotBlank() }?.let { src ->
                        Text(
                            text = "Source: $src",
                            color = JellioTextTertiary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Flip, contentDescription = null, tint = JellioTextTertiary, modifier = Modifier.size(16.dp))
                        Text("Tap card to reveal answer", color = JellioTextTertiary, fontSize = 13.sp)
                    }
                }
            }
        }

        if (!state.isCardFlipped) {
            Button(
                onClick = onFlip,
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text("Show Answer", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GradeButton("Again", 0, JellioDanger, !state.isSubmittingReview, { onGrade(0) }, Modifier.weight(1f))
                GradeButton("Hard", 1, Color(0xFFF59E0B), !state.isSubmittingReview, { onGrade(1) }, Modifier.weight(1f))
                GradeButton("Good", 2, Color(0xFF4ADE80), !state.isSubmittingReview, { onGrade(2) }, Modifier.weight(1f))
                GradeButton("Easy", 3, Color(0xFF60A5FA), !state.isSubmittingReview, { onGrade(3) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    grade: Int,
    accent: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(1.dp, accent),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
        contentPadding = PaddingValues(vertical = 12.dp),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("($grade)", fontSize = 10.sp, color = JellioTextSecondary)
        }
    }
}

@Composable
private fun VocabCardEditorDialog(
    title: String,
    wordEditable: Boolean,
    initialWord: String,
    initialSentence: String,
    initialDefinition: String,
    initialTranslation: String,
    onDismiss: () -> Unit,
    onSave: (word: String, sentence: String, definition: String, translation: String) -> Unit,
) {
    var word by remember { mutableStateOf(initialWord) }
    var sentence by remember { mutableStateOf(initialSentence) }
    var definition by remember { mutableStateOf(initialDefinition) }
    var translation by remember { mutableStateOf(initialTranslation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JellioBgElevated,
        titleContentColor = JellioText,
        textContentColor = JellioText,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wordEditable) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text("Word", color = JellioTextSecondary, fontSize = 12.sp) },
                        singleLine = true,
                        colors = jellioTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = { Text("Translation", color = JellioTextSecondary, fontSize = 12.sp) },
                    singleLine = true,
                    colors = jellioTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = definition,
                    onValueChange = { definition = it },
                    label = { Text("Definition", color = JellioTextSecondary, fontSize = 12.sp) },
                    colors = jellioTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sentence,
                    onValueChange = { sentence = it },
                    label = { Text("Context Sentence", color = JellioTextSecondary, fontSize = 12.sp) },
                    colors = jellioTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(word, sentence, definition, translation) },
                enabled = !wordEditable || word.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = JellioSecondary, contentColor = JellioBg),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JellioTextSecondary)
            }
        },
    )
}
