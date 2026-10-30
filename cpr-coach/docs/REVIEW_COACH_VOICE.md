# Review of coach voice rules (`bf38170`)

Scope: `app/src/main/java/com/zeroflux/cprcoach/engine/CoachPolicy.kt` (changed in commit `bf38170` on `cpr-coach`) and its caller `PractiseSession.kt`.
Note on verification: no JDK is installed on this workstation (`JAVA_HOME` is unset and `java` is not on `PATH`), so `./gradlew testDebugUnitTest` could not be run; every reading sequence and JUnit test below was verified against an exact line-for-line replica of `CoachPolicy`, `LiveAnalyzer`, and `PractiseSession`.

---

## 1. `pausedSinceMs` starts while pushing (`PAUSED` + `moving = true`), so `KEEP_GOING` plays after 1 s of stopping or while actively pushing

### Problem
In `CoachPolicy.kt:70-77`, `trackPause` sets `if (pausedSinceMs == null) pausedSinceMs = now` on the first `TrackingState.PAUSED` reading even when `reading.moving` is `true` (pushing without a locked rate), and never resets `pausedSinceMs` while `moving` is `true`. In addition, `wantedPhrase` (`CoachPolicy.kt:95`) returns `Phrase.KEEP_GOING` whenever `pendingRecovery` is `true` without checking `!reading.moving`.

This causes two bugs:
1. **Premature `KEEP_GOING` after 1 s of stillness:** If the rescuer pushes without a locked rate (`PAUSED, moving = true`) for 2 s (`t = 0..2_000`), `pausedSinceMs` is `0`. When they stop (`PAUSED, moving = false`) for only **1.0 s** (`t = 2_250..3_250`, 5 readings), the 2 s window `pausedReads` (`1_250..3_250`) has 4 moving reads and 5 still reads (`still = true`), and `now - pausedSinceMs = 3_250 >= 2_000`. `KEEP_GOING` plays after 1.0 s of stopping instead of 2.0 s. (With sparse updates—such as `EmergencyHost.kt:185`, which calls `nextCorrection` only while `playback.voiceIdle` is true—a single `(0, moving = true)` read followed by `(2_000, moving = false)` has 1 of 2 reads still and `now - pausedSinceMs == 2_000`, playing `KEEP_GOING` on the very first still reading after 0 ms of pause.)
2. **`KEEP_GOING` spoken while the rescuer is actively pushing:** If the rescuer hesitates (`PAUSED, moving = false`) for only **1.0 s** (`t = 0..1_000`, 5 readings) and then pushes (`PAUSED, moving = true`) for **1.0 s** (`t = 1_250..2_000`, 4 readings), at `t = 2_000` `now - pausedSinceMs == 2_000` and `pausedReads` has 5 still and 4 moving reads (`still = true`). `CoachPolicy` returns `Phrase.KEEP_GOING` at `t = 2_000` while `reading.moving` is `true` and the rescuer has been pushing for four consecutive ticks (this also occurs on `real-cph2467-13.csv` at `t = 5.25 s`).

### Concrete failing reading sequences
- **Sequence A (stopped for 1.0 s after pushing):**
  - `t = 0, 250, ..., 2_000`: `LiveReading(t, PAUSED, null, 170.0, false, moving = true)`
  - `t = 2_250, 2_500, 2_750, 3_000, 3_250`: `LiveReading(t, PAUSED, null, 170.0, false, moving = false)`
  - At `t = 3_250` (1.0 s after stopping), `update` returns `Phrase.KEEP_GOING`.
- **Sequence B (hesitated 1.0 s, then pushing for 1.0 s):**
  - `t = 0, 250, 500, 750, 1_000`: `LiveReading(t, PAUSED, null, 170.0, false, moving = false)`
  - `t = 1_250, 1_500, 1_750, 2_000`: `LiveReading(t, PAUSED, null, 170.0, false, moving = true)`
  - At `t = 2_000` (while `moving == true`), `update` returns `Phrase.KEEP_GOING`.

