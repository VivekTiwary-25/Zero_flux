# Review: Emergency Spoken Corrections and Call Voice Routing

## Summary

1. **`shoulders_over_hands` line:** Yes, it is spoken in Emergency mode when the big word is `SHOULDERS_OVER_HANDS`. The word shows after 1.0 s of steady leaning, and the spoken phrase plays after 2.0 s once voice cooldown allows.
2. **`elbows_straight` and `keep_going`:** Both are wired and spoken in Emergency mode. Neither is spoken while the big word is `SHOULDERS_OVER_HANDS`: `elbows_straight` plays when the big word is `STRAIGHTEN_ARMS` (or `STRAIGHTEN`), and `keep_going` plays when the big word is `KEEP_PUSHING` because stopping movement resets the word to `KEEP_PUSHING` immediately.
3. **Speech suppression:**
   - **Scripted opening steps:** Dynamic spoken corrections are fully suppressed while the scripted opening sequence (`em_call`, `em_hands`, `em_push`) plays.
   - **Connected call (`CallRouteController`):** Speech is **not** suppressed today. Metronome clicks and voice clips both continue playing over the speakerphone during a connected cellular call (`MODE_IN_CALL`). A minimal patch is provided below to mute spoken clips during a call while preserving the 110 BPM metronome.

---

## 1. Is `shoulders_over_hands` spoken when the big word is `SHOULDERS_OVER_HANDS`?

**Yes.** The spoken correction is fully wired from camera frames to audio output.

### Path and execution

1. **Posture detection:**
   - In `engine/PostureJudge.kt:56-65`, when rescuer shoulders deviate forward or backward beyond the calibrated baseline range, `verdict()` produces `PostureReading(good = false, issue = PostureIssue.SHOULDERS_NOT_OVER_HANDS, ...)`.
2. **Big word selection (`EmergencyCoach.kt`):**
   - In `EmergencyCoach.kt:98-102`, `wordFor(reading)` checks if the rescuer is pushing (`beating` with on-target or null rate, or `PAUSED && reading.moving`).
   - In `EmergencyCoach.kt:123`, `postureWord(reading)` maps `PostureIssue.SHOULDERS_NOT_OVER_HANDS` to `EmergencyWord.SHOULDERS_OVER_HANDS`.
   - In `EmergencyCoach.kt:71-76`, `candidate` must hold for `holdMs` (1,000 ms). Once held for 1.0 s, `coach.word` becomes `EmergencyWord.SHOULDERS_OVER_HANDS`.
   - In `EmergencyHost.kt:212`, `instructionKey` selects `word.key` (`"posture_shoulders_over_hands"`).
   - In `ui/EmergencyScreen.kt:137-147`, `BasicText` displays the localized string for `"posture_shoulders_over_hands"` (present in all 10 language JSON files in `assets/strings/<lang>.json`).
3. **Voice correction selection (`CoachPolicy.kt`):**
   - In `EmergencyHost.kt:188-190`, the 250 ms tick loop calls:
     ```kotlin
     if (playback.voiceIdle) {
         coach.nextCorrection(reading)?.let { playback.speak(it.key) }
     }
     ```
   - In `EmergencyCoach.kt:80`, `nextCorrection(reading)` delegates directly to `policy.update(reading)`.
   - In `engine/CoachPolicy.kt:163`, `wantedPhrases(reading, now)` appends `Phrase.SHOULDERS_OVER_HANDS` when `issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS`.
   - In `engine/CoachPolicy.kt:77-82`, `requiredMs(Phrase.SHOULDERS_OVER_HANDS)` requires `postureSteadyMs` (2,000 ms).
   - In `engine/CoachPolicy.kt:59`, voice cooldown requires `now - lastSpokenMs >= cooldownMs` (6,000 ms).
   - Once steady leaning persists for 2.0 s and cooldown passes, `CoachPolicy.update` returns `Phrase.SHOULDERS_OVER_HANDS`.
4. **Playback dispatch:**
   - `EmergencyHost.kt:189` passes `Phrase.SHOULDERS_OVER_HANDS.key` (`"shoulders_over_hands"`) to `playback.speak("shoulders_over_hands")`.
   - In `EmergencyHost.kt:368-372, 380-399`, `EmergencyPlayback` adds the key to `pending` and calls `audio.play("shoulders_over_hands")`.
   - `assets/clips/<lang>/shoulders_over_hands.wav` exists for all 10 supported languages (`bn`, `en`, `gu`, `hi`, `kn`, `ml`, `mr`, `or`, `ta`, `te`).

### Timing relation between screen word and voice

- At `t = 0.0 s` of leaning: screen word is `KEEP_PUSHING` (or previous word).
- At `t = 1.0 s` of steady leaning: screen word switches to `SHOULDERS_OVER_HANDS`.
- At `t = 2.0 s` of steady leaning: voice speaks `"shoulders_over_hands"`.
- Because 2.0 s > 1.0 s, the big word on screen is already `SHOULDERS_OVER_HANDS` when `"shoulders_over_hands"` plays.
- This was empirically verified in `EmergencyTimelineTest.kt` on `tools/testdata/real-cph2467-15.csv`:
  - `t = 67.00s`: big word transitions to `posture_shoulders_over_hands`.
  - `t = 68.00s`: voice speaks `shoulders_over_hands` while the big word is `posture_shoulders_over_hands`.

