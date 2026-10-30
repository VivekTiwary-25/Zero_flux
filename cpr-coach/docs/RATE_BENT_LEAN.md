# Rate loss with bent arms and leaning

## Finding

This is mainly a periodicity failure, not weak wrist motion.
Bent and lean verdicts do not veto rate estimation.
`moving=true` only means that the wrist moved around a fitted line over 500 ms.
It does not mean that the movement repeated at a stable compression period.
`LiveAnalyzer` maps `RateEstimator.NoBeat` to `PAUSED` after its two-second moving-only rate hold expires.

Reference: `cpr-coach` revision `e50a11a985738d4c6aa90849b13b6b0f9dac63a2`.
No app code was changed.

## Replay method

Run the supplied NumPy tool against the extracted reference data:

```sh
python3 cpr-coach/tools/rate_bent_lean.py \
  --data-dir ../ref-t261031-0410-6138/cpr-coach/tools/testdata \
  --window-ms 3500 --diagnostic-all-gates
```

The replay feeds every original frame before each 250 ms read.
Ticks start at the first CSV timestamp and end at the last available tick.
All times below are seconds from the first row.
Window endpoints are inclusive.
Thus 57.5–59.0 contains seven read ticks, not seven compressions.

The port includes the current `LandmarkGuard`, visibility and finite-value checks, accepted-frame gap resets, interpolation, detrending, lag selection, recent correlation, movement hysteresis, five-value rate median, and display hold.
Coordinates, aspect and visibility are rounded to Kotlin Float first.
The aspect is 4:3, as in the existing coordinate replay tests.
The CSV does not store aspect, so this cannot verify the live camera aspect.
The stored double `wrist_signal` is retained.
The stored elbow angle is only used for the finite-value gate.
Posture verdicts are not replayed because they cannot change rate or movement.
Recorded side changes reset the guard reference.

An independently written scalar estimator agreed with the vector replay on all 2,802 tracked baseline reads across recordings 1–15 and nobody.
There were zero first-rejection mismatches.
The largest candidate-rate difference was 1.14e-13/min.
This checks estimator numerics, not independent correctness of every guard decision.
An independent source review found no baseline guard or analyzer state-machine mismatch.
The actual CLI was also run across all 16 CSVs.
Kotlin, Gradle, Android and the phone UI were not run.

## Current gates

`PoseMath.wristSignal` stores `(hipY - wristY) / torsoLength`.
Torso length uses aspect-scaled X and image Y.
The estimator retains six seconds of accepted frames.
It needs at least three seconds and integer density of at least six frames/s.
It interpolates at 30 Hz and subtracts a centered 31-sample mean.
Its last-1.5-second detrended RMS must be at least 0.015 torso lengths.

The lag search is 10–30 samples.
It picks the shortest local peak reaching 60% of the strongest positive peak.
The chosen peak needs global correlation 0.35, or 0.7 near a search edge.
Recent correlation must reach 0.4.
That test pools matches at one, two, three and four periods when history permits.
Older mismatched cycles can therefore reject a currently moving wrist.

The historical `RATE_*.md` files do not all describe this revision.
Some quote eight frames/s or recent correlation 0.5.
`compare_signals.py` is a signal diagnostic, not this app replay.
The earlier four-second-window and one-cycle-recent proposals are rechecked below.

## Measured windows

These are baseline medians over read ticks after the existing guard.
RMS is recent detrended RMS in torso lengths.
ACF is the selected global peak.
Recent is the pooled recent-cycle correlation.
C and R count first rejections at the global and recent correlation gates.
No listed window has an amplitude or density first rejection.
Shown includes held values; raw means an estimator-accepted rate.

