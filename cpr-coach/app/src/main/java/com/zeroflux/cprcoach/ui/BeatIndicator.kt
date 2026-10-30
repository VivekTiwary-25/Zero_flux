package com.zeroflux.cprcoach.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.zeroflux.cprcoach.audio.AudioEngine
import com.zeroflux.cprcoach.engine.PoseFrame
import com.zeroflux.cprcoach.engine.PushDetector
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Where the beat is right now. Both lambdas are cheap and return null when the metronome is not sounding. */
class BeatSource(val phase: () -> Double?, val periodMs: () -> Double?) {
    companion object {
        fun of(engine: () -> AudioEngine?): BeatSource =
            BeatSource({ engine()?.beatPhase() }, { engine()?.beatPeriodMs() })
    }
}

/**
 * Detected pushes, newest last, for the display only. Fed from the pose callback, read while drawing.
 * Only real [PushDetector] events are ever stored.
 */
class PushLog {
    private val lock = Any()
    private val detector = PushDetector()
    private val times = LongArray(CAPACITY)
    private var count = 0

    /** Never throws: a failure here must not touch the pose pipeline. */
    fun onFrame(frame: PoseFrame) {
        try {
            val sample = frame.sample
            synchronized(lock) {
                if (sample == null) {
                    detector.gap()
                    return
                }
                val event = detector.update(frame.timeMs, sample.wristSignal) ?: return
                // The frame arrives after capture; place the push where it happened.
                val at = event.timeMs - frame.latencyMs.coerceIn(0L, MAX_LATENCY_MS)
                add(at)
            }
        } catch (_: Exception) {
        }
    }

    fun clear() = synchronized(lock) {
        detector.reset()
        count = 0
    }

    internal fun add(timeMs: Long) = synchronized(lock) {
        if (count == CAPACITY) {
            System.arraycopy(times, 1, times, 0, CAPACITY - 1)
            count--
        }
        times[count++] = timeMs
    }

    /** Copies the newest pushes into [out] (oldest first) and returns how many. No allocation. */
    fun snapshot(out: LongArray): Int = synchronized(lock) {
        val n = min(count, out.size)
        System.arraycopy(times, count - n, out, 0, n)
        n
    }

    private companion object {
        const val CAPACITY = 16
        const val MAX_LATENCY_MS = 300L
    }
}

/** Pure beat geometry, so the drawing stays thin and this can be tested. */
object BeatMath {
    /** Within this share of a beat (about 110 ms at 110 a minute) a push counts as on the beat. */
    const val ON_BEAT_FRACTION = 0.2

    /** The beat's size now: 1 at the click, easing to 0 well before the next. */
    fun swell(phase: Double): Float {
        val p = phase.coerceIn(0.0, 1.0)
        return (1.0 - p / SWELL_SPAN).coerceAtLeast(0.0).pow(2).toFloat()
    }

    /** Signed ms from the nearest beat to [pushMs]: negative early, positive late. Null for unusable input. */
    fun offsetMs(pushMs: Long, nowMs: Long, phase: Double, periodMs: Double): Double? {
        if (!(periodMs > 0.0) || !phase.isFinite()) return null
        val lastBeat = nowMs - phase * periodMs
        var d = (pushMs - lastBeat) % periodMs
        if (d >= periodMs / 2) d -= periodMs
        if (d < -periodMs / 2) d += periodMs
        return d
    }

    fun onBeat(offsetMs: Double, periodMs: Double): Boolean = abs(offsetMs) <= ON_BEAT_FRACTION * periodMs

    private const val SWELL_SPAN = 0.45
}

private const val TRACK_BEATS = 8

/**
 * Pulsing circle that swells on the beat and, with [pushes], a track of recent pushes against the beat ticks.
 * Everything animates in the draw phase: the composable itself never recomposes per frame.
 * With no beat it shows a still, dim ring and nothing else.
 */