---

## 2. Are `elbows_straight` and `keep_going` spoken there?

### In Emergency mode generally: YES

1. **`elbows_straight`:**
   - Path: `EmergencyHost.kt:188-190` -> `EmergencyCoach.kt:80` -> `CoachPolicy.kt:161-162`.
   - Trigger: `reading.posture?.issue == PostureIssue.ELBOWS_BENT` (or `reading.elbowsBent == true` when posture is null).
   - When steady for `postureSteadyMs` (2.0 s) and cooldown has passed, `CoachPolicy.update` returns `Phrase.ELBOWS_STRAIGHT`.
   - `EmergencyPlayback.speak("elbows_straight")` plays `assets/clips/<lang>/elbows_straight.wav`.
   - Verified on `real-cph2467-15.csv`: spoken at `t = 42.25s` and `t = 57.75s`.
2. **`keep_going`:**
   - Path: `EmergencyHost.kt:188-190` -> `EmergencyCoach.kt:80` -> `CoachPolicy.kt:88-120, 150`.
   - Trigger: `reading.tracking == TrackingState.PAUSED` with mostly still readings (`!reading.moving`) for `MIN_PAUSE_MS` (2.0 s).
   - Once `pendingRecovery` is true and cooldown has passed, `CoachPolicy.update` returns `Phrase.KEEP_GOING`.
   - `EmergencyPlayback.speak("keep_going")` plays `assets/clips/<lang>/keep_going.wav`.
   - Verified on `real-cph2467-15.csv`: spoken at `t = 35.00s`.

### While the big word is specifically `SHOULDERS_OVER_HANDS`: NO

Neither `elbows_straight` nor `keep_going` is spoken while the big word is `SHOULDERS_OVER_HANDS`:

1. **`elbows_straight`:**
   - In `EmergencyCoach.kt:121-125`, `reading.posture?.issue` returns a single enum value. `ELBOWS_BENT` and `SHOULDERS_NOT_OVER_HANDS` are mutually exclusive.
   - In `CoachPolicy.kt:161`, when `reading.posture != null`, `elbowsBent` is evaluated as `issue == PostureIssue.ELBOWS_BENT`, which is false during leaning.
   - If form degrades from leaning to bent elbows, the big word requires 1.0 s of hold to flip to `STRAIGHTEN_ARMS` (`EmergencyCoach.kt:76`), whereas `elbows_straight` voice requires 2.0 s of steady time (`CoachPolicy.kt:80`). By the time `elbows_straight` is spoken, the screen word is already `STRAIGHTEN_ARMS`.
2. **`keep_going`:**
   - As soon as the rescuer stops pushing (`reading.moving == false`), `pushing` becomes false in `EmergencyCoach.kt:99`.
   - In `EmergencyCoach.kt:111-112`, `wordFor` returns `EmergencyWord.KEEP_PUSHING`.
   - In `EmergencyCoach.kt:76`, `if (wanted == EmergencyWord.KEEP_PUSHING ...)` has no hold delay; `word` flips to `KEEP_PUSHING` on that exact tick.
   - `keep_going` requires 2.0 s of confirmed stillness (`CoachPolicy.kt:117-118`).
   - Therefore, the big word is always `KEEP_PUSHING` for 2 full seconds before `keep_going` can ever be spoken.

---

## 3. Speech suppression during opening steps and connected calls

### Scripted opening steps: Spoken corrections ARE suppressed

- **Path:** `EmergencyHost.kt:160-163, 188-190, 332-333, 355-360`, `EmergencySequence.kt:23, 62`.
- **What happens:**
  1. `EmergencyPlayback.start()` invokes `sequence.start(now)`, which enqueues `["em_call", "em_hands", "em_push"]` into `pending`.
  2. `voiceIdle` is defined as:
     ```kotlin
     val voiceIdle: Boolean
         get() = !stopped && sequenceStarted && engine != null && currentKey == null && pending.isEmpty()
     ```
  3. In `EmergencyHost.kt:188-190`:
     ```kotlin
     if (playback.voiceIdle) {
         coach.nextCorrection(reading)?.let { playback.speak(it.key) }
     }
     ```
  4. While any of the three opening clips are queued in `pending` or currently playing (`currentKey != null`), `playback.voiceIdle` evaluates to `false`.
  5. `coach.nextCorrection(reading)` is skipped entirely. Even if called, `playback.speak()` (`EmergencyHost.kt:369`) guards with `if (!voiceIdle) return`.
  6. The scripted opening prompts play without interruption. Camera corrections (`push_faster`, `elbows_straight`, `shoulders_over_hands`, etc.) can only play after `em_push` has finished playing.

### Connected call (`CallRouteController`): Speech is NOT suppressed

