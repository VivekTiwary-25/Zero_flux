# Scorecard check on real recordings

Machine check and analysis of Practise scorecard metrics on real phone recordings.
Replayed `tools/testdata/real-cph2467-14.csv` and `real-cph2467-15.csv` through `LiveAnalyzer` and `SessionMetrics`.
Each recording was fed as `PoseFrame` with `ArmLandmarks` at 4:3 aspect ratio, sampled every 250 ms never before the last frame.
Implemented by `app/src/test/java/com/zeroflux/cprcoach/engine/ScorecardReplayTest.kt`.

## Scorecard results

| Metric | real-cph2467-14.csv | real-cph2467-15.csv |
|---|---|---|
| Session time (`sessionSeconds`) | 63 s | 71 s |
| Measured time (`measuredSeconds`) | 20 s | 49 s |
| Time in range (`secondsInRange`) | 20 s | 45 s |
| Average rate (`averageRate`) | 113 / min | 107 / min |
| Elbows straight (`elbowsStraightPercent`) | 95% | 75% |
| Pauses (`pauses`) | 0 | 1 |

---

## 1. Elbows-straight %: PostureJudge vs ElbowGate

### Code path
`elbowsStraightPercent` comes entirely from the new `PostureJudge` verdict, not the old `ElbowGate`.

In `engine/LiveAnalyzer.kt:67-90`:
1. Frames with landmarks enter `addFrame(frame: PoseFrame)`.
2. `addFrame(sample, arm != null)` is invoked with `judged = true`.
3. At `LiveAnalyzer.kt:107`, the old gate update `if (!judged) elbowVerdict = elbows.update(...)` is bypassed because `judged == true`.
4. `PostureJudge.update(sample, arm, frame.aspect)` runs on line 81.
5. On lines 85-89, `elbowVerdict` is assigned directly from the posture reading:
   - `true` when `reading.issue == PostureIssue.ELBOWS_BENT`
   - `false` when `reading.good == true`
   - `null` otherwise (e.g. leaning, warming up, or in hysteresis gap)
6. `read(nowMs)` forwards `elbowVerdict` as `LiveReading.elbowsBent`.
7. `SessionMetrics.kt:43-46` updates `elbowSeenMs` and `elbowStraightMs` only when `reading.elbowsBent` is non-null.

### Verification
Running both paths side by side on `real-cph2467-15.csv` proves the cutover:
- Under `PostureJudge` (the actual app path): `elbowsStraightPercent` is **75%** (`bentMs = 14750 ms`, `straightMs = 43750 ms`).
- Under the old `ElbowGate` (sample-only path): `elbowsStraightPercent` is **100%** (`bentMs = 0 ms`, `straightMs = 56250 ms`).
The old `ElbowGate` required `degrees < 100.0` (`BENT_BELOW_DEGREES`) to trigger bent elbows. The bent angles in recording 15 sit between 124° and 155°, so `ElbowGate` completely missed both bent blocks.

---

## 2. Plausibility against picture labels

### Ground truth labels from pictures
- **Recording 15:**
  - 3.5–32.0 s: straight (28.5 s)
  - 32.3–34.0 s: reposition (1.7 s)
  - 34.5–37.3 s: straight (2.8 s)
  - 37.4–38.0 s: unlabelled gap (0.6 s)
  - 38.2–43.4 s: bent block 1 (5.2 s)
  - 44.0–53.4 s: straight (9.4 s)
  - 54.0–61.5 s: bent block 2 (7.5 s)
  - 65.0–69.5 s: leaning (4.5 s)
  Total labelled bent time: 5.2 + 7.5 = 12.7 s (about 13 s).
  Total labelled straight time: 28.5 + 2.8 + 9.4 = 40.7 s.
  Expected straight percentage over judged straight/bent time: `40.7 / (40.7 + 12.7) = 76.2%`.

- **Scorecard measurement on recording 15:**
  - Straight time (`elbowStraightMs`): 43,750 ms (43.75 s)
  - Bent time (`elbowBentMs`): 14,750 ms (14.75 s)
  - Total judged elbow time (`elbowSeenMs`): 58,500 ms (58.5 s)
  - Measured `elbowsStraightPercent`: `Math.round(100.0 * 43750 / 58500) = 75%`.
  - Leaning time (4,000 ms around 65–70 s) produces `posture.issue == SHOULDERS_NOT_OVER_HANDS`. Because `elbowVerdict` is null during leaning, leaning is excluded from both numerator and denominator.
  - The measured 14.75 s of bent time reflects the ~12.7 s labelled bent periods plus ~2 s of sustain window and hysteresis (`SUSTAIN_MS = 1000L`).

