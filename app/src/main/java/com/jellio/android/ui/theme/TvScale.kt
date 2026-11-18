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
    val screenHeightDp = config.screenHeightDp
    // Use the shorter edge or width appropriately so portrait phones don't shrink icons too harshly
    val effectiveWidth = if (screenWidthDp < 600) screenWidthDp * 1.45f else screenWidthDp.toFloat()
    val scale = effectiveWidth / ReferenceScreenWidthDp
    val minAllowed = if (screenHeightDp < 500) 0.65f else MinTvScale
    return scale.coerceIn(minAllowed, MaxTvScale)
}

@Composable
fun Dp.scaled(): Dp = this * LocalTvScale.current
