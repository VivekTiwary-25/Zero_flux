package com.zeroflux.cprcoach.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.zeroflux.cprcoach.BuildConfig
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.io.File
import java.io.PrintWriter
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/** One frame the pose model ran on. Both fields are null when it found no person. */
data class PoseFrame(
    val timeMs: Long,
    val sample: PoseSample?,
    val arm: ArmLandmarks?,
    /** Camera frame to result, in milliseconds. Real, for docs/MEASUREMENTS.md. */
    val latencyMs: Long,
    /** Width over height of the rotated image the landmarks are normalised to. */
    val aspect: Float = 4f / 3f,
)

/** Runs MediaPipe on camera frames, on-device. Results arrive on MediaPipe's own thread. */
class PoseEngine(context: Context, private val onFrame: (PoseFrame) -> Unit) {
    private val submitted = ConcurrentHashMap<Long, Float>()
    private val lifecycleLock = Any()
    private var lastTimestampMs = 0L
    private var trace: PrintWriter? = null
    @Volatile private var closed = false
    private var submitting = 0
    private var delegateName = "none"
    private var lastAcceptedMs = 0L
    // Only touched on MediaPipe's result thread.
    private var nearRight = false
    private var traceRowsSinceFlush = 0
    private val appContext: Context = context.applicationContext ?: context
    private val governor = LoadGovernor()
    @Volatile private var frameGapMs = LoadGovernor.GAPS_MS[0]
    @Volatile private var thermalStatus = -1
    @Volatile private var batteryTempC = Float.NaN
    private var lastEnvMs = 0L
    private var power: PowerManager? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    private val statsLatency = ArrayList<Long>()
    private var statsStartMs = 0L
    // Guarded by statsLatency; count camera deliveries before the governor skips anything.
    private var cameraStatsStartMs = 0L
    private var cameraIntervals = 0L
    private var analysisWidth = 0
    private var analysisHeight = 0
    private var frameDump: FrameDump? = null
    private val modelChoice: Pair<String, ByteBuffer?> = resolveModel(context)
    private val modelName: String = modelChoice.first
    private val landmarker: PoseLandmarker? = try {
        create(context, Delegate.GPU).also { delegateName = "GPU" }
    } catch (e: Exception) {
        Log.w(TAG, "GPU delegate unavailable, using CPU", e)
        try {
            create(context, Delegate.CPU).also { delegateName = "CPU" }
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

    init {
        try {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm != null) {
                power = pm
                thermalStatus = pm.currentThermalStatus
                val l = PowerManager.OnThermalStatusChangedListener { thermalStatus = it }
                pm.addThermalStatusListener(l)
                thermalListener = l
            }
        } catch (e: Exception) {
            Log.w(TAG, "thermal status unavailable", e)
        }
    }

    /** Real values only: thermal status, headroom (NaN if unknown), battery temperature. Apps get no CPU temperature. */
    private fun readBatteryTemp() {
        try {
            val intent: Intent? = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
            batteryTempC = if (tenths == Int.MIN_VALUE) Float.NaN else tenths / 10f
        } catch (e: Exception) {
            batteryTempC = Float.NaN
            Log.w(TAG, "battery temperature unavailable", e)
        }
    }

    private fun headroom(): Float = try {
        power?.getThermalHeadroom(10) ?: Float.NaN
    } catch (e: Exception) {
        Float.NaN
    }

    private fun adapt(timeMs: Long, latency: Long) {
        try {
            if (lastEnvMs == 0L || timeMs - lastEnvMs >= ENV_EVERY_MS) {
                lastEnvMs = timeMs
                readBatteryTemp()
            }
            val old = frameGapMs
            val next = synchronized(governor) { governor.update(timeMs, latency, thermalStatus, batteryTempC) }
            frameGapMs = next
            if (old != next) {
                Log.i(TAG, "frame_gap_ms $old -> $next thermal_status=$thermalStatus battery_temp_c=$batteryTempC latency_ms=$latency")
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "load governor failed", e)
        }
    }

    /** Debug builds only: pose_model lite|heavy from the launching intent, read from the app's external models folder. */
    private fun resolveModel(context: Context): Pair<String, ByteBuffer?> {
        if (!BuildConfig.DEBUG) return MODEL_NAME to null
        try {
            DebugOptions.refreshFrom(context)
            val wanted = DebugOptions.poseModel
            if (wanted != "lite" && wanted != "heavy") return MODEL_NAME to null
            val file = File(File(context.getExternalFilesDir(null), "models"), "pose_landmarker_$wanted.task")
            if (!file.isFile) {
                Log.w(TAG, "pose_model=$wanted requested but pose_landmarker_$wanted.task is missing from models/; using full")
                return MODEL_NAME to null
            }
            val bytes = file.readBytes()
            val direct = ByteBuffer.allocateDirect(bytes.size)
            direct.put(bytes).flip()
            Log.i(TAG, "pose_model=$wanted loaded (${bytes.size} bytes)")
            return wanted to direct
        } catch (e: Throwable) {
            Log.w(TAG, "pose_model switch failed; using full", e)
            return MODEL_NAME to null
        }
    }

    private fun create(context: Context, delegate: Delegate): PoseLandmarker {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder().setDelegate(delegate).also { b ->
                    val buffer = modelChoice.second
                    if (buffer != null) b.setModelAssetBuffer(buffer.duplicate()) else b.setModelAssetPath(MODEL)
                }.build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, _ ->
                // Native teardown may wait for this listener; never take its lifecycle lock here.
                if (closed) return@setResultListener
                try {
                    val timestamp = result.timestampMs()
                    val aspect = submitted.remove(timestamp) ?: return@setResultListener
                    val latency = SystemClock.uptimeMillis() - timestamp
                    logStats(SystemClock.uptimeMillis(), latency)
                    adapt(timestamp, latency)
                    val marks = result.landmarks().firstOrNull()
                    val world = try { result.worldLandmarks().firstOrNull() } catch (e: RuntimeException) { null }
                    onFrame(toFrame(marks, world, timestamp, aspect, latency))
                } catch (e: RuntimeException) {
                    // This runs on MediaPipe's thread, where an escape would kill the process and the beat.
                    Log.e(TAG, "pose result failed", e)
                }
            }
            .setErrorListener { e -> Log.e(TAG, "pose error", e) }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    /** Takes ownership of [image] and always closes it. */
    fun submit(image: ImageProxy) {
        val now = SystemClock.uptimeMillis()
        if (!closed) {
            synchronized(statsLatency) {
                if (cameraStatsStartMs == 0L) cameraStatsStartMs = now else cameraIntervals++
                analysisWidth = image.width
                analysisHeight = image.height
            }
        }
        val accepted = synchronized(lifecycleLock) {
            if (closed || now - lastAcceptedMs < frameGapMs) false else {
                lastAcceptedMs = now
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
        } catch (e: Throwable) {
            // A bad frame must not kill the camera thread, even out of memory. The session sees tracking lost.
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
                    onFrame(PoseFrame(now, null, null, SystemClock.uptimeMillis() - now).also { writeTrace(it) })
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
            val bitmap = proxy.toBitmap()
            detector.detectAsync(BitmapImageBuilder(bitmap).build(), processing, now)
            frameDump?.offer(bitmap, rotation, now)
        }
    }

    /** One line every few seconds, so a slow phone can be diagnosed from logcat. */
    private fun logStats(timeMs: Long, latency: Long) {
        synchronized(statsLatency) {
            if (statsStartMs == 0L) statsStartMs = timeMs
            statsLatency.add(latency)
            val span = timeMs - statsStartMs
            if (span < STATS_EVERY_MS) return
            val sorted = statsLatency.sorted()
            val cameraSpan = timeMs - cameraStatsStartMs
            val cameraFps = if (cameraSpan > 0L) cameraIntervals * 1000.0 / cameraSpan else Double.NaN
            Log.i(
                TAG,
                "pose delegate=$delegateName model=$modelName results_per_s=%.1f latency_median_ms=%d latency_p90_ms=%d frame_gap_ms=%d thermal_status=%d thermal_headroom=%.2f battery_temp_c=%.1f camera_frames_per_s=%.1f analysis_size=%dx%d"
                    .format(
                        statsLatency.size * 1000.0 / span, sorted[sorted.size / 2], sorted[sorted.size * 9 / 10],
                        frameGapMs, thermalStatus, headroom(), batteryTempC,
                        cameraFps, analysisWidth, analysisHeight,
                    ),
            )
            statsLatency.clear()
            statsStartMs = timeMs
            cameraStatsStartMs = timeMs
            cameraIntervals = 0L
        }
    }

    private fun toFrame(marks: List<NormalizedLandmark>?, world: List<Landmark>?, timeMs: Long, aspect: Float, latency: Long): PoseFrame {
        if (marks == null) return PoseFrame(timeMs, null, null, latency, aspect).also { writeTrace(it) }
        fun vis(i: Int) = marks[i].visibility().orElse(0f)
        val leftScore = vis(LEFT_SHOULDER) + vis(LEFT_ELBOW) + vis(LEFT_WRIST) + vis(LEFT_HIP)
        val rightScore = vis(RIGHT_SHOULDER) + vis(RIGHT_ELBOW) + vis(RIGHT_WRIST) + vis(RIGHT_HIP)
        // Hysteresis: keep the current near arm unless the other is clearly better seen.
        val right = if (nearRight) !(leftScore > rightScore + SIDE_SWITCH_MARGIN) else rightScore > leftScore + SIDE_SWITCH_MARGIN
        nearRight = right
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
        val sideOn = PoseMath.sideOn(
            marks[LEFT_SHOULDER].x(), vis(LEFT_SHOULDER),
            marks[RIGHT_SHOULDER].x(), vis(RIGHT_SHOULDER),
            arm, right, aspect,
        )
        val worldElbow = worldArm(world, right)
        val sample = PoseSample(
            timeMs = timeMs,
            wristSignal = PoseMath.wristSignal(arm, aspect),
            elbowDegrees = PoseMath.elbowDegrees(arm, aspect),
            minVisibility = minOf(arm.shoulderVis, arm.elbowVis, arm.wristVis, arm.hipVis),
            elbowVisibility = arm.elbowVis,
            sideOn = sideOn,
            worldElbowDegrees = worldElbow?.first,
        )
        return PoseFrame(timeMs, sample, arm, latency, aspect).also { writeTrace(it, if (right) "R" else "L", sideOn, worldElbow) }
    }

    /** World elbow angle and shoulder-over-wrist metres for the chosen arm, or null if unavailable. */
    private fun worldArm(world: List<Landmark>?, right: Boolean): Pair<Double?, Double?>? {
        if (world == null || world.size <= RIGHT_HIP) return null
        return try {
            val s = world[if (right) RIGHT_SHOULDER else LEFT_SHOULDER]
            val e = world[if (right) RIGHT_ELBOW else LEFT_ELBOW]
            val w = world[if (right) RIGHT_WRIST else LEFT_WRIST]
            WorldMath.elbowDegrees(s.x(), s.y(), s.z(), e.x(), e.y(), e.z(), w.x(), w.y(), w.z()) to
                WorldMath.shoulderOverWrist(s.x(), s.z(), w.x(), w.z())
        } catch (ex: RuntimeException) {
            null
        }
    }

    /** CSV keeps its original prefix; appended coordinates/visibility describe the chosen near arm. */
    fun startTrace(dir: File) {
        synchronized(this) {
            trace?.close()
            frameDump?.close()
            frameDump = null
            dir.mkdirs()
            val sessionId = System.currentTimeMillis()
            if (BuildConfig.DEBUG) {
                DebugOptions.refreshFrom(appContext)
                if (DebugOptions.captureFrames) frameDump = FrameDump.create(File(dir, "frames-$sessionId"))
            }
            trace = PrintWriter(File(dir, "session-$sessionId.csv").bufferedWriter()).also {
                it.println("t_ms,wrist_signal,elbow_deg,min_vis,latency_ms,frame_gap_ms,thermal_status,battery_temp_c,side,shoulder_x,shoulder_y,elbow_x,elbow_y,wrist_x,wrist_y,hip_x,hip_y,shoulder_vis,elbow_vis,wrist_vis,hip_vis,side_on,world_elbow_deg,world_shoulder_over_wrist")
            }
        }
    }

    fun stopTrace() {
        synchronized(this) {
            trace?.close()
            trace = null
            frameDump?.close()
            frameDump = null
        }
    }

    private fun writeTrace(frame: PoseFrame, side: String = "", sideOn: Boolean? = null, world: Pair<Double?, Double?>? = null) {
        synchronized(this) {
            val s = frame.sample
            val a = frame.arm
            trace?.println("${frame.timeMs},${s?.wristSignal ?: ""},${s?.elbowDegrees ?: ""},${s?.minVisibility ?: ""},${frame.latencyMs},$frameGapMs,$thermalStatus,${batteryTempC.takeIf { it.isFinite() } ?: ""},$side,${a?.shoulderX ?: ""},${a?.shoulderY ?: ""},${a?.elbowX ?: ""},${a?.elbowY ?: ""},${a?.wristX ?: ""},${a?.wristY ?: ""},${a?.hipX ?: ""},${a?.hipY ?: ""},${a?.shoulderVis ?: ""},${a?.elbowVis ?: ""},${a?.wristVis ?: ""},${a?.hipVis ?: ""},${sideOn?.let { if (it) 1 else 0 } ?: ""},${world?.first ?: ""},${world?.second ?: ""}")
            // A crash must not lose the recording: it is the evidence.
            if (++traceRowsSinceFlush >= TRACE_FLUSH_ROWS) {
                trace?.flush()
                traceRowsSinceFlush = 0
            }
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
                thermalListener?.let { power?.removeThermalStatusListener(it) }
            } catch (e: RuntimeException) {
                Log.w(TAG, "thermal listener removal failed", e)
            }
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
        const val MODEL = "pose_landmarker_full.task"
        const val MODEL_NAME = "full"
        const val ENV_EVERY_MS = 5_000L
        const val STATS_EVERY_MS = 5_000L
        const val TRACE_FLUSH_ROWS = 30
        const val SIDE_SWITCH_MARGIN = 0.4f
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
object WorldMath {
    /** 3D angle at the elbow from MediaPipe world landmarks (metres). Null for non-finite or degenerate input. */
    fun elbowDegrees(
        sx: Float, sy: Float, sz: Float, ex: Float, ey: Float, ez: Float, wx: Float, wy: Float, wz: Float,
    ): Double? {
        val ax = (sx - ex).toDouble(); val ay = (sy - ey).toDouble(); val az = (sz - ez).toDouble()
        val bx = (wx - ex).toDouble(); val by = (wy - ey).toDouble(); val bz = (wz - ez).toDouble()
        val lengths = sqrt(ax * ax + ay * ay + az * az) * sqrt(bx * bx + by * by + bz * bz)
        if (!lengths.isFinite() || lengths < 1e-9) return null
        return Math.toDegrees(acos(((ax * bx + ay * by + az * bz) / lengths).coerceIn(-1.0, 1.0)))
    }

    /** Horizontal (x/z plane; world y is vertical) distance in metres between shoulder and wrist. */
    fun shoulderOverWrist(sx: Float, sz: Float, wx: Float, wz: Float): Double? =
        hypot((sx - wx).toDouble(), (sz - wz).toDouble()).takeIf { it.isFinite() }
}

object PoseMath {
    /** Shoulder-to-shoulder gap over torso length: about 0.8 facing the camera, about 0 side-on. 0.35 is roughly 25 degrees of turn. */
    const val SIDE_ON_MAX_SHOULDER_RATIO = 0.35

    /** Below this the far shoulder is hidden, which is itself what a side-on view looks like. */
    const val FAR_SHOULDER_MIN_VISIBILITY = 0.3f

    /**
     * True when the shoulders nearly overlap horizontally relative to torso length, so a 2D elbow angle is meaningful.
     * Torso uses the chosen near-side shoulder and hip, as in [wristSignal]. Garbage input gives false.
     */
    fun sideOn(
        leftX: Float, leftVis: Float,
        rightX: Float, rightVis: Float,
        arm: ArmLandmarks, nearRight: Boolean, aspect: Float,
    ): Boolean {
        val dx = ((arm.shoulderX - arm.hipX) * aspect).toDouble()
        val dy = (arm.shoulderY - arm.hipY).toDouble()
        val torso = sqrt(dx * dx + dy * dy)
        if (!torso.isFinite() || torso <= 1e-3) return false
        val farVis = if (nearRight) leftVis else rightVis
        if (!(farVis >= FAR_SHOULDER_MIN_VISIBILITY)) return farVis.isFinite()
        val gap = Math.abs(((leftX - rightX) * aspect).toDouble())
        return gap.isFinite() && gap / torso <= SIDE_ON_MAX_SHOULDER_RATIO
    }

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