The 75% result is plausible and matches the picture labels.

- **Recording 14:**
  - Picture labels: 3.0–24.0 s straight (21 s), then pause at 24.7–33.5 s (8.8 s), pose lost after 33.2 s.
  - Scorecard measurement: `straightMs = 25000 ms`, `bentMs = 1250 ms`, `elbowsStraightPercent = 95%`.
  - The 1,250 ms of bent verdict occurred at 27.5–28.5 s while hands shifted during the pause. Active pushing was 100% straight.

---

## 3. Pause counting: implausible numbers and code path analysis

### Observation
Pause counting on both recordings is implausible:
- `real-cph2467-14.csv` contains an 8.8 s real pause (24.7–33.5 s), but the scorecard reports **0 pauses**.
- `real-cph2467-15.csv` contains only a 1.7 s short reposition (32.3–34.0 s), but the scorecard reports **1 pause**.

### Root cause: strict stillness reset in SessionMetrics
In `engine/SessionMetrics.kt:47-64`:

```kotlin
when (reading.tracking) {
    TrackingState.PAUSED -> if (reading.moving) pausedMs = 0 else {
        pausedMs += dt
        if (pausedMs >= minPauseMs && !pauseCounted) {
            pauses++
            pauseCounted = true
        }
    }
    TrackingState.BEATING -> {
        pausedMs = 0
        pauseCounted = false
    }
    TrackingState.LOST, TrackingState.WARMING -> pausedMs = 0
}
```

`SessionMetrics` requires an unbroken streak of 2,000 ms (`minPauseMs`) where `reading.tracking == TrackingState.PAUSED` and `reading.moving == false`.
Any tick where `reading.moving == true` immediately resets `pausedMs` to 0.

### Why recording 14 misses its 8.8 s pause
1. The rescuer stopped pushing at 24.5 s. Tracking went `PAUSED` at 24.75 s.
2. At 25.00 s, `LiveAnalyzer` briefly held the previous rate (`heldRate`), reporting `BEATING`.
3. From 25.25 s to 33.50 s, tracking was `PAUSED`.
4. However, `LiveAnalyzer.updateMoving()` (`LiveAnalyzer.kt:159-183`) calculates wrist RMS displacement over a 500 ms window. Landmark jitter from MediaPipe exceeded `movingOnRms` (0.020) intermittently:
   - `moving == true` at 26.00–26.25 s
   - `moving == true` at 27.75 s
   - `moving == true` at 28.50–29.00 s
   - `moving == true` at 29.75–31.00 s
   - `moving == true` at 32.50–33.50 s
5. The stretches of stillness between jitter spikes lasted at most 1,250 ms (e.g. 26.50–27.50 s and 31.25–32.25 s). None reached 2,000 ms.
6. Each jitter spike reset `pausedMs = 0`.
7. At 33.75 s, tracking transitioned to `TrackingState.LOST` (rescuer moved away). `TrackingState.LOST` also sets `pausedMs = 0`.
8. `pausedMs` never reached 2,000 ms, so `pauses` stayed 0.

### Why recording 15 counts its 1.7 s reposition
1. The rescuer repositioned from 32.3 s to 34.0 s.
2. Tracking transitioned from `BEATING` to `PAUSED` at 33.00 s.
3. During this interval, the rescuer's wrists remained still on the chest. `moving` stayed `false` continuously from 32.50 s through 35.25 s (2.75 s continuous).
4. Under `TrackingState.PAUSED`, unbroken stillness accumulated from 33.00 s to 35.00 s.
5. At 34.75 s, `pausedMs` reached 2,000 ms, incrementing `pauses` to 1.
6. A reposition labelled at 1.7 s duration was thus counted as a full session pause.

### Comparison with CoachPolicy
`CoachPolicy.kt:77-86` handles pauses robustly by using a majority vote over a rolling history:

```kotlin
val still = pausedReads.count { !it.second } * 2 >= pausedReads.size
pendingRecovery = !pauseAcknowledged && now - pausedSinceMs!! >= MIN_PAUSE_MS && still
```

In `CoachPolicy`, temporary jitter spikes do not erase pause progress. This allowed `CoachPolicy` to trigger `KEEP_GOING` for recording 14 at 27.5 s (`PauseVoiceReplayTest.kt:82`).
`SessionMetrics.kt` lacks this majority filter, making its pause counter brittle against camera tracking noise.
