package com.zeroflux.cprcoach.lab

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.io.File
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** One tracked frame: the near-side arm and hip, in image-normalised coordinates. */
data class LabFrame(
    val timeMs: Long,
    val rightSide: Boolean,
    val shoulder: Point,
    val elbow: Point,
    val wrist: Point,
    val hip: Point,
    val elbowDegrees: Double,
    /** Wrist height above the hip, in torso lengths. This is the signal the rate will come from. */
    val wristSignal: Double,
    val minVisibility: Float,
    val inferenceMs: Long,
    val delegate: String,
) {
    data class Point(val x: Float, val y: Float, val visibility: Float)
}

/** M0 test only: runs MediaPipe on camera frames and saves a CSV to tune the rate math from. */
class LabPoseTracker(
    context: Context,
    private val traceDir: File,
    private val onFrame: (LabFrame?) -> Unit,
) {
    private val startTimes = ConcurrentHashMap<Long, Long>()
    private val aspects = ConcurrentHashMap<Long, Float>()
    private var lastTimestampMs = 0L
    private var writer: PrintWriter? = null
    private val delegateName: String
    private val landmarker: PoseLandmarker

    init {
        val (created, name) = try {
            createLandmarker(context, Delegate.GPU) to "GPU"
        } catch (e: RuntimeException) {
            Log.w(LabAudioService.TAG, "GPU delegate unavailable, falling back to CPU", e)
            createLandmarker(context, Delegate.CPU) to "CPU"
        }
        landmarker = created
        delegateName = name
    }

    private fun createLandmarker(context: Context, delegate: Delegate): PoseLandmarker {
        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")
                    .setDelegate(delegate)
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumPoses(1)
            .setResultListener { result, _ ->
                val timestamp = result.timestampMs()
                val started = startTimes.remove(timestamp) ?: return@setResultListener
                val aspect = aspects.remove(timestamp) ?: return@setResultListener
                val latency = SystemClock.uptimeMillis() - started
                val frame = result.landmarks().firstOrNull()?.let { toFrame(it, timestamp, aspect, latency) }
                frame?.let(::writeRow)
                onFrame(frame)
            }
            .setErrorListener { e -> Log.e(LabAudioService.TAG, "pose error", e) }
            .build()
        return PoseLandmarker.createFromOptions(context, options)
    }

    /** Takes ownership of [image] and always closes it. */
    fun submit(image: ImageProxy) {
        image.use { proxy ->
            val now = SystemClock.uptimeMillis()
            if (now <= lastTimestampMs) return // MediaPipe rejects non-increasing timestamps.
            lastTimestampMs = now
            val rotation = proxy.imageInfo.rotationDegrees
            val rotated = rotation == 90 || rotation == 270
            val width = if (rotated) proxy.height else proxy.width
            val height = if (rotated) proxy.width else proxy.height
            startTimes[now] = now
            aspects[now] = width.toFloat() / height
            val mpImage = BitmapImageBuilder(proxy.toBitmap()).build()
            val processing = ImageProcessingOptions.builder().setRotationDegrees(rotation).build()
            landmarker.detectAsync(mpImage, processing, now)
        }
    }

    private fun toFrame(
        marks: List<NormalizedLandmark>,
        timeMs: Long,
        aspect: Float,
        latency: Long,
    ): LabFrame {
        fun vis(i: Int) = marks[i].visibility().orElse(0f)
        fun point(i: Int) = LabFrame.Point(marks[i].x(), marks[i].y(), vis(i))

        val leftScore = vis(LEFT_SHOULDER) + vis(LEFT_ELBOW) + vis(LEFT_WRIST) + vis(LEFT_HIP)
        val rightScore = vis(RIGHT_SHOULDER) + vis(RIGHT_ELBOW) + vis(RIGHT_WRIST) + vis(RIGHT_HIP)
        val useRight = rightScore > leftScore
        val shoulder = point(if (useRight) RIGHT_SHOULDER else LEFT_SHOULDER)
        val elbow = point(if (useRight) RIGHT_ELBOW else LEFT_ELBOW)
        val wrist = point(if (useRight) RIGHT_WRIST else LEFT_WRIST)
        val hip = point(if (useRight) RIGHT_HIP else LEFT_HIP)

        // Normalised x and y have different units, so angles and lengths need x scaled by the aspect ratio.
        val torso = distance(shoulder, hip, aspect)
        return LabFrame(
            timeMs = timeMs,
            rightSide = useRight,
            shoulder = shoulder,
            elbow = elbow,
            wrist = wrist,
            hip = hip,
            elbowDegrees = angleAt(elbow, shoulder, wrist, aspect),
            wristSignal = if (torso > 1e-3) (hip.y - wrist.y) / torso else 0.0,
            minVisibility = minOf(shoulder.visibility, elbow.visibility, wrist.visibility, hip.visibility),
            inferenceMs = latency,
            delegate = delegateName,
        )
    }

    private fun distance(a: LabFrame.Point, b: LabFrame.Point, aspect: Float): Double {
        val dx = ((a.x - b.x) * aspect).toDouble()
        val dy = (a.y - b.y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    private fun angleAt(
        vertex: LabFrame.Point,
        a: LabFrame.Point,
        b: LabFrame.Point,
        aspect: Float,
    ): Double {
        val ax = ((a.x - vertex.x) * aspect).toDouble()
        val ay = (a.y - vertex.y).toDouble()
        val bx = ((b.x - vertex.x) * aspect).toDouble()
        val by = (b.y - vertex.y).toDouble()
        val lengths = sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by)
        if (lengths < 1e-6) return 0.0
        val cosine = ((ax * bx + ay * by) / lengths).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine)).let { abs(it) }
    }

    fun startRecording(): File {
        stopRecording()
        traceDir.mkdirs()
        val file = File(traceDir, "trace-${System.currentTimeMillis()}.csv")
        writer = PrintWriter(file.bufferedWriter()).also {
            it.println("t_ms,side,sh_x,sh_y,sh_v,el_x,el_y,el_v,wr_x,wr_y,wr_v,hip_x,hip_y,hip_v,elbow_deg,wrist_signal,latency_ms,delegate")
        }
        return file
    }

    fun stopRecording() {
        synchronized(this) {
            writer?.close()
            writer = null
        }
    }

    private fun writeRow(f: LabFrame) {
        synchronized(this) {
            writer?.println(
                listOf(
                    f.timeMs, if (f.rightSide) "R" else "L",
                    f.shoulder.x, f.shoulder.y, f.shoulder.visibility,
                    f.elbow.x, f.elbow.y, f.elbow.visibility,
                    f.wrist.x, f.wrist.y, f.wrist.visibility,
                    f.hip.x, f.hip.y, f.hip.visibility,
                    "%.1f".format(f.elbowDegrees), "%.4f".format(f.wristSignal),
                    f.inferenceMs, f.delegate,
                ).joinToString(",")
            )
        }
    }

    fun close() {
        stopRecording()
        landmarker.close()
    }

    private companion object {
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