| Recording 15 window | Ticks | RMS | ACF | Recent | C / R | Raw / shown / held |
|---|---:|---:|---:|---:|---:|---:|
| Straight 3.5–32 | 115 | .0646 | .9058 | .8842 | 1 / 4 | 110 / 112 / 2 |
| Straight 44–53.4 | 38 | .0640 | .8470 | .8246 | 0 / 0 | 38 / 38 / 0 |
| Bent 38.2–43.4 | 21 | .0632 | .2564 | .0796 | 19 / 2 | 0 / 7 / 7 |
| Bent 54–61.5 | 31 | .0723 | .4478 | .2609 | 7 / 11 | 13 / 13 / 0 |
| Lean 65–69.5 | 19 | .0801 | .1827 | .1246 | 18 / 1 | 0 / 0 / 0 |
| Reported outage 39.5–43.5 | 17 | .0627 | .2541 | .0796 | 16 / 0 | 1 / 3 / 2 |
| Reported outage 57.5–59 | 7 | .0806 | .2709 | .1846 | 5 / 2 | 0 / 0 / 0 |
| Reported outage 64–69.5 | 23 | .0806 | .1917 | .1462 | 18 / 3 | 2 / 4 / 2 |

The replay need not reproduce the exact original display boundaries.
The current guard, 250 ms polling and existing holds explain the few rated boundary ticks inside the reported outages.
They do not invalidate the reported missing-rate problem.

### First bent block

Raw `wrist_signal` SD is .06377, versus .06368 and .06822 in the straight blocks.
Its raw range is .25322, versus .25620 and .33940.
Recent RMS during the outage never drops below .05236, well above .015.
No frames are guard-rejected inside 38.2–43.4.

The transition at 37–38 s is different.
Raw signal spans -1.21546 to +.47982 there.
Some disturbed samples survive the current guard and remain in the six-second window.
They distort detrending and correlation long after the wrist settles.
At 41 s the guarded RMS is .05236 but global ACF is only .222.
This is not a reason to lower the amplitude floor.

### Second bent block and phantom wrists

Raw signal SD rises to .08811.
Consecutive-sample difference SD rises to .07796, versus .03503 and .03776 in the straight blocks.
There are 57 consecutive wrist relocations exceeding .035 image-height units, versus 2 and 6 in the straight blocks.
These counts use unscaled image-plane Euclidean distance.

The phantom is mainly a horizontal relocation under the elbow.
It is not an elbow/wrist vertical inversion.
Throughout 54–61.5, wrist Y remains below elbow Y by .0710–.1589 image heights.
At 57.956–58.017, the wrist moves .0564 while the elbow moves only .0090 in 61 ms.
Wrist visibility remains high in both clusters, with medians about .985.
Visibility cannot identify the correct wrist.

Let A be image-plane shoulder-to-elbow length, F elbow-to-wrist length, and H horizontal wrist-to-elbow distance divided by A.
The under-elbow cluster H<.20 has 61 frames and median F/A=.704.
The separated cluster H>=.30 has 80 frames and median F/A=.671.
A short-forearm threshold alone would not isolate the phantom.
A near-straight elbow angle alone would also remove real straight-arm frames.

The existing guard rejects 52 frames during 54–61.5.
It rejects eight in 57.5–59.
Rate rejection still occurs on the remaining signal.
Dropping bad frames is necessary for landmark integrity, but is not by itself evidence that rate will lock.

### Lean block

Raw signal SD is .10496 and range is .46861.
This exceeds both straight blocks.
The existing guard rejects 12 frames in 65–69.5.
The remaining recent RMS stays at least .07009.
At 66 s global ACF is .150 and recent correlation is -.064 despite RMS .084.
At 68 s recent correlation has recovered to .442, but global ACF is still only .183.
Both the transition history and local waveform irregularity matter.

## Smallest conservative candidate

Shorten only the retained history to 3.5 seconds.
Keep the wrist signal, amplitude floor, minimum history, density, all correlation gates and display policy unchanged.
This is a one-line candidate, not a claim of uninterrupted rate throughout every target block.
It recovers real estimator rates in all three blocks, but it does not solve the early lean dropout.

```diff
diff --git a/cpr-coach/app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt b/cpr-coach/app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt
--- a/cpr-coach/app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt
+++ b/cpr-coach/app/src/main/java/com/zeroflux/cprcoach/engine/RateEstimator.kt
@@ -39,3 +39,3 @@
 class RateEstimator(
-    private val windowMs: Long = 6_000,
+    private val windowMs: Long = 3_500,
     private val minDataMs: Long = 3_000,
```

The diff is a proposal only.
It was not applied to app code.

