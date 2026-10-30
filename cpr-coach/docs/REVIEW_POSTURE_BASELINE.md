# Independent Review of the Personal-Baseline Posture Judge

Review of commit `f99a1bc`, which introduces dynamic baseline learning for elbow angle and shoulder-over-wrist offset in `PostureJudge.kt`.

## Executive Summary & Likelihood Ranking (2-Minute Demo)

The personal-baseline posture judge contains serious behavioral defects that directly degrade the user experience during a 2-minute CPR demo and in production:

1. **Rank 1 (Guaranteed in demo): Pause/Resume destroys the baseline.** `PractiseSession.togglePause()` replaces `analyzer` with a fresh `LiveAnalyzer()`, throwing away the learned baseline. For the next 6+ seconds after unpausing, mild elbow bends cannot be detected. If the rescuer unpauses while fatigued, the baseline is permanently lost for the remainder of the session.
2. **Rank 2 (Very High): Baseline formed with mild bend (160°–173°) or lean.** Any rescuer whose straight-arm elbow angle is $\le 173^\circ$ collapses to the fixed fallback of $145.0^\circ$ due to clamping `(be - 28.0).coerceIn(145.0, 158.0)`, completely disabling the sensitivity boost for mild bends ($146^\circ$–$151^\circ$). Furthermore, `learnBaseline` does not check lean offset at all; a rescuer leaning during baseline locks in a corrupt offset baseline, which later falsely flags upright posture as leaning and ignores severe forward/backward lean.
3. **Rank 3 (High in Emergency CPR): EmergencyHost has no calibration and degrades posture coaching.** Emergency CPR starts immediately without framing or posture countdown. If a panicked rescuer begins pushing with bent arms ($<160^\circ$), no baseline is ever established: bent arms between $145^\circ$ and $158^\circ$ are never flagged, and the lean detection threshold is loosened from $0.28$ to $0.45$, ignoring substantial lean throughout the emergency.
4. **Rank 4 (Medium in 2-min demo, High in real CPR): Rescuer swap or camera bump locks in a stale baseline.** `PostureJudge` provides no baseline expiration, relearning, or adaptation once `baselineElbow != null`. When a second rescuer steps in or the camera shifts, normal posture for the new setup exceeds `leanDelta = 0.19`, triggering permanent false lean alerts.
5. **Rank 5 (Low-to-Medium): Wrist crossing hip X coordinate causes an instantaneous sign flip and false lean.** The horizontal direction scalar `dir = if (wx >= hx) 1.0 else -1.0` has zero hysteresis around `wx == hx`. Jitter or body sway across the hip X flips offset sign (e.g., $+0.25$ to $-0.25$, $\Delta = 0.50$), immediately firing a false `SHOULDERS_NOT_OVER_HANDS` alert.
6. **Handled / Non-issues:** NaN and non-finite values are safely caught by visibility and `isFinite()` checks. Side-on flicker is safely isolated because `learnBaseline` is gated behind `clearlyOn`.

---

## 1. Baseline Formed While Mildly Bent or Leaning

### Concrete Sequence & Root Cause
In `PostureJudge.kt:132-135,147-159`:
```kotlin
val bentBelow = if (be != null) (be - bentDrop).coerceIn(BENT_BELOW_DEGREES, BENT_BELOW_MAX_DEGREES) else BENT_BELOW_DEGREES
val leanNow = abs(offMed) > LEAN_ABSOLUTE || (bo != null && abs(offMed - bo) > leanDelta)
```
Constants: `BASELINE_ELBOW_FROM = 160.0`, `BENT_DROP_DEGREES = 28.0`, `BENT_BELOW_DEGREES = 145.0`, `BENT_BELOW_MAX_DEGREES = 158.0`, `LEAN_DELTA = 0.19`, `LEAN_ABSOLUTE = 0.45`.

