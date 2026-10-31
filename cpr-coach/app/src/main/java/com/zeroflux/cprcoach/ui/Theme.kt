package com.zeroflux.cprcoach.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
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

/** Camera palette: fixed contrast over the preview, independent of the phone theme. */
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
    val Teal = Color(0xFFAAC8B1)

    /** Needs correcting. */
    val Amber = Color(0xFFDFC18A)

    val GlassFill = Color(0x1FFFFFFF)
    val GlassEdge = Color(0x33FFFFFF)

    /** Camera overlays are flat and translucent, with no blur, so pose tracking stays smooth. */
    val CameraScrim = Color(0x99101827)
}

/** Prototype tokens; legacy field names keep screen changes small during parallel merges. */
@Immutable
data class CoachPalette(
    val Navy: Color,
    val Charcoal: Color,
    val Raised: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val Coral: Color,
    val CoralDeep: Color,
    val Teal: Color,
    val Amber: Color,
    val GlassFill: Color,
    val GlassEdge: Color,
    val OnAction: Color,
    val OnDanger: Color = Color.White,
)

private val LightColors = CoachPalette(
    Navy = Color(0xFFF8F9F6), Charcoal = Color.White, Raised = Color.White,
    TextPrimary = Color(0xFF243C32), TextSecondary = Color(0xFF68756D),
    Coral = Color(0xFFA13E35), CoralDeep = Color(0xFFA13E35),
    Teal = Color(0xFF285E4E), Amber = Color(0xFF886622),
    GlassFill = Color.White, GlassEdge = Color(0xFFDFE5DC), OnAction = Color.White,
)
private val DarkColors = CoachPalette(
    Navy = Color(0xFF171E1B), Charcoal = Color(0xFF202A24), Raised = Color(0xFF202A24),
    TextPrimary = Color(0xFFE3EAE4), TextSecondary = Color(0xFFA3AFA6),
    Coral = Color(0xFFDFA494), CoralDeep = Color(0xFFA95543),
    Teal = Color(0xFFAAC8B1), Amber = Color(0xFFDFC18A),
    GlassFill = Color(0xFF202A24), GlassEdge = Color(0xFF39473F), OnAction = Color(0xFF142219),
)
val LocalCoachColors = staticCompositionLocalOf { LightColors }
internal val LocalCameraChrome = staticCompositionLocalOf { false }