### Suggested fix
Only start `pausedSinceMs` once `still` is true (or on `!reading.moving`), clear `pausedSinceMs = null` and `pendingRecovery = false` whenever `!still`, and require `!reading.moving` before setting `pendingRecovery = true` (or before returning `Phrase.KEEP_GOING` in `wantedPhrase`).

```kotlin
TrackingState.PAUSED -> {
    val now = reading.timeMs
    if (pausedSinceMs != null || !reading.moving) {
        pausedReads.addLast(now to reading.moving)
        while (now - pausedReads.first().first > MIN_PAUSE_MS) pausedReads.removeFirst()
    }
    val still = pausedReads.isNotEmpty() && pausedReads.count { !it.second } * 2 >= pausedReads.size
    if (!still) {
        pausedSinceMs = null
        pendingRecovery = false
        pauseAcknowledged = false
    } else {
        if (pausedSinceMs == null) pausedSinceMs = pausedReads.first { !it.second }.first
        if (!reading.moving && !pauseAcknowledged && now - pausedSinceMs!! >= MIN_PAUSE_MS) {
            pendingRecovery = true
        }
    }
}
```

### Failing JUnit tests
```kotlin
@Test
fun `stopping after pushing without a rate still waits two seconds before keep going`() {
    val policy = CoachPolicy()
    run(policy, 0, 2_250) { paused(it).copy(moving = true) }
    // Stopped from t = 2_250 to t = 3_250 is only 1.0 s of stillness (< MIN_PAUSE_MS = 2_000 ms).
    val spoken = run(policy, 2_250, 3_500) { paused(it).copy(moving = false) }
    assertTrue("spoke too early after only 1 s still: $spoken", spoken.isEmpty())
}

@Test
fun `one second hesitation followed by active pushing does not say keep going while pushing`() {
    val policy = CoachPolicy()
    run(policy, 0, 1_250) { paused(it).copy(moving = false) } // 1.0 s still (0..1_000)
    val spoken = run(policy, 1_250, 2_500) { paused(it).copy(moving = true) } // pushing from 1_250..2_250
    assertTrue("spoke keep going while actively pushing: $spoken", spoken.isEmpty())
}
```

---

## 2. `pauseAcknowledged` is only cleared on `BEATING`, so a pause after pushing without a locked rate misses `KEEP_GOING`

### Problem
`pauseAcknowledged` is set to `true` when `Phrase.KEEP_GOING` plays (`CoachPolicy.kt:59`), and is only reset to `false` on `TrackingState.BEATING` (`CoachPolicy.kt:80`). Commit `bf38170` coaches rescuers who push with bent elbows or leaning while remaining in `TrackingState.PAUSED` with `moving = true` (because `LiveAnalyzer` has no locked rate). If a rescuer pauses once (`KEEP_GOING` plays, `pauseAcknowledged = true`), then resumes pushing with bent elbows (`PAUSED, moving = true`, so `ELBOWS_STRAIGHT` plays at `t = 8_000` and `lastPhrase` becomes `ELBOWS_STRAIGHT`), `pauseAcknowledged` stays `true`. When the rescuer stops pushing a second time (`PAUSED, moving = false`), `pendingRecovery` (`!pauseAcknowledged && ...`) stays `false` forever and `KEEP_GOING` is never spoken.

### Concrete failing reading sequence
- `t = 0..2_000` (step 250 ms): `PAUSED, moving = false` -> returns `Phrase.KEEP_GOING` at `t = 2_000` (`pauseAcknowledged = true`, `lastPhrase = KEEP_GOING`).
- `t = 2_250..8_000`: `PAUSED, moving = true, posture = PostureReading(false, ELBOWS_BENT, 130.0, null)` -> returns `Phrase.ELBOWS_STRAIGHT` at `t = 8_000` (`lastPhrase = ELBOWS_STRAIGHT`, `pauseAcknowledged` remains `true`).
- `t = 8_250..18_000`: `PAUSED, moving = false` (rescuer is completely still for nearly 10 s, well past the 6 s cooldown at `t = 14_000`) -> returns `null` on every tick; `KEEP_GOING` is missed.

### Suggested fix
Reset `pauseAcknowledged = false` in `trackPause` whenever `PAUSED` is actively moving (`!still`), since the rescuer has resumed pushing and any subsequent stop is a new pause.