#### Case A: Mild bend during baseline ($160^\circ$–$165^\circ$)
1. Rescuer starts CPR with arms slightly bent at $162^\circ$.
2. Because $162.0 \ge 160.0$ (`BASELINE_ELBOW_FROM`), `learnBaseline()` accepts every frame. After 6 s, `baselineElbow = 162.0`.
3. `bentBelow` is computed as:
   $$(162.0 - 28.0) = 134.0 \xrightarrow{\text{coerceIn}(145.0, 158.0)} 145.0^\circ$$
4. **Dead zone for any rescuer $\le 173^\circ$:** Because $173 - 28 = 145$, any baseline between $160^\circ$ and $173^\circ$ collapses to $145.0^\circ$.
5. **What is missed:** The rescuer later tires and pushes at $147^\circ$ (a noticeable bend, e.g., Recording 15 bent block 2 median $151^\circ$). For a straight rescuer (baseline $178^\circ$), `bentBelow` is $150.0^\circ$ and $147^\circ$ is promptly flagged. But for this rescuer, $147.0 < 145.0$ is FALSE. The bend is completely ignored.
6. **What is falsely flagged:** Nothing is falsely flagged, but `straightFrom = 145 + 8 = 153.0^\circ`. The rescuer pushing at $147^\circ$ is left in limbo (`good = null`, `issue = null`).

#### Case B: Leaning during baseline (e.g., offset $+0.30$)
1. `learnBaseline()` only checks `if (elbowMed < BASELINE_ELBOW_FROM) return`. It performs **no validation on `offMed`**.
2. A rescuer starts pushing with straight arms ($175^\circ$) while leaning back (offset $+0.30$, where good posture is $0.05$–$0.15$).
3. `baselineOffset = 0.30` is recorded as their personal reference.
4. **What is falsely flagged:** At $t = 8$ s, the rescuer straightens up to perfect upright posture (offset $0.05$).
   $$|\text{offMed} - bo| = |0.05 - 0.30| = 0.25 > 0.19\ (\text{LEAN\_DELTA})$$
   `leanNow` becomes TRUE. After 1 s sustain, the app falsely berates the rescuer with "Shoulders over hands" when their shoulders are perfectly over hands!
5. **What is missed:** The rescuer leans even more in the initial direction up to $0.44$:
   $$|0.44 - 0.30| = 0.14 \le 0.19 \quad \text{and} \quad |0.44| \le 0.45\ (\text{LEAN\_ABSOLUTE})$$
   A severe diagonal lean of $0.44$ is completely missed.

### Verified JUnit Test
```kotlin
@Test
fun `baseline formed at 162 degrees misses subsequent mild bend at 147 degrees`() {
    val scenario = { t: Double -> Pose(elbow = if (t < 8.0) 162.0 else 147.0) }
    val judge = PostureJudge()
    val results = run(14.0, judge, scenario).filter { it.first > 11_000 }
    assertTrue("baseline was learned", judge.baseline != null)
    assertEquals(162.0, judge.baseline!!.first, 0.5)
    // (162 - 28) = 134 clamps to 145, so 147 is never flagged
    val flaggedBent = results.any { it.second.issue == PostureIssue.ELBOWS_BENT }
    assertTrue("mild bend at 147 is completely missed", !flaggedBent)
}

@Test
fun `baseline formed while leaning falsely flags upright posture as leaning`() {
    val scenario = { t: Double ->
        Pose(elbow = 175.0, offset = if (t < 8.0) 0.30 else 0.05)
    }
    val judge = PostureJudge()
    val results = run(14.0, judge, scenario).filter { it.first > 11_000 }
    assertTrue("baseline was learned", judge.baseline != null)
    assertEquals(0.30, judge.baseline!!.second, 0.05)
    // Upright posture delta |0.05 - 0.30| = 0.25 > 0.19 flags false lean
    val flaggedLean = results.all { it.second.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS }
    assertTrue("upright posture is falsely flagged as leaning", flaggedLean)
}
```

---

## 2. Pause / Resume: Baseline Annihilation

