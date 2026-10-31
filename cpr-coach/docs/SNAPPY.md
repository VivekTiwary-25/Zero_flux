# Snappier feel

Phone: CPH2467 (557d9cdd), build ee682a1, measured 2026-10-31 over adb. Nothing was installed or cleared.

## Before (real numbers)

| Step | Runs (s) | Typical | How measured |
|---|---|---|---|
| 1. Cold open to Home | 1.29, 1.06, 1.08, 1.01 | about 1.1 | `am start -S -W`, TotalTime (force-stopped first) |
| 2. Tap Practise to camera showing, first Practise after launch | 2.6, 1.84, 1.84 | about 1.8 | tap end (device clock) to logcat `Preview stream state to STREAMING` |
| 2b. Same, app already warm | 1.34, 1.33 | about 1.3 | same |
| 3. Tap Emergency to audio output started | 0.28, 0.26, 0.34 | about 0.3 | tap end to `MetronomeService: started` and `AudioTrack: start` |
| 4. Stop to scorecard | not timeable | see below | |

Notes
- Step 3: there is no log line for the first spoken clip. The voice and the beat share one AudioTrack, so I timed the track start. First sound is that plus about 0.19 s output latency plus one clip file read, so roughly 0.5 to 0.7 s. This is an estimate.
- Step 4: Emergency needs a 1.0 s hold (`HOLD_TO_STOP_MILLIS`). Measured with a 1.5 s long press: audio stop and camera teardown logged at 1.00 s after press start, i.e. no extra delay after the hold completes, and no dropped-frame warning. The scorecard frame itself writes no log line, so its exact appearance time cannot be read from logs. Film it to time it.

## Two slowest

1. Practise (1.3 to 2.6 s). Right after the tap the main thread is blocked for about 0.85 s (logcat `Skipped 68-83 frames`, `Davey 829-999 ms`). The cause is `PoseEngine` constructor creating the MediaPipe landmarker and GPU delegate on the main thread, from `PractiseHost`. That is model load inside protected code (PoseEngine), so it is left alone. Camera open adds about 0.5 s after that, which is the CameraX/driver path.
2. Cold open (about 1.1 s). Process start to first frame, with a 39-frame main-thread stall during the first composition (AppRoot builds VoiceRouter, loads two string files and the command table). I could not attribute it further without a profiler, and the code is in AppRoot, which other branches are editing. Left alone.

## What changed

`MainActivity.kt` only: after Home is drawn, a low-priority daemon thread calls `System.loadLibrary("mediapipe_tasks_jni")`. That 15 MB library is otherwise loaded on the main thread during the Practise tap stall. Loading twice is a no-op, errors are ignored, no screen, behaviour or protected file is touched.

## After

Not yet measured on the phone; due after install.

Expected, not measured: Practise saves the library load part of the 0.85 s stall, likely 0.05 to 0.2 s. This is a small gain. The bulk (GPU delegate and model creation) stays. A bigger gain needs building the landmarker off the main thread or ahead of the tap, which means editing PoseEngine or PractiseHost and needs a decision. Cold open is unchanged.
