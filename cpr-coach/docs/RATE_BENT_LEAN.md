# Rate loss with bent arms and leaning

## Result

This is mainly a periodicity failure, not weak wrist motion.
Bent-arm and lean verdicts do not veto rate estimation.
`moving=true` measures motion over 500 ms. It does not establish a repeating beat.

The proposal below shows a rate at every replay tick in the three reported outages.
These are 39.5–43.5, 57.5–59 and 64–69.5 seconds in recording 15.
Shown ticks rise from 7/47 to 47/47. Fresh estimator rates rise from 3/47 to 44/47.
Nobody stays at zero. Recording 14's pause goes from one false rate to zero.
This is replay evidence, not verified rate accuracy or a shipping recommendation.
The full second bent label still has five blank ticks at 55–56 seconds.
The unknown future phantom-frame filter is not validated by this proposal.

Reference: `cpr-coach` revision `adf940947fbca2fe083615955ac1a70111551624`.
App code was not changed. The Kotlin diff below is unapplied.
This report replaces the earlier partial 3.5-second-history proposal.

## Replay and verification

Run from the task checkout. Python 3 and NumPy are required.

```sh
python3 cpr-coach/tools/rate_bent_lean.py \
  --data-dir ../ref-t261031-0346-9d11/cpr-coach/tools/testdata \
  --detrend-half 7 --still-window-ms 750 --recent-cycles 2 \
  --min-recent-correlation 0.25 --global-clip 0.75 \
  --diagnostic-all-gates --records
```

The command emits baseline and proposal JSON for recordings 1–15 and nobody.
Add `--phantom-sensitivity` to apply the diagnostic mask to the proposal only.
Both CLI commands were run across all 16 files.
Assertions checked the three outage counts and both negative controls.

Every source frame is fed before each 250 ms read.
Ticks run from the first CSV timestamp through the last available tick.
Times in this report are seconds from the first row. Endpoints are inclusive.
Thus 57.5–59 contains seven reads, not seven compressions.
The replay includes the guard, visibility, finite checks, accepted-frame gap resets,
interpolation, detrending, lag choice, recent correlation, movement hysteresis,
five-value median, stale suppression and display hold.

The previous port omitted stale accepted-frame suppression and dated holds from the
read tick instead of the last accepted frame. Both are corrected here.
This lowers some baseline all-recording display counts from the earlier report.
It does not change recording 15's baseline window counts.
Coordinates, visibility and aspect are rounded to Kotlin Float first.
The recorded double `wrist_signal` is retained. Recorded side changes reach the guard.
Aspect is assumed to be 4:3, as in existing coordinate replay tests.
The CSV does not store aspect, so actual camera geometry cannot be verified.
Posture is omitted because it cannot change rate or movement.

A separate source review found no remaining baseline rate-path mismatch.
Scalar DSP loops and vector DSP agreed on all 2,793 evaluated reads for each variant.
State, first rejection and chosen integer lag all agreed.
Maximum candidate-rate differences were 1.14e-13/min before and 1.56e-13/min after.
This checks numerical agreement, not an independent implementation of every gate.
The proposed diff passed `patch --dry-run -p2` in the reference app directory.
Kotlin compilation, Gradle, Android and phone UI were not run.

## Why it pauses

[`RateEstimator.kt`](../app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt)
retains six seconds of accepted samples.
It requires three seconds of history and integer density of at least six frames/s.
It interpolates at 30 Hz and removes a centered 31-sample mean.
Recent 1.5-second RMS must reach 0.015 torso lengths.
It selects a local autocorrelation peak at lags 10–30 samples.
The shortest peak reaching 60% of the strongest positive peak wins.
Global correlation must reach .35, or .7 at lags <=13 or >=27.
Recent correlation must reach .4. It pools up to four preceding cycles.

[`LiveAnalyzer.kt`](../app/src/main/java/com/zeroflux/cprcoach/engine/LiveAnalyzer.kt)
uses a separate 500 ms line-detrended RMS for movement.
Its movement thresholds are .03 on and .02 off.
On `NoBeat`, it can hold the last measured rate for two seconds while moving.
When that hold fails or expires, the state becomes `PAUSED` even if moving is true.
A visible but stale accepted sample is `WARMING`, with no number.

