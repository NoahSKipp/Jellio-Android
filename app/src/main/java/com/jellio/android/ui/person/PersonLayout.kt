package com.jellio.android.ui.person

// Pure layout rules for the person page, kept free of Compose so they can be unit tested.

internal const val CompactWidthDp = 600
internal const val CompactCreditColumns = 3
internal const val CreditMinCellDp = 104
internal const val CreditBatchSize = 24
internal const val CreditPrefetchDistance = 9
internal const val BioCollapsedLines = 4
internal const val NameMaxLines = 3
internal const val NameBaseSp = 26f
internal const val NameMinSp = 15f
internal const val NameShrinkFactor = 0.9f

internal fun isCompactWidth(widthDp: Int): Boolean = widthDp < CompactWidthDp

// Phones get exactly three poster columns; wider screens fit as many cells of
// at least CreditMinCellDp as the usable width allows, never fewer than three.
internal fun creditGridColumns(widthDp: Int, horizontalPaddingDp: Int, spacingDp: Int): Int {
    if (isCompactWidth(widthDp)) return CompactCreditColumns
    val usable = widthDp - horizontalPaddingDp * 2
    val fit = (usable + spacingDp) / (CreditMinCellDp + spacingDp)
    return maxOf(CompactCreditColumns, fit)
}

// The toggle has to survive expansion: once expanded the text no longer
// overflows, so the answer measured while collapsed is remembered.
internal fun bioOverflowMemo(expanded: Boolean, previous: Boolean, hasVisualOverflow: Boolean): Boolean =
    if (expanded) previous else hasVisualOverflow

// How many credits to show: the first batch, then one more batch each time the
// grid scrolls near the end of what is shown.
internal fun initialCreditCount(total: Int, batch: Int = CreditBatchSize): Int = minOf(total, batch)

internal fun nextCreditCount(shown: Int, total: Int, batch: Int = CreditBatchSize): Int =
    minOf(total, shown + batch)

// lastVisibleIndex is the grid item index; leadingItems is how many non-credit
// items (header, Known For, section title, filter row) come before the first credit.
internal fun shouldLoadMoreCredits(
    lastVisibleIndex: Int,
    leadingItems: Int,
    shown: Int,
    total: Int,
    prefetch: Int = CreditPrefetchDistance,
): Boolean {
    if (shown >= total) return false
    val lastCredit = lastVisibleIndex - leadingItems
    return lastCredit >= shown - 1 - prefetch
}

// True when a wrapped text broke inside a word. lineEnds are exclusive end
// offsets of every line in order; a break after whitespace or a hyphen is fine.
internal fun breaksMidWord(text: String, lineEnds: List<Int>): Boolean {
    for (i in 0 until lineEnds.size - 1) {
        val end = lineEnds[i]
        if (end <= 0 || end >= text.length) continue
        val before = text[end - 1]
        val after = text[end]
        if (!before.isWhitespace() && !after.isWhitespace() && before != '-' && before != '­') return true
    }
    return false
}

// Next font size for the name, or null when it is already at the floor.
internal fun shrunkNameSize(currentSp: Float): Float? {
    if (currentSp <= NameMinSp) return null
    return maxOf(NameMinSp, currentSp * NameShrinkFactor)
}

// Starting guess so very long single words rarely need several relayouts.
internal fun initialNameSize(text: String, availableWidthDp: Float, baseSp: Float = NameBaseSp): Float {
    val longest = text.split(Regex("\\s+")).maxOfOrNull { it.length } ?: return baseSp
    if (longest == 0 || availableWidthDp <= 0f) return baseSp
    val fitting = availableWidthDp / (longest * 0.62f)
    return fitting.coerceIn(NameMinSp, baseSp)
}
