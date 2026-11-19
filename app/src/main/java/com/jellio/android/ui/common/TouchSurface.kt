package com.jellio.android.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.selected as semanticsSelected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.contentColorFor as tvContentColorFor

/**
 * Touch- and D-pad-compatible drop-in replacement for androidx.tv.material3.Surface
 * (clickable and selectable overloads).
 *
 * androidx.tv:tv-material clickable/selectable Surface uses TV-only modifiers that ignore
 * touch taps and long-presses. This implementation uses standard Compose Foundation
 * combinedClickable so touch taps, long-presses, D-pad/keyboard focus, and Enter/Center
 * presses all work seamlessly.
 */

@Immutable
data class ClickableSurfaceColors(
    val containerColor: Color,
    val contentColor: Color,
    val focusedContainerColor: Color,
    val focusedContentColor: Color,
    val pressedContainerColor: Color,
    val pressedContentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
)

@Immutable
data class ClickableSurfaceShape(
    val shape: Shape,
    val focusedShape: Shape,
    val pressedShape: Shape,
    val disabledShape: Shape,
    val focusedDisabledShape: Shape,
)

@Immutable
data class ClickableSurfaceScale(
    val scale: Float,
    val focusedScale: Float,
    val pressedScale: Float,
    val disabledScale: Float,
    val focusedDisabledScale: Float,
)

@Immutable
data class ClickableSurfaceBorder(
    val border: Border,
    val focusedBorder: Border,
    val pressedBorder: Border,
    val disabledBorder: Border,
    val focusedDisabledBorder: Border,
)

@Immutable
data class ClickableSurfaceGlow(
    val glow: Glow,
    val focusedGlow: Glow,
    val pressedGlow: Glow,
)

object ClickableSurfaceDefaults {
    @Composable
    fun colors(
        containerColor: Color = TvMaterialTheme.colorScheme.surface,
        contentColor: Color = tvContentColorFor(containerColor).takeOrElse { TvLocalContentColor.current },
        focusedContainerColor: Color = TvMaterialTheme.colorScheme.inverseSurface,
        focusedContentColor: Color = tvContentColorFor(focusedContainerColor).takeOrElse { contentColor },
        pressedContainerColor: Color = focusedContainerColor,
        pressedContentColor: Color = focusedContentColor,
        disabledContainerColor: Color = containerColor.copy(alpha = 0.4f),
        disabledContentColor: Color = contentColor.copy(alpha = 0.4f),
    ): ClickableSurfaceColors = ClickableSurfaceColors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = focusedContainerColor,
        focusedContentColor = focusedContentColor,
        pressedContainerColor = pressedContainerColor,
        pressedContentColor = pressedContentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
    )

    @Composable
    fun shape(
        shape: Shape = RoundedCornerShape(8.dp),
        focusedShape: Shape = shape,
        pressedShape: Shape = shape,
        disabledShape: Shape = shape,
        focusedDisabledShape: Shape = disabledShape,
    ): ClickableSurfaceShape = ClickableSurfaceShape(
        shape = shape,
        focusedShape = focusedShape,
        pressedShape = pressedShape,
        disabledShape = disabledShape,
        focusedDisabledShape = focusedDisabledShape,
    )

    fun scale(
        scale: Float = 1f,
        focusedScale: Float = 1.1f,
        pressedScale: Float = scale,
        disabledScale: Float = scale,
        focusedDisabledScale: Float = disabledScale,
    ): ClickableSurfaceScale = ClickableSurfaceScale(
        scale = scale,
        focusedScale = focusedScale,
        pressedScale = pressedScale,
        disabledScale = disabledScale,
        focusedDisabledScale = focusedDisabledScale,
    )

    fun border(
        border: Border = Border.None,
        focusedBorder: Border = border,
        pressedBorder: Border = focusedBorder,
        disabledBorder: Border = border,
        focusedDisabledBorder: Border = disabledBorder,
    ): ClickableSurfaceBorder = ClickableSurfaceBorder(
        border = border,
        focusedBorder = focusedBorder,
        pressedBorder = pressedBorder,
        disabledBorder = disabledBorder,
        focusedDisabledBorder = focusedDisabledBorder,
    )

    fun glow(
        glow: Glow = Glow.None,
        focusedGlow: Glow = glow,
        pressedGlow: Glow = glow,
    ): ClickableSurfaceGlow = ClickableSurfaceGlow(
        glow = glow,
        focusedGlow = focusedGlow,
        pressedGlow = pressedGlow,
    )
}

