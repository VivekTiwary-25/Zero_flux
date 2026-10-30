# Rate accuracy test

## People and setup

Two students run each test: one rescuer and one holder. A third person counts compressions by hand and runs a stopwatch; if no third person is available, record video that shows the compressions and a stopwatch, then count the compressions by hand from the recording. Use a cushion or pillow, or a home-made mannequin if one is ready. Use the same surface for all three runs and record exactly which surface was used in `docs/MEASUREMENTS.md` by including it in the table's Run field.

The holder stands at the rescuer's side and holds the app phone sideways (landscape), framing the rescuer's arms and the compression surface. The rescuer does compressions on the chosen surface. Use the app only to record its trace: do not follow its metronome. To set a pace, use a separate phone's metronome or no metronome.

## Three runs

1. Open CPR Coach, choose **Practise**, and complete or skip the walkthrough if it appears. At the camera's framing state, position the holder and phone as above. When ready, tap **Start**; the app gives its countdown and begins the live session. Start the timed count on the first compression after the countdown. The holder watches the app; the rescuer does not use its rate display or metronome as a pace guide.
2. Run for about 60 seconds, aiming for about 90 compressions per minute. The counter starts the stopwatch and counts each compression by hand. Stop counting and stop the watch together at the end of the interval; write down the total count and actual elapsed seconds. Have the holder stop the app session after the interval so this run has its own trace.
3. Repeat the same setup and procedure for two more separate sessions, aiming for about 110 and then about 130 compressions per minute. Use a separate phone's metronome if needed, or work without one. Do not change the surface between runs. Record each human count, actual stopwatch time, app phone, run/target label, and surface used.

## Pull and analyze traces

After the runs, connect the app phone to the computer with adb available and pull the trace files:

```sh
adb pull /sdcard/Android/data/com.zeroflux.cprcoach/files/traces/ ./traces/
```

For each run, identify the CSV trace for that session in `traces/` and pass its path, that run's hand-counted total as `N`, and its measured stopwatch duration in seconds as `S`:

```sh
python cpr-coach/tools/analyze_trace.py <csv> --count N --seconds S
```

Replace `<csv>`, `N`, and `S` with the trace path, compression count, and actual elapsed seconds; do not use the target pace as the count or time. The analyzer reports the human rate and the difference between the app rate and human rate when a usable beat is present. If there is no usable beat, retain that outcome rather than substituting an app rate.

Enter the measurements in `docs/MEASUREMENTS.md`, using one row per run in its table: date, app phone, run/target label (include the surface used), human count, actual seconds, human rate, app rate, difference, FPS, and latency. Copy the analyzer's reported values; leave unavailable values unavailable. Do not record a target pace as a measured result, and do not claim an accuracy result before the three runs are measured.

## Tools for reading a recording

Pull the traces as above. The recording now has extra columns: beside the time, wrist signal, elbow angle, minimum visibility and latency, it carries the frame gap, thermal status and battery temperature, the tracked side, and each joint's coordinates and visibility. Give the comparison tool one session's CSV, that run's hand count and its measured stopwatch seconds:

```sh
python cpr-coach/tools/compare_signals.py <csv> --count N --seconds S
```

It needs NumPy. It reports each signal the file can supply, names any it cannot build, and compares the confident windows with the hand count; leave an unavailable signal unavailable rather than substituting another. Apply the rule in `docs/RATE_NEXT_STEPS.md` to judge a confident beat and to choose between signals; do not choose by eye or from one window. Set up and film the run as `docs/FILMING.md` describes, so the video and the hand count cover the same interval as the trace.
