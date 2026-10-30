package com.zeroflux.cprcoach.ui

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroflux.cprcoach.engine.TrackingState

/** Transparent camera chrome only: the caller owns the preview, tracking and session clock. */
@Composable
fun CameraOverlay(
    state: CameraUiState,
    micState: MicState,
    onStart: () -> Unit,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    showStart: Boolean = true,
) {
    Box(modifier.fillMaxSize()) {
        // Leaf composables receive only their displayed values. Glow animation frames
        // invalidate drawing rather than recomposing this overlay.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp)) {
            when (state) {
                is CameraUiState.Framing -> FramingOverlay(state.ready, state.hintKey, showStart, onStart)
                is CameraUiState.Countdown -> CountdownDigit(state.secondsLeft)
                is CameraUiState.Live -> LiveOverlay(state, onPauseToggle, onStop)
            }
            // Countdown has no other chrome, but the mic remains reachable in every state.
            MicButton(
                state = micState,
                onTap = onMicTap,
                modifier = Modifier.align(Alignment.BottomEnd).sizeIn(minWidth = 56.dp, minHeight = 56.dp),
            )
        }
        if (state is CameraUiState.Live) {
            val rate = visibleCameraRate(state.tracking, state.ratePerMinute, state.isPaused)
            CameraEdgeGlow(if (rate != null) state.inRange else null)
        }
    }
}

