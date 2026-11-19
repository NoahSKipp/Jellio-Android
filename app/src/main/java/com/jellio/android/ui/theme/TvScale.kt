package com.jellio.android.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp

private val ReferenceScreenWidthDp = 960

private val MinTvScale = 0.68f
private val MaxTvScale = 1.25f

val LocalTvScale = compositionLocalOf { 1f }

@Composable
fun rememberTvScale(): Float {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp
    if (screenWidthDp < 600) return 0.92f
    val screenHeightDp = config.screenHeightDp
    val scale = screenWidthDp.toFloat() / ReferenceScreenWidthDp
    val minAllowed = if (screenHeightDp < 500) 0.65f else MinTvScale
    return scale.coerceIn(minAllowed, MaxTvScale)
}

@Composable
fun Dp.scaled(): Dp = this * LocalTvScale.current
