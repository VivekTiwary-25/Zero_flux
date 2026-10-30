package com.zeroflux.cprcoach.lab

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * M0 test only: answers "does app audio survive a speaker call, and does the mic still hear?".
 * It runs as a foreground service so it keeps going while the dialer covers the app.
 */
class LabAudioService : Service() {

    @Volatile private var running = false
    private var metronomeThread: Thread? = null
    private var micThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running) return START_NOT_STICKY
        val usage = intent?.getIntExtra(EXTRA_USAGE, AudioAttributes.USAGE_MEDIA) ?: AudioAttributes.USAGE_MEDIA
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        running = true
        metronomeThread = Thread({ runMetronome(usage) }, "lab-metronome").also { it.start() }
        micThread = Thread({ runMicMeter() }, "lab-mic").also { it.start() }
        Log.i(TAG, "started usage=$usage")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        metronomeThread?.join(500)
        micThread?.join(500)
        _micDb.value = null
        Log.i(TAG, "stopped")
        super.onDestroy()
    }

    private fun runMetronome(usage: Int) {
        val beatSamples = SAMPLE_RATE * 60 / BPM
        val beat = ShortArray(beatSamples)
        val clickSamples = SAMPLE_RATE * CLICK_MS / 1000
        for (i in 0 until clickSamples) {
            val fade = 1.0 - i.toDouble() / clickSamples
            beat[i] = (sin(2 * PI * CLICK_HZ * i / SAMPLE_RATE) * fade * Short.MAX_VALUE * 0.9).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(beatSamples * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        // A blocking stream of whole beats keeps the tempo sample-exact, unlike a timer.
        while (running) {
            track.write(beat, 0, beat.size)
        }
        track.stop()
        track.release()
    }

    @SuppressLint("MissingPermission")
    private fun runMicMeter() {
        val rate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "mic failed to initialise")
            record.release()
            return
        }
        record.startRecording()
        val buffer = ShortArray(rate) // one second per log line
        while (running) {
            var filled = 0
            while (running && filled < buffer.size) {
                val read = record.read(buffer, filled, buffer.size - filled)
                if (read <= 0) break
                filled += read
            }
            if (filled == 0) continue
            var sum = 0.0
            for (i in 0 until filled) sum += buffer[i].toDouble() * buffer[i]
            val rms = sqrt(sum / filled)
            val db = if (rms < 1.0) -90.0 else 20 * log10(rms / Short.MAX_VALUE)
            _micDb.value = db
            Log.i(TAG, "mic_db=%.1f silenced=%b".format(db, record.activeRecordingConfiguration?.isClientSilenced))
        }
        record.stop()
        record.release()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Metronome", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("CPR Coach metronome")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "CPR_M0"
        private const val EXTRA_USAGE = "usage"
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 1
        private const val SAMPLE_RATE = 48000
        private const val BPM = 110
        private const val CLICK_MS = 30
        private const val CLICK_HZ = 1800.0

        private val _micDb = MutableStateFlow<Double?>(null)
        val micDb: StateFlow<Double?> = _micDb

        fun start(context: Context, usage: Int) {
            context.startForegroundService(
                Intent(context, LabAudioService::class.java).putExtra(EXTRA_USAGE, usage)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LabAudioService::class.java))
        }
    }
}