| Recording 15 window | Raw before → after | Shown before → after | Held before → after | Median shown/min before → after |
|---|---:|---:|---:|---:|
| Straight 3.5–32 | 110 → 113 | 112 → 113 | 2 → 0 | 106 → 106 |
| Straight 44–53.4 | 38 → 38 | 38 → 38 | 0 → 0 | 110 → 107.5 |
| Bent 38.2–43.4 | 0 → 10 | 7 → 15 | 7 → 5 | 91 → 107 |
| Bent 54–61.5 | 13 → 17 | 13 → 23 | 0 → 6 | 111 → 113 |
| Lean 65–69.5 | 0 → 4 | 0 → 5 | 0 → 1 | none → 117 |
| Outage 39.5–43.5 | 1 → 9 | 3 → 11 | 2 → 2 | 91 → 108 |
| Outage 57.5–59 | 0 → 5 | 0 → 7 | 0 → 2 | none → 112 |
| Outage 64–69.5 | 2 → 6 | 4 → 9 | 2 → 3 | 113.5 → 115 |

The first outage gets fresh rates from 41.5 through 43.5, instead of only at 43.5.
The second gets fresh rates from 58.0 through 59.0.
Its 57.5 and 57.75 values are held at 82/min, not fresh measurements.
The lean block gets fresh rates at 68.5, 68.75, 69.0 and 69.5.
It is still `PAUSED` from 65.0 through 68.25.
A moving-only hold extension would hide that failure, not measure a rate.

## All recordings and controls

Counts are rate-display ticks and fresh estimator-rate ticks.
They are availability, not compression counts or accuracy.
No independent compression-count labels exist.

| Recording | All ticks | Shown 6 s → 3.5 s | Raw 6 s → 3.5 s |
|---|---:|---:|---:|
| 1 | 75 | 0 → 0 | 0 → 0 |
| 2 | 159 | 11 → 15 | 11 → 13 |
| 3 | 437 | 72 → 156 | 39 → 72 |
| 4 | 176 | 43 → 52 | 34 → 46 |
| 5 | 274 | 168 → 172 | 161 → 164 |
| 6 | 177 | 100 → 125 | 71 → 105 |
| 7 | 216 | 113 → 148 | 76 → 105 |
| 8 | 55 | 20 → 24 | 20 → 22 |
| 9 | 221 | 111 → 133 | 84 → 117 |
| 10 | 198 | 87 → 99 | 80 → 90 |
| 11 | 130 | 73 → 83 | 57 → 63 |
| 12 | 210 | 87 → 99 | 58 → 67 |
| 13 | 178 | 110 → 120 | 75 → 95 |
| 14 | 253 | 81 → 80 | 81 → 80 |
| 15 | 286 | 197 → 225 | 182 → 210 |
| Nobody | 288 | 0 → 0 | 0 → 0 |

Recordings 1–15 total 1,273 → 1,531 displayed ticks and 1,029 → 1,249 fresh-rate ticks.
Held ticks total 244 → 282.
Recording 14's pause at 24.7–33.5 has 36 ticks.
The baseline already reports one fresh rate, 108/min at 25.0 s.
The 3.5-second candidate reports none, including no held rate.
Its maximum selected recent correlation in that pause is .38154, below .4.
Nobody remains zero across all 288 ticks.
Only eight nobody ticks reach past warming, so this is limited negative evidence.

The candidate was selected on these same recordings.
The controls are acceptance checks, not held-out specificity validation.

## Phantom-drop sensitivity

Run the optional counterfactual separately:

```sh
python3 cpr-coach/tools/rate_bent_lean.py \
  --data-dir ../ref-t261031-0410-6138/cpr-coach/tools/testdata \
  --window-ms 3500 --diagnostic-all-gates --phantom-sensitivity
```

This applies a diagnostic mask to the alternate only.
Define U as H<.20 and F/A<.90, and B as H>=.30.
Find U↔B transitions within 120 ms with wrist displacement>.035 and elbow displacement<.020.
Exclude each contiguous U run touching one of those transitions.
The mask uses unscaled image geometry and future frames.
It is not a causal guard proposal or ground-truth wrist annotation.
Masked frames update visible-person time but feed neither rate nor posture.
The existing guard still processes the remaining frames.

The mask excludes 66/1,597 frames in recording 15.
It excludes 49/158 in the second bent block and 12/32 in its short outage.
It excludes none in the first bent block or either straight block.
It excludes 4/100 in the labelled lean block.