@Composable
fun BeatIndicator(
    beat: BeatSource?,
    pushes: PushLog?,
    modifier: Modifier = Modifier,
    showTrack: Boolean = true,
    pulseColor: Color = CoachColors.Teal,
) {
    val source by rememberUpdatedState(beat)
    val log by rememberUpdatedState(pushes)
    val motionEnabled = rememberCoachMotionEnabled()
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var wasActive = true
        while (isActive) {
            val frameNanos = awaitFrame()
            val active = try {
                source?.phase?.invoke() != null
            } catch (_: Exception) {
                false
            }
            // Off means still: one last frame to clear, then no more invalidation.
            if (active || wasActive) tick = frameNanos
            wasActive = active
        }
    }
    val scratch = remember { LongArray(TRACK_BEATS + 2) }
    Box(modifier.clearAndSetSemantics { }) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_VARIABLE") val frame = tick // subscribe to frames: invalidates drawing only
            try {
                val phase = source?.phase?.invoke()
                val period = source?.periodMs?.invoke()
                val live = phase != null && period != null && period > 0.0
                val h = size.height
                val w = size.width
                val base = min(h, w) * 0.5f
                val centerX = if (showTrack) base else w / 2f
                val center = Offset(centerX, h / 2f)
                if (!live) {
                    drawCircle(
                        CoachColors.TextSecondary.copy(alpha = 0.35f), radius = base * 0.5f, center = center,
                        style = Stroke(width = max(1f, base * 0.08f)),
                    )
                    if (showTrack) drawLine(
                        CoachColors.TextSecondary.copy(alpha = 0.2f), Offset(base * 2f + 12f, h / 2f), Offset(w, h / 2f),
                        strokeWidth = 2f, cap = StrokeCap.Round,
                    )
                    return@Canvas
                }
                val s = BeatMath.swell(phase!!)
                val grow = if (motionEnabled) 0.45f * s else 0f
                drawCircle(
                    pulseColor.copy(alpha = 0.45f + 0.5f * s), radius = base * 0.5f * (1f + grow) * 0.9f, center = center,
                )
                if (!showTrack) return@Canvas

                val trackLeft = base * 2f + 12f
                val trackW = w - trackLeft
                if (trackW <= 0f) return@Canvas
                val windowMs = period!! * TRACK_BEATS
                val now = SystemClock.uptimeMillis()
                val y = h / 2f
                fun xAt(timeMs: Long): Float = w - ((now - timeMs) / windowMs).toFloat() * trackW
                drawLine(
                    CoachColors.TextSecondary.copy(alpha = 0.25f), Offset(trackLeft, y), Offset(w, y),
                    strokeWidth = 2f, cap = StrokeCap.Round,
                )
                // Beat ticks, newest at the right edge as the beat lands.
                val lastBeat = now - (phase * period).toLong()
                var k = 0
                while (k <= TRACK_BEATS) {
                    val x = xAt(lastBeat - (k * period).toLong())
                    if (x >= trackLeft) {
                        drawLine(
                            CoachColors.TextPrimary.copy(alpha = 0.45f), Offset(x, y - h * 0.3f), Offset(x, y + h * 0.3f),
                            strokeWidth = 2f, cap = StrokeCap.Round,
                        )
                    }
                    k++
                }
                val n = log?.snapshot(scratch) ?: 0
                val radius = h * 0.17f
                for (i in 0 until n) {
                    val t = scratch[i]
                    val age = (now - t).toDouble()
                    if (age < 0.0 || age > windowMs) continue
                    val x = xAt(t)
                    if (x < trackLeft) continue
                    val off = BeatMath.offsetMs(t, now, phase, period) ?: continue
                    val color = if (BeatMath.onBeat(off, period)) CoachColors.Teal else CoachColors.Amber
                    drawCircle(color.copy(alpha = (1.0 - 0.6 * age / windowMs).toFloat()), radius, Offset(x, y))
                }
            } catch (_: Exception) {
                // Drawing must never take the screen down.
            }
        }
    }
}

private suspend fun awaitFrame(): Long = androidx.compose.runtime.withFrameNanos { it }
