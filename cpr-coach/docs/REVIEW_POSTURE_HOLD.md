# Review: posture issue held through side-on flicker

Reviewed commit `f0d6188921cd56cdf4fa8bba20c1bbdd4591edbc` and its `PostureJudge.kt` diff. The hold is bounded, but there is a real false-correction window when a rescuer fixes posture or swaps while usable arm frames continue and side-on confidence remains ambiguous.

## Finding: an old correction can remain eligible for up to three seconds

`PostureJudge.held()` reports the previously active elbow or lean issue until three seconds after `lastJudgedMs`. Frames with valid landmarks but an ambiguous side window return this held issue. They do not refresh `lastJudgedMs`, but `LiveAnalyzer.addFrame()` stores each returned reading and refreshes `postureAtMs`. Therefore `POSTURE_STALE_MS=500` does not cap the hold while those frames keep arriving. The three-second judge deadline does cap it.

Concrete sequence: establish a lean issue using clearly side-on frames with offset `0.70`; at time T, the judge reports `SHOULDERS_NOT_OVER_HANDS`. Starting at T+33 ms, send visible valid frames every 33 ms with corrected offset `0.05` and `sideOn` alternating true/false. Once the old frames roll out, the one-second side window has about a 50% non-side-on share, so it is neither clearly on (at most 20%) nor clearly not-on (at least 80%). `held()` keeps returning the old lean issue until 3000 ms after the last judged frame; during the initial transition, clear-side frames can advance `lastJudgedMs`, so the deadline may be slightly later than T+3000. The same path applies to a confirmed bent-elbow issue. This is a false amber correction for someone whose current posture is no longer the old issue. `CoachPolicy` accepts the posture issue during BEATING or moving PAUSED readings and waits two seconds before its posture phrase, so a spoken correction can also occur before the hold expires if its cooldown and repeat rules permit.

This sequence requires continuing visible, valid frames and unresolved side confidence; it is not evidence that an ordinary side-on correction remains falsely flagged for three seconds. Once clearly side-on judging resumes, the rolling median and one-second clear sustain can add delay while old samples leave the median. Personal baselines are not relearned or changed by the hold; they remain session-fixed and are used by subsequent judged frames.

## Turn-away and leaving controls

A clearly-not-side-on window clears the old medians and returns unknown immediately, then reports `NOT_SIDE_ON` after `NOT_SIDE_ON_MS`; `CoachPolicy` does not speak that issue. Thus the hold does not keep the correction visible through a sustained detected turn-away. If the person leaves and no valid posture frames arrive, `currentPosture()` expires the last reading after 500 ms; analyzer tracking also becomes LOST after its 500 ms accepted-frame timeout, which silences posture coaching. A continuous visible swap with ambiguous side confidence is the remaining exposed case.

## Verification limits

The referenced `PostureJudgeTest.leanSurvivesAShortSideOnFlicker` provides a JUnit scenario where a confirmed lean remains reported through a one-second side-on flag flicker, then a long turn-away clears it. The test could not be executed here: `./gradlew` is not executable in the archive, and `bash gradlew ...` timed out downloading Gradle 9.6.0. The conclusion above is based on the reviewed commit diff and source control flow, not a new executed swap/fix JUnit case. No app code was changed.