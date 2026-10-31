package com.zeroflux.cprcoach

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var cameraPermission by mutableStateOf(false)
    private var requestOnResume = true
    private var permissionRequestInFlight = false
    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionRequestInFlight = false
        refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestOnResume = savedInstanceState?.getBoolean("request_permissions_on_resume", true) ?: true
        permissionRequestInFlight = savedInstanceState?.getBoolean("permission_request_in_flight", false) ?: false
        refreshPermissions()
        setContent { AppRoot(this, cameraPermission) }
        warmPoseLibrary()
    }

    /**
     * Loads MediaPipe's native library on a low-priority thread once Home has been drawn, so the
     * first Practise tap does not pay for it on the main thread. Loading twice is a no-op, and a
     * failure here is ignored: PoseEngine loads the library itself and handles its own errors.
     */
    private fun warmPoseLibrary() {
        window.decorView.post {
            Thread({
                try {
                    System.loadLibrary("mediapipe_tasks_jni")
                } catch (_: Throwable) {
                }
            }, "pose-lib-warm").apply {
                priority = Thread.MIN_PRIORITY
                isDaemon = true
            }.start()
        }
    }

    override fun onStop() {
        super.onStop()
        // Dialog returns and configuration changes are not another app open.
        if (!permissionRequestInFlight && !isChangingConfigurations) requestOnResume = true
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        if (requestOnResume && !permissionRequestInFlight) {
            requestOnResume = false
            requestMissingPermissions()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("request_permissions_on_resume", requestOnResume)
        outState.putBoolean("permission_request_in_flight", permissionRequestInFlight)
        super.onSaveInstanceState(outState)
    }

    private fun requestMissingPermissions() {
        val prefs = getSharedPreferences("launch_permissions", MODE_PRIVATE)
        // Older installs used one flag after requesting every permission.
        val previouslyRequestedAll = prefs.getBoolean("requested", false)
        val permissions = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED &&
                (!prefs.getBoolean(permission, previouslyRequestedAll) ||
                    shouldShowRequestPermissionRationale(permission))
        }
        if (permissions.isEmpty()) return
        val editor = prefs.edit()
        permissions.forEach { editor.putBoolean(it, true) }
        editor.apply()
        permissionRequestInFlight = true
        permissionRequest.launch(permissions.toTypedArray())
    }

    private fun refreshPermissions() {
        cameraPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
}