Older `RATE_*.md` studies use different thresholds or estimator-only replay.
In particular, `compare_signals.py` is not a faithful analyzer replay.
The four-second-window and adjacent-cycle proposals are not established fixes here.

## Measured baseline windows

RMS, ACF and Recent are medians over read ticks after the current guard.
RMS is in torso lengths. ACF is the selected global peak.
Recent is the pooled recent-cycle correlation at that lag.
C / R counts first rejection at the global / recent correlation gate.
Guard counts are rejected frames delivered during the window's read ticks.
No listed window has an amplitude or density first rejection.

| Recording 15 window | Ticks | RMS | ACF | Recent | C / R | Guard rejects |
|---|---:|---:|---:|---:|---:|---:|---:|
| Straight 3.5–32 | 115 | 0.0646 | 0.9058 | 0.8842 | 1 / 4 | 0 |
| Straight 44–53.4 | 38 | 0.0640 | 0.8470 | 0.8246 | 0 / 0 | 4 |
| Bent 38.2–43.4 | 21 | 0.0632 | 0.2564 | 0.0796 | 19 / 2 | 0 |
| Bent 54–61.5 | 31 | 0.0723 | 0.4478 | 0.2609 | 7 / 11 | 52 |
| Lean 65–69.5 | 19 | 0.0801 | 0.1827 | 0.1246 | 18 / 1 | 12 |
| Outage 39.5–43.5 | 17 | 0.0627 | 0.2541 | 0.0796 | 16 / 0 | 0 |
| Outage 57.5–59 | 7 | 0.0806 | 0.2709 | 0.1846 | 5 / 2 | 8 |
| Outage 64–69.5 | 23 | 0.0806 | 0.1917 | 0.1462 | 18 / 3 | 21 |

### Motion and transition history

| Raw frame window | Signal SD | Signal range | Consecutive-difference SD | Wrist jumps >.035 |
|---|---:|---:|---:|---:|
| Straight 3.5–32 | .06368 | .25620 | .03503 | 2 |
| Straight 44–53.4 | .06822 | .33940 | .03776 | 5 |
| Bent 38.2–43.4 | .06377 | .25322 | .03661 | 0 |
| Bent 54–61.5 | .08811 | .36728 | .07796 | 57 |
| Lean 65–69.5 | .10496 | .46861 | .06835 | 15 |

These raw statistics use only frames inside each window.
Jumps are unscaled image-plane wrist distances between consecutive in-window frames.
They are not compression counts or confirmed phantom counts.

The first bent block has normal amplitude and no guard rejects.
The preceding 37–38-second transition spans -1.21546 to +.47982 in raw wrist signal.
Fourteen accepted transition samples span -.66437 to -.26326.
Disturbed accepted history stays in the six-second correlation window.
At 41 seconds RMS is .05236, but ACF is only .222 and Recent is .080.
Lowering the amplitude floor cannot fix that rejection.

Lean has more raw motion than either straight block.
At 66 seconds RMS is .08418, ACF is .150 and Recent is -.064.
At 68 seconds Recent recovers to .442, but ACF remains .182.
Both old transition influence and local waveform changes matter.

### Phantom wrists

The second bent block has rapid horizontal wrist relocations.
The wrist remains below the elbow by .0710–.1589 image heights.
This is not simply an inverted elbow/wrist Y order.
Let A be shoulder-to-elbow length, F elbow-to-wrist length, and H be horizontal
wrist-to-elbow distance divided by A. Use unscaled image coordinates here.
The H<.20 cluster has 61 frames and median F/A=.704.
The H>=.30 cluster has 80 frames and median F/A=.671.
Median wrist visibility is .9849 and .9856 respectively.
Visibility and a short-forearm cutoff cannot reliably separate the clusters.

The current guard rejects 52 frames in the second bent block and eight in its outage.
The remaining signal still fails periodicity.
Phantom removal is needed for landmark integrity. It is not, by itself, a rate fix.

## Smallest successful candidate found

Keep the six-second history, three-second warm-up, density gate, signal,
.015 amplitude floor, global .35/.7 gates, lag selection and display policy.
Change five estimator choices:

