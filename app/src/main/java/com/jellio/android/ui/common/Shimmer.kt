package com.jellio.android.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// A soft highlight sweeping across a placeholder. The animated value is only
// read while drawing, so the skeleton is not recomposed every frame.
fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerProgress",
    )
    val base = Color.White.copy(alpha = 0.06f)
    val highlight = Color.White.copy(alpha = 0.15f)
    drawBehind {
        val width = size.width.coerceAtLeast(1f)
        val startX = -width + progress * width * 2f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(base, highlight, base),
                startX = startX,
                endX = startX + width,
            ),
        )
    }
}
