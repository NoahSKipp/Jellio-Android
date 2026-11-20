package com.jellio.android.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier

// For overlays anchored to the top edge: keeps them below the status bar and
// camera cutout and away from side cutouts, also while the bars are hidden.
fun Modifier.topOverlayInsets(): Modifier = windowInsetsPadding(
    WindowInsets.statusBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.End),
)

// For overlays anchored to a side or the bottom corner: clears side cutouts and bars.
fun Modifier.sideOverlayInsets(): Modifier = windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.End),
)
