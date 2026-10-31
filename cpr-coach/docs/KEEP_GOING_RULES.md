# Keep going rules

Base: `adf9409`. Patch: `patches/keep_going_rules.patch`.
The patch changes only `CoachPolicy.kt` and two test files.
It leaves `requiredMs`, tracking, movement detection, rate calculation, and scorecard code unchanged.

## Rule changes

- Confirm a mostly-still pause after 2.0 s. Do not speak before 2.5 s of observed stop time.
- Count a still BEATING tail only if it lasted at least 1.0 s before PAUSED. This keeps a plain stop prompt near 3 s.
- Four consecutive moving reads at 250 ms intervals end the old pause. They span 0.75 s. One moving read remains tolerable noise.
- A locked beat bridges movement-detector dropouts when re-arming the next pause cue. Moving hands must be observed at both ends of the 3 s interval.
- A confirmed pause can finish during still WARMING for at most 1.0 s after the last PAUSED read. New movement or a longer gap cancels it. LOST stays silent.
- Rate speech waits through the first 1.0 s after a PAUSED read. Rate calculation does not change. Praise still needs an in-range rate.

The relock guard matters on recording 15. Without it, removing the false cue lets `push_faster` play at 39.50 s. Its cooldown then loses the first elbow correction. The guard preserves `elbows_straight` at 42.25 s. Rate still outranks posture once it is steady.

## Before and after

Times are recording-relative seconds. P means Practise. E means Emergency clip starts, not its screen word. None means no `keep_going` clip before the recording ends.

| Check | Before on adf9409 | After |
|---|---|---|
| 13, real stop near 36 s | P 41.75; E none | P 42.00; E 42.00 |
| 14, rest 24.7–33.5 s | P 27.50; E 27.50 | P 28.00; E 28.00 |
| 15, 1.7 s reposition, pushing again from 34.5 s | P 35.00; E 35.00, both wrong | No cue in either mode |
| Plain synthetic stops at 12.00 and 28.00 s | Cues 15.50 and 31.50; delay 3.50 s each | Cues 15.00 and 31.00; delay 3.00 s each |
| Review edge 1, locked beat with brief movement dropouts | Exact review test fails. The second cue is missing. | Regression passes. The second stop gets its cue. |
| Review edge 2, movement inside a confirmed pause | Exact review test fails. An early cue appears in the prohibited window. | Same early-window check passes. A later fresh stop still gets its cue. |
| 15, posture speech | Elbows 42.25 and 57.75; shoulders 68.00, both modes | Same times, both modes |

The measured Emergency baseline for 13 is silent after `good` at 36.00 s. It differs from the older `REPLAY_13_15.md` result, which used another commit. This report uses the requested base, not the old number.

Both review snippets ran against the unchanged policy before any policy edit. Both failed assertions. Edge 1's permanent fixture extends its initial stop by 0.50 s and shifts later reads by 0.50 s to allow the new cue delay. Its six-second locked-beat sequence and dropout spacing stay the same. Edge 2 keeps the original early-window sequence and also checks eventual recovery. Neither fix was left out.

The two older repeat-cue fixtures now allow the initial stop to finish before resuming. They still require the next cue. Their incidental exact initial clip times were removed. `PauseVoiceReplayTest` was not weakened or edited.

## All six replays

These are every `keep_going` clip start. Scripted Emergency clips are not included.

| Recording | Before P | After P | Before E | After E |
|---|---|---|---|---|
| 10 | 5.25, 29.25 | 5.75, 28.75 | 29.25 | 28.75 |
| 11 | 31.50 | None | None | None |
| 12 | 32.75, 44.25 | 33.25, 44.75 | 44.25 | 44.75 |
| 13 | 41.75 | 42.00 | None | 42.00 |
| 14 | 27.50 | 28.00 | 27.50 | 28.00 |
| 15 | 35.00 | None | 35.00 | None |

No cue coincides with a moving policy read in 10–15. No cue falls in the documented pushing blocks of 12–15, including 15's resumed straight block. The available labels do not independently mark every physical push or stop in 10–12. The remaining cues in 10 and 12 are listed above; detector stillness alone is not human ground truth.

The real-stop delays are about 6.0 s on 13 and 3.3 s on 14. Recording 13 still has late tracker recognition. The plain synthetic stop delay is 3.0 s, so the 55–70 s phone-sheet expectation stays intact in that scenario. A recent spoken phrase can still delay speech through the unchanged 6 s cooldown.

## Scorecard effect

`SessionMetrics` retains the rule described in `NIGHT_DECISIONS.md` for `fcb4cc6`.
A temporary replay probe measured these counts before and after. It was removed after use.

| Recording | Scorecard pauses, before → after | Voice cues after, P / E | Agreement after |
|---|---|---|---|
| 13 | 1 → 1 | 1 / 1 | Yes, one real stop |
| 14 | 1 → 1 | 1 / 1 | Yes, one real rest |
| 15 | 1 → 1 | 0 / 0 | No. Scorecard still counts the reposition; voice rejects it. |

All six probe scorecards were identical before and after. Tracking and rate fields in every emitted timeline sample matched the baseline in both modes for recordings 10–15.

## Verification

On Windows, set `ANDROID_HOME` to the installed SDK. From the extracted Android project:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA/Android/Sdk"
./gradlew.bat testDebugUnitTest
# Repeat for NN = 10, 11, 12, 13, 14, 15, before and after the patch.
$env:CPR_TRACE = 'tools/testdata/real-cph2467-NN.csv'
./gradlew.bat testDebugUnitTest --tests '*TraceTimelineTest*' --tests '*EmergencyTimelineTest*' --rerun-tasks
```

Read `app/build/trace-timeline.txt` and `app/build/emergency-timeline.txt` after each run, before overwrite. Both diagnostic tests ran successfully for each recording, before and after.

The final clean suite reports 334 tests: 329 passed, 5 skipped, 0 failures, 0 errors. `PauseVoiceReplayTest` passed both tests. `EngineFuzzTest` passed all 9 tests. The skips are the two opt-in timeline tests without `CPR_TRACE` and three existing disabled `RateRobustnessTest` cases. The opt-in tests were exercised separately above.

`git apply --cached --check` succeeded against the pristine reference index from `adf9409`. No commit was made. No adb command, phone interaction, APK install, or listening check was run.
