package com.zeroflux.cprcoach.audio

/** Where Emergency audio should go. MEDIA is the normal loudspeaker path. */
enum class AudioRoute { MEDIA, VOICE_SPEAKER }

/**
 * Pure decision logic: a call forces voice usage plus the built-in speaker, otherwise media.
 * Each method returns the new route only when it changed, else null, so repeated input is a no-op.
 * Single-threaded use (main thread).
 */
class CallRoutePolicy {
    var route: AudioRoute = AudioRoute.MEDIA
        private set
    private var stopped = false

    fun onCallActive(active: Boolean): AudioRoute? {
        if (stopped) return null
        return change(if (active) AudioRoute.VOICE_SPEAKER else AudioRoute.MEDIA)
    }

    /** Stopping during a call restores media; afterwards nothing changes again. */
    fun onStop(): AudioRoute? {
        if (stopped) return null
        stopped = true
        return change(AudioRoute.MEDIA)
    }

    private fun change(next: AudioRoute): AudioRoute? {
        if (next == route) return null
        route = next
        return next
    }

    companion object {
        /** AudioManager.MODE_IN_CALL = 2, MODE_IN_COMMUNICATION = 3. */
        fun isCallMode(mode: Int): Boolean = mode == 2 || mode == 3
    }
}