1. Use a 15-sample trend mean instead of 31 samples. It removes faster posture drift.
2. Limit residual magnitude only for global autocorrelation.
   The bound is `.75 * median(abs(detrended samples))`.
   Clip symmetrically around zero. This limits large transition residuals.
   This is median-absolute-amplitude clipping, not median absolute deviation.
3. Check the last 750 ms instead of 1,500 ms for amplitude and recent correlation.
   Integer sampling gives 22 samples, about 733 ms.
4. Pool two recent cycle offsets instead of four.
5. Set the recent-correlation floor to .25 instead of .4.

The recent test still uses the unclipped detrended waveform.
Clipping it too would change stop detection and fails early lean in the tested variant.
The shorter recent check reduces old-cycle influence after a stop or posture change.
The .25 threshold is empirical. It is not a proven universal threshold.
At 65 seconds movement is false, so a fresh accepted rate is required there.
The proposal supplies one; it does not bypass movement or extend the hold.

This is the smallest successful combination found, not proof of an absolute minimum.
Restoring any one of these five choices fails a target or control below.
The scratch array in the diff is sorted for the median and reused for clipped samples.
The original samples remain available for the amplitude and recent gates.

```diff
--- a/cpr-coach/app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt
+++ b/cpr-coach/app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt
@@ -41,8 +41,8 @@
     private val minDataMs: Long = 3_000,
     private val minCorrelation: Double = 0.35,
     private val minAmplitude: Double = 0.015,
-    private val stillWindowMs: Long = 1_500,
-    private val minRecentCorrelation: Double = 0.4,
+    private val stillWindowMs: Long = 750,
+    private val minRecentCorrelation: Double = 0.25,
 ) {
     private val times = ArrayDeque<Long>()
     private val values = ArrayDeque<Double>()
@@ -100,9 +100,9 @@
         return out
     }
 
-    /** Removes slow drift (the holder's hand, the rescuer shifting) with a one-second moving average. */
+    /** Removes slow drift (the holder's hand, the rescuer shifting) with a half-second moving average. */
     private fun detrend(x: DoubleArray): DoubleArray {
-        val half = SAMPLE_HZ / 2
+        val half = SAMPLE_HZ / 4
         val prefix = DoubleArray(x.size + 1)
         for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]
         return DoubleArray(x.size) { i ->
@@ -132,8 +132,8 @@
         var a = 0.0
         var b = 0.0
         for (i in start until x.size) a += x[i] * x[i]
-        // Bound the reference to four cycles to limit accumulated whole-sample lag error.
-        val maxOffset = minOf(start, 4 * lag)
+        // Use two nearby cycles so posture transitions do not dominate the recent check.
+        val maxOffset = minOf(start, 2 * lag)
         for (offset in lag..maxOffset step lag) {
             for (i in start until x.size) {
                 cross += x[i] * x[i - offset]
@@ -145,7 +145,15 @@
     }
 
     /** Normalised autocorrelation, indexed by lag in samples. Lags outside the search range are 0. */
-    private fun autocorrelation(x: DoubleArray): DoubleArray {
+    private fun autocorrelation(input: DoubleArray): DoubleArray {
+        // Bound large transition residuals only for global period selection.
+        // Reuse the median scratch buffer; amplitude and recent checks keep input.
+        val x = DoubleArray(input.size) { abs(input[it]) }
+        x.sort()
+        val mid = x.size / 2
+        val median = if (x.size % 2 == 1) x[mid] else (x[mid - 1] + x[mid]) / 2
+        val bound = 0.75 * median
+        for (i in x.indices) x[i] = input[i].coerceIn(-bound, bound)
         val maxLag = minOf(MAX_LAG + 1, x.size - 1)
         val r = DoubleArray(maxLag + 1)
         for (lag in (MIN_LAG - 1)..maxLag) {
```

## Before and after

Raw means a fresh estimator acceptance. Shown includes the existing display hold.
Median shown rates are per minute. Availability is not accuracy.