| Window | Shown 6 s | Shown 6 s + mask | Shown 3.5 s | Shown 3.5 s + mask |
|---|---:|---:|---:|---:|
| Bent 38.2–43.4 | 7 | 7 | 15 | 15 |
| Bent 54–61.5 | 13 | 10 | 23 | 23 |
| Lean 65–69.5 | 0 | 0 | 5 | 5 |
| Outage 39.5–43.5 | 3 | 3 | 11 | 11 |
| Outage 57.5–59 | 0 | 0 | 7 | 7 |
| Outage 64–69.5 | 4 | 3 | 9 | 8 |

Dropping suspect wrists alone does not restore lock.
It can reduce availability because interpolation and history change.
The 3.5-second candidate still has zero pause and nobody rate ticks with this mask.
The exact forthcoming landmark-drop change was not supplied, so this mask cannot validate that implementation.

## Rejected simpler alternatives

These use the same full replay, without the diagnostic mask.
Cells count shown ticks in the three reported outages, then the pause and nobody.

| Change | 39.5–43.5 /17 | 57.5–59 /7 | 64–69.5 /23 | Pause /36 | Nobody /288 |
|---|---:|---:|---:|---:|---:|
| Baseline 6 s | 3 | 0 | 4 | 1 | 0 |
| History 5 s | 7 | 5 | 4 | 1 | 0 |
| History 4 s | 9 | 7 | 8 | 1 | 0 |
| History 3.5 s | 11 | 7 | 9 | 0 | 0 |
| History 3.25 s | 12 | 6 | 10 | 1 | 0 |
| History 3.1 s | 12 | 5 | 10 | 4 | 0 |
| Recent matches only adjacent cycle | 3 | 7 | 4 | 0 | 0 |
| History 4 s + adjacent cycle | 9 | 7 | 8 | 5 | 0 |
| Global correlation floor .20 | 6 | 0 | 4 | 1 | 0 |
| History 4 s + global floor .20 | 11 | 7 | 9 | 1 | 0 |

The result is not monotonic in window length.
Do not present four seconds, a lower amplitude threshold, or adjacent-cycle matching as a complete fix.
Do not extend the display hold to claim successful rate measurement.

## Stronger filtering is not a complete fix either

Two compact DSP alternatives were also replayed.
They are not part of the proposed one-line diff.
The tool exposes them for reproduction.

Use `--window-ms 3500 --detrend-half 11 --signed-sqrt` for a 23-sample trend mean and signed-square-root residuals before correlation.
The RMS gate still runs before the transform, in the original units.
This reduces the correlation influence of large residuals.
It gives raw/shown counts of 12/14, 7/7 and 13/13 in the three reported outages.
Both named controls remain zero.
Across all 15 recordings, shown/raw/held totals become 1,694/1,472/222.
The labelled lean block improves to nine fresh-rate ticks out of 19.
Fresh lean rates start at 67.5 instead of 68.5.
Every tick from 65.0 through 67.25 still has no rate.
Three changes are not justified as a complete solution to that remaining failure.

Use `--detrend-half 7` with the default six-second history for a 15-sample trend mean.
That covers all 23 reported lean-outage ticks, but reports fresh pause rates at 24.75 and 25.0.
Those rates are about 110/min.
Raising its recent threshold to .6 removes both false pause rates.
It also reduces lean coverage to 14/23 and second-bent coverage to 5/7.
Nobody remains zero in these runs.
This is a real sensitivity/specificity conflict, not an amplitude shortage.

## Decision and limits

The one-line history reduction is the smallest measured conservative improvement here.
It produces fresh rates inside each target block and passes both named negative checks.
It is not a demonstrated solution for continuous lock throughout the bent and lean blocks.
In particular, early leaning remains unresolved.
The strict requirement of a rate throughout all three outages is not met by this candidate.
Do not ship it under that claim.

Labels for recording 15 straight and bent blocks came from saved pictures, not a verified compression count.
The lean label is the recorder's unconfirmed account.
Fresh and held numbers are not proof of correct rate.
The remaining decision needs correctly tracked wrists and synchronized counted compressions through the transitions.
