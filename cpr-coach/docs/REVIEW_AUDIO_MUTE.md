# Audio mute review

## Findings, ranked by likelihood

1. **Most likely: listening consumes speech clips silently.** `VoiceRouter.kt:87-90` sets `AudioEngine.voiceMuted` while `MicState.LISTENING`. `AudioEngine.kt:640-643` skips mixing a muted sample but still increments its clip position; `AudioMixBlock.clipPosition` (`607-611`) and `AudioEngine.commit` (`458-477`) then finish the clip and post its completion. The click and voice use the same streaming `AudioTrack` (`16-20,449-455,613-645`), so clicks can continue while a phrase is irrevocably lost. The audio test `AudioEngineRecoveryTest.voiceMuteStillConsumesVoiceButNotTheMetronome` confirms this is intentional behavior today. **Phone check:** enter Emergency and tap the mic while its opening instructions are playing; listen for which instructions are missing, then turn the mic off and check whether they return. Repeat without enabling the mic as a control; do not call emergency services. **Minimal patch if phrases should wait through listening:**

   ```diff
   --- a/app/src/main/java/com/zeroflux/cprcoach/audio/AudioEngine.kt
   +++ b/app/src/main/java/com/zeroflux/cprcoach/audio/AudioEngine.kt
   @@
   +    private var voiceMuted = false
   @@
   -        minOf(samples?.size ?: 0, initialClipPosition + accepted)
   +        minOf(samples?.size ?: 0, initialClipPosition + if (voiceMuted) 0 else accepted)
   @@
   +        this.voiceMuted = voiceMuted
   @@
   -                if (!voiceMuted) mix += samples[pos].toInt()
   -                pos++
   +                if (!voiceMuted) {
   +                    mix += samples[pos].toInt()
   +                    pos++
   +                }
   ```

   This pauses rather than consumes the current clip while muted. It changes the existing tested policy; decide whether urgent instructions should instead preempt listening, and update the existing test to verify the chosen behavior.

2. **Likely in Practice: no phrase was scheduled.** `PractiseSession.kt:93-109` plays only when `CoachPolicy.update` returns a correction. `CoachPolicy.kt:13-16,33-61,93-110` intentionally stays quiet when tracking is lost and applies steadiness, cooldown, and repeat suppression. `PractiseHost.kt:323-339` has nothing to repeat until a phrase has played. **Phone check:** establish tracking, start a session, and wait for a stable correction; compare with clicks while the camera has lost the rescuer. In Emergency, the opening instructions are a better known-phrase control. No patch is indicated unless a specific eligible correction is shown not to be requested.

3. **Possible: missing, invalid, or stale clip load.** `AudioEngine.play` (`135-206`) logs and completes without samples when the language/key is absent or loading fails; `setLanguage` (`93-109`) reloads assets, and `WavClip.parse` (`WavClip.kt:27-76`) can reject bad WAV data. A queued play captures its old language (`AudioEngine.kt:141-147`); the loader checks the epoch before caching (`183-186`) but not before installing samples (`188-201`), so a rapid language change can install an old-language clip. Clicks need no clip asset. **Phone check:** with mic off, compare a known opening/repeat clip in two languages, then rapidly change language while one is loading; inspect logcat for `missing clip`, `bad clip`, or `clip load failed`. **Minimal patch for stale language loads:** before installing `samples` in the loader's synchronized block, also reject when the captured `epoch`/`code` no longer matches the current language; complete the pending callback without installing stale samples.
   
   ```diff
   --- a/app/src/main/java/com/zeroflux/cprcoach/audio/AudioEngine.kt
   +++ b/app/src/main/java/com/zeroflux/cprcoach/audio/AudioEngine.kt
   @@
               val outcome = synchronized(lock) {
                   if (token != playToken) LoadOutcome.SUPERSEDED
   -               else if (released || samples == null) {
   +               else if (released || samples == null ||
   +                   synchronized(cacheLock) { languageEpoch != epoch || languageCode != code }
   +               ) {
                       postToMain(clipDone)
                       clipDone = null
                       LoadOutcome.DONE
                   }
   ```

