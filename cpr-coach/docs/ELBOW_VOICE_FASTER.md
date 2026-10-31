# Elbow voice timing

Base: `adf9409`.
Patch: `patches/elbow_voice_faster.patch`.

## Decision

Reduce elbow voice steady time from 2,000 ms to 1,000 ms.
Give elbows a separate `CoachPolicy.elbowSteadyMs` constructor parameter.
Keep shoulder voice steady time at 2,000 ms.
Keep the six-second cooldown and phrase priorities.
Do not change `trackPause` or any pace code.
Leave `PostureJudge` unchanged, including its median, entry sustain, and exit sustain.

This is the best safe tested setting, not the requested 2.5-second result on both blocks.

## Before and after

Times are seconds from recording start.
These are replay clip starts, not device playback latency.
Practise and Emergency gave the same elbow times.

| Recording and bend | Modes | Before clip | After clip | Before delay | After delay |
| --- | --- | ---: | ---: | ---: | ---: |
| 12, 18–29 s | Both | 20.75 | 19.75 | 2.75 | 1.75 |
| 15, 38.2–43.4 s | Both | 42.25 | 41.25 | 4.05 | 3.05 |
| 15, 54–61.5 s | Both | 57.75 | 56.75 | 3.75 | 2.75 |

Recording 15 still speaks `shoulders_over_hands` at 68.00 s in both modes.
Emergency still shows `posture_shoulders_over_hands` over [67.00, 70.25).
All shoulder issue timestamps match the baseline in all six recordings.

## What stopped further acceleration

A full replay with a 250 ms elbow voice delay added `elbows_straight` at 41.50 s in recording 12.
This happened in both modes, outside the allowed 18–29 s bend.
The original judge already reports bent elbows there.
The rescuer is marked moving at 41.25, 41.50, 41.75, and 42.00 s, then still at 42.25 s.
The one-second voice wait suppresses this clip.

A separate policy study fed every recorded 250 ms analyzer read back through the real `CoachPolicy`.
Its 2,000 ms control reproduced the baseline correction times.
The full 250 ms and 1,000 ms session replays agreed with the study.

| Elbow voice wait | 12 bend clip | Extra 12 clip outside bend | 15 bend clips |
| ---: | ---: | ---: | --- |
| 250 ms | 19.00 | 41.50 | 41.00, 56.00 |
| 500 ms | 19.25 | 41.75 | 41.00, 56.25 |
| 750 ms | 19.50 | 42.00 | 41.00, 56.50 |
| 800 ms | 19.75 | None | 41.25, 56.75 |
| 1,000 ms | 19.75 | None | 41.25, 56.75 |
| 2,000 ms | 20.75 | None | 42.25, 57.75 |

The 500, 750, and 800 ms rows are policy-study results, not full session replays.
Choose 1,000 ms rather than 800 ms for more margin with the same measured gain.

Recording 15 speaks `keep_going` at 35.00 s.
The required cooldown forbids any next correction before 41.00 s.
That is already 2.80 s after the first bend starts.
Its elbow voice clock also resets at 40.00 s when the PAUSED read is not moving.
It restarts at 40.25 s.
For the second bend, PAUSED reads are not moving at 55.00–55.50 s.
The elbow voice clock starts at 55.75 s.
With the safe one-second voice wait, these clocks produce 41.25 and 56.75 s.

No shorter judge sustain was run or shipped.
It cannot remove these movement gaps or the cooldown.
It also cannot safely be used to justify a voice wait that already adds an outside-bend clip.

## Straight-stretch safety

Ranges below are half-open.
Counts include every 250 ms read, not just the last read in each second.
Each row applies separately to both modes.

| Recording and checked ranges | Bent reads before → after | Elbow clips before → after |
| --- | ---: | ---: |
| 10, whole recording | 0 → 0 | 0 → 0 |
| 11, whole recording | 0 → 0 | 0 → 0 |
| 12, outside [18, 29) | 24 → 24 | 0 → 0 |
| 13, whole recording | 0 → 0 | 0 → 0 |
| 14, [3, 24) | 0 → 0 | 0 → 0 |
| 15, [3.5, 32) | 0 → 0 | 0 → 0 |
| 15, [34.5, 37.3) | 0 → 0 | 0 → 0 |
| 15, [44, 53.4) | 5 → 5 | 0 → 0 |

No new bent timestamp appeared in any checked range.
Recording 12's 24 outside-bend reads are baseline behavior, not new warnings.
Recording 15's known tail has the same five reads: 44.00, 44.25, 44.50, 44.75, and 45.00 s.
The next read at 45.25 s is no longer bent.
The tail did not grow.

## Tracking and rate check

Run both timeline scenarios for recordings 10–15 before and after the change.
Temporary diagnostic output captured every existing 250 ms poll without adding analyzer reads.
Practise captured time, tracking, rate, and posture issue from its live UI state.
Emergency captured those fields and `moving` from its analyzer read.
Compare the complete ordered time arrays and compare each field at each timestamp.
Compare bent and shoulder timestamp sets, not only totals.
Compare clip starts and Emergency shoulder word stretches from the timeline files.
The temporary diagnostics and timing-study test are not in the patch.

| Recording | Reads per mode | Tracking differences | Rate differences | Posture issue differences |
| --- | ---: | ---: | ---: | ---: |
| 10 | 199 | 0 | 0 | 0 |
| 11 | 131 | 0 | 0 | 0 |
| 12 | 211 | 0 | 0 | 0 |
| 13 | 179 | 0 | 0 | 0 |
| 14 | 254 | 0 | 0 | 0 |
| 15 | 287 | 0 | 0 | 0 |

Total: 1,261 reads per mode, 2,522 matched reads across both modes.
Emergency `moving` also matched at all 1,261 reads.
No edit was made to tracking, movement, rate estimation, push detection, or landmark guarding.

## Tests and patch check

The final `testDebugUnitTest` run passed: 327 passed, 5 skipped, 0 failed.
Two skipped tests are the opt-in timelines with `CPR_TRACE` unset.
Three are existing `RateRobustnessTest` skips.
The separate baseline and final timeline runs passed for all six recordings in both modes.

The patch tests cover the one-second elbow boundary, the unchanged two-second shoulder boundary, interrupted warnings, short moving warnings, and the six-second cooldown.
An old repeat test no longer pins the obsolete two-second elbow time.
The suite exposed an invalid fuzz assertion at seed 55: it forbade an elbow correction from ever repeating, even after the posture had cleared.
Replace that assertion in the policy fuzz checks with the actual six-second cooldown invariant.
Existing fixed-then-bent tests still require the correction to repeat.
No production repeat logic changed.

Generate the patch with `git diff --no-index` against the unmodified archive.
No commit is needed or created.
`git apply --check --whitespace=error-all` passed in an isolated repository extracted from `adf9409`.
Applying the patch succeeded.
All three applied source files matched the tested files after line-ending normalization.

From the reference Android folder, in PowerShell:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
foreach ($n in 10..15) {
    $env:CPR_TRACE = "tools/testdata/real-cph2467-$n.csv"
    ./gradlew.bat testDebugUnitTest --tests '*TraceTimelineTest*' --tests '*EmergencyTimelineTest*' --rerun-tasks
    # Read and preserve both timeline files before the next recording overwrites them.
}
Remove-Item Env:CPR_TRACE
./gradlew.bat testDebugUnitTest
```

Timeline files: `app/build/trace-timeline.txt` and `app/build/emergency-timeline.txt`.
Local measurement files remain under `../ref-t261031-0524-b617/evidence/`.
No adb, phone, live listening, or on-device playback check was run.
