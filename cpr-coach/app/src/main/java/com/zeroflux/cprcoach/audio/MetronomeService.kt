package com.zeroflux.cprcoach.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.os.IBinder
import android.util.Log
import com.zeroflux.cprcoach.ui.AssetStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Foreground mediaPlayback service so the metronome keeps playing while the dialer covers the app.
 * A denied notification must not stop the audio.
 */
class MetronomeService : Service() {

    private var activeLanguage: String? = null
    private var ownedEngine: AudioEngine? = null
    private var routeController: CallRouteController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val languageCode = intent?.getStringExtra(EXTRA_LANGUAGE)?.takeIf { it.isNotBlank() } ?: "en"
        // Even a queued stop must acknowledge startForegroundService before stopping.
        if (!enterForeground(languageCode)) {
            stopPlayback()
            return START_NOT_STICKY
        }
        if (stopRequested) {
            stopPlayback()
            return START_NOT_STICKY
        }
        // A newer start supersedes a stop command that was still queued.
        if (intent?.action == ACTION_STOP) return START_NOT_STICKY
        try {
            ensureEngine(languageCode)
        } catch (e: Exception) {
            Log.e(TAG, "engine start failed", e)
            stopPlayback()
            return START_NOT_STICKY
        }
        if (stopRequested) {
            stopPlayback()
            return START_NOT_STICKY
        }
        Log.i(TAG, "started language=$languageCode")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        leaveForeground()
        releaseEngine()
        super.onDestroy()
    }

    private fun stopPlayback() {
        releaseEngine()
        leaveForeground()
        stopSelf()
    }

    private fun leaveForeground() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "stopForeground failed", e)
        }
    }

    private fun ensureEngine(languageCode: String) {
        val existing = ownedEngine
        if (existing != null) {
            if (languageCode != activeLanguage) {
                existing.setLanguage(languageCode)
                activeLanguage = languageCode
            }
            if (!existing.metronomeRunning) existing.startMetronome(METRONOME_BPM)
            return
        }
        // Media on the loudspeaker; CallRouteController switches to voice+speaker only during a call
        // (M0: media audio stopped during a call, voice audio kept playing).
        val created = AudioEngine(this, AudioAttributes.USAGE_MEDIA)
        try {
            created.setLanguage(languageCode)
            created.startMetronome(METRONOME_BPM)
            routeController = CallRouteController(this, created).also { it.start() }
        } catch (e: Exception) {
            routeController?.stop()
            routeController = null
            try {
                created.release()
            } catch (releaseError: Exception) {
                Log.w(TAG, "engine release failed", releaseError)
            }
            throw e
        }
        activeLanguage = languageCode
        ownedEngine = created
        _engine.value = created
    }

    private fun releaseEngine() {
        routeController?.stop()
        routeController = null
        val current = ownedEngine ?: return
        ownedEngine = null
        _engine.compareAndSet(current, null)
        activeLanguage = null
        try {
            current.release()
        } catch (e: Exception) {
            Log.w(TAG, "engine release failed", e)
        }
    }

    /** Notification permission denial does not prevent media foreground promotion. */
    private fun enterForeground(languageCode: String): Boolean {
        return try {
            val title = notificationTitle(languageCode)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW),
            )
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true)
                .build()
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "foreground promotion failed; stopping service", e)
            false
        }
    }

    private fun notificationTitle(languageCode: String): String {
        val code = languageCode.ifBlank { "en" }
        val resolved = AssetStrings.load(this, code).get(TITLE_KEY)
        return if (resolved == TITLE_KEY) TITLE_FALLBACK else resolved
    }

    companion object {
        private const val TAG = "MetronomeService"
        private const val EXTRA_LANGUAGE = "language"
        private const val ACTION_STOP = "com.zeroflux.cprcoach.audio.STOP_METRONOME"
        private const val CHANNEL_ID = "cpr_metronome"
        private const val NOTIFICATION_ID = 1
        private const val METRONOME_BPM = 110
        private const val TITLE_KEY = "metronome_notification_title"
        private const val TITLE_FALLBACK = "CPR metronome"

        @Volatile
        private var stopRequested = false

        private val _engine = MutableStateFlow<AudioEngine?>(null)
        val engine: StateFlow<AudioEngine?> = _engine

        fun start(context: Context, languageCode: String) {
            stopRequested = false
            val intent = Intent(context, MetronomeService::class.java)
                .putExtra(EXTRA_LANGUAGE, languageCode)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService failed", e)
            }
        }

        fun stop(context: Context) {
            stopRequested = true
            try {
                // Queue the stop behind any pending foreground start, rather than destroying
                // the service before it has had a chance to promote itself.
                context.startService(Intent(context, MetronomeService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                Log.w(TAG, "stop command failed", e)
            }
        }
    }
}
