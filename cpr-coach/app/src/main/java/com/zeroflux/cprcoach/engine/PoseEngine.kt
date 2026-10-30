package com.zeroflux.cprcoach.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.acos
import kotlin.math.sqrt

/** One frame the pose model ran on. Both fields are null when it found no person. */
data class PoseFrame(
    val timeMs: Long,
    val sample: PoseSample?,
    val arm: ArmLandmarks?,
    /** Camera frame to result, in milliseconds. Real, for docs/MEASUREMENTS.md. */
    val latencyMs: Long,
)

/** Runs MediaPipe on camera frames, on-device. Results arrive on MediaPipe's own thread. */
class PoseEngine(context: Context, private val onFrame: (PoseFrame) -> Unit) {
    private val submitted = ConcurrentHashMap<Long, Float>()
    private val lifecycleLock = Any()
    private var lastTimestampMs = 0L
    private var trace: PrintWriter? = null
    @Volatile private var closed = false
    private var submitting = 0
    private val landmarker: PoseLandmarker? = try {
        create(context, Delegate.GPU)
    } catch (e: Exception) {
        Log.w(TAG, "GPU delegate unavailable, using CPU", e)
        try {
            create(context, Delegate.CPU)
        } catch (cpuError: Exception) {
            Log.e(TAG, "Pose unavailable: CPU delegate failed", cpuError)
            null
        } catch (cpuError: LinkageError) {
            Log.e(TAG, "Pose unavailable: native library failed", cpuError)
            null
        }
    } catch (e: LinkageError) {
        Log.e(TAG, "Pose unavailable: native library failed", e)
        null
    }

    private fun create(context: Context, delegate: Delegate): PoseLandmarker {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setModelAssetPath("pose_landmarker_full.task").setDelegate(delegate).build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, _ ->
                // Native teardown may wait for this listener; never take its lifecycle lock here.
                if (closed) return@setResultListener
                val timestamp = result.timestampMs()
                val aspect = submitted.remove(timestamp) ?: return@setResultListener
                val latency = SystemClock.uptimeMillis() - timestamp
                val marks = result.landmarks().firstOrNull()
                onFrame(toFrame(marks, timestamp, aspect, latency))
            }
            .setErrorListener { e -> Log.e(TAG, "pose error", e) }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    /** Takes ownership of [image] and always closes it. */
    fun submit(image: ImageProxy) {
        val accepted = synchronized(lifecycleLock) {
            if (closed) false else {
                submitting++
                true
            }
        }
        if (!accepted) {
            image.close()
            return
        }
        try {
            detect(image)
        } catch (e: RuntimeException) {
            // A bad frame must not kill the camera thread. The session sees tracking lost.
            Log.e(TAG, "pose frame failed", e)
        } finally {
            // Only the last submission can own teardown after close() has marked us closed.
            val shouldClose = synchronized(lifecycleLock) {
                submitting--
                closed && submitting == 0
            }
            if (shouldClose) closeInBackground()
        }
    }

    private fun detect(image: ImageProxy) {
        image.use { proxy ->
            val now = SystemClock.uptimeMillis()
            if (now <= lastTimestampMs) return // MediaPipe rejects non-increasing timestamps.
            lastTimestampMs = now
            val detector = landmarker
            if (detector == null) {
                if (!closed) {
                    onFrame(PoseFrame(now, null, null, SystemClock.uptimeMillis() - now).also(::writeTrace))
                }
                return
            }
            // MediaPipe drops frames when it is busy, and those never get a result.
            submitted.keys.removeAll { now - it > STALE_FRAME_MS }
            val rotation = proxy.imageInfo.rotationDegrees
            val rotated = rotation == 90 || rotation == 270
            val width = if (rotated) proxy.height else proxy.width
            val height = if (rotated) proxy.width else proxy.height
            submitted[now] = width.toFloat() / height
            val processing = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
            detector.detectAsync(BitmapImageBuilder(proxy.toBitmap()).build(), processing, now)
        }
    }

