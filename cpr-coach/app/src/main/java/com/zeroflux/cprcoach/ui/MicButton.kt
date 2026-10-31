package com.zeroflux.cprcoach.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

@Composable
fun MicButton(
    state: MicState,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val camera = LocalCameraChrome.current
    val colors = LocalCoachColors.current
    val green = if (camera) CoachColors.Teal else colors.Teal
    val surface = if (camera) CoachColors.Charcoal else colors.Charcoal
    val edge = if (camera) CoachColors.GlassEdge else colors.GlassEdge
    val muted = if (camera) CoachColors.TextSecondary else colors.TextSecondary
    val ink = if (camera) {
        if (state == MicState.LISTENING) CoachColors.Navy else CoachColors.TextPrimary
    } else {
        if (state == MicState.LISTENING) colors.OnAction else colors.TextPrimary
    }
    val motionEnabled = rememberCoachMotionEnabled()
    val listening = state == MicState.LISTENING
    val description = strings.get(
        when (state) {
            MicState.OFF -> "mic_enable"
            MicState.LOADING -> "mic_loading"
            MicState.LISTENING -> "mic_listening"
        },
    )
    val ring = remember { Animatable(0f) }

    LaunchedEffect(state, motionEnabled) {
        ring.snapTo(0f)
        if (listening && motionEnabled) {
            while (coroutineContext.isActive) {
                ring.animateTo(1f, tween(durationMillis = 1800, easing = LinearEasing))
                ring.snapTo(0f)
            }
        }
    }

    Box(
        modifier
            .size(64.dp)
            .drawBehind {
                if (listening) {
                    val progress = if (motionEnabled) ring.value else 0f
                    drawCircle(
                        color = green.copy(alpha = 0.22f * (1f - progress)),
                        radius = size.minDimension / 2 + 6.dp.toPx() * progress,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            .clip(CircleShape)
            .background(if (listening) green else surface, CircleShape)
            .border(1.dp, if (listening) green else edge, CircleShape)
            .semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        if (state == MicState.LOADING) {
            if (motionEnabled) {
                CircularProgressIndicator(
                    modifier = Modifier.size(38.dp).clearAndSetSemantics { },
                    color = muted,
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent,
                )
            } else {
                // A static open ring, not a made-up determinate progress value.
                Canvas(Modifier.size(38.dp)) {
                    val inset = 2.dp.toPx()
                    drawArc(
                        color = muted,
                        startAngle = -90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - inset * 2, size.height - inset * 2),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        Canvas(Modifier.size(24.dp)) {
            val unit = size.minDimension / 24f
            val stroke = Stroke(2f * unit, cap = StrokeCap.Round)
            drawRoundRect(
                color = ink,
                topLeft = Offset(9f * unit, 2f * unit),
                size = Size(6f * unit, 12f * unit),
                cornerRadius = CornerRadius(3f * unit),
                style = stroke,
            )
            drawArc(
                color = ink,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(5f * unit, 5f * unit),
                size = Size(14f * unit, 14f * unit),
                style = stroke,
            )
            drawLine(ink, Offset(12f * unit, 19f * unit), Offset(12f * unit, 22f * unit), stroke.width, StrokeCap.Round)
            drawLine(ink, Offset(9f * unit, 22f * unit), Offset(15f * unit, 22f * unit), stroke.width, StrokeCap.Round)
        }
    }
}

/** Observe changes as well as the initial value, including while a screen stays open. */
@Composable
internal fun rememberCoachMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var enabled by remember(resolver) {
        mutableStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        observer.onChange(false)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return enabled
}