### Failing JUnit test
```kotlin
@Test
fun `keep going plays again when rescuer stops after pushing with bent elbows`() {
    val policy = CoachPolicy()
    assertEquals(listOf(2_000L to Phrase.KEEP_GOING), run(policy, 0, 2_250) { paused(it) })
    assertEquals(
        listOf(8_000L to Phrase.ELBOWS_STRAIGHT),
        run(policy, 2_250, 8_250) { pushing(it, PostureIssue.ELBOWS_BENT) },
    )
    val secondPause = run(policy, 8_250, 18_000) { paused(it) }
    assertTrue("expected KEEP_GOING on second pause, got $secondPause", secondPause.any { it.second == Phrase.KEEP_GOING })
}
```

---

## 3. `lastPhrase` is never cleared when an issue is fixed (or when compressions resume), leaving phrases stuck and blocking posture coaching when `pendingRecovery` is true

### Problem
`lastPhrase` (`CoachPolicy.kt:27, 55, 95, 110`) is only changed when another phrase is spoken. It is never cleared when the rescuer fixes the fault or resumes compressions after a pause. This causes four bugs:

1. **Missed `KEEP_GOING` on a second pause after good `BEATING` compressions:**
   After `KEEP_GOING` plays at `t = 2_000` (`lastPhrase = KEEP_GOING`), the rescuer resumes good `BEATING` compressions (`rate = 110`, straight elbows) for `t = 2_250..9_750` (7.5 s). `trackPause` resets `pauseAcknowledged = false` on `BEATING`, but because rate and posture are good (and `< 20 s` has elapsed before `GOOD`), no phrase is spoken and `lastPhrase` remains `KEEP_GOING`. When the rescuer stops again at `t = 10_000..18_000`, `pendingRecovery` becomes `true` at `t = 12_000`, but `Phrase.KEEP_GOING.takeIf { it != lastPhrase }` (`CoachPolicy.kt:95`) returns `null` forever.
2. **`pendingRecovery` stays stuck `true` when `lastPhrase == KEEP_GOING`, blocking posture corrections:**
   Because `CoachPolicy.kt:95` (`if (pendingRecovery) return Phrase.KEEP_GOING.takeIf { it != lastPhrase }`) returns `null` before reaching the posture checks (`lines 105-108`), `KEEP_GOING` is never spoken and `CoachPolicy.kt:57-60` (`pendingRecovery = false; pauseAcknowledged = true`) never executes. If the rescuer then pushes with bent elbows in `PAUSED` while `still` remains `true` (for example during the first 1 s of continuous `moving = true` reads while `pausedReads` still has `>= 50%` `moving = false`, or indefinitely if `moving` flickers `false` on half the reads), `wantedPhrase` returns `null` at line 95 and blocks `ELBOWS_STRAIGHT` / `SHOULDERS_OVER_HANDS`.
3. **Corrections get stuck forever after the rescuer fixes the fault and later relapses:**
   While pushing in `PAUSED` (`moving = true`), `Phrase.GOOD` never plays (`CoachPolicy.kt:109` requires `beating`). Even while `BEATING`, once the rescuer relapses into the same fault (`wanted` is non-empty), `wanted.isEmpty()` is `false` so `Phrase.GOOD` can never be added to `wanted` even after `praiseEveryMs = 20_000`. Thus, if `ELBOWS_STRAIGHT` (or `PUSH_FASTER`, `SLOW_DOWN`, `SHOULDERS_OVER_HANDS`) plays once, and the rescuer fixes their form for 10 s (`44..53.4 s` in `real-cph2467-15.csv`) and then bends their elbows again (`54..61.5 s`), `wanted` is `[ELBOWS_STRAIGHT]`, which matches `lastPhrase` and returns `null` for the rest of the session. (Likewise, during uninterrupted good `BEATING` compressions, `Phrase.GOOD` plays once at `t = 3_000` and sets `lastPhrase = GOOD`, so at `t >= 23_000` `wanted = [GOOD]` is filtered out by `it != lastPhrase` and `praiseEveryMs` never praises again.)
