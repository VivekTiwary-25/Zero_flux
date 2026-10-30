package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.CameraUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The sound this session needs. AudioEngine provides it in the app, and tests use a fake. */
interface SessionAudio {
    fun play(key: String)
    fun startMetronome()
    fun stopMetronome()
    fun stopVoice() {}
}

/**
 * One Practise session: framing, countdown, the 2-minute live session, then the scorecard.
 * Call [onFrame] for every pose result and [tick] about four times a second, with one clock.
 * All calls must come from one thread.
 */
class PractiseSession(
    private val audio: SessionAudio,
    private val totalSeconds: Int = 120,
    private val countdownSeconds: Int = 3,
) {
    private enum class Phase { FRAMING, COUNTDOWN, LIVE, DONE }

    private var phase = Phase.FRAMING
    private val framing = FramingAdvisor()
    private var analyzer = LiveAnalyzer()
    private var coach = CoachPolicy()
    private var metrics = SessionMetrics()
    private var countdownEndMs = 0L
    private var lastTickMs: Long? = null
    private var liveMsLeft = 0L
    private var isPaused = false
    private var latestFraming = FramingAdvice(false, "frame_no_person")

    private val _ui = MutableStateFlow<CameraUiState>(CameraUiState.Framing(false, "frame_no_person"))
    val ui: StateFlow<CameraUiState> = _ui

    private val _scorecard = MutableStateFlow<Scorecard?>(null)

    /** Becomes non-null once when the session ends. */
    val scorecard: StateFlow<Scorecard?> = _scorecard

    fun onFrame(frame: PoseFrame) {
        when (phase) {
            Phase.FRAMING -> {
                latestFraming = framing.update(frame.arm, frame.timeMs)
                _ui.value = CameraUiState.Framing(latestFraming.ready, latestFraming.hintKey)
            }
            Phase.LIVE -> if (!isPaused) analyzer.addFrame(frame.sample)
            Phase.COUNTDOWN, Phase.DONE -> Unit
        }
    }

    /** Only works once framing says ready. */
    fun start(nowMs: Long) {
        if (phase != Phase.FRAMING || !latestFraming.ready) return
        phase = Phase.COUNTDOWN
        countdownEndMs = nowMs + countdownSeconds * 1000L
        audio.play("countdown")
        _ui.value = CameraUiState.Countdown(countdownSeconds)
    }

    fun tick(nowMs: Long) {
        when (phase) {
            Phase.COUNTDOWN -> tickCountdown(nowMs)
            Phase.LIVE -> tickLive(nowMs)
            Phase.FRAMING, Phase.DONE -> Unit
        }
        lastTickMs = nowMs
    }

    private fun tickCountdown(nowMs: Long) {
        val leftMs = countdownEndMs - nowMs
        if (leftMs > 0) {
            _ui.value = CameraUiState.Countdown(((leftMs + 999) / 1000).toInt())
            return
        }
        phase = Phase.LIVE
        liveMsLeft = totalSeconds * 1000L
        analyzer = LiveAnalyzer()
        coach = CoachPolicy()
        metrics = SessionMetrics()
        audio.startMetronome()
        publishLive(analyzer.read(nowMs))
    }

    private fun tickLive(nowMs: Long) {
        if (isPaused) {
            publishPaused()
            return
        }
        val reading = analyzer.read(nowMs)
        liveMsLeft -= nowMs - (lastTickMs ?: nowMs)
        metrics.add(reading)
        coach.update(reading)?.let { audio.play(it.key) }
        if (liveMsLeft <= 0) {
            finish(playDone = true)
            return
        }
        publishLive(reading)
    }

    private fun publishLive(reading: LiveReading) {
        _ui.value = CameraUiState.Live(
            tracking = reading.tracking,
            ratePerMinute = reading.ratePerMinute,
            inRange = reading.inRange,
            elbowsBent = reading.elbowsBent,
            secondsLeft = ((liveMsLeft + 999) / 1000).toInt().coerceAtLeast(0),
            totalSeconds = totalSeconds,
            isPaused = isPaused,
        )
    }

    /** Paused by the user: nothing is being measured, so there is no rate and no tracking state to report. */
    private fun publishPaused() {
        _ui.value = CameraUiState.Live(
            tracking = TrackingState.WARMING,
            ratePerMinute = null,
            inRange = null,
            elbowsBent = null,
            secondsLeft = ((liveMsLeft + 999) / 1000).toInt().coerceAtLeast(0),
            totalSeconds = totalSeconds,
            isPaused = true,
        )
    }

    fun togglePause() {
        if (phase != Phase.LIVE) return
        isPaused = !isPaused
        lastTickMs = null
        if (isPaused) {
            audio.stopMetronome()
            metrics.pause()
            publishPaused()
        } else {
            // The rescuer has moved, so old readings no longer apply.
            analyzer = LiveAnalyzer()
            audio.startMetronome()
        }
    }

    fun stop() {
        when (phase) {
            Phase.LIVE -> finish(playDone = false)
            // Nothing was measured yet, so go back to framing rather than show a scorecard.
            Phase.COUNTDOWN -> {
                audio.stopVoice()
                phase = Phase.FRAMING
                _ui.value = CameraUiState.Framing(latestFraming.ready, latestFraming.hintKey)
            }
            Phase.FRAMING, Phase.DONE -> Unit
        }
    }

    private fun finish(playDone: Boolean) {
        phase = Phase.DONE
        audio.stopMetronome()
        if (playDone) audio.play("session_done")
        _scorecard.value = metrics.scorecard()
    }
}
