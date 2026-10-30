package com.zeroflux.cprcoach.ui

import android.graphics.Paint
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.zeroflux.cprcoach.engine.OverlayJoint
import com.zeroflux.cprcoach.engine.OverlayMapping
import com.zeroflux.cprcoach.engine.OverlayPose
import com.zeroflux.cprcoach.engine.PostureIssue
import com.zeroflux.cprcoach.engine.PostureReading
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val Neutral = Color(0xFFE2E8F0)

/**
 * Near-arm skeleton over the camera. Takes no touches. Reads [pose] only while drawing, so
 * 60 Hz motion never recomposes the screen. Teal good, amber needs correcting, grey unsure.
 */
@Composable
fun ArmOverlay(
    pose: OverlayPose,
    posture: PostureReading?,
    showLabels: Boolean,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val issue = posture?.issue?.takeIf { showLabels && posture.good == false }
    val label = issue?.let { strings.get(it.key) }
    val currentPosture by rememberUpdatedState(posture)
    val currentIssue by rememberUpdatedState(issue)
    val currentLabel by rememberUpdatedState(label)
    var tick by remember { mutableLongStateOf(0L) }
    val out = remember { OverlayPose.Out() }
    val screen = remember { FloatArray(OverlayJoint.COUNT * 2) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { isFakeBoldText = true }
    }
    LaunchedEffect(pose) {
        while (true) {
            if (pose.isVisible(SystemClock.uptimeMillis())) {
                withFrameNanos { tick = it }
            } else {
                delay(100L)
            }
        }
    }
    Canvas(Modifier.fillMaxSize().then(modifier)) {
        tick // draw-phase read: invalidates only this canvas
        try {
            if (pose.at(SystemClock.uptimeMillis(), out)) {
                drawArm(out, screen, currentPosture, currentIssue, currentLabel, showLabels, pose.aspect, paint)
            }
        } catch (ignored: RuntimeException) {
            // A bad frame must never take the screen down.
        }
    }
}

private fun DrawScope.drawArm(
    out: OverlayPose.Out,
    s: FloatArray,
    posture: PostureReading?,
    issue: PostureIssue?,
    label: String?,
    showLabels: Boolean,
    aspect: Float,
    paint: Paint,
) {
    val w = size.width
    val h = size.height
    if (!OverlayMapping.valid(w, h, aspect)) return
    for (j in 0 until OverlayJoint.COUNT) {
        val x = OverlayMapping.mapX(out.x[j], w, h, aspect)
        val y = OverlayMapping.mapY(out.y[j], w, h, aspect)
        if (!x.isFinite() || !y.isFinite()) return
        s[j * 2] = x
        s[j * 2 + 1] = y
    }
    val base = when (posture?.good) {
        true -> CoachColors.Teal
        false -> CoachColors.Amber
        null -> Neutral
    }
    val a = out.alpha.coerceIn(0f, 1f)
    val color = base.copy(alpha = a)
    val thin = base.copy(alpha = 0.55f * a)
    val shadow = Color.Black.copy(alpha = 0.35f * a)
    val d = density
    val shoulder = OverlayJoint.SHOULDER
    val elbow = OverlayJoint.ELBOW
    val wrist = OverlayJoint.WRIST
    val hip = OverlayJoint.HIP

    fun line(from: Int, to: Int, c: Color, width: Float) =
        drawLine(c, Offset(s[from * 2], s[from * 2 + 1]), Offset(s[to * 2], s[to * 2 + 1]), width, StrokeCap.Round)

    line(shoulder, wrist, thin, 1.5f * d)
    line(shoulder, elbow, shadow, 6f * d)
    line(elbow, wrist, shadow, 6f * d)
    line(shoulder, hip, shadow, 6f * d)
    line(shoulder, elbow, color, 4f * d)
    line(elbow, wrist, color, 4f * d)
    line(shoulder, hip, color, 4f * d)
    for (j in 0 until OverlayJoint.COUNT) {
        val c = Offset(s[j * 2], s[j * 2 + 1])
        drawCircle(shadow, 7f * d, c)
        drawCircle(color, 5.5f * d, c)
    }
    if (!showLabels) return

    paint.textSize = 16f * d
    paint.color = color.toArgb()
    paint.setShadowLayer(3f * d, 0f, 0f, shadow.toArgb())
    val canvas = drawContext.canvas.nativeCanvas
    val degrees = posture?.elbowDegrees
    if (degrees != null && degrees.isFinite()) {
        canvas.drawText("${degrees.roundToInt()}°", s[elbow * 2] + 12f * d, s[elbow * 2 + 1] - 10f * d, paint)
    }
    if (label != null && issue != null) {
        val joint = if (issue == PostureIssue.ELBOWS_BENT) elbow else shoulder
        val dy = if (joint == elbow) 24f * d else -14f * d
        canvas.drawText(label, s[joint * 2] + 12f * d, s[joint * 2 + 1] + dy, paint)
    }
}