### Concrete Sequence & Root Cause
In `PostureJudge.kt:27-29,64-72`:
```kotlin
// The rescuer's own first good pushing; one per session (kept by reset(), a new judge starts fresh).
private var baselineElbow: Double? = null
private var baselineOffset: Double? = null

fun reset() {
    elbowWin.clear()
    offsetWin.clear()
    sideWin.clear()
    lastTimeMs = null
    notSideSinceMs = null
    elbow.reset()
    lean.reset()
}
```
The author of `PostureJudge` explicitly intended `reset()` to retain the baseline within a session ("kept by reset(), a new judge starts fresh").
However, look at `PractiseSession.kt:143-156`:
```kotlin
fun togglePause() {
    if (phase != Phase.LIVE) return
    isPaused = !isPaused
    lastTickMs = null
    if (isPaused) {
        audio.stopMetronome()
        metrics.pause()
        publishPaused()
    } else {
        // The rescuer has moved, so old readings no longer apply.
        analyzer = LiveAnalyzer() // <--- THROWS AWAY POSTURE JUDGE AND BASELINE!
        audio.startMetronome()
    }
}
```
1. Rescuer pushes straight at $178^\circ$ for 8 seconds. Baseline is established (`178.0`, `0.05`).
2. Rescuer pauses at $t = 30$ s to catch their breath.
3. Rescuer unpauses: `PractiseSession` executes `analyzer = LiveAnalyzer()`.
4. The new `LiveAnalyzer` instantiates a brand new `PostureJudge()`.
5. **The baseline is completely wiped.** `baselineElbow` and `baselineOffset` are `null`.
6. For the next 6+ seconds, `bentBelow` defaults to $145.0^\circ$. Mild bends ($146^\circ$–$151^\circ$) cannot be flagged.
7. If the rescuer resumed pushing because they were fatigued, their arms are often bent ($<160^\circ$). Because `learnBaseline` requires $\ge 160^\circ$, **a new baseline is NEVER established for the rest of the 2-minute session**.

### Verdict & Recommendation
This is completely unacceptable in a practice session. The baseline must survive pauses within the same session.
- **Fix:** Either pass the existing `judge` or baseline `Pair<Double, Double>` to the new `LiveAnalyzer`, or call `analyzer.reset()` instead of reallocating `LiveAnalyzer()`.

### Verified JUnit Test
```kotlin
@Test
fun `pause and resume wipes baseline so mild bend is missed`() {
    val initialJudge = PostureJudge()
    run(8.0, initialJudge) { Pose(elbow = 178.0) }
    assertTrue(initialJudge.baseline != null)

    // PractiseSession.togglePause() constructs new LiveAnalyzer() -> new PostureJudge()
    val postPauseJudge = PostureJudge()
    val afterResume = run(8.0, postPauseJudge) { Pose(elbow = 147.0) }.filter { it.first > 4_000 }
    assertTrue("baseline was lost across pause", postPauseJudge.baseline == null)
    val flaggedBent = afterResume.any { it.second.issue == PostureIssue.ELBOWS_BENT }
    assertTrue("mild bend at 147 missed after resume", !flaggedBent)
}
```

---

## 3. Rescuer Swap or Camera Moved Mid-Session

### Concrete Sequence & Root Cause
Once learned, the baseline is completely frozen in `PostureJudge.kt:148`:
```kotlin
if (baselineElbow != null) return
```
There is no decay, no rolling window, and no re-anchor detection for posture baseline.

#### Scenario A: Rescuer swap (standard CPR protocol every 2 minutes or when fatigued)
1. Rescuer 1 establishes baseline: `baselineElbow = 178.0`, `baselineOffset = 0.05`.
2. Rescuer 2 swaps in at $t = 60$ s. Rescuer 2 has different body proportions or kneeling position.
3. Rescuer 2 pushes upright with arms at $178^\circ$ and normal offset $0.26$ (well within valid range, as recorded in `PostureJudge.kt` for recording 14: $0.12$–$0.30$).
4. In `PostureJudge`:
   $$|0.26 - 0.05| = 0.21 > 0.19\ (\text{LEAN\_DELTA})$$
5. Rescuer 2 is **permanently flagged as leaning** for their entire CPR shift despite good technique.
6. Conversely, if Rescuer 1 established a baseline of $162^\circ$, Rescuer 2's mild bend at $148^\circ$ will never be flagged.

