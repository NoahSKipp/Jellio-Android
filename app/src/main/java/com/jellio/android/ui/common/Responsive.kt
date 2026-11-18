package com.jellio.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

enum class WindowLayoutMode {
    COMPACT_PORTRAIT, // Phone portrait (width < 600dp and height >= width)
    COMPACT_LANDSCAPE, // Phone landscape (height < 500dp)
    TABLET_OR_WIDE, // Tablet / foldable / wide landscape (>= 600dp width and >= 500dp height)
}

@Composable
fun rememberWindowLayoutMode(): WindowLayoutMode {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    return when {
        widthDp < 600 -> WindowLayoutMode.COMPACT_PORTRAIT
        heightDp < 500 -> WindowLayoutMode.COMPACT_LANDSCAPE
        else -> WindowLayoutMode.TABLET_OR_WIDE
    }
}

@Composable
fun isCompactScreen(): Boolean = rememberWindowLayoutMode() != WindowLayoutMode.TABLET_OR_WIDE
