package com.zeroflux.cprcoach.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Keeps Emergency audio on the loudspeaker: media normally, voice usage plus a forced built-in
 * speaker only while a call is active. Call detection reads AudioManager.getMode() (no permission).
 * Main thread only. We never set the audio mode, so our own routing cannot flip the detection.
 */
class CallRouteController(context: Context, private val engine: AudioEngine) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val executor = context.applicationContext.mainExecutor
    private val handler = Handler(Looper.getMainLooper())
    private val policy = CallRoutePolicy()
    private var started = false
    private var weSetDevice = false
    private var weSetSpeakerphone = false

    private val modeListener = AudioManager.OnModeChangedListener { check() }
    private val poll = object : Runnable {
        override fun run() {
            if (!started) return
            check()
            handler.postDelayed(this, POLL_MS)
        }
    }

    fun start() {
        if (started) return
        started = true
        engine.setUsage(AudioAttributes.USAGE_MEDIA)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                audio.addOnModeChangedListener(executor, modeListener)
            } catch (e: Exception) {
                Log.w(TAG, "mode listener failed; polling only", e)
            }
        }
        handler.post(poll)
    }

    /** Restores any state we set, so the phone is never left on speaker. */
    fun stop() {
        if (!started) return
        started = false
        handler.removeCallbacks(poll)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                audio.removeOnModeChangedListener(modeListener)
            } catch (_: Exception) {
            }
        }
        policy.onStop()?.let { apply(it) }
        restoreDevice()
    }

    private fun check() {
        if (!started) return
        val inCall = CallRoutePolicy.isCallMode(audio.mode)
        policy.onCallActive(inCall)?.let { apply(it) }
    }

    private fun apply(route: AudioRoute) {
        Log.i(TAG, "route -> $route")
        if (route == AudioRoute.VOICE_SPEAKER) {
            forceSpeaker()
            engine.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        } else {
            engine.setUsage(AudioAttributes.USAGE_MEDIA)
            restoreDevice()
        }
    }

    private fun forceSpeaker() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val current = audio.communicationDevice
                if (current?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) return
                val speaker = audio.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker == null) {
                    Log.w(TAG, "no built-in speaker communication device")
                    return
                }
                weSetDevice = audio.setCommunicationDevice(speaker)
                if (!weSetDevice) Log.w(TAG, "setCommunicationDevice refused")
            } else {
                @Suppress("DEPRECATION")
                if (!audio.isSpeakerphoneOn) {
                    @Suppress("DEPRECATION")
                    audio.isSpeakerphoneOn = true
                    weSetSpeakerphone = true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "force speaker failed", e)
        }
    }

    private fun restoreDevice() {
        try {
            if (weSetDevice) {
                weSetDevice = false
                if (Build.VERSION.SDK_INT >= 31) audio.clearCommunicationDevice()
            }
            if (weSetSpeakerphone) {
                weSetSpeakerphone = false
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = false
            }
        } catch (e: Exception) {
            Log.w(TAG, "restore route failed", e)
        }
    }

    private companion object {
        const val TAG = "CallRoute"
        const val POLL_MS = 1000L
    }
}