4. **Possible brief restoration delay after a mic tap or capture failure; no permanent latch found.** An ordinary tap while listening takes `VoiceRouter.toggle`'s non-OFF branch and calls `controller.stop()` (`143-150`); `HandsFreeController.stopSession` publishes `OFF` (`116-124`), then the separate state collector clears mute (`VoiceRouter.kt:87-90`). Capture errors likewise end in the job completion handler publishing `OFF` (`HandsFreeController.kt:75-103`). A slow collector can briefly leave mute enabled, but the code does not show it remaining enabled after `OFF` is collected. **Phone check:** while clicks and a known phrase are active, tap the listening mic off and immediately request another phrase; repeat after forcing a recorder failure. **Minimal patch:**

   ```diff
   --- a/app/src/main/java/com/zeroflux/cprcoach/VoiceRouter.kt
   +++ b/app/src/main/java/com/zeroflux/cprcoach/VoiceRouter.kt
   @@
       fun toggle() {
           if (released) return
   +       if (state.value != MicState.OFF) {
   +           stopListening()
   +           return
   +       }
           commandTimeoutJob?.cancel()
           micEpoch += 1
   -       if (state.value != MicState.OFF) {
   -           controller.stop()
   -           return
   -       }
   ```

   `stopListening()` synchronously unmutes engines (`111-118`) and invalidates queued transcripts.

5. **Low likelihood if clicks are clearly audible: output volume, focus, or route.** `AudioEngine.openTrack` creates one mono `AudioTrack` with `USAGE_MEDIA` and sonification content (`407-447`); it has no per-clip volume or audio-focus request. Thus media volume, focus attenuation, or a general track failure should affect clicks too. Emergency alone switches the shared track between media and voice-communication usage during calls (`CallRouteController.kt:64-105`); an output-device/stream issue can be call-specific. AudioTrack open/write failures retry and log `AudioTrack unavailable` (`320-377`). **Phone check:** compare media volume outside a call, then in a connected call check call volume, speaker/earpiece/Bluetooth routing, and whether the click also changes. Do not use an emergency number for this test. No voice-only output patch is supported when the click is unchanged.

## Listening-end and screen-change audit

The normal stop paths restore mute. Manual stop calls `controller.stop()` and then the `OFF` collector unmutes (`VoiceRouter.kt:143-150,87-90`). A matched command or language command calls `stopListening()` before its action (`69-81`); the 15-second no-command timeout calls `toggle()` (`163-175`). VAD silence changes state to `OFF` when recording stops, then drains transcripts for at most three seconds (`HandsFreeController.kt:220-231,305-310`). Permission revocation, recorder/VAD/model/recognizer exceptions, and normal job completion all finish the session and publish `OFF` (`HandsFreeController.kt:75-103,136-290`). Permission denied or missing speech assets before start returns without entering `LISTENING` (`VoiceRouter.kt:151-159`); the controller also rejects denied permission with `OFF` (`HandsFreeController.kt:70-74`).

Route replacement and unbind stop listening (`VoiceRouter.kt:94-118`); language change stops the controller and synchronously clears mute (`130-141`). Audio attach applies the current mute state, detach unmutes (`120-127`), and release unmutes before dropping engines (`178-188`). Practice walkthrough/scorecard binding disallows commands and is disposed on screen changes (`PractiseHost.kt:109-127,148-170`); the camera binding is removed on disposal or session-owner replacement (`229-244`). A fresh camera session resets its clips (`PractiseHost.kt:210-244,336-339`); `PractiseSession.start` does not control mic mute (`PractiseSession.kt:60-67`). `MicButton.kt:44-90` only displays state and delegates taps; it does not own audio mute. To check these paths on a phone, while listening navigate Home and re-enter Practice, switch through walkthrough/scorecard, restart a session, change language, revoke mic permission in Settings, and leave the mic quiet for over six seconds; after each, verify the mic indicator returns to off and a known phrase can be heard. A denied permission or unavailable model should never show listening; permission loss while recording should return off. The 15-second no-command timeout is a separate fallback if listening remains active.

## Distinguish a mute from a missing request

Clicks alone do not prove a phrase was requested: Practice suppresses speech while tracking is lost or a correction is in cooldown. The strongest direct reproduction is a known Emergency opening phrase overlapping a listening session. If the mic is off and known clips still fail, investigate asset logs and language loading; if clicks also fail, investigate the shared track, volume, focus, or route before proposing a voice-only fix.
