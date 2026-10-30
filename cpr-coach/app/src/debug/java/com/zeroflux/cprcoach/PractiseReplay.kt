package com.zeroflux.cprcoach

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import com.zeroflux.cprcoach.engine.ArmLandmarks
import com.zeroflux.cprcoach.engine.PoseFrame
import com.zeroflux.cprcoach.engine.PoseSample
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Debug input only: replay values and the synthetic framing arm are NOT camera measurements. */
internal object PractiseReplay {
    fun requestedFile(context: Context): String? {
        if (!BuildConfig.DEBUG) return null
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current.intent.getStringExtra("replay")
            val base = current.baseContext
            if (base === current) break
            current = base
        }
        return null
    }

    suspend fun play(context: Context, name: String, onFrame: (PoseFrame) -> Unit) {
        check(BuildConfig.DEBUG) { "Replay is available only in debug builds" }
        val rows = withContext(Dispatchers.IO) {
            val external = requireNotNull(context.getExternalFilesDir(null)) { "External files unavailable" }.canonicalFile
            val directory = File(external, "traces").canonicalFile
            require(directory == File(external, "traces")) { "Replay directory escapes external files" }
            // Accept a filename, not a path, including after resolving symlinks.
            require(name.isNotBlank() && name == File(name).name && '/' !in name && '\\' !in name) {
                "Replay must name a file directly inside traces/"
            }
            val file = File(directory, name).canonicalFile
            require(file.parentFile == directory) { "Replay file escapes traces/" }
            readRows(file)
        }
        val firstTime = rows.first().timeMs
        val startedAt = SystemClock.uptimeMillis()
        for (row in rows) {
            currentCoroutineContext().ensureActive()
            val timeMs = Math.addExact(startedAt, row.timeMs - firstTime)
            // Absolute deadlines avoid accumulating parsing/callback/delay drift.
            val waitMs = timeMs - SystemClock.uptimeMillis()
            if (waitMs > 0L) delay(waitMs)
            onFrame(row.frameAt(timeMs))
        }
        // EOF is not an indefinitely visible person; the normal session clock keeps running.
        onFrame(PoseFrame(SystemClock.uptimeMillis(), null, null, 0L))
    }

    internal fun readRows(file: File): List<ReplayRow> = file.bufferedReader().use { reader ->
        require(reader.readLine()?.removePrefix("\uFEFF") == "t_ms,wrist_signal,elbow_deg,min_vis,latency_ms") {
            "Expected a session CSV header"
        }
        val rows = ArrayList<ReplayRow>()
        reader.lineSequence().forEachIndexed { index, line ->
            if (line.isBlank()) return@forEachIndexed
            val cells = line.split(',')
            require(cells.size >= 5) { "Invalid CSV row ${index + 2}" }
            val time = cells[0].trim().toLong()
            require(time >= 0L && (rows.isEmpty() || time > rows.last().timeMs)) {
                "Replay timestamps must be nonnegative and strictly increasing"
            }
            // Session traces use empty pose fields for no person. Never fill in missing values.
            val pose = cells.subList(1, 4).map { it.trim() }
            val sample = if (pose.any { it.isEmpty() }) null else {
                val wrist = pose[0].toDouble()
                val elbow = pose[1].toDouble()
                val visibility = pose[2].toFloat()
                require(wrist.isFinite() && elbow.isFinite() && visibility.isFinite()) { "Non-finite pose value" }
                require(elbow in 0.0..180.0 && visibility in 0f..1f) { "Invalid pose value" }
                PoseSample(time, wrist, elbow, visibility)
            }
            // Missing replay latency has no measurement; this sentinel is never recorded or displayed.
            val latency = cells[4].trim().let { if (it.isEmpty()) 0L else it.toLong() }
            require(latency >= 0L) { "Invalid replay latency" }
            rows.add(ReplayRow(time, sample, latency))
        }
        require(rows.isNotEmpty()) { "Replay contains no frames" }
        rows
    }
}

internal data class ReplayRow(val timeMs: Long, val sample: PoseSample?, val latencyMs: Long) {
    fun frameAt(timeMs: Long): PoseFrame {
        val rebased = sample?.copy(timeMs = timeMs)
        // CSV has no coordinates. This explicitly synthetic arm tests framing, not pose geometry.
        val arm = rebased?.let {
            ArmLandmarks(
                shoulderX = 0.5f, shoulderY = 0.3f, shoulderVis = it.minVisibility,
                elbowX = 0.5f, elbowY = 0.5f, elbowVis = it.minVisibility,
                wristX = 0.5f, wristY = 0.7f, wristVis = it.minVisibility,
                hipX = 0.7f, hipY = 0.5f, hipVis = it.minVisibility,
            )
        }
        return PoseFrame(timeMs, rebased, arm, latencyMs)
    }
}