- **Path:** `audio/CallRouteController.kt:26-33, 64-79`, `audio/CallRoutePolicy.kt:16-19, 39`, `audio/AudioEngine.kt:218-224, 613-649`, `EmergencyHost.kt:167-198`.
- **What happens:**
  1. `MetronomeService.ensureEngine()` initializes and starts `CallRouteController(this, created)` (`MetronomeService.kt:93`).
  2. `CallRouteController` observes `AudioManager.mode` via `OnModeChangedListener` (API 31+) and a 1-second polling loop (`CallRouteController.kt:26-33`).
  3. When an emergency call connects, `audio.mode == AudioManager.MODE_IN_CALL` (value `2`). `CallRoutePolicy.isCallMode(audio.mode)` returns `true` (`CallRoutePolicy.kt:39`).
  4. `CallRouteController.apply(AudioRoute.VOICE_SPEAKER)` executes:
     - Sets the communication device to `TYPE_BUILTIN_SPEAKER` (API 31+) or sets `isSpeakerphoneOn = true` (API 30).
     - Calls `engine.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)`.
  5. `AudioEngine.setUsage()` only reopens the `AudioTrack` with communication attributes (`AudioEngine.kt:218-224`). Line 215 notes: *"beat phase, clip position, mute and callbacks are untouched because they only advance on accepted writes."*
  6. Neither `CallRouteController` nor `CallRoutePolicy` notifies `EmergencyPlayback`, `EmergencyCoach`, or `AudioEngine.setVoiceMuted()`.
  7. In `EmergencyHost.kt:167-198`, the coaching loop continues running normally:
     - `playback.voiceIdle` remains `true` between clips.
     - `coach.nextCorrection(reading)?.let { playback.speak(it.key) }` continues submitting corrections (`shoulders_over_hands`, `elbows_straight`, `keep_going`, `good`, `push_faster`, `slow_down`).
     - `playback.tick()` continues scheduling sequence clips (`em_aed` at 30 s, `em_continue` at 60 s, `em_swap` every 2 min).
  8. In `AudioEngine.render()`, voice clip samples and metronome click samples are mixed together into the same track (`AudioEngine.kt:638-649`).
  9. **Result:** Voice clips continue playing over the speakerphone alongside the 110 BPM metronome while the rescuer is talking with emergency services (112 / dispatcher).

### Pitfall of unsuppressed speech during calls

As noted in `cpr-coach/docs/CALL_AUDIO_NOTES.md:7`:
- In `USAGE_VOICE_COMMUNICATION`, audio shares `STREAM_VOICE_CALL` with the dispatcher.
- Full-volume coaching clips (`"Shoulders straight over your hands!"`, `"Straighten your arms!"`, `"Keep going!"`, `"Push faster!"`) shout over the phone call, masking dispatcher instructions and bleeding into the device microphone.
- The 110 BPM metronome click should remain audible so the rescuer maintains compression pace, but spoken coaching clips should be muted during an active cellular call.

---

## 4. Minimal Patch: Mute voice clips during active calls

To suppress spoken clips while preserving the metronome beat during a connected call, `AudioEngine` should maintain a separate `callMuted` state (independent of `VoiceRouter`'s mic-listening mute), and `CallRouteController` should toggle it when entering and leaving `VOICE_SPEAKER` mode.

```diff
--- a/app/src/main/java/com/zeroflux/cprcoach/audio/AudioEngine.kt
+++ b/app/src/main/java/com/zeroflux/cprcoach/audio/AudioEngine.kt
@@ -37,3 +37,4 @@ class AudioEngine(
     @Volatile private var voiceMuted = false
+    @Volatile private var callMuted = false
     @Volatile private var currentUsage = initialUsage
@@ -240,2 +241,10 @@ class AudioEngine(
     }
+
+    /** Silences voice clips during a call without stopping the metronome. */
+    fun setCallMuted(muted: Boolean) {
+        synchronized(lock) {
+            callMuted = muted
+            if (muted) stopClips()
+        }
+    }
@@ -626,3 +635,3 @@ private class AudioMixBlock(private val click: ShortArray) {
         playToken: Int,
-        voiceMuted: Boolean,
+        muted: Boolean,
     ) {
@@ -645,3 +654,3 @@ private class AudioMixBlock(private val click: ShortArray) {
         if (samples != null && pos < samples.size) {
-            if (!voiceMuted) mix += samples[pos].toInt()
+            if (!muted) mix += samples[pos].toInt()
             pos++
--- a/app/src/main/java/com/zeroflux/cprcoach/audio/CallRouteController.kt
+++ b/app/src/main/java/com/zeroflux/cprcoach/audio/CallRouteController.kt
@@ -60,2 +60,3 @@ class CallRouteController(context: Context, private val engine: AudioEngine) {
         policy.onStop()?.let { apply(it) }
+        engine.setCallMuted(false)
         restoreDevice()
@@ -73,4 +74,6 @@ class CallRouteController(context: Context, private val engine: AudioEngine) {
         if (route == AudioRoute.VOICE_SPEAKER) {
             forceSpeaker()
             engine.setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
+            engine.setCallMuted(true)
         } else {
+            engine.setCallMuted(false)
             engine.setUsage(AudioAttributes.USAGE_MEDIA)
```