@Immutable
data class SelectableSurfaceColors(
    val containerColor: Color,
    val contentColor: Color,
    val focusedContainerColor: Color,
    val focusedContentColor: Color,
    val pressedContainerColor: Color,
    val pressedContentColor: Color,
    val selectedContainerColor: Color,
    val selectedContentColor: Color,
    val disabledContainerColor: Color,
    val disabledContentColor: Color,
    val focusedSelectedContainerColor: Color,
    val focusedSelectedContentColor: Color,
    val pressedSelectedContainerColor: Color,
    val pressedSelectedContentColor: Color,
)

@Immutable
data class SelectableSurfaceShape(
    val shape: Shape,
    val focusedShape: Shape,
    val pressedShape: Shape,
    val selectedShape: Shape,
    val disabledShape: Shape,
    val focusedSelectedShape: Shape,
    val focusedDisabledShape: Shape,
    val pressedSelectedShape: Shape,
)

@Immutable
data class SelectableSurfaceScale(
    val scale: Float,
    val focusedScale: Float,
    val pressedScale: Float,
    val selectedScale: Float,
    val disabledScale: Float,
    val focusedSelectedScale: Float,
    val focusedDisabledScale: Float,
    val pressedSelectedScale: Float,
)

@Immutable
data class SelectableSurfaceBorder(
    val border: Border,
    val focusedBorder: Border,
    val pressedBorder: Border,
    val selectedBorder: Border,
    val disabledBorder: Border,
    val focusedSelectedBorder: Border,
    val focusedDisabledBorder: Border,
    val pressedSelectedBorder: Border,
)

@Immutable
data class SelectableSurfaceGlow(
    val glow: Glow,
    val focusedGlow: Glow,
    val pressedGlow: Glow,
    val selectedGlow: Glow,
    val focusedSelectedGlow: Glow,
    val pressedSelectedGlow: Glow,
)

