package com.zeroflux.cprcoach.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One meaning per colour, across the whole app. Do not use these for anything else. */
object CoachColors {
    val Navy = Color(0xFF0B1220)
    val Charcoal = Color(0xFF151D2E)
    val Raised = Color(0xFF1E2940)
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF9FB0C8)

    /** Emergency only. */
    val Coral = Color(0xFFFF5A5F)
    val CoralDeep = Color(0xFFD63B45)

    /** Good, in range, ready. */
    val Teal = Color(0xFF2DD4BF)

    /** Needs correcting. */
    val Amber = Color(0xFFFBBF24)

    val GlassFill = Color(0x1FFFFFFF)
    val GlassEdge = Color(0x33FFFFFF)

    /** Camera overlays are flat and translucent, with no blur, so pose tracking stays smooth. */
    val CameraScrim = Color(0x99101827)
}

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = CoachColors.Navy,
            surface = CoachColors.Charcoal,
            primary = CoachColors.Teal,
            onBackground = CoachColors.TextPrimary,
            onSurface = CoachColors.TextPrimary,
        ),
        typography = Typography(),
        content = content,
    )
}

/**
 * Clay: only for Emergency, Practise, Start and Call 112. Soft raised surface that presses in.
 * [pressedScale] sets how far it sinks, so Emergency can have the richest press.
 */
@Composable
fun ClayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = CoachColors.Raised,
    colorDeep: Color = CoachColors.Charcoal,
    shape: Shape = RoundedCornerShape(32.dp),
    elevation: Dp = 12.dp,
    pressedScale: Float = 0.97f,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val motionEnabled = rememberCoachMotionEnabled()
    val pressAnimation = if (motionEnabled) spring() else snap()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        pressAnimation,
        label = "clayPress",
    )
    val lift by animateFloatAsState(
        if (pressed) 0.3f else 1f,
        pressAnimation,
        label = "clayLift",
    )
    Box(
        modifier
            .scale(scale)
            .shadow(elevation * lift, shape, ambientColor = color, spotColor = color)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(color, colorDeep)))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent)), shape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Glass: walkthrough cards, scorecard sheet, language picker and chip. Never on the camera screen. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(CoachColors.GlassFill, Color(0x0DFFFFFF))))
            .border(1.dp, CoachColors.GlassEdge, shape),
        content = content,
    )
}
