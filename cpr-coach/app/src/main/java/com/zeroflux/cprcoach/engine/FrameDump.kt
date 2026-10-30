package com.zeroflux.cprcoach.engine

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.zeroflux.cprcoach.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Debug-only launch options, read from the launching Activity's intent extras:
 * `--es pose_model lite|full|heavy` and `--ez capture_frames true`.
 * Always inert in release builds.
 */
object DebugOptions {
    @Volatile var poseModel: String? = null
        private set
    @Volatile var captureFrames: Boolean = false
        private set

    /** Picks up the extras if [context] is (or wraps) an Activity; otherwise keeps what an earlier call saw. */
    fun refreshFrom(context: Context?) {
        if (!BuildConfig.DEBUG) return
        try {
            var c: Context? = context
            while (c is ContextWrapper && c !is Activity) c = c.baseContext
            val intent = (c as? Activity)?.intent ?: return
            poseModel = intent.getStringExtra("pose_model")?.trim()?.lowercase()
            captureFrames = intent.getBooleanExtra("capture_frames", false)
        } catch (e: RuntimeException) {
            Log.w("CPR_POSE", "debug options unreadable: ${e.javaClass.simpleName}")
        }
    }
}

/**
 * Saves a small rotated JPEG about every [INTERVAL_MS], named by the trace t_ms, for labelling later.
 * The work happens on a separate thread; [offer] never blocks and drops the frame when that thread is busy.
 * Images are private: nothing here logs a path or file name.
 */
class FrameDump private constructor(private val dir: File) {
    private val busy = AtomicBoolean(false)
    private val saved = AtomicInteger(0)
    private val closed = AtomicBoolean(false)
    @Volatile private var lastOfferMs = Long.MIN_VALUE
    private val executor = Executors.newSingleThreadExecutor(ThreadFactory { r ->
        Thread(r, "CPR_FRAME_DUMP").also { it.isDaemon = true; it.priority = Thread.MIN_PRIORITY }
    })

    /** Called on the analyzer thread with the unrotated frame. Cheap unless a capture is due. */
    fun offer(source: Bitmap, rotationDegrees: Int, tMs: Long) {
        try {
            if (closed.get() || !shouldCapture(tMs, lastOfferMs, saved.get(), busy.get())) return
            if (!busy.compareAndSet(false, true)) return
            lastOfferMs = tMs
            val rotated = rotationDegrees == 90 || rotationDegrees == 270
            val width = if (rotated) source.height else source.width
            val scale = TARGET_WIDTH.toFloat() / width
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postRotate(rotationDegrees.toFloat())
            }
            // One small copy here, so the camera frame can be reused; compressing happens elsewhere.
            val small = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            try {
                executor.execute { write(small, tMs) }
            } catch (e: Throwable) {
                small.recycle()
                busy.set(false)
            }
        } catch (e: Throwable) {
            busy.set(false)
            Log.w(TAG, "frame capture skipped: ${e.javaClass.simpleName}")
        }
    }

    private fun write(bitmap: Bitmap, tMs: Long) {
        try {
            if (closed.get()) return
            dir.mkdirs()
            FileOutputStream(File(dir, "$tMs.jpg")).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            saved.incrementAndGet()
        } catch (e: Throwable) {
            Log.w(TAG, "frame capture failed: ${e.javaClass.simpleName}")
        } finally {
            bitmap.recycle()
            busy.set(false)
        }
    }

    fun close() {
        if (closed.compareAndSet(false, true)) executor.shutdown()
    }

    companion object {
        private const val TAG = "CPR_POSE"
        const val INTERVAL_MS = 500L
        const val MAX_IMAGES = 1200
        const val TARGET_WIDTH = 480

        /** Pure rule: due, under the cap, and the writer is free. */
        fun shouldCapture(nowMs: Long, lastMs: Long, savedCount: Int, busy: Boolean): Boolean =
            !busy && savedCount < MAX_IMAGES && (lastMs == Long.MIN_VALUE || nowMs - lastMs >= INTERVAL_MS)

        /** Null in release builds, so nothing can run there. */
        fun create(dir: File): FrameDump? {
            if (!BuildConfig.DEBUG) return null
            return try {
                dir.mkdirs()
                FrameDump(dir)
            } catch (e: Throwable) {
                Log.w(TAG, "frame capture unavailable: ${e.javaClass.simpleName}")
                null
            }
        }
    }
}
