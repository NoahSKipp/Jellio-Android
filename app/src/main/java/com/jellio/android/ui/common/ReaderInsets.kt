package com.jellio.android.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

// Reader surfaces keep clear of the status bar, navigation bar and the camera
// cutout in both orientations. The keyboard is left out on purpose, so a bar
// does not ride up with it.
@Composable
private fun readerInsets(): WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

// A top bar: under the status bar and cutout, and clear of side cutouts and
// a side navigation bar in landscape.
@Composable
fun Modifier.readerTopInsets(): Modifier =
    windowInsetsPadding(readerInsets().only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))

@Composable
fun Modifier.readerBottomInsets(): Modifier =
    windowInsetsPadding(readerInsets().only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))

@Composable
fun Modifier.readerHorizontalInsets(): Modifier =
    windowInsetsPadding(readerInsets().only(WindowInsetsSides.Horizontal))

@Composable
fun Modifier.readerAllInsets(): Modifier = windowInsetsPadding(readerInsets())

@Composable
fun readerTopInset(): Dp = readerInsets().asPaddingValues().calculateTopPadding()

@Composable
fun readerBottomInset(): Dp = readerInsets().asPaddingValues().calculateBottomPadding()