| Recording 15 window | Ticks | Raw before → after | Shown before → after | Held before → after | Median shown before → after |
|---|---:|---:|---:|---:|---:|
| Straight 3.5–32 | 115 | 110 → 112 | 112 → 114 | 2 → 2 | 106 → 107 |
| Straight 44–53.4 | 38 | 38 → 38 | 38 → 38 | 0 → 0 | 110 → 111 |
| Bent 38.2–43.4 | 21 | 0 → 16 | 7 → 21 | 7 → 5 | 91 → 104 |
| Bent 54–61.5 | 31 | 13 → 24 | 13 → 26 | 0 → 2 | 111 → 111 |
| Lean 65–69.5 | 19 | 0 → 18 | 0 → 19 | 0 → 1 | none → 114 |
| Outage 39.5–43.5 | 17 | 1 → 15 | 3 → 17 | 2 → 2 | 91 → 107 |
| Outage 57.5–59 | 7 | 0 → 7 | 0 → 7 | 0 → 0 | none → 110 |
| Outage 64–69.5 | 23 | 2 → 22 | 4 → 23 | 2 → 1 | 113.5 → 114 |

The first outage holds 93/min at 39.5 and 39.75 seconds.
It has fresh rates at every read from 40 through 43.5 seconds.
Those fresh estimates range from 102.95 to 113.05/min.
The second outage has seven fresh rates, ranging from 106.97 to 115.25/min.
The lean outage holds 116/min only at 65.25 seconds.
Its other 22 reads are fresh rates, ranging from 111.70 to 119.41/min.
The full second bent label still has no rate at 55, 55.25, 55.5, 55.75 and 56 seconds.
Do not describe the result as uninterrupted lock throughout every labelled segment.

## All recordings and negative controls

All rows use the same configuration. No recording-specific signal rules are used.

| Recording | Ticks | Shown before → after | Raw before → after |
|---|---:|---:|---:|
| 1 | 75 | 0 → 0 | 0 → 0 |
| 2 | 159 | 11 → 42 | 11 → 34 |
| 3 | 437 | 69 → 204 | 39 → 138 |
| 4 | 176 | 43 → 71 | 34 → 63 |
| 5 | 274 | 168 → 182 | 161 → 177 |
| 6 | 177 | 99 → 131 | 71 → 122 |
| 7 | 216 | 109 → 146 | 76 → 120 |
| 8 | 55 | 20 → 27 | 20 → 27 |
| 9 | 221 | 110 → 155 | 84 → 138 |
| 10 | 198 | 87 → 94 | 80 → 91 |
| 11 | 130 | 72 → 93 | 57 → 86 |
| 12 | 210 | 85 → 116 | 58 → 94 |
| 13 | 178 | 108 → 114 | 75 → 87 |
| 14 | 253 | 81 → 84 | 81 → 82 |
| 15 | 286 | 197 → 259 | 182 → 247 |
| Nobody | 288 | 0 → 0 | 0 → 0 |

Recordings 1–15 total 1,259 → 1,718 shown ticks and 1,029 → 1,506 fresh-rate ticks.
Held ticks fall from 230 to 212.
Recording 14's pause has 36 ticks. Baseline reports 108/min at 25 seconds.
The proposal reports zero fresh and zero held rates throughout 24.7–33.5 seconds.
Nobody has zero fresh and zero held rates across all 288 ticks.
Only eight nobody ticks get beyond warming, so this is limited negative evidence.
The recordings were used to select the candidate. They are not held-out validation.
Extra availability in unlabelled recordings is not proof that those extra rates are correct.

## Minimality checks

Cells are fresh / shown counts in the three reported outages.
Pause and nobody columns count shown ticks; zero also means zero fresh ticks.
Each restoration changes only the named choice from the proposal.