#### Scenario B: Phone / camera bumped or adjusted mid-session
1. A bystander adjusts the phone angle by $10^\circ$, shifting camera perspective.
2. The projected horizontal offset $(wx - sx) \cdot dir / armLen$ shifts by $0.20$.
3. Since baseline is immutable, the rescuer is permanently flagged as leaning or prevented from clearing.

### Verified JUnit Test
```kotlin
@Test
fun `rescuer swap mid session falsely flags second rescuer due to stale offset baseline`() {
    val judge = PostureJudge()
    // Rescuer 1 establishes baseline at offset 0.05
    run(8.0, judge) { Pose(elbow = 178.0, offset = 0.05) }
    assertEquals(0.05, judge.baseline!!.second, 0.02)

    // Rescuer 2 steps in with upright posture at offset 0.26
    val results = run(14.0, judge) { t ->
        Pose(elbow = 178.0, offset = if (t < 8.0) 0.05 else 0.26)
    }.filter { it.first > 11_000 }
    val flaggedLean = results.all { it.second.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS }
    assertTrue("rescuer 2 falsely flagged as leaning due to stale baseline", flaggedLean)
}
```

---

## 4. EmergencyHost: No Straight Start Guaranteed

### Concrete Sequence & Root Cause
`EmergencyHost.kt:97,157-159` initializes `analyzerBox = remember { arrayOf(LiveAnalyzer()) }` and starts CPR immediately. Unlike `PractiseSession`, there is no framing check, no calibration, and no countdown.
In a real emergency:
1. Rescuer is terrified, untrained, or panicking. They begin compressions immediately with bent arms (e.g., $150^\circ$, or dipping between $142^\circ$–$154^\circ$).
2. `elbowMed` is consistently $< 160.0^\circ$ (`BASELINE_ELBOW_FROM`).
3. **`learnBaseline` never executes.** `baselineElbow` and `baselineOffset` remain `null` for the entire emergency.
4. **Degraded posture coaching consequences:**
   - Bent elbows: `bentBelow` remains fixed at $145.0^\circ$. Bends between $145^\circ$ and $158^\circ$ are NEVER flagged. The rescuer delivers suboptimal compressions indefinitely with zero feedback.
   - Leaning: Without a baseline (`bo == null`), lean detection falls back to:
     $$\text{leanNow} = |\text{offMed}| > 0.45\ (\text{LEAN\_ABSOLUTE})$$
     (Notice the old fixed threshold before commit `f99a1bc` was `LEAN_ENTER = 0.28`). At $0.45$, the threshold is so loose that substantial diagonal lean ($0.29$–$0.40$) is completely ignored!
5. In the most critical mode of the app, posture feedback is crippled unless the rescuer accidentally pushes straight for 6 seconds.

### Verified JUnit Test
```kotlin
@Test
fun `emergency CPR starting with bent arms never establishes baseline and misses mild bends`() {
    val judge = PostureJudge()
    val results = run(30.0, judge) { Pose(elbow = 150.0) }
    assertTrue("no baseline established", judge.baseline == null)
    val flaggedBent = results.any { it.second.issue == PostureIssue.ELBOWS_BENT }
    assertTrue("bent arms at 150 never flagged without baseline", !flaggedBent)
}
```

---

## 5. Numeric Edge Cases

