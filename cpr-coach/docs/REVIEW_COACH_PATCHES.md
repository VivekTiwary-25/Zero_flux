# Review of Coach Patches

Second opinion on `emergency_word_clear.patch` and `elbow_voice_faster.patch` applied together onto an `adf9409` extract of `cpr-coach`.

---

## 1. EmergencyCoach (`emergency_word_clear.patch`)

### Findings

The patch introduces a critical bug: `leavingPosture` bypasses the candidate hold timer (`holdMs = 1_000L`) whenever `word` is a posture word and the incoming `wanted` word is not a posture word.

```kotlin
val leavingPosture = word.isPostureWord() && !wanted.isPostureWord()
if (wanted == EmergencyWord.KEEP_PUSHING || leavingPosture || reading.timeMs - candidateSinceMs >= holdMs) {
    word = wanted
}
```

This breaks two core behaviors:

#### 1A. Single-read off-target rate during bent posture
When a rescuer is pushing with bent arms at target rate (110 rpm), `word` is `STRAIGHTEN_ARMS`.
If locked rate fluctuates off-target for just one 250 ms reading (e.g., 90 rpm), `wordFor(reading)` evaluates to `FASTER`.
Because `FASTER` is not a posture word, `leavingPosture` evaluates to `true`.
- The word **flashes FASTER immediately** on that single reading (0 ms hold), violating the invariant that new instructions must hold before showing.
- When the rate returns to 110 rpm on the next reading (250 ms later), `wanted` returns to `STRAIGHTEN_ARMS`.
- However, `word` is now `FASTER` (`FASTER.isPostureWord()` is `false`), so `leavingPosture` is `false`.
- The candidate switches back to `STRAIGHTEN_ARMS`, resetting `candidateSinceMs` and requiring a full `holdMs` (1,000 ms) before `STRAIGHTEN_ARMS` can be shown again.
- During that 1,000 ms wait, `word` remains `FASTER`.
- Result: On a single 250 ms rate fluctuation, `FASTER` is displayed for **1.25 s (1,250 ms)**, leaving "Straighten arms" empty for that entire time.

**Exact word sequence (250 ms ticks, off-target rate 90 rpm at t=2750 ms):**
- `t = 2500 ms`: `STRAIGHTEN_ARMS` (rate 110 rpm, bent)
- `t = 2750 ms`: `FASTER` (rate 90 rpm, bent — flashes immediately)
- `t = 3000 ms`: `FASTER` (rate 110 rpm, bent — candidate resets to `STRAIGHTEN_ARMS`)
- `t = 3250 ms`: `FASTER` (candidate hold = 250 ms)
- `t = 3500 ms`: `FASTER` (candidate hold = 500 ms)
- `t = 3750 ms`: `FASTER` (candidate hold = 750 ms)
- `t = 4000 ms`: `STRAIGHTEN_ARMS` (candidate hold = 1,000 ms reaches `holdMs`)

#### 1B. Single-read posture dropout during bent posture
If posture drops out for a single reading (posture `null`, or posture reading briefly evaluated `good` due to landmark noise or angle flicker):
- `postureWord` returns `null`. With rate at 110 rpm, `wordFor` returns `GOOD`.
- Because `word` was `STRAIGHTEN_ARMS` and `GOOD` is not a posture word, `leavingPosture` evaluates to `true`.
- The word immediately clears to `GOOD` on that single dropout read.
- On the next read (250 ms later), posture is bent again. Candidate resets to `STRAIGHTEN_ARMS`.
- Since `word` is now `GOOD`, `leavingPosture` is `false`, and `STRAIGHTEN_ARMS` must wait for `holdMs` (1,000 ms).
- Result: `GOOD` is displayed for **1.25 s (1,250 ms)** to a rescuer with bent arms, leaving "Straighten arms" empty for 1.25 s.

**Exact word sequence (250 ms ticks, dropout at t=2750 ms):**
- `t = 2500 ms`: `STRAIGHTEN_ARMS` (rate 110 rpm, bent)
- `t = 2750 ms`: `GOOD` (rate 110 rpm, posture null or good — clears immediately)
- `t = 3000 ms`: `GOOD` (rate 110 rpm, bent — candidate resets to `STRAIGHTEN_ARMS`)
- `t = 3250 ms`: `GOOD` (candidate hold = 250 ms)
- `t = 3500 ms`: `GOOD` (candidate hold = 500 ms)
- `t = 3750 ms`: `GOOD` (candidate hold = 750 ms)
- `t = 4000 ms`: `STRAIGHTEN_ARMS` (candidate hold = 1,000 ms reaches `holdMs`)

### Frequency in recordings 10–15
**0 times.** In real replays of recordings 10–15, there are no isolated 1-read off-target rate excursions or 1-read posture dropouts during established bent blocks:
- In recording 12, the bend ends at ~28.5 s, and the subsequent rate lock at 121 rpm (SLOWER) is sustained across multiple reads (28.75–30.5 s), not an isolated single-read spike.
- In recording 15, the bent blocks [38.2, 43.4] s and [54, 61.5] s maintain continuous rate and posture until genuine posture clearance at 45.25 s or full pause at 65.0 s.

However, the vulnerability is inherent in the logic and reliably reproduces in unit testing.

### Failing test (as text)

