package com.jellio.android.data

import com.jellio.android.data.model.BaseItemDto

// Past this share of its runtime an episode counts as finished.
private const val FINISHED_FRACTION = 0.9

// A saved position clearly short of the end: something the person is in the
// middle of, whatever the played flag says (a rewatch, or an episode marked
// played at about 90% that was then picked up again).
fun isPartlyWatched(item: BaseItemDto): Boolean {
    val position = item.UserData?.PlaybackPositionTicks ?: 0L
    if (position <= 0L) return false
    val runtime = item.RunTimeTicks ?: 0L
    return runtime <= 0L || position < runtime * FINISHED_FRACTION
}

// Marked played and not being resumed partway.
fun isFinished(item: BaseItemDto): Boolean = item.UserData?.Played == true && !isPartlyWatched(item)