object SelectableSurfaceDefaults {
    @Composable
    fun colors(
        containerColor: Color = TvMaterialTheme.colorScheme.surface,
        contentColor: Color = tvContentColorFor(containerColor).takeOrElse { TvLocalContentColor.current },
        focusedContainerColor: Color = TvMaterialTheme.colorScheme.inverseSurface,
        focusedContentColor: Color = tvContentColorFor(focusedContainerColor).takeOrElse { contentColor },
        pressedContainerColor: Color = focusedContainerColor,
        pressedContentColor: Color = focusedContentColor,
        selectedContainerColor: Color = TvMaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.5f),
        selectedContentColor: Color = TvMaterialTheme.colorScheme.inverseOnSurface,
        disabledContainerColor: Color = containerColor.copy(alpha = 0.4f),
        disabledContentColor: Color = contentColor.copy(alpha = 0.4f),
        focusedSelectedContainerColor: Color = focusedContainerColor,
        focusedSelectedContentColor: Color = focusedContentColor,
        pressedSelectedContainerColor: Color = pressedContainerColor,
        pressedSelectedContentColor: Color = pressedContentColor,
    ): SelectableSurfaceColors = SelectableSurfaceColors(
        containerColor = containerColor,
        contentColor = contentColor,
        focusedContainerColor = focusedContainerColor,
        focusedContentColor = focusedContentColor,
        pressedContainerColor = pressedContainerColor,
        pressedContentColor = pressedContentColor,
        selectedContainerColor = selectedContainerColor,
        selectedContentColor = selectedContentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor = disabledContentColor,
        focusedSelectedContainerColor = focusedSelectedContainerColor,
        focusedSelectedContentColor = focusedSelectedContentColor,
        pressedSelectedContainerColor = pressedSelectedContainerColor,
        pressedSelectedContentColor = pressedSelectedContentColor,
    )

    @Composable
    fun shape(
        shape: Shape = RoundedCornerShape(8.dp),
        focusedShape: Shape = shape,
        pressedShape: Shape = shape,
        selectedShape: Shape = shape,
        disabledShape: Shape = shape,
        focusedSelectedShape: Shape = shape,
        focusedDisabledShape: Shape = disabledShape,
        pressedSelectedShape: Shape = shape,
    ): SelectableSurfaceShape = SelectableSurfaceShape(
        shape = shape,
        focusedShape = focusedShape,
        pressedShape = pressedShape,
        selectedShape = selectedShape,
        disabledShape = disabledShape,
        focusedSelectedShape = focusedSelectedShape,
        focusedDisabledShape = focusedDisabledShape,
        pressedSelectedShape = pressedSelectedShape,
    )

    fun scale(
        scale: Float = 1f,
        focusedScale: Float = 1.1f,
        pressedScale: Float = scale,
        selectedScale: Float = scale,
        disabledScale: Float = scale,
        focusedSelectedScale: Float = focusedScale,
        focusedDisabledScale: Float = disabledScale,
        pressedSelectedScale: Float = scale,
    ): SelectableSurfaceScale = SelectableSurfaceScale(
        scale = scale,
        focusedScale = focusedScale,
        pressedScale = pressedScale,
        selectedScale = selectedScale,
        disabledScale = disabledScale,
        focusedSelectedScale = focusedSelectedScale,
        focusedDisabledScale = focusedDisabledScale,
        pressedSelectedScale = pressedSelectedScale,
    )

    fun border(
        border: Border = Border.None,
        focusedBorder: Border = border,
        pressedBorder: Border = focusedBorder,
        selectedBorder: Border = border,
        disabledBorder: Border = border,
        focusedSelectedBorder: Border = focusedBorder,
        focusedDisabledBorder: Border = disabledBorder,
        pressedSelectedBorder: Border = border,
    ): SelectableSurfaceBorder = SelectableSurfaceBorder(
        border = border,
        focusedBorder = focusedBorder,
        pressedBorder = pressedBorder,
        selectedBorder = selectedBorder,
        disabledBorder = disabledBorder,
        focusedSelectedBorder = focusedSelectedBorder,
        focusedDisabledBorder = focusedDisabledBorder,
        pressedSelectedBorder = pressedSelectedBorder,
    )

    fun glow(
        glow: Glow = Glow.None,
        focusedGlow: Glow = glow,
        pressedGlow: Glow = glow,
        selectedGlow: Glow = glow,
        focusedSelectedGlow: Glow = focusedGlow,
        pressedSelectedGlow: Glow = glow,
    ): SelectableSurfaceGlow = SelectableSurfaceGlow(
        glow = glow,
        focusedGlow = focusedGlow,
        pressedGlow = pressedGlow,
        selectedGlow = selectedGlow,
        focusedSelectedGlow = focusedSelectedGlow,
        pressedSelectedGlow = pressedSelectedGlow,
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tonalElevation: Dp = 0.dp,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by resolvedInteractionSource.collectIsFocusedAsState()
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()

    val containerColor = when {
        !enabled -> colors.disabledContainerColor
        isPressed -> colors.pressedContainerColor
        isFocused -> colors.focusedContainerColor
        else -> colors.containerColor
    }
    val contentColor = when {
        !enabled -> colors.disabledContentColor
        isPressed -> colors.pressedContentColor
        isFocused -> colors.focusedContentColor
        else -> colors.contentColor
    }
    val currentShape = when {
        !enabled && isFocused -> shape.focusedDisabledShape
        !enabled -> shape.disabledShape
        isPressed -> shape.pressedShape
        isFocused -> shape.focusedShape
        else -> shape.shape
    }
    val currentBorder = when {
        !enabled && isFocused -> border.focusedDisabledBorder
        !enabled -> border.disabledBorder
        isPressed -> border.pressedBorder
        isFocused -> border.focusedBorder
        else -> border.border
    }
    val targetScale = when {
        !enabled && isFocused -> scale.focusedDisabledScale
        !enabled -> scale.disabledScale
        isPressed -> scale.pressedScale
        isFocused -> scale.focusedScale
        else -> scale.scale
    }
    val animatedScale by animateFloatAsState(targetValue = targetScale, label = "touchSurfaceScale")
    val rippleColor = if (contentColor != Color.Unspecified) contentColor else Color.White

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(currentShape)
            .background(color = containerColor, shape = currentShape)
            .applySurfaceBorder(currentBorder, currentShape)
            .combinedClickable(
                interactionSource = resolvedInteractionSource,
                indication = ripple(color = rippleColor),
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(
            TvLocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun Surface(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tonalElevation: Dp = 0.dp,
    shape: SelectableSurfaceShape = SelectableSurfaceDefaults.shape(),
    colors: SelectableSurfaceColors = SelectableSurfaceDefaults.colors(),
    scale: SelectableSurfaceScale = SelectableSurfaceDefaults.scale(),
    border: SelectableSurfaceBorder = SelectableSurfaceDefaults.border(),
    glow: SelectableSurfaceGlow = SelectableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isFocused by resolvedInteractionSource.collectIsFocusedAsState()
    val isPressed by resolvedInteractionSource.collectIsPressedAsState()

    val containerColor = when {
        !enabled -> colors.disabledContainerColor
        isPressed && selected -> colors.pressedSelectedContainerColor
        isPressed -> colors.pressedContainerColor
        isFocused && selected -> colors.focusedSelectedContainerColor
        isFocused -> colors.focusedContainerColor
        selected -> colors.selectedContainerColor
        else -> colors.containerColor
    }
    val contentColor = when {
        !enabled -> colors.disabledContentColor
        isPressed && selected -> colors.pressedSelectedContentColor
        isPressed -> colors.pressedContentColor
        isFocused && selected -> colors.focusedSelectedContentColor
        isFocused -> colors.focusedContentColor
        selected -> colors.selectedContentColor
        else -> colors.contentColor
    }
    val currentShape = when {
        !enabled && isFocused -> shape.focusedDisabledShape
        !enabled -> shape.disabledShape
        isPressed && selected -> shape.pressedSelectedShape
        isPressed -> shape.pressedShape
        isFocused && selected -> shape.focusedSelectedShape
        isFocused -> shape.focusedShape
        selected -> shape.selectedShape
        else -> shape.shape
    }
    val currentBorder = when {
        !enabled && isFocused -> border.focusedDisabledBorder
        !enabled -> border.disabledBorder
        isPressed && selected -> border.pressedSelectedBorder
        isPressed -> border.pressedBorder
        isFocused && selected -> border.focusedSelectedBorder
        isFocused -> border.focusedBorder
        selected -> border.selectedBorder
        else -> border.border
    }
    val targetScale = when {
        !enabled && isFocused -> scale.focusedDisabledScale
        !enabled -> scale.disabledScale
        isPressed && selected -> scale.pressedSelectedScale
        isPressed -> scale.pressedScale
        isFocused && selected -> scale.focusedSelectedScale
        isFocused -> scale.focusedScale
        selected -> scale.selectedScale
        else -> scale.scale
    }
    val animatedScale by animateFloatAsState(targetValue = targetScale, label = "touchSelectableSurfaceScale")
    val rippleColor = if (contentColor != Color.Unspecified) contentColor else Color.White

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .clip(currentShape)
            .background(color = containerColor, shape = currentShape)
            .applySurfaceBorder(currentBorder, currentShape)
            .semantics { semanticsSelected = selected }
            .combinedClickable(
                interactionSource = resolvedInteractionSource,
                indication = ripple(color = rippleColor),
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(
            TvLocalContentColor provides contentColor,
            androidx.compose.material3.LocalContentColor provides contentColor,
        ) {
            content()
        }
    }
}

// androidx.tv.material3.Border defaults `shape` to an internal empty GenericShape sentinel
// (ShapeTokens.BorderDefaultShape) meaning "inherit the Surface's shape". Passing that empty
// GenericShape directly to Modifier.border crashes Bitmap.createBitmap with
// "width and height must be > 0".
private val DefaultBorderShapeSentinel: Shape =
    Border(border = BorderStroke(0.dp, Color.Transparent)).shape

private fun Modifier.applySurfaceBorder(border: Border, fallbackShape: Shape): Modifier {
    if (border == Border.None) return this
    val width = border.border.width
    if (width == Dp.Unspecified || width <= 0.dp) return this
    val resolvedShape = if (border.shape == DefaultBorderShapeSentinel || border.shape is GenericShape) {
        fallbackShape
    } else {
        border.shape
    }
    return this.border(border = border.border, shape = resolvedShape)
}
