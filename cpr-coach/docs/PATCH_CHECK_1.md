# Patch check 1

Checked `rate_golden_test.patch` and `emergency_word_clear.patch` from `origin/main` (`20e19452`) together on an `adf9409` extract of `cpr-coach`. `CPR_WRITE_GOLDEN` was unset. No app code was edited by hand.

## Apply

Both patches were applied with `git apply` from the extract parent.

| Patch | Result |
| --- | --- |
| `cpr-coach/docs/patches/rate_golden_test.patch` | Applied cleanly |
| `cpr-coach/docs/patches/emergency_word_clear.patch` | Applied cleanly |

## testDebugUnitTest

One run of `testDebugUnitTest` in the patched extract, `ANDROID_HOME` set to `%LOCALAPPDATA%/Android/Sdk`, `CPR_WRITE_GOLDEN` unset. Gradle exit 0. `BUILD SUCCESSFUL`.

JUnit XML under `app/build/test-results/testDebugUnitTest`: **346 tests, 0 failures, 0 errors, 5 skipped**.

No failure messages. Nothing failed, so nothing was fixed.

The 5 skips are not failures:

- `EmergencyTimelineTest` `print what Emergency shows and says for a trace` — `Set CPR_TRACE to a session CSV to run the Emergency timeline`
- `TraceTimelineTest` `print what the session shows and says for a trace` — `Set CPR_TRACE to a session CSV to run the timeline`
- `RateRobustnessTest` `shallow alternate strokes still count as compressions` — `@Ignore`
- `RateRobustnessTest` `continuous high cadence never becomes a pause at twelve fps` — `@Ignore`
- `RateRobustnessTest` `rhythmic holder motion alone is not a compression beat` — `@Ignore`

## RateGoldenTest

Passed for all 16 recordings (`tests="16" skipped="0" failures="0" errors="0"`):

- `negative-cph2467-nobody.csv`
- `real-cph2467-1.csv`
- `real-cph2467-2.csv`
- `real-cph2467-3.csv`
- `real-cph2467-4.csv`
- `real-cph2467-5.csv`
- `real-cph2467-6.csv`
- `real-cph2467-7.csv`
- `real-cph2467-8.csv`
- `real-cph2467-9.csv`
- `real-cph2467-10.csv`
- `real-cph2467-11.csv`
- `real-cph2467-12.csv`
- `real-cph2467-13.csv`
- `real-cph2467-14.csv`
- `real-cph2467-15.csv`