    private fun toFrame(marks: List<NormalizedLandmark>?, timeMs: Long, aspect: Float, latency: Long): PoseFrame {
        if (marks == null) return PoseFrame(timeMs, null, null, latency).also(::writeTrace)
        fun vis(i: Int) = marks[i].visibility().orElse(0f)
        val leftScore = vis(LEFT_SHOULDER) + vis(LEFT_ELBOW) + vis(LEFT_WRIST) + vis(LEFT_HIP)
        val rightScore = vis(RIGHT_SHOULDER) + vis(RIGHT_ELBOW) + vis(RIGHT_WRIST) + vis(RIGHT_HIP)
        val right = rightScore > leftScore
        val s = marks[if (right) RIGHT_SHOULDER else LEFT_SHOULDER]
        val e = marks[if (right) RIGHT_ELBOW else LEFT_ELBOW]
        val w = marks[if (right) RIGHT_WRIST else LEFT_WRIST]
        val h = marks[if (right) RIGHT_HIP else LEFT_HIP]
        val arm = ArmLandmarks(
            s.x(), s.y(), s.visibility().orElse(0f),
            e.x(), e.y(), e.visibility().orElse(0f),
            w.x(), w.y(), w.visibility().orElse(0f),
            h.x(), h.y(), h.visibility().orElse(0f),
        )
        val sample = PoseSample(
            timeMs = timeMs,
            wristSignal = PoseMath.wristSignal(arm, aspect),
            elbowDegrees = PoseMath.elbowDegrees(arm, aspect),
            minVisibility = minOf(arm.shoulderVis, arm.elbowVis, arm.wristVis, arm.hipVis),
        )
        return PoseFrame(timeMs, sample, arm, latency).also(::writeTrace)
    }

    /** Saves every frame as CSV, so accuracy can be measured and the maths re-tested on a PC. */
    fun startTrace(dir: File) {
        synchronized(this) {
            trace?.close()
            dir.mkdirs()
            trace = PrintWriter(File(dir, "session-${System.currentTimeMillis()}.csv").bufferedWriter()).also {
                it.println("t_ms,wrist_signal,elbow_deg,min_vis,latency_ms")
            }
        }
    }

    fun stopTrace() {
        synchronized(this) {
            trace?.close()
            trace = null
        }
    }

    private fun writeTrace(frame: PoseFrame) {
        synchronized(this) {
            val s = frame.sample
            trace?.println("${frame.timeMs},${s?.wristSignal ?: ""},${s?.elbowDegrees ?: ""},${s?.minVisibility ?: ""},${frame.latencyMs}")
        }
    }

    /**
     * Rejects new frames immediately, without waiting for submission or native teardown.
     * A listener already past its closed check can still invoke onFrame after this returns.
     * PractiseHost makes that harmless by clearing its active flag before closing, then
     * checking that flag on the main thread before delivering any posted frame to the session.
     */
    fun close() {
        val shouldClose = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            submitting == 0
        }
        // If a submit is active, its finally block takes ownership instead. The lock makes
        // these two ownership transitions mutually exclusive, including repeated close calls.
        if (shouldClose) closeInBackground()
    }

    private fun closeInBackground() {
        // Native close may wait for a result listener. Keep it off the caller/main thread
        // and outside lifecycleLock, even when no submit was active at close().
        Thread({
            submitted.clear()
            stopTrace()
            try {
                landmarker?.close()
            } catch (e: RuntimeException) {
                Log.e(TAG, "pose close failed", e)
            } catch (e: LinkageError) {
                Log.e(TAG, "pose close failed", e)
            }
        }, "CPR_POSE_CLOSE").start()
    }

    private companion object {
        const val TAG = "CPR_POSE"
        const val STALE_FRAME_MS = 5_000L
        const val LEFT_SHOULDER = 11
        const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13
        const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15
        const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23
        const val RIGHT_HIP = 24
    }
}

/** Geometry on normalised landmarks. x is scaled by the aspect ratio, since x and y use different units. */
object PoseMath {
    fun wristSignal(arm: ArmLandmarks, aspect: Float): Double {
        val dx = ((arm.shoulderX - arm.hipX) * aspect).toDouble()
        val dy = (arm.shoulderY - arm.hipY).toDouble()
        val torso = sqrt(dx * dx + dy * dy)
        return if (torso > 1e-3) (arm.hipY - arm.wristY) / torso else 0.0
    }

    fun elbowDegrees(arm: ArmLandmarks, aspect: Float): Double {
        val ax = ((arm.shoulderX - arm.elbowX) * aspect).toDouble()
        val ay = (arm.shoulderY - arm.elbowY).toDouble()
        val bx = ((arm.wristX - arm.elbowX) * aspect).toDouble()
        val by = (arm.wristY - arm.elbowY).toDouble()
        val lengths = sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by)
        if (lengths < 1e-6) return 0.0
        return Math.toDegrees(acos(((ax * bx + ay * by) / lengths).coerceIn(-1.0, 1.0)))
    }
}
