# Pose speed: audit, debug switches, what is and is not measured

## Measured (earlier, on phones)
- CPH2467: 22-24 pose results a second, 40 ms latency, full model, GPU delegate, nobody in frame.
- RMX3392: 22 results a second at 72 ms.
- Nothing here has been measured with a person in frame after these changes. No phone was available for this work.

## Audit of the frame path (read from code, not profiled)
- Thread: `PoseCameraView` gives ImageAnalysis its own single-thread executor, so `PoseEngine.submit` runs off the main thread. Confirmed in code. `detectAsync` returns at once; MediaPipe runs inference on its own thread and drops frames when busy.
- Backpressure: `STRATEGY_KEEP_ONLY_LATEST`, output RGBA_8888. Correct for a live coach; no change.
- Resolution: analysis asks for 640x480 (model input is 256 px), preview 1280x960. Fine; going lower saves little because MediaPipe resizes on the GPU.
- `proxy.toBitmap()` per frame: with RGBA output this is one buffer copy of about 1.2 MB, plus a fresh Bitmap each time (GC churn). Probably 1-2 ms, small next to 40 ms. Not changed: reusing the Bitmap is unsafe unless MediaPipe is proven to have copied it before `detectAsync` returns. If profiling ever shows GC pauses, this is the first thing to try.
- LIVE_STREAM: right mode. With a person tracked, the pose detector is skipped and only the landmark model runs, so the person-in-frame cost is expected to differ from the empty-frame numbers above. This is the main unmeasured thing.
- Governor (`LoadGovernor`): gaps are 30/60/90 ms (about 30/15/10 per second). Median latency above 150 ms for 4 s steps down to 60 ms, which caps output at 15 per second, under the 20 target. It steps back up only after 30 s under 90 ms. So a phone that spikes above 150 ms early (model warm-up, first frames) can sit at 15 per second for 30 s or more. Neither target phone is near 150 ms at 40 and 72 ms, so this should not fire; it is the thing to look at in the stats line (`frame_gap_ms`) if a run comes in at 15. Not changed: it is a thermal safety.
- Fixed: `PoseFrame.aspect` is now the real rotated-image aspect on every frame with landmarks (the model and result path already carried it; the frame just dropped it).

## Debug-only switches (no effect in release builds)
- `adb shell am start -n com.zeroflux.cprcoach/.MainActivity --es pose_model lite|full|heavy`
  Lite and heavy are read from `getExternalFilesDir(null)/models/pose_landmarker_<name>.task`; if missing or unreadable the app logs a line and uses full. Only full ships in assets. The model is chosen when `PoseEngine` is created, from the launching Activity's intent, so it applies to Practise (which passes an Activity context). Emergency builds its engine from the application context and reads the options last seen by `PoseCameraView`, so treat model switching there as best-effort.
- `--ez capture_frames true`: while a trace session is running, saves a JPEG about every 500 ms (width 480, rotated upright) to `traces/frames-<session id>/<t_ms>.jpg`, `t_ms` matching trace rows. Written on a separate low-priority thread; a frame is dropped if the previous one is still being written. Cap 1200 per session. Every throw is caught and logged by exception class only. These are private images: paths are never logged.
- The `CPR_POSE` stats line now carries `model=lite|full|heavy`.
- Trace rows are written in Practise and Emergency as before (debug builds).

## Trace columns added
After `side_on`: `world_elbow_deg` (3D angle at the elbow from MediaPipe world landmarks, chosen arm) and `world_shoulder_over_wrist` (horizontal x/z distance in metres; world y is vertical). Empty when no person. `PoseSample.worldElbowDegrees: Double? = null`. Earlier columns are unchanged; `PractiseReplay` reads only the first five cells and the Python tools use `csv.DictReader`, so longer rows are fine.

## Benchmark
`python tools/bench_pose.py --serial <adb serial> --tap <x>,<y>` downloads lite and heavy into `tools/pose_models/`, pushes them, runs each model for 40 s on the framing screen, and prints results per second, latency median and p90, thermal status. Not run yet. The app has no deep link to the framing screen, hence `--tap` (or the script waits for you to open it). Keep the same person and lighting for each model.

## Not measured
Everything above under "Audit" is from reading code. Person-in-frame rate on both phones, lite and heavy speed, the cost of frame capture on the pose path, and thermal behaviour over several minutes are all unknown until the script is run.