private val LightScheme = lightColorScheme(
    primary = LightColors.Teal, onPrimary = LightColors.OnAction,
    primaryContainer = Color(0xFFEAF0E7), onPrimaryContainer = LightColors.Teal,
    secondary = LightColors.Teal, onSecondary = LightColors.OnAction,
    secondaryContainer = Color(0xFFEAF0E7), onSecondaryContainer = LightColors.Teal,
    tertiary = LightColors.Amber, onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5EDDB), onTertiaryContainer = LightColors.Amber,
    background = LightColors.Navy, onBackground = LightColors.TextPrimary,
    surface = LightColors.Charcoal, onSurface = LightColors.TextPrimary,
    surfaceVariant = Color(0xFFE9EEE4), onSurfaceVariant = LightColors.TextSecondary,
    surfaceTint = LightColors.Teal,
    surfaceBright = LightColors.Charcoal, surfaceDim = LightColors.Navy,
    surfaceContainerLowest = LightColors.Charcoal, surfaceContainerLow = LightColors.Navy,
    surfaceContainer = LightColors.Charcoal, surfaceContainerHigh = LightColors.Charcoal,
    surfaceContainerHighest = Color(0xFFE9EEE4),
    outline = LightColors.TextSecondary, outlineVariant = LightColors.GlassEdge,
    error = LightColors.Coral, onError = Color.White,
    errorContainer = Color(0xFFF5EAE5), onErrorContainer = LightColors.Coral,
    inverseSurface = DarkColors.Charcoal, inverseOnSurface = DarkColors.TextPrimary,
    inversePrimary = DarkColors.Teal,
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA3BCA7), onPrimary = DarkColors.OnAction,
    primaryContainer = Color(0xFF293B30), onPrimaryContainer = DarkColors.Teal,
    secondary = DarkColors.Teal, onSecondary = DarkColors.OnAction,
    secondaryContainer = Color(0xFF293B30), onSecondaryContainer = DarkColors.Teal,
    tertiary = DarkColors.Amber, onTertiary = DarkColors.Navy,
    tertiaryContainer = Color(0xFF3C3425), onTertiaryContainer = DarkColors.Amber,
    background = DarkColors.Navy, onBackground = DarkColors.TextPrimary,
    surface = DarkColors.Charcoal, onSurface = DarkColors.TextPrimary,
    surfaceVariant = Color(0xFF2B382E), onSurfaceVariant = DarkColors.TextSecondary,
    surfaceTint = DarkColors.Teal,
    surfaceBright = Color(0xFF2B382E), surfaceDim = DarkColors.Navy,
    surfaceContainerLowest = DarkColors.Navy, surfaceContainerLow = DarkColors.Charcoal,
    surfaceContainer = DarkColors.Charcoal, surfaceContainerHigh = DarkColors.Charcoal,
    surfaceContainerHighest = Color(0xFF2B382E),
    outline = DarkColors.TextSecondary, outlineVariant = DarkColors.GlassEdge,
    error = DarkColors.Coral, onError = DarkColors.Navy,
    errorContainer = Color(0xFF3D2C27), onErrorContainer = DarkColors.Coral,
    inverseSurface = LightColors.Charcoal, inverseOnSurface = LightColors.TextPrimary,
    inversePrimary = LightColors.Teal,
)
private val CameraScheme = darkColorScheme(
    background = CoachColors.Navy, surface = CoachColors.Charcoal, primary = CoachColors.Teal,
    onBackground = CoachColors.TextPrimary, onSurface = CoachColors.TextPrimary,
)
private val CoachTypography = Typography()
private val CameraShapes = Shapes()
private val CoachShapes = Shapes(
    small = RoundedCornerShape(9.dp), medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalCoachColors provides if (dark) DarkColors else LightColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = CoachTypography,
            shapes = CoachShapes,
            content = content,
        )
    }
}

/** Camera geometry, type and chrome stay unchanged; only good/correction hues are re-skinned. */
@Composable
fun CameraChrome(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCameraChrome provides true) {
        MaterialTheme(colorScheme = CameraScheme, typography = CoachTypography, shapes = CameraShapes, content = content)
    }
}

/** Flat prototype actions outside the camera; preserve existing camera button treatment. */
@Composable
fun ClayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = if (LocalCameraChrome.current) CoachColors.Raised else LocalCoachColors.current.Raised,
    colorDeep: Color = if (LocalCameraChrome.current) CoachColors.Charcoal else color,
    shape: Shape = RoundedCornerShape(if (LocalCameraChrome.current) 32.dp else 12.dp),
    elevation: Dp = if (LocalCameraChrome.current) 12.dp else 0.dp,
    pressedScale: Float = 0.97f,
    content: @Composable BoxScope.() -> Unit,
) {
    val camera = LocalCameraChrome.current
    val colors = LocalCoachColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val motionEnabled = rememberCoachMotionEnabled()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, if (motionEnabled) spring() else snap(), label = "clayPress")
    val lift by animateFloatAsState(if (pressed) 0.3f else 1f, if (motionEnabled) spring() else snap(), label = "clayLift")
    Box(
        modifier
            .scale(scale)
            .shadow(elevation * lift, shape, ambientColor = color, spotColor = color)
            .clip(shape)
            .then(
                if (camera) Modifier
                    .background(Brush.verticalGradient(listOf(color, colorDeep)))
                    .border(1.dp, Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color.Transparent)), shape)
                else Modifier.background(color).border(1.dp, colors.GlassEdge, shape),
            )
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Flat paper cards. Never placed over the camera preview. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(LocalCoachColors.current.GlassFill)
            .border(1.dp, LocalCoachColors.current.GlassEdge, shape),
        content = content,
    )
}