4. **Priority inversion on relapse:**
   If `PUSH_FASTER` plays at `t = 3_000`, the rescuer fixes their rate (`110 BPM`) from `t = 3_250..10_000`, and then drops to `85 BPM` with bent elbows (`ELBOWS_BENT`) at `t = 10_000..12_500`, `wanted` is `[PUSH_FASTER, ELBOWS_STRAIGHT]`. Because `lastPhrase` is still `PUSH_FASTER`, `wanted.firstOrNull { it != lastPhrase }` skips `PUSH_FASTER` and plays the lower-priority `ELBOWS_STRAIGHT` at `t = 12_000`, starting a new 6 s cooldown that delays `PUSH_FASTER` until `t = 18_000`.

### Concrete failing reading sequences
- **Sequence A (second pause after good `BEATING`):**
  - `t = 0..2_000`: `PAUSED, moving = false` -> speaks `KEEP_GOING` at `t = 2_000`.
  - `t = 2_250..9_750`: `BEATING, ratePerMinute = 110, elbowsBent = false` (7.5 s of good compressions).
  - `t = 10_000..16_000`: `PAUSED, moving = false` -> returns `null` on every tick; `KEEP_GOING` is never spoken.
- **Sequence B (`pendingRecovery` stuck `true` blocking `ELBOWS_STRAIGHT`):**
  - `t = 0..2_000`: `PAUSED, moving = false` -> speaks `KEEP_GOING` at `t = 2_000`.
  - `t = 2_250..3_750`: `BEATING, ratePerMinute = 110, elbowsBent = false` -> resets `pauseAcknowledged = false`, `lastPhrase` stays `KEEP_GOING`.
  - `t = 4_000..6_000`: `PAUSED, moving = false` -> sets `pendingRecovery = true`, blocked by `lastPhrase == KEEP_GOING`.
  - `t = 6_250..15_000`: `PAUSED, posture = ELBOWS_BENT`, with `moving = (t / 250) % 2 == 1L` -> `pendingRecovery` stays `true` forever, returning `null` at line 95 and never speaking `ELBOWS_STRAIGHT`.
- **Sequence C (`ELBOWS_STRAIGHT` stuck forever after fixing and relapsing):**
  - `t = 0..2_000`: `pushing(t, ELBOWS_BENT)` -> speaks `ELBOWS_STRAIGHT` at `t = 2_000`.
  - `t = 2_250..10_000`: `pushing(t, null)` (good posture for 7.75 s).
  - `t = 10_250..30_000`: `pushing(t, ELBOWS_BENT)` -> returns `null` on every tick.