@Composable
private fun FramingOverlay(ready: Boolean, hintKey: String?, showStart: Boolean, onStart: () -> Unit) {
    val strings = LocalStrings.current
    Row(
        Modifier.fillMaxSize().padding(bottom = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        KneelingGuide(ready, Modifier.weight(1f).fillMaxSize())
        Column(
            Modifier.weight(1f).background(CoachColors.CameraScrim, RoundedCornerShape(24.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            hintKey?.let {
                Text(strings.get(it), color = CoachColors.TextPrimary, fontSize = 24.sp, textAlign = TextAlign.Center)
            }
            if (ready) {
                Text(strings.get("frame_ready"), color = CoachColors.Teal, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            if (showStart) {
                // ClayButton has no enabled parameter. A disabled, non-clickable counterpart
                // avoids both an active accessibility action and a false press animation.
                if (ready) {
                    ClayButton(
                        onClick = onStart,
                        color = CoachColors.Teal,
                        colorDeep = CoachColors.Charcoal,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(strings.get("frame_start"), color = CoachColors.Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        Modifier.fillMaxWidth().height(56.dp)
                            .background(CoachColors.CameraScrim, RoundedCornerShape(32.dp))
                            .semantics { role = Role.Button; disabled() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(strings.get("frame_start"), color = CoachColors.TextSecondary, fontSize = 22.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun KneelingGuide(ready: Boolean, modifier: Modifier) {
    val outline = remember {
        Path().apply {
            // Side profile: neck, back, hips, folded leg, knee, torso and straight arms.
            moveTo(0.48f, 0.27f)
            cubicTo(0.40f, 0.30f, 0.30f, 0.44f, 0.25f, 0.55f)
            cubicTo(0.22f, 0.61f, 0.30f, 0.68f, 0.39f, 0.73f)
            lineTo(0.18f, 0.77f)
            quadraticTo(0.13f, 0.77f, 0.13f, 0.81f)
            lineTo(0.44f, 0.81f)
            quadraticTo(0.51f, 0.79f, 0.47f, 0.72f)
            lineTo(0.35f, 0.57f)
            lineTo(0.51f, 0.39f)
            lineTo(0.64f, 0.73f)
            lineTo(0.74f, 0.76f)
            quadraticTo(0.79f, 0.75f, 0.75f, 0.72f)
            lineTo(0.69f, 0.69f)
            lineTo(0.59f, 0.33f)
            lineTo(0.55f, 0.27f)
        }
    }
    val color = if (ready) CoachColors.Teal.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.25f)
    Canvas(modifier) {
        val unit = minOf(size.width, size.height)
        val stroke = 3.dp.toPx() / unit
        withTransform({
            translate((size.width - unit) / 2f, (size.height - unit) / 2f)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            drawCircle(color, radius = 0.075f, center = Offset(0.53f, 0.18f), style = Stroke(stroke))
            drawPath(outline, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun CountdownDigit(secondsLeft: Int) {
    val strings = LocalStrings.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            strings.get("camera_numeric_value").replace("{value}", secondsLeft.toString()),
            modifier = Modifier.background(CoachColors.CameraScrim, RoundedCornerShape(24.dp)).padding(horizontal = 40.dp),
            color = CoachColors.TextPrimary,
            fontSize = 144.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

// Presentation precedence only. Never retain a previous reading or infer a rate from other data.
private fun visibleCameraRate(tracking: TrackingState, rate: Int?, isPaused: Boolean): Int? =
    if (!isPaused && tracking == TrackingState.BEATING) rate else null

private fun cameraStatusKey(tracking: TrackingState, isPaused: Boolean): String? = when (tracking) {
    TrackingState.LOST -> "live_cant_see"
    TrackingState.PAUSED -> "live_paused"
    TrackingState.WARMING, TrackingState.BEATING -> if (isPaused) "live_paused" else null
}

@Composable
private fun LiveOverlay(state: CameraUiState.Live, onPauseToggle: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            RateReadout(
                visibleCameraRate(state.tracking, state.ratePerMinute, state.isPaused),
                state.inRange,
                cameraStatusKey(state.tracking, state.isPaused),
                Modifier.weight(1f),
            )
            TimeLeftRing(state.secondsLeft, state.totalSeconds)
        }
        Row(
            Modifier.fillMaxWidth().padding(end = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                if (!state.isPaused && state.tracking == TrackingState.BEATING) {
                    ElbowIndicator(state.elbowsBent)
                }
            }
            LiveControls(state.isPaused, onPauseToggle, onStop)
        }
    }
}

@Composable
private fun RateReadout(rate: Int?, inRange: Boolean?, statusKey: String?, modifier: Modifier) {
    val strings = LocalStrings.current
    val color = if (rate == null) CoachColors.TextSecondary else when (inRange) {
        true -> CoachColors.Teal
        false -> CoachColors.Amber
        null -> CoachColors.TextPrimary
    }
    Column(
        modifier.background(CoachColors.CameraScrim, RoundedCornerShape(24.dp)).padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (rate == null) strings.get("live_no_rate")
                else strings.get("camera_numeric_value").replace("{value}", rate.toString()),
            color = color,
            fontSize = 104.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
        if (rate != null) {
            Text(strings.get("live_per_minute"), color = CoachColors.TextPrimary, fontSize = 22.sp)
        }
        statusKey?.let {
            Text(
                strings.get(it),
                color = if (it == "live_paused") CoachColors.Amber else CoachColors.TextSecondary,
                fontSize = 24.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ElbowIndicator(bent: Boolean?) {
    if (bent == null) return
    val strings = LocalStrings.current
    val color = if (bent) CoachColors.Amber else CoachColors.Teal
    Row(
        Modifier.background(CoachColors.CameraScrim, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(24.dp)) {
            val start = Offset(size.width * 0.25f, size.height * 0.1f)
            val elbow = Offset(size.width * (if (bent) 0.7f else 0.4f), size.height * 0.5f)
            val end = Offset(size.width * 0.55f, size.height * 0.9f)
            drawLine(color, start, elbow, 3.dp.toPx(), StrokeCap.Round)
            drawLine(color, elbow, end, 3.dp.toPx(), StrokeCap.Round)
        }
        Text(strings.get(if (bent) "live_elbows_bent" else "live_elbows_straight"), color = color, fontSize = 18.sp)
    }
}

@Composable
private fun TimeLeftRing(secondsLeft: Int, totalSeconds: Int) {
    val strings = LocalStrings.current
    val progress = animateFloatAsState(
        targetValue = if (totalSeconds > 0) (secondsLeft.toFloat() / totalSeconds).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(400),
        label = "cameraTimeLeft",
    )
    Box(
        Modifier.size(120.dp).background(CoachColors.CameraScrim, RoundedCornerShape(60.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(5.dp)) {
            val stroke = 5.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawCircle(CoachColors.TextSecondary.copy(alpha = 0.3f), style = Stroke(stroke))
            drawArc(
                CoachColors.TextPrimary, startAngle = -90f, sweepAngle = 360f * progress.value,
                useCenter = false, topLeft = topLeft, size = Size(diameter, diameter),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            strings.get("camera_numeric_value").replace("{value}", secondsLeft.toString()),
            color = CoachColors.TextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LiveControls(isPaused: Boolean, onPauseToggle: () -> Unit, onStop: () -> Unit) {
    val strings = LocalStrings.current
    val colors = ButtonDefaults.textButtonColors(contentColor = CoachColors.TextPrimary, containerColor = CoachColors.CameraScrim)
    TextButton(onClick = onPauseToggle, modifier = Modifier.sizeIn(minWidth = 88.dp, minHeight = 56.dp), colors = colors) {
        Text(strings.get(if (isPaused) "live_resume" else "live_pause"), fontSize = 20.sp)
    }
    TextButton(onClick = onStop, modifier = Modifier.sizeIn(minWidth = 88.dp, minHeight = 56.dp), colors = colors) {
        Text(strings.get("live_stop"), fontSize = 20.sp)
    }
}

@Composable
private fun CameraEdgeGlow(inRange: Boolean?) {
    val reducedMotion = cameraReducedMotion()
    val animatedColor = animateColorAsState(
        targetValue = when (inRange) {
            true -> CoachColors.Teal
            false -> CoachColors.Amber
            null -> Color.Transparent
        },
        animationSpec = tween(if (reducedMotion) 0 else 400),
        label = "cameraEdgeColor",
    )
    // Lost, warming, paused and absent readings must remove even a fading old glow immediately.
    if (inRange == null) return
    Canvas(Modifier.fillMaxSize()) {
        val current = if (reducedMotion) {
            if (inRange) CoachColors.Teal else CoachColors.Amber
        } else animatedColor.value
        val color = current.copy(alpha = current.alpha * 0.35f)
        val edge = 32.dp.toPx()
        drawRect(Brush.horizontalGradient(listOf(color, Color.Transparent), endX = edge), size = Size(edge, size.height))
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, color), startX = size.width - edge, endX = size.width),
            topLeft = Offset(size.width - edge, 0f), size = Size(edge, size.height),
        )
        drawRect(Brush.verticalGradient(listOf(color, Color.Transparent), endY = edge), size = Size(size.width, edge))
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, color), startY = size.height - edge, endY = size.height),
            topLeft = Offset(0f, size.height - edge), size = Size(size.width, edge),
        )
    }
}

@Composable
private fun cameraReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    var reduced by remember(resolver) {
        mutableStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        observer.onChange(false)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