| Variant | 39.5–43.5 /17 | 57.5–59 /7 | 64–69.5 /23 | Pause /36 | Nobody /288 |
|---|---:|---:|---:|---:|---:|
| Proposal | 15 / 17 | 7 / 7 | 22 / 23 | 0 | 0 |
| Remove clipping | 11 / 13 | 7 / 7 | 22 / 23 | 0 | 0 |
| Restore 31-sample trend | 15 / 17 | 2 / 6 | 3 / 4 | 0 | 0 |
| Restore 1.5-second recent window | 13 / 15 | 7 / 7 | 23 / 23 | 4 | 0 |
| Restore four cycle offsets | 11 / 13 | 6 / 6 | 22 / 23 | 1 | 0 |
| Restore recent threshold .4 | 15 / 17 | 7 / 7 | 21 / 21 | 0 | 0 |
| Use clip multiplier 1 instead of .75 | 15 / 17 | 7 / 7 | 22 / 23 | 1 | 0 |
| Shorten proposal history to 3.5 seconds | 14 / 16 | 7 / 7 | 21 / 23 | 6 | 0 |
| Baseline with only 3.5-second history | 9 / 11 | 5 / 7 | 6 / 9 | 0 | 0 |

Shortening history alone is a partial improvement, not a solution to early lean.
In the stronger proposal, shortening history also removes useful negative evidence
against late-pause artifacts. It is not needed for the successful candidate.
Changing only the baseline trend to 15 samples covers the lean outage, but produces
two false pause rates at 24.75 and 25 seconds. A stronger global detector alone is unsafe.

## Phantom-drop sensitivity

The optional mask is an offline diagnostic, not a deployable guard.
Define U as H<.20 and F/A<.90, and B as H>=.30.
Find U↔B transitions within 120 ms, with wrist displacement>.035 and elbow displacement<.020.
Exclude contiguous U runs touching those transitions.
This uses future frames. It does not establish ground-truth wrist identity.
Masked frames update visible-person time but feed neither rate nor posture.

The mask excludes 66/1,597 frames in recording 15.
It excludes 49/158 in the second bent block and 12/32 in its short outage.
It excludes none in either straight block or the first bent block, but four in labelled lean.

| Window | Baseline raw / shown | Baseline + mask | Proposal raw / shown | Proposal + mask |
|---|---:|---:|---:|---:|
| Bent 38.2–43.4 | 0 / 7 | 0 / 7 | 16 / 21 | 16 / 21 |
| Bent 54–61.5 | 13 / 13 | 10 / 10 | 24 / 26 | 24 / 26 |
| Lean 65–69.5 | 0 / 0 | 0 / 0 | 18 / 19 | 17 / 17 |
| Outage 39.5–43.5 | 1 / 3 | 1 / 3 | 15 / 17 | 15 / 17 |
| Outage 57.5–59 | 0 / 0 | 0 / 0 | 7 / 7 | 7 / 7 |
| Outage 64–69.5 | 2 / 4 | 2 / 3 | 22 / 23 | 21 / 21 |

Both negative controls remain zero after the mask.
The full mask creates blanks at 65 and 65.25 seconds by changing recent correlation.
Restricting this sensitivity mask to the known 54–61.5-second phantom block preserves
all three proposal outage counts: 17/17, 7/7 and 23/23 shown.
That restriction is an experiment, not a recording-specific app rule.
The actual forthcoming frame-drop implementation was not supplied.
It must be replayed separately; full-mask results do not justify claiming guaranteed lock after it.

## Limits and decision

The observed problem is periodicity rejection with ample motion.
The proposed diff meets the three reported outage-display checks and both negative checks
on the current reference. It also supplies fresh rates throughout most of each outage.
It is more effective than the previous one-line history change.

There is a low-amplitude, low-rate tradeoff.
A throwaway 25 Hz, six-second sine replay checked 60, 80, 100, 110, 120, 140 and 170/min.
At amplitude .08 torso lengths, both versions accept every tested rate.
Proposal error at the final read is at most 1.13/min in that sweep.
At amplitude .03, the proposal rejects 60 and 80/min as still; the baseline accepts them.
At amplitude .04, it rejects 60/min while the baseline accepts it.
The shorter detrend attenuates slow motion more strongly even though the amplitude floor is unchanged.
This needs product evaluation before shipping. Lowering the floor is not part of this patch.

No independent compression-count labels establish real-rate accuracy.
Straight and bent labels were read from saved pictures, not human-verified compression counts.
Lean is the recorder's unconfirmed account.
Use the diff as a measured experiment, not a clinically validated rate detector.
Before deployment, check counted low-amplitude compressions, held-out stops and the exact
phantom-drop implementation. Do not hide those limits with a longer display hold.