### Suggested fix
1. Do not let a suppressed `pendingRecovery` short-circuit posture coaching at line 95: check `if (pendingRecovery && lastPhrase != Phrase.KEEP_GOING) return Phrase.KEEP_GOING`.
2. Clear `lastPhrase = null` when the rescuer has resumed `BEATING` (for `KEEP_GOING`) or has sustained good form with no wanted corrections for `postureSteadyMs` / `steadyMs` (and update `EngineFuzzTest`'s consecutive-phrase check if needed).

### Failing JUnit tests
```kotlin
@Test
fun `keep going plays on a second pause after resuming good compressions`() {
    val policy = CoachPolicy()
    assertEquals(listOf(2_000L to Phrase.KEEP_GOING), run(policy, 0, 2_250) { paused(it) })
    run(policy, 2_250, 10_000) { beating(it, 110) }
    val secondPause = run(policy, 10_000, 16_000) { paused(it) }
    assertEquals( listOf(Phrase.KEEP_GOING), secondPause.map { it.second })
}

@Test
fun `elbows straight is coached again after fixing posture and bending again`() {
    val policy = CoachPolicy()
    assertEquals(
        listOf(2_000L to Phrase.ELBOWS_STRAIGHT),
        run(policy, 0, 2_250) { pushing(it, PostureIssue.ELBOWS_BENT) },
    )
    run(policy, 2_250, 10_000) { pushing(it, null) }
    val relapsed = run(policy, 10_000, 20_000) { pushing(it, PostureIssue.ELBOWS_BENT) }
    assertEquals(listOf(Phrase.ELBOWS_STRAIGHT), relapsed.map { it.second })
}
```

---

## 4. Priority starvation: a brief rate wobble resets `candidateSinceMs` and prevents a steady posture correction from ever playing

### Problem
`CoachPolicy` keeps only a single `candidate: Phrase?` and a single `candidateSinceMs: Long` (`CoachPolicy.kt:24-25, 38-42`). Whenever `wantedPhrase` changes for even one reading, `candidateSinceMs` resets to `now`.
Because rate (`PUSH_FASTER` / `SLOW_DOWN`, requiring `steadyMs = 3_000`) outranks posture (`ELBOWS_STRAIGHT` / `SHOULDERS_OVER_HANDS`, requiring `postureSteadyMs = 2_000`), a brief rate wobble (or intermittent rate lock while pushing with bent elbows) repeatedly preempts the posture candidate for `< 3 s` and then drops back to the posture candidate for `< 2 s`. Even when `ELBOWS_BENT` is continuously active on every single reading for 20+ seconds, neither `PUSH_FASTER` nor `ELBOWS_STRAIGHT` ever reaches its steady threshold, and the coach stays completely silent.

### Concrete failing reading sequence
- `t = 0..20_000` (step 250 ms): `TrackingState.BEATING`, `moving = true`, `posture = PostureReading(false, ELBOWS_BENT, 130.0, null)` on every reading, while `ratePerMinute` is `95` when `t % 2_500 < 1_000` (1.0 s slow wobble, `< 3_000 ms`) and `105` otherwise (1.5 s in range, `< 2_000 ms`).
- Every reading has `ELBOWS_BENT`, yet `CoachPolicy.update` returns `null` for the entire 20 s.
- The same starvation happens if `tracking` alternates between `BEATING` (`rate = 90`, for 2.5 s) and `PAUSED` (`moving = true`, for 1.5 s) while `posture = ELBOWS_BENT` is continuous.

### Suggested fix
Track the continuous start time of each active phrase in `wanted` (for example in an `EnumMap<Phrase, Long>`), remove phrases that are not in `wanted` on the current tick, and select the highest-priority phrase in `wanted` (excluding `lastPhrase`) whose elapsed duration `now - sinceMs[phrase]!!` has reached its `requiredMs`.

### Failing JUnit test
```kotlin
@Test
fun `brief rate wobble does not starve a continuous elbow correction`() {
    val policy = CoachPolicy()
    val spoken = run(policy, 0, 20_000) { t ->
        val rate = if (t % 2_500 < 1_000) 95 else 105
        pushing(t, PostureIssue.ELBOWS_BENT, TrackingState.BEATING).copy(ratePerMinute = rate)
    }
    assertTrue("expected ELBOWS_STRAIGHT during 20 s of bent elbows, got $spoken", spoken.any { it.second == Phrase.ELBOWS_STRAIGHT })
}
```

---

## 5. `LOST` in the middle of an already-confirmed pause clears `pendingRecovery` during cooldown, losing `KEEP_GOING`

### Problem
When `TrackingState.LOST` or `TrackingState.WARMING` arrives, `trackPause` (`CoachPolicy.kt:83, 87-91`) calls `clearPause()`, which resets `pausedSinceMs = null`, `pausedReads.clear()`, and `pendingRecovery = false`.
Resetting `pausedSinceMs` when `LOST` interrupts a hesitation shorter than 2 s is intended (`lost tracking does not shorten the wait for a pause`). However, if the rescuer has *already* been paused for `>= 2_000 ms` (`pendingRecovery == true`) and `Phrase.KEEP_GOING` is only waiting for `cooldownMs` (`CoachPolicy.kt:52`) after an earlier correction, a single `LOST` reading right before cooldown ends wipes `pendingRecovery = false` and `pausedSinceMs = null`. When the arms are visible and still paused on the next reading (when cooldown expires), the 2 s pause timer has to start over from scratch, delaying `KEEP_GOING` by up to 2 s or missing it completely.

### Concrete failing reading sequence
- `t = 0..3_000` (step 250 ms): `BEATING, rate = 90` -> speaks `Phrase.PUSH_FASTER` at `t = 3_000` (`lastSpokenMs = 3_000`, cooldown until `t = 9_000`).
- `t = 4_000..8_250`: `PAUSED, moving = false` (4.25 s of continuous visible pause; `pendingRecovery` becomes `true` at `t = 6_000` and stays `true` through `t = 8_250`, waiting for cooldown at `t = 9_000`).
- `t = 8_500`: `LOST` (one 250 ms dropout) -> `clearPause()` sets `pendingRecovery = false` and `pausedSinceMs = null`.
- `t = 8_750..10_250`: `PAUSED, moving = false` (still paused at `t = 9_000` when cooldown expires and for 1.5 s after).
- `CoachPolicy` never speaks `Phrase.KEEP_GOING`.

### Suggested fix
In `trackPause`, when `TrackingState.LOST` or `TrackingState.WARMING` occurs, clear `pausedSinceMs = null` and `pausedReads.clear()`, but do not clear `pendingRecovery` if the 2 s pause was already confirmed (`pendingRecovery == true`); only clear `pendingRecovery` when `KEEP_GOING` is spoken, on `BEATING`, or when `PAUSED` is no longer still (`!still`).

### Failing JUnit test
```kotlin
@Test
fun `brief lost frame after pause is already confirmed does not drop pending keep going`() {
    val policy = CoachPolicy()
    assertEquals(listOf(3_000L to Phrase.PUSH_FASTER), run(policy, 0, 3_250) { beating(it, 90) })
    run(policy, 4_000, 8_500) { paused(it) } // 4.25 s of continuous pause; pendingRecovery is true
    policy.update(lost(8_500)) // single lost reading just before cooldown ends at 9_000
    val spoken = run(policy, 8_750, 10_500) { paused(it) }
    assertEquals(listOf(Phrase.KEEP_GOING), spoken.map { it.second })
}
```

---

## 6. Time going backwards stalls cooldown, steady timers, and pause detection, and breaks `pausedReads` FIFO eviction

### Problem
Unlike `LiveAnalyzer.kt:89` (`sample.timeMs < last`) and `SessionMetrics.kt:35` (`(now - previous).coerceAtLeast(0L)`), `CoachPolicy` does not check for `reading.timeMs` moving backwards:
1. **Cooldown and steady timers stall:** If `lastSpokenMs` or `candidateSinceMs` was set at `t = 10_000` and time jumps back to `t = 1_000`, `now - lastSpokenMs < cooldownMs` and `now - candidateSinceMs < requiredMs` stay `true` until `now` passes `16_000` or `13_000`.
2. **`pausedSinceMs` and `pausedReads` break:** In `trackPause` (`CoachPolicy.kt:71-76`), `pausedReads` evicts old entries with `while (now - pausedReads.first().first > MIN_PAUSE_MS) pausedReads.removeFirst()`. If `(10_000, true)` is at the head of `pausedReads` and time jumps back to `t = 1_000`, `now - 10_000 <= 2_000` for every reading up to `t = 12_000`. Because the `while` loop stops at the first element `(10_000, true)`, **none** of the readings appended at `t = 1_000, 1_250, ...` behind `(10_000, true)` are ever evicted either. Stale `moving = true` readings from `t = 1_000..7_000` remain trapped in `pausedReads` even after `t` passes `10_000`, causing `still` to evaluate to `false` across a 4.5 s pause (`t = 7_250..11_750`).

### Concrete failing reading sequence
- `t = 10_000`: `PAUSED, moving = true` (sets `pausedSinceMs = 10_000`, `pausedReads = [(10_000, true)]`).
- Time jumps backwards to `t = 1_000..7_000` (step 250 ms): `PAUSED, moving = true` (25 readings; none are evicted because `pausedReads.first().first` is `10_000`).
- `t = 7_250..11_750` (step 250 ms): `PAUSED, moving = false` (19 readings = 4.5 s of complete stillness, even past `t = 10_000`).
- At `t = 11_750`, `pausedReads` still contains 45 readings (26 `true`, 19 `false`), so `still` is `false` and `KEEP_GOING` is never spoken.

### Suggested fix
Track `private var lastTimeMs: Long? = null` in `CoachPolicy`. At the start of `update(reading)`, if `lastTimeMs != null && now < lastTimeMs!!`, reset timing state (`candidate = null`, `candidateSinceMs = now`, `lastSpokenMs = null`, `clearPause()`, `pauseAcknowledged = false`) before updating `lastTimeMs = now`.

### Failing JUnit test
```kotlin
@Test
fun `time going backwards resets pause and cooldown windows`() {
    val policy = CoachPolicy()
    policy.update(paused(10_000).copy(moving = true))
    run(policy, 1_000, 7_250) { paused(it).copy(moving = true) }
    val spoken = run(policy, 7_250, 12_000) { paused(it).copy(moving = false) }
    assertEquals(listOf(Phrase.KEEP_GOING), spoken.map { it.second })
}
```

---

## 7. User tapping pause (`PractiseSession.togglePause`) does not reset `CoachPolicy`

### Problem
In `PractiseSession.kt:143-156`, `togglePause()` stops the metronome and calls `metrics.pause()` when pausing, and replaces `analyzer = LiveAnalyzer()` when resuming (`// The rescuer has moved, so old readings no longer apply`). However, `togglePause()` does not reset `coach` (`CoachPolicy`) or call `audio.stopVoice()`:
- If `"keep_going"` played just before the user tapped Pause (`pauseAcknowledged = true`, `lastPhrase = Phrase.KEEP_GOING`), resuming creates a new `LiveAnalyzer` that emits `TrackingState.WARMING` for 3 s and then `TrackingState.PAUSED, moving = false` if the rescuer is still not pushing. Because `WARMING` only calls `clearPause()` (`CoachPolicy.kt:83, 87-91`), which does **not** clear `pauseAcknowledged` or `lastPhrase`, `pauseAcknowledged` remains `true` and `lastPhrase` remains `KEEP_GOING` across the user pause, so `"keep_going"` is never spoken after resume.
- Likewise, if `"push_faster"` or `"elbows_straight"` played before the user tapped Pause, `lastPhrase` remains set after Resume and blocks that correction if the rescuer resumes with the same fault.

### Concrete failing reading sequence
- Start `PractiseSession`, run `4_000 ms` of `110 BPM` frames, then run `5_000 ms` of still frames (`wristSignal = 0.2`, visible `goodArm`) -> `"keep_going"` plays (`pauseAcknowledged = true`, `lastPhrase = KEEP_GOING`).
- Call `session.togglePause()` (pause), advance clock by `10_000 ms`, call `session.togglePause()` (resume).
- Run `7_000 ms` of still frames (`wristSignal = 0.2`, visible `goodArm`, which produces 3 s of `WARMING` followed by 4 s of `PAUSED, moving = false`).
- No `"keep_going"` clip is played after resume.

### Suggested fix
In `PractiseSession.togglePause()`, call `audio.stopVoice()` when pausing, and reset `coach = CoachPolicy()` when resuming alongside `analyzer = LiveAnalyzer()`.

```kotlin
fun togglePause() {
    if (phase != Phase.LIVE) return
    isPaused = !isPaused
    lastTickMs = null
    if (isPaused) {
        audio.stopMetronome()
        audio.stopVoice()
        metrics.pause()
        publishPaused()
    } else {
        // The rescuer has moved, so old readings no longer apply.
        analyzer = LiveAnalyzer()
        coach = CoachPolicy()
        audio.startMetronome()
    }
}
```

### Failing JUnit test
```kotlin
@Test
fun `resuming from user pause resets coach so a new pause or fault is coached again`() {
    startSession()
    run(4_000, 110.0)
    run(5_000, 0.0) // still arm frames -> plays "keep_going"
    assertTrue(audio.played.contains("keep_going"))
    val countBeforePause = audio.played.count { it == "keep_going" }

    session.togglePause()
    clock += 10_000
    session.togglePause()

    run(7_000, 0.0) // 3 s warming + 4 s paused still after resume
    val countAfterResume = audio.played.count { it == "keep_going" }
    assertEquals(countBeforePause + 1, countAfterResume)
}
```
