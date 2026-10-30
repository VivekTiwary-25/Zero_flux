# Android Lint and Runtime Hazard Audit

Run on reference copy with `gradlew lintDebug` (`minSdk = 30`, `compileSdk = 37`, `targetSdk = 37`).
Lint reported 1 error, 19 warnings, and 1 hint.

Real issues are ranked worst first below (missing permissions, NewApi above API 30, wrong service types, and runtime crash hazards). Style, dependency updates, and non-crashing platform suggestions are skipped.

---

## 1. Missing Permissions & Runtime Crash in Voice Control

- **File & Line:** `app/src/main/java/com/zeroflux/cprcoach/voice/HandsFreeController.kt:158`
- **Issue:** `MissingPermission` (Error — build failed).
- **Code:**
  ```kotlin
  val input = AudioRecord(
      MediaRecorder.AudioSource.VOICE_COMMUNICATION,
      SAMPLE_RATE,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT,
      maxOf(minBufferBytes, silero.windowSize * Short.SIZE_BYTES),
  )
  ```
- **Hazard:** Android Lint's `MissingPermission` detector flags `AudioRecord` because the check is performed via an indirect helper function (`hasMicrophonePermission()` at line 157) rather than an inline check recognized by static analysis. At runtime, if `RECORD_AUDIO` is revoked or denied when entering `capture()`, `AudioRecord(...)` throws `java.lang.SecurityException`, which is uncaught here and will crash the hands-free voice coroutine and session.
- **Fix:** Wrap the `AudioRecord` constructor in an explicit inline `ContextCompat.checkSelfPermission` check and catch `SecurityException`.

---

## 2. Unguarded Foreground Service Microphone Promotion Crash

- **File & Line:** `app/src/main/java/com/zeroflux/cprcoach/lab/LabAudioService.kt:40-44`
- **Issue:** Foreground service microphone permission requirement (Android 14+ / targetSdk 34+ runtime crash).
- **Code:**
  ```kotlin
  startForeground(
      NOTIFICATION_ID,
      buildNotification(),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
  )
  ```
- **Hazard:** On Android 14+ (`targetSdk` is 37), calling `startForeground()` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` requires runtime `RECORD_AUDIO` permission to be granted at invocation time. If called without permission, Android throws `SecurityException`. Unlike `MetronomeService.kt:118-138`, `LabAudioService` does not wrap `startForeground()` in a `try/catch` block, resulting in a fatal process crash on the main thread in `onStartCommand()`.
- **Fix:** Guard with `ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)` and wrap `startForeground()` in `try/catch (e: Exception)` to fail gracefully.

---

## 3. Uncaught SecurityException on Background Thread

- **File & Line:** `app/src/main/java/com/zeroflux/cprcoach/lab/LabAudioService.kt:99`
- **Issue:** Suppressed `MissingPermission` with no runtime exception handling.
- **Code:**
  ```kotlin
  @SuppressLint("MissingPermission")
  private fun runMicMeter() {
      ...
      val record = AudioRecord(...)
  ```
- **Hazard:** Line 95 suppresses Lint's `MissingPermission` check with `@SuppressLint("MissingPermission")`. Line 99 instantiates `AudioRecord` on a standalone thread (`Thread({ runMicMeter() }, "lab-mic")`). If microphone permission is missing or revoked, `AudioRecord` throws `SecurityException`. Because this thread has no exception handler, an unhandled exception terminates the entire application process.
- **Fix:** Wrap `AudioRecord` initialization in `try/catch (e: SecurityException)` and abort metering if permission is denied.

---

## 4. Unguarded InlinedApi Above minSdk 30

- **File & Line:** `app/src/main/java/com/zeroflux/cprcoach/lab/LabActivity.kt:72`
- **Issue:** `InlinedApi` (Warning).
- **Code:**
  ```kotlin
  permissions.launch(
      arrayOf(
          Manifest.permission.CAMERA,
          Manifest.permission.RECORD_AUDIO,
          Manifest.permission.POST_NOTIFICATIONS,
      )
  )
  ```
- **Hazard:** `Manifest.permission.POST_NOTIFICATIONS` requires API level 33, while `minSdk` is 30. Unlike `MainActivity.kt:31` which correctly guards with `if (Build.VERSION.SDK_INT >= 33)`, `LabActivity.kt:72` passes `POST_NOTIFICATIONS` unconditionally on Android 11–12 (API 30–32).
- **Fix:** Only add `POST_NOTIFICATIONS` to the permission request array when `Build.VERSION.SDK_INT >= 33`.

---

## 5. Foreground Service Type & Audio Usage Semantic Mismatch

- **File & Line:** `app/src/main/java/com/zeroflux/cprcoach/audio/MetronomeService.kt:87, 132` / `app/src/main/AndroidManifest.xml:43`
- **Issue:** Declared foreground service type vs audio stream attribute mismatch.
- **Code:**
  - `AndroidManifest.xml:43`: `android:foregroundServiceType="mediaPlayback"`
  - `MetronomeService.kt:87`: `val created = AudioEngine(this, AudioAttributes.USAGE_VOICE_COMMUNICATION)`
  - `MetronomeService.kt:132`: `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`
- **Hazard:** `MetronomeService` uses `USAGE_VOICE_COMMUNICATION` so metronome clicks remain audible during an active phone call. However, under Android 14+ service policies, `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` is designated for media playback (`USAGE_MEDIA`). While `enterForeground()` (line 118) catches promotion exceptions to prevent crashes, the service type and stream usage are semantically mismatched.
- **Fix:** Retain `try/catch` in `enterForeground()` and ensure compliance with Google Play foreground service declaration policies.

---

## Skipped Lint Warnings (Non-Crashing / Style)

The following warnings from `lintDebug` were evaluated and skipped per instructions:
- **`UseKtx` (5 occurrences):** Style recommendations to use `SharedPreferences.edit` and `String.toUri` (`AppSettings.kt:50,56`, `EmergencyHost.kt:31`, `LabActivity.kt:117`, `MainActivity.kt:27`).
- **`LockedOrientationActivity` / `DiscouragedApi` (3 occurrences):** Fixed orientation warnings in `AndroidManifest.xml:23,34` for ChromeOS and future Android 16 behavior.
- **`GradleDependency` / `AndroidGradlePluginVersion` / `NewerVersionAvailable` (6 occurrences):** Version bump suggestions in `gradle/libs.versions.toml`.
- **`ChromeOsAbiSupport` (1 occurrence):** Missing x86_64 ABI filter for ChromeOS in `app/build.gradle.kts:20`.
- **`DataExtractionRules` (1 occurrence):** Deprecated `android:allowBackup` in `AndroidManifest.xml:14`.
- **`ObsoleteSdkInt` (1 occurrence):** Unnecessary `-v26` folder qualifier for `res/mipmap-anydpi-v26` when `minSdk = 30`.
- **`MonochromeLauncherIcon` (1 occurrence):** Missing `<monochrome>` layer in launcher adaptive icon.
- **`AutoboxingStateCreation` (1 hint):** `mutableStateOf(0.0)` instead of `mutableDoubleStateOf(0.0)` in `LabActivity.kt:137`.