### A. Wrist Crossing Hip X: Sign Flip & False Lean
In `PostureJudge.kt:118-120`:
```kotlin
// Positive: shoulder is behind the hands (on the hips' side of the wrist). Negative: ahead of them.
val dir = if (wx >= hx) 1.0 else -1.0
val offset = (wx - sx) * dir / armLen
```
1. In a side-on view, if the rescuer is positioned such that wrist X is close to hip X (common when the phone is positioned near the victim's waist or head):
2. Suppose $hx = 0.500$, shoulder is behind wrist $wx - sx = 0.10$, $armLen = 0.40$.
3. When $wx = 0.501$ ($wx \ge hx$): $dir = +1.0 \implies offset = +0.25$.
4. When $wx = 0.499$ ($wx < hx$): $dir = -1.0 \implies offset = -0.25$.
5. A tiny natural lateral sway of $2$ mm causes an instant **swing of $0.50$ in offset**!
6. If the baseline was learned at $+0.25$, flipping to $-0.25$ yields $|-0.25 - (+0.25)| = 0.50 > 0.19$, instantly triggering a false `SHOULDERS_NOT_OVER_HANDS` alert!
7. Unlike `LandmarkGuard.kt`, which maintains a persistent `facing` latch with hysteresis, `PostureJudge` recomputes `dir` raw per frame.

#### Verified JUnit Test
```kotlin
@Test
fun `wrist crossing hip x causes offset sign flip and false lean alert`() {
    val judge = PostureJudge()
    // Baseline established with wrist right of hip (hipDx = -0.2)
    run(8.0, judge) { Pose(elbow = 178.0, offset = 0.25, hipDx = -0.2) }
    assertEquals(0.25, judge.baseline!!.second, 0.05)

    // Wrist drifts slightly left of hip (hipDx = +0.01). Geometry is identical, but dir flips +1 -> -1
    val after = run(14.0, judge) { t ->
        Pose(elbow = 178.0, offset = 0.25, hipDx = if (t < 8.0) -0.2 else 0.01)
    }.filter { it.first > 11_000 }
    val flaggedLean = after.all { it.second.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS }
    assertTrue("wrist crossing hip x falsely triggers lean alert", flaggedLean)
}
```

### B. Isolated Straight Frame Timing Glitch in `learnBaseline`
In `PostureJudge.kt:149-153`:
```kotlin
val last = lastBaseT
lastBaseT = t
if (elbowMed < BASELINE_ELBOW_FROM) return
if (last != null && t - last in 1..MAX_GAP_MS) baseMs += t - last
```
`lastBaseT = t` is recorded **before** the elbow angle check.
If a rescuer pushes with bent arms ($150^\circ$) and has an isolated 1-frame tracking jitter of $161^\circ$, `t - last` evaluates to $33$ ms (time since the preceding bent frame!).
`baseMs` accumulates $33$ ms of straight credit, and `baseOffsets.add(offMed)` captures the offset during that isolated jitter frame. Over 2 minutes, sporadic glitches can accumulate to 6 s of "straight pushing" and freeze an inaccurate baseline.
`lastBaseT` should only be updated when `elbowMed >= BASELINE_ELBOW_FROM`.

### C. Handled Edge Cases
- **NaN / Infinite values:** Safely rejected at lines 81–87 (`aspect.isFinite()`, `coords.any { !it.isFinite() }`).
- **Zero arm length:** Safely rejected at line 116 (`armLen < MIN_ARM_LENGTH` where `MIN_ARM_LENGTH = 0.05`).
- **Side-on flicker:** Gated safely at lines 107 and 129; `learnBaseline` is never called unless `clearlyOn` is true.

---

## Recommended Remedies

1. **Persist Baseline across Pause (`PractiseSession.kt`):**
   Instead of `analyzer = LiveAnalyzer()` in `togglePause()`, either call a new `analyzer.reset()` method that clears transient windows while preserving `judge.baseline`, or pass `analyzer.baseline` to the new `LiveAnalyzer`.
2. **Constrain Baseline Offset in `learnBaseline`:**
   Reject baseline frames where $|offMed| > 0.25$. Do not learn an offset baseline if the rescuer is leaning during the first 6 seconds.
3. **Smooth Facing Direction (`dir`):**
   Determine facing direction using hips and shoulders or adopt `LandmarkGuard`'s facing latch, rather than raw per-frame `wx >= hx`.
4. **Emergency Mode Fallback:**
   In `EmergencyHost`, restore a stricter fixed lean threshold (e.g., $0.28$) when no baseline exists, rather than relaxing to $0.45$.
5. **Re-anchor or Relearn Baseline:**
   If sustained good tracking diverges from the baseline for $>10$ s, or when `LandmarkGuard` signals `REANCHOR` or `SIDE_SWITCH`, trigger baseline invalidation/relearning.
