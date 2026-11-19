package com.jellio.android.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.jellio.android.ui.theme.scaled
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.State
import androidx.compose.ui.unit.dp

@Composable
private fun skeletonPulse(): State<Float> = rememberInfiniteTransition(label = "skeleton").animateFloat(
    initialValue = 0.05f,
    targetValue = 0.11f,
    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
    label = "skeletonAlpha",
)

private fun Modifier.skeletonFill(pulse: State<Float>, radius: Dp): Modifier = drawBehind {
    drawRoundRect(Color.White.copy(alpha = pulse.value), cornerRadius = CornerRadius(radius.toPx()))
}

// One poster-shaped placeholder, for grids still filling in (search).
@Composable
fun SkeletonPoster(modifier: Modifier = Modifier) {
    val pulse = skeletonPulse()
    Row(modifier = modifier.aspectRatio(2f / 3f).skeletonFill(pulse, 12.dp)) {}
}

@Composable
fun SkeletonRow(landscape: Boolean = false, modifier: Modifier = Modifier) {
    val pulse = skeletonPulse()
    val startPad = responsiveRowStartPadding()
    Column(modifier = modifier.fillMaxWidth().clipToBounds().padding(start = startPad, bottom = RowSpacing)) {
        Row(
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp).width(150.dp).height(16.dp).skeletonFill(pulse, 6.dp),
        ) {}
        // Laid out at full size and cut off at the screen edge, never squeezed.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState(), enabled = false),
        ) {
            repeat(if (landscape) 4 else 8) {
                val cardWidth = if (landscape) 220.dp else PosterWidth.scaled()
                Row(
                    modifier = Modifier
                        .width(cardWidth)
                        .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                        .skeletonFill(pulse, 12.dp),
                ) {}
            }
        }
    }
}

// The whole Home while its first rows load: matches the mobile portrait hero + rows
// or the wide/TV top banner + rows so placeholders sit where real items appear.
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val pulse = skeletonPulse()
    val isCompactPortrait = com.jellio.android.ui.common.rememberWindowLayoutMode() ==
        com.jellio.android.ui.common.WindowLayoutMode.COMPACT_PORTRAIT
    Column(modifier = modifier.fillMaxSize().clipToBounds()) {
        if (isCompactPortrait) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .skeletonFill(pulse, 0.dp),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    Row(modifier = Modifier.width(220.dp).height(26.dp).skeletonFill(pulse, 8.dp)) {}
                    Row(modifier = Modifier.width(180.dp).height(14.dp).skeletonFill(pulse, 6.dp)) {}
                    Row(modifier = Modifier.fillMaxWidth(0.88f).height(14.dp).skeletonFill(pulse, 6.dp)) {}
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.width(104.dp).height(38.dp).skeletonFill(pulse, 20.dp)) {}
                        Row(modifier = Modifier.width(104.dp).height(38.dp).skeletonFill(pulse, 20.dp)) {}
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            SkeletonRow(landscape = false)
            SkeletonRow(landscape = false)
        } else {
            Row(
                modifier = Modifier
                    .padding(start = 48.dp, end = 48.dp, top = 28.dp)
                    .fillMaxWidth()
                    .height(200.dp)
                    .skeletonFill(pulse, 16.dp),
            ) {}
            SkeletonRow(landscape = true)
            SkeletonRow()
        }
    }
}