```kotlin
@Test
fun `one-read off-target rate during bent posture must not flash FASTER or clear straighten arms`() {
    val coach = EmergencyCoach(0)
    coach.feed(0, 2_500) { beating(it, 110, bent = true) }
    assertEquals(EmergencyWord.STRAIGHTEN, coach.word)

    // Single read with rate 90 (off target) while still bent
    coach.update(beating(2_750, 90, bent = true))
    // FASTER must not flash on a single read
    assertEquals(EmergencyWord.STRAIGHTEN, coach.word)

    // Rate returns to target
    coach.update(beating(3_000, 110, bent = true))
    assertEquals(EmergencyWord.STRAIGHTEN, coach.word)
}

@Test
fun `one-read posture dropout during bent posture must not clear straighten arms`() {
    val coach = EmergencyCoach(0)
    coach.feed(0, 2_500) { beating(it, 110, bent = true) }
    assertEquals(EmergencyWord.STRAIGHTEN, coach.word)

    // Single read posture dropout (rate fine, posture temporarily good or null)
    coach.update(beating(2_750, 110, bent = false))
    // Posture word must not clear on a single-read dropout
    assertEquals(EmergencyWord.STRAIGHTEN, coach.word)

    // Posture returns to bent
    coach.update(beating(3_000, 110, bent = true))
    assertEquals(EmergencyWord.STRAIGHTEN, coach.word)
}
```

### Minimal fix

**Recommended (revert patch):**
Reject `emergency_word_clear.patch`. Revert to the baseline condition in `EmergencyCoach.kt`:
```kotlin
if (wanted == EmergencyWord.KEEP_PUSHING || reading.timeMs - candidateSinceMs >= holdMs) word = wanted
```
The baseline 1 s hold was specifically designed to filter out rate and posture noise ("An instruction must not outlive the reading behind it, but a new one has to hold or it flickers").

**Alternative (if prompt clearance to GOOD is required without hold):**
At minimum, never allow `leavingPosture` when `wanted` is a rate correction (`FASTER` or `SLOWER`). Furthermore, require confirmed good posture (`reading.posture?.good == true`), preventing raw null dropouts from clearing the word:
```kotlin
val leavingPosture = word.isPostureWord() && wanted == EmergencyWord.GOOD && reading.posture?.good == true
if (wanted == EmergencyWord.KEEP_PUSHING || leavingPosture || reading.timeMs - candidateSinceMs >= holdMs) {
    word = wanted
}
```

---

## 2. CoachPolicy (`elbow_voice_faster.patch`)

### Findings

Say plainly: **Nothing found.**

With `elbowSteadyMs = 1_000L`, brief bent reads (stops, sitting up, repositioning) **do not** trigger `Phrase.ELBOWS_STRAIGHT`.

### Analysis

1. **Duration requirement:** `CoachPolicy.requiredMs(Phrase.ELBOWS_STRAIGHT)` is `1_000L` ms. Any bent read or sequence lasting less than 1,000 ms cannot satisfy `now - since >= requiredMs(p)`.
2. **Stops and stillness:** When a rescuer stops pushing and is still (`tracking == TrackingState.PAUSED` and `moving == false`), `wantedPhrases` returns `emptyList()`. Line 48 (`wantedSinceMs.keys.retainAll(wanted.toSet())`) immediately removes `ELBOWS_STRAIGHT`, resetting any accumulated time.
3. **Repositioning and movement during pause:** If the rescuer moves while `PAUSED` (sitting up, repositioning hands), `wantedPhrases` only includes `ELBOWS_STRAIGHT` if `reading.posture?.issue == PostureIssue.ELBOWS_BENT`. Before `PostureJudge` can emit `ELBOWS_BENT`, it requires:
   - Side-on confidence sustained over a 1,000 ms window (`SIDE_WINDOW_MS`),
   - Rolling median span of at least 500 ms,
   - `Issue.step` sustained bent angle for `SUSTAIN_MS = 1_000L` ms.
   Coupled with `CoachPolicy`'s 1,000 ms steady wait, triggering voice requires at least 2.0 to 2.5 s of continuous, side-on bent movement.
4. **Recording 12 pause at 41.25–42.00 s:**
   In recording 12, there is an outside-bend pause where the rescuer moves while PAUSED with bent elbows for 750 ms (4 readings from 41.25 to 42.00 s, still at 42.25 s).
   - At 250 ms, 500 ms, and 750 ms voice delays, this brief pause triggered an invalid `elbows_straight` clip at 41.50–42.00 s.
   - At `elbowSteadyMs = 1_000L`, this clip is **completely suppressed**.

### Recordings 10–15 check

Every elbow clip in recordings 10–15 was inspected in both Practise and Emergency modes:

| Recording | Bend blocks | Practise clips | Emergency clips | Elbow clips outside bent blocks |
| :--- | :--- | :--- | :--- | :---: |
| 10 | None | 5.25s `keep_going`, 29.25s `keep_going` | Same | **0** |
| 11 | None | 8.25s `good`, 31.50s `keep_going` | Same | **0** |
| 12 | 18.0–29.0 s | 10.75s `slow_down`, **19.75s `elbows_straight`**, 32.75s `keep_going`, 44.25s `keep_going` | Same | **0** |
| 13 | None | 12.00s `push_faster`, 35.00s `good`, 41.75s `keep_going` | Same | **0** |
| 14 | None | 7.75s `good`, 27.50s `keep_going` | Same | **0** |
| 15 | 38.2–43.4 s, 54.0–61.5 s | 8.00s `good`, 35.00s `keep_going`, **41.25s `elbows_straight`**, **56.75s `elbows_straight`**, 68.00s `shoulders_over_hands` | Same | **0** |

- In recording 12, the single `elbows_straight` clip is at 19.75 s, inside [18.0, 29.0] s.
- In recording 15, the two `elbows_straight` clips are at 41.25 s and 56.75 s, inside [38.2, 43.4] s and [54.0, 61.5] s respectively.
- Total elbow clips outside bent blocks across all six recordings: **0**.
