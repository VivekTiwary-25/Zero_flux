package com.zeroflux.cprcoach

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.ContactsContract
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zeroflux.cprcoach.audio.MetronomeService
import com.zeroflux.cprcoach.ui.AppSettings
import com.zeroflux.cprcoach.ui.AssetStrings
import com.zeroflux.cprcoach.ui.CoachTheme
import com.zeroflux.cprcoach.ui.EmergencyContact
import com.zeroflux.cprcoach.ui.EmergencyContactDialog
import com.zeroflux.cprcoach.ui.HomeScreen
import com.zeroflux.cprcoach.ui.DashboardScreen
import com.zeroflux.cprcoach.ui.GuideScreen
import com.zeroflux.cprcoach.ui.LocalStrings
import com.zeroflux.cprcoach.voice.VoiceCommand

private enum class Screen { HOME, PRACTISE, EMERGENCY, GUIDE, DASHBOARD }

@Composable
fun AppRoot(activity: Activity, cameraPermission: Boolean) {
    val settings = remember { AppSettings(activity.applicationContext) }
    var language by remember { mutableStateOf(settings.language) }
    var emergencyContact by remember { mutableStateOf(settings.emergencyContact) }
    var showContactDialog by remember { mutableStateOf(false) }
    val contactPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        val contact = activity.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(0)?.trim().orEmpty()
            val number = cursor.getString(1)?.trim().orEmpty()
            if (name.isNotBlank() && number.isNotBlank()) EmergencyContact(name, number) else null
        }
        if (contact != null) {
            settings.emergencyContact = contact
            emergencyContact = contact
        }
    }
    var screen by remember { mutableStateOf(Screen.HOME) }
    var cameraVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val voice = remember {
        VoiceRouter(activity.applicationContext, scope, language) { chosen ->
            settings.language = chosen
            language = chosen
        }
    }
    val micState by voice.state.collectAsState()
    val strings = remember(language) { AssetStrings.load(activity, language.code) }
    val goHome: () -> Unit = {
        // Stop immediately, not only after Compose disposes the old host.
        MetronomeService.stop(activity)
        cameraVisible = false
        screen = Screen.HOME
    }

    SideEffect { voice.setLanguage(language) }
    DisposableEffect(voice) {
        onDispose {
            MetronomeService.stop(activity)
            voice.release()
        }
    }
    DisposableEffect(activity) {
        val previousOrientation = activity.requestedOrientation
        val keptAwake = activity.window.attributes.flags and
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        onDispose {
            activity.requestedOrientation = previousOrientation
            if (!keptAwake) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    // Observe these in composition, not only inside SideEffect: child navigation can recompose alone.
    val orientation = if (screen != Screen.HOME && cameraVisible) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    val keepScreenOn = screen == Screen.PRACTISE || screen == Screen.EMERGENCY
    SideEffect {
        activity.requestedOrientation = orientation
        if (!keepScreenOn) {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    // Home consumes back too: the app must not silently exit with a service still running.
    // On Home, back leaves the app as usual.
    BackHandler(enabled = screen != Screen.HOME, onBack = goHome)

    CoachTheme {
        CompositionLocalProvider(LocalStrings provides strings) {
            when (screen) {
                Screen.HOME -> {
                    DisposableEffect(voice) {
                        val unbind = voice.bind(
                            setOf(VoiceCommand.EMERGENCY, VoiceCommand.PRACTISE),
                            allowLanguages = true,
                        ) { command ->
                            screen = when (command) {
                                VoiceCommand.EMERGENCY -> Screen.EMERGENCY
                                VoiceCommand.PRACTISE -> Screen.PRACTISE
                                else -> Screen.HOME
                            }
                        }
                        onDispose { unbind() }
                    }
                    HomeScreen(
                        language = language,
                        micState = micState,
                        onEmergency = { screen = Screen.EMERGENCY },
                        onPractise = { screen = Screen.PRACTISE },
                        onGuide = { screen = Screen.GUIDE },
                        onLanguageChosen = { chosen ->
                            voice.setLanguage(chosen)
                            settings.language = chosen
                            language = chosen
                        },
                        onMicTap = voice::toggle,
                        onDashboard = { screen = Screen.DASHBOARD },
                        emergencyContactName = emergencyContact?.name,
                        onEmergencyContact = { showContactDialog = true },
                    )
                    if (showContactDialog) {
                        EmergencyContactDialog(
                            current = emergencyContact,
                            onSave = { typed ->
                                // A blank name or number clears the contact (the store enforces it).
                                settings.emergencyContact = typed
                                emergencyContact = settings.emergencyContact
                                showContactDialog = false
                            },
                            onPick = {
                                showContactDialog = false
                                contactPicker.launch(
                                    Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI),
                                )
                            },
                            onClear = {
                                settings.emergencyContact = null
                                emergencyContact = null
                                showContactDialog = false
                            },
                            onDismiss = { showContactDialog = false },
                        )
                    }
                }
                Screen.PRACTISE -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    PractiseHost(
                        settings = settings,
                        language = language,
                        voice = voice,
                        cameraPermission = cameraPermission,
                        onCameraVisibleChange = { cameraVisible = it },
                        onHome = goHome,
                    )
                }
                // Edge to edge: its screens apply their own insets, and the camera and frame fill the display.
                Screen.EMERGENCY -> Box(Modifier.fillMaxSize()) {
                    EmergencyHost(
                        language = language,
                        voice = voice,
                        cameraPermission = cameraPermission,
                        onCameraVisibleChange = { cameraVisible = it },
                        onHome = goHome,
                        emergencyContact = emergencyContact,
                    )
                }
                Screen.GUIDE -> Box(Modifier.fillMaxSize()) {
                    GuideScreen(
                        onBack = goHome,
                        onEmergency = { screen = Screen.EMERGENCY },
                    )
                }
                Screen.DASHBOARD -> Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                    DashboardScreen(onHome = goHome)
                }
            }
        }
    }
}
