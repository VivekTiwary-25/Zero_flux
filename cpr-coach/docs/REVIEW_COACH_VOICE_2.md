# Second review of coach voice rules

Scope: `app/src/main/java/com/zeroflux/cprcoach/engine/CoachPolicy.kt` after commits `1c3aa4d`, `4dfb3b6`, and `63f3fe4`. I found two serious regressions in pause handling. The per-phrase `wantedSinceMs` map is bounded by the six-value `Phrase` enum; I found no map-growth issue. The new per-phrase steady timers also address the earlier priority-starvation finding.


## 1. Small movement-detector dropouts can suppress the next Keep going forever

After Keep going has played, a second Keep going requires `pushedSinceCue`. That flag is set after three continuous seconds of `moving=true`; each `BEATING` read with `moving=false` resets `pushingSinceMs`. Consequently, a locked beat can continue for six seconds while brief movement dropouts prevent the flag from ever setting. The next still pause sets `pendingRecovery`, but `wantedPhrases` rejects Keep going because it was the previous phrase and no pushing was recorded since the cue.

Reading sequence: `PAUSED, moving=false` at 0..2,000 ms causes `KEEP_GOING`. Then use `BEATING, rate=110` from 2,250 through 8,250 ms, with `moving=false` only at 2,250, 4,250, 6,250, and 8,250 ms, and true otherwise. Then use `PAUSED, moving=false` from 8,500 ms onward. The second Keep going is never spoken, although the rate remained locked throughout the intervening six seconds.

Failing JUnit test:
```kotlin
@Test fun `brief moving dropouts during a locked beat do not lose next pause cue`() {
    val policy = CoachPolicy()
    fun paused(t: Long) = LiveReading(t, TrackingState.PAUSED, null, 170.0, false)
    assertEquals(
        Phrase.KEEP_GOING,
        (0L..2_000L step 250L).mapNotNull { policy.update(paused(it)) }.single()
    )
    for (t in 2_250L until 8_500L step 250L) {
        val moving = t !in setOf(2_250L, 4_250L, 6_250L, 8_250L)
        policy.update(LiveReading(t, TrackingState.BEATING, 110, 170.0, false, moving))
    }
    val nextPause = (8_500L until 13_000L step 250L)
        .mapNotNull { policy.update(paused(it)) }
    assertEquals(listOf(Phrase.KEEP_GOING), nextPause)
}
```

## 2. Several moving reads do not break a confirmed pause

The code clears `pauseConfirmed` only when the rolling two-second sample window is mostly moving. Four consecutive moving reads span 750 ms, still leave the window mostly still, and do not reset `pausedSinceMs`. `stillNow` temporarily defers the cue, but after its one-second lookback the old confirmation permits Keep going after just one second of new stillness.

Reading sequence: `BEATING, rate=90, moving=true` from 0 to 3,000 ms speaks `PUSH_FASTER`. `PAUSED, moving=false` from 3,250 to 7,500 ms confirms a pause. Four moving reads at 7,750, 8,000, 8,250, and 8,500 ms are followed by still reads from 8,750 ms. `KEEP_GOING` is returned at 9,750 ms, only one second after movement stopped and before a fresh two-second still pause.

Failing JUnit test:
```kotlin
@Test fun `sustained movement interrupts confirmed pause stillness`() {
    val policy = CoachPolicy()
    fun paused(t: Long, moving: Boolean = false) =
        LiveReading(t, TrackingState.PAUSED, null, 170.0, false, moving)
    for (t in 0L until 3_250L step 250L) {
        policy.update(LiveReading(t, TrackingState.BEATING, 90, 170.0, false, true))
    }
    for (t in 3_250L until 7_750L step 250L) policy.update(paused(t))
    for (t in 7_750L until 8_750L step 250L) policy.update(paused(t, true))
    val afterMovement = (8_750L until 10_750L step 250L)
        .mapNotNull { policy.update(paused(it)) }
    assertTrue(
        "A new two-second still period has not elapsed: $afterMovement",
        afterMovement.none { it == Phrase.KEEP_GOING },
    )
}
```

## Verification

The requested `./gradlew testDebugUnitTest` could not start because the archived `gradlew` is not executable; invoking it as `bash ./gradlew testDebugUnitTest` then failed during Android Gradle configuration because no Android SDK is installed or configured (`ANDROID_HOME` and `ANDROID_SDK_ROOT` are unset, and no project `local.properties` supplies `sdk.dir`). Therefore these JUnit regressions could not be executed in the reference copy. The expected outcomes above are from tracing `CoachPolicy.update` and its state transitions, not from a test run.
