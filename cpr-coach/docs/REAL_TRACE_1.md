# First real recording: intermittent periodic motion, not a reliable compression rate

## Conclusion

**The wrist signal contains short, visibly quasi-periodic stretches. It does not support a sustained, independently verified CPR rate.** The desktop tool's “no usable beat” is not evidence of a flat signal: its window-coverage and interpolation-gap checks prevent it from reaching autocorrelation on this recording. A replay of the Android estimator defaults accepts only **5 of 294 frame updates**, at approximately **63/min** and **84/min**; most updates fail correlation or frame density.

No code or thresholds were changed. No phone was accessed. No Android compilation was attempted; this is a Python/NumPy investigation, not an app validation.

## Input and provenance

- Input: [`tools/testdata/real-rmx3392-1.csv`](../tools/testdata/real-rmx3392-1.csv), columns `t_ms,wrist_signal,elbow_deg,min_vis,latency_ms`.
- Input SHA-256: `359d4ecb410346c7d8bcce4bea9f0138603843fc15dbad9cb274b0e0275f82f9`.
- Source revision inspected: [`738a27399c090e4f58db6fe8945de5a1b4b528bc`](https://github.com/VivekTiwary-25/Zero_flux/tree/738a27399c090e4f58db6fe8945de5a1b4b528bc/cpr-coach). Analysis used that revision's `RateEstimator.kt`, `LiveAnalyzer.kt`, `PoseEngine.kt`, and `tools/analyze_trace.py`. The recording's APK revision is not encoded in the CSV; source replay is not proof of precisely what that APK displayed.
- Git fetch attempts timed out. An available reference archive was extracted outside the task checkout; the current revision was identified with `git ls-remote`, and pinned raw files supplied the current trace and estimator. `SPEC.md`, `Theme.kt`, `Contract.kt`, and the desktop analyzer were byte-compared with the pinned revision; they matched the archive. The estimator had changed, so replay used the pinned version, including its non-positive-peak guard.
- Analysis scripts, data copies, and plots were kept outside this checkout, under `../ref-t261030-1922-bd4d/`. Actual run: `python analyze_real.py` from that directory, using NumPy **2.4.6**. Only this document and the required swarm report are deliverables.

## What the rows show

| Measurement | Result |
|---|---:|
| Rows, all finite in every column | 294 |
| First / last timestamp | 134938 / 172475 ms |
| Elapsed time | 37.537 s |
| Median inter-frame gap | 108 ms |
| Gap 5th / 95th percentile | 40.2 / 283.6 ms |
| Worst gap | 464 ms, from 29.729 to 30.193 s relative to the first row |
| Median instantaneous FPS, `median(1000 / gap)` | 9.26 |
| Effective whole-recording FPS, `(rows - 1) / elapsed` | 7.81 |
| Gaps exceeding 66.667 / 125 / 250 ms | 238 / 113 / 29 of 293 |
| Gaps exceeding the caller's 500 ms tracking timeout | 0 |
| Latency median / 95th percentile / maximum | 144.5 / 300.8 / 403 ms |
| Minimum visibility, minimum / median | 0.779814 / 0.878197 |
| Elbow minimum / median / maximum | 60.66 / 100.76 / 154.67 degrees |
| Wrist signal minimum / median / maximum | -0.566331 / -0.446590 / -0.018752 torso lengths |

“About nine FPS” describes the median interval, not sustained throughput. The long gaps pull effective throughput below the estimator's source-defined floor of eight frames/s. Late rolling windows have only 39–47 rows over nearly six seconds; early windows commonly have 49–56.

At a hypothetical target rate of 100–120/min, the **measured** median gap would provide 5.56–4.63 samples per cycle, but the worst gap spans almost an entire cycle: only 1.29–1.08 samples per cycle at that local spacing. These are sampling calculations, not measured compression rates. The data is not uniformly sampled, so a single Nyquist limit inferred from median FPS would be misleading. Linear interpolation to the estimator's 30 Hz grid does not restore missing motion extrema or phase. This limits detection without proving that all periodicity is unrecoverable.

The file contains no thermal or battery-temperature column. The task identifies a hot phone; the CSV itself cannot establish heat as the cause of the gaps.

## Spectrum and autocorrelation: where there is a beat-like pattern

### Method

All times below are seconds since `134938 ms`. Rows were replayed in original timestamp order; there are no non-increasing timestamps or missing signal values. For each frame, retain rows whose timestamp is at least `current_timestamp - 6000 ms`, then reproduce the default estimator:

1. Require at least two rows and a timestamp span of 3000 ms. Apply the exact integer expression `row_count * 1000 // span_ms >= 8`; do not replace it with median instantaneous FPS or `(row_count - 1) / span`.
2. Linearly interpolate at `first_timestamp + i * 1000 / 30`, for `floor(span_ms * 30 / 1000) + 1` points. Subtract the edge-truncated, centered 31-point moving average.
3. Check RMS of the last 45 detrended points against 0.015 torso lengths.
4. Compute normalized dot-product autocorrelation, including neighboring lags 9 and 31. Search local peaks at lags 10–30 inclusive: Kotlin integer division makes the minimum lag **10**, not a rounded-up 11. Pick the shortest peak at least 0.6 times the best positive peak; require its correlation to be at least 0.35.
5. Require normalized correlation of the final 45 points with the lagged preceding points to be at least 0.5. Parabolically refine the selected lag for the candidate rate. These constants are source configuration, not measurements or proposed new thresholds.

For spectrum inspection, subtract the detrended signal's mean, apply a Hann window, and take the squared magnitude of a 16384-point zero-padded real FFT at the interpolation grid's 30 Hz. Select the maximum in the source's nominal 60–170 cycles/min band. Zero padding locates a broad peak; it does **not** create independent frequency resolution. A six-second span has roughly 10/min Fourier spacing; a four-second span roughly 15/min. Decimal peak locations below identify numerical outputs, not clinical precision.

As an interpolation cross-check, fit the original irregularly timed wrist rows to `offset + linear_drift + a*sin(2*pi*f*t) + b*cos(2*pi*f*t)`, scanning 60–170/min at 0.1/min increments with NumPy least squares. Report the frequency minimizing residual sum of squares and the variance reduction versus offset plus drift alone. This is descriptive, not a significance test or a compression classifier.

### Measured short stretches

Plots of the measured wrist rows, detrended waveform, spectrum, and autocorrelation were generated and visually inspected outside the checkout. The initial large shift in wrist level and the irregular oscillations thereafter must not be counted as known compressions.

| Actual row span | Rows | Hann spectrum peak (/min) | ACF candidate (/min) | ACF strength | Recent correlation | Irregular-time fit peak (/min) | Variance reduction |
|---|---:|---:|---:|---:|---:|---:|---:|
| 5.182–8.944 s, four-second diagnostic | 31 | 62.7 | 66.7 | 0.538 | 0.561 | 61.2 | 19.2% |
| 7.013–10.978 s, four-second diagnostic | 38 | 64.9 | 67.9 | 0.480 | 0.355 | 65.5 | 36.5% |
| 15.998–21.947 s, default six-second retention | 49 | 95.1 | 83.9 | 0.453 | 0.539 | 77.3 | 25.2% |
| 18.145–21.947 s, four-second diagnostic | 32 | 98.4 | 91.5 | 0.396 | 0.501 | 99.4 | 35.4% |
| 16.845–22.788 s, default six-second retention | 48 | 98.1 | 90.2 | 0.402 | 0.134 | 93.0 | 34.7% |

**Observation:** a slow repeat around 60–70/min is visible in the earlier short stretch; a later repeat is closer to 90–100/min on the shorter diagnostic window. The six-second and shorter-window estimates disagree, and the later recent-beat correlation subsequently collapses. The spectrum has broad/competing peaks, not a stable line maintained across the recording.

**Inference:** intermittent rhythmic motion exists, but its rate changes, and/or pose errors, arm changes, and non-compression motion contaminate it. None of those causes can be uniquely identified from these scalar rows. There is no defensible whole-session average compression rate and no evidence here for claiming a sustained 100–120/min compression cadence. The four-second windows are diagnostic experiments, not replacement production readings.

## Default estimator replay: which check rejects each window?

The table samples the continuous replay at one-second requested endpoints, selecting the last recorded row at or before each endpoint. The **actual span** is the estimator's retained row span, not an assumed exact six seconds. Startup through 2.947 s remains in minimum-duration warmup. The final recorded row is included separately.

`frames` means the frame-density warmup check; `correlation` includes no positive local peak or a selected peak below 0.35; `recent` means the final 1.5-second comparison failed. `BEAT` means all default gates passed. The gate column is the **first failing check**. RMS, ACF, recent correlation, and candidate rate were also computed diagnostically behind earlier gates; a candidate in a rejected row is **not** an output rate. All comparisons used unrounded numbers.

| Requested end (s) | Actual span (s) | Rows | Rows/s | Recent RMS | ACF | Recent corr. | ACF candidate (/min) | FFT peak (/min) | First result |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---|
| 3 | 0.000–2.947 | 26 | 8.82 | — | — | — | — | — | duration |
| 4 | 0.000–3.965 | 33 | 8.32 | .035 | .189 | -.165 | 76.5 | 79.2 | correlation |
| 5 | 0.000–4.910 | 43 | 8.76 | .028 | .164 | .054 | 77.7 | 82.5 | correlation |
| 6 | 0.000–5.936 | 49 | 8.25 | .031 | .069 | -.238 | 80.2 | 83.7 | correlation |
| 7 | 0.954–6.841 | 50 | 8.49 | .041 | .040 | -.045 | 81.4 | 86.4 | correlation |
| 8 | 2.033–7.990 | 48 | 8.06 | .036 | .017 | .481 | 67.2 | 83.9 | correlation |
| 9 | 2.947–8.944 | 49 | 8.17 | .033 | .163 | .536 | 65.2 | 60.1 | correlation |
| 10 | 3.965–9.927 | 54 | 9.06 | .022 | .305 | .506 | 64.2 | 63.2 | correlation |
| 11 | 5.182–10.978 | 52 | 8.97 | .031 | .494 | .354 | 65.9 | 65.3 | recent |
| 12 | 6.005–11.962 | 56 | 9.40 | .025 | .209 | .064 | 62.0 | 64.6 | correlation |
| 13 | 6.841–12.755 | 55 | 9.30 | .026 | .374 | .357 | 67.8 | 61.6 | recent |
| 14 | 8.090–13.999 | 56 | 9.48 | .029 | .136 | -.123 | 64.3 | 61.0 | correlation |
| 15 | 9.019–14.965 | 56 | 9.42 | .025 | .023 | .512 | 103.1 | 65.7 | correlation |
| 16 | 10.056–15.998 | 51 | 8.58 | .015196 | .126 | -.371 | 100.8 | 112.5 | correlation |
| 17 | 10.978–16.963 | 51 | 8.52 | .028 | .140 | -.099 | 88.2 | 112.4 | correlation |
| 18 | 11.962–17.902 | 49 | 8.25 | .034 | .174 | .175 | 92.7 | 112.5 | correlation |
| 19 | 13.095–18.956 | 50 | 8.53 | .021 | .189 | .215 | 69.8 | 66.4 | correlation |
| 20 | 13.999–19.963 | 49 | 8.22 | .017 | .296 | -.048 | 78.6 | 78.6 | correlation |
| 21 | 14.965–20.756 | 48 | 8.29 | .014885 | .351 | -.064 | 80.8 | 83.7 | stillness |
| 22 | 15.998–21.947 | 49 | 8.24 | .025 | .453 | .539 | 83.9 | 95.1 | BEAT |
| 23 | 16.845–22.788 | 48 | 8.08 | .020 | .402 | .134 | 90.2 | 98.1 | recent |
| 24 | 17.902–23.872 | 47 | 7.87 | .028 | .345 | .259 | 93.6 | 91.5 | frames |
| 25 | 19.016–24.965 | 45 | 7.56 | .018 | .111 | -.598 | 91.9 | 88.9 | frames |
| 26 | 20.102–25.997 | 43 | 7.29 | .032 | .046 | -.153 | 83.9 | 60.1 | frames |
| 27 | 21.080–26.787 | 43 | 7.53 | .028 | .002 | .258 | 82.6 | 60.1 | frames |
| 28 | 22.162–27.965 | 44 | 7.58 | .027 | .186 | .609 | 82.1 | 83.9 | frames |
| 29 | 23.014–28.970 | 43 | 7.22 | .022 | .170 | .464 | 80.9 | 85.7 | frames |
| 30 | 23.767–29.729 | 43 | 7.21 | .019 | .238 | -.101 | 80.9 | 85.7 | frames |
| 31 | 25.038–30.990 | 39 | 6.55 | .013 | .365 | .169 | 81.4 | 86.0 | frames |
| 32 | 25.997–31.914 | 40 | 6.76 | .021 | .282 | -.315 | 78.7 | 85.4 | frames |
| 33 | 27.049–32.975 | 39 | 6.58 | .020 | .112 | -.165 | 77.3 | 60.1 | frames |
| 34 | 28.157–33.981 | 39 | 6.70 | .010 | .189 | -.085 | 65.0 | 60.1 | frames |
| 35 | 28.920–34.908 | 44 | 7.35 | .013 | — | — | — | 60.1 | frames |
| 36 | 30.193–35.958 | 45 | 7.81 | .019 | — | — | — | 77.1 | frames |
| 37 | 30.882–36.813 | 47 | 7.92 | .029 | — | — | — | 100.2 | frames |
| 37.537 | 31.658–37.537 | 44 | 7.48 | .030 | .122 | .177 | 112.1 | 101.4 | frames |

Dashes after warmup mean no selected positive ACF peak, not zero correlation at every lag. Overlapping windows are not independent observations.

### All-frame replay, not just sampled endpoints

| First result | Frame updates |
|---|---:|
| Minimum-duration warmup | 26 |
| Too few frames per elapsed time | 104 |
| Stillness | 3 |
| Correlation / peak selection | 134 |
| Recent-beat check | 22 |
| Accepted beat | 5 |

Accepted updates, relative timestamp → measured estimator output:

- `10.272 s → 63.44/min`
- `21.431 s → 85.26/min`
- `21.646 s → 83.64/min`
- `21.685 s → 83.50/min`
- `21.947 s → 83.91/min`

Stillness rejected at 16.119, 16.177, and 20.756 s; their recent RMS values were 0.013466, 0.012808, and 0.014885. This is not predominantly a stillness failure.

Removing **only** the FPS gate accepts **zero additional updates**: the 104 previously blocked updates then split into 20 stillness, 77 correlation, and 7 recent-beat failures. Lowering the FPS floor alone cannot fix this recording.

A second, scalar transcription of interpolation, detrending, peak selection, and gate order agreed with the NumPy port on **all 294 first-result labels**. Maximum accepted-rate difference was `1.42e-14/min`. This checks the transcription against an independently expressed implementation; it is not execution of Kotlin or the phone app.

At recorded timestamps, every sample passes `LiveAnalyzer`'s visibility/finite-value checks, and no inter-row gap exceeds its 500 ms reset threshold. No reset is therefore needed for this replay. UI read timing, session start/stop timing, rate smoothing/display scheduling, and actual human actions are absent from the CSV. Five estimator acceptances do not prove that a holder saw five displayed readings.

## Why the desktop tool says “no usable beat”

Two separate prerequisites prevent this file from supplying that tool with usable windows:

1. `summarize()` first selects only rows inside each nominal six-second interval. `beat_rate()` then starts a fresh six-second interpolation grid at the **first selected row** and requires its final grid time to be no later than the last selected row. None of the **32** nominal windows in this recording has that exact coverage. All **32** return at this span check, before signal analysis. The first selected interval, for example, covers only `0.000–5.936 s`. Irregular row times and clipping away the bracketing rows make this a window-construction problem, not a spectral result.
2. Independently, `beat_rate()` forbids interpolation brackets wider than **66.667 ms**, derived from twice its 30 Hz grid period. The measured median source gap is **108 ms**. Even after keeping source rows bracketing each requested interval, **all 32** intervals contain gaps above its cap. That fixed cap assumes source timing much denser than this file provides.

These were checked by instrumenting the same window-selection and bracketing conditions, rather than treating the CLI's reported lack of a rate as proof that no motion exists. Android's estimator has no equivalent per-gap interpolation cap: it instead checks aggregate frame density and then interpolates across retained gaps. The desktop result and the Android replay answer different questions.

## Elbow: plausible values, unverified joints

**Data:** all angles lie between 60.66 and 154.67 degrees. The median is 100.76 degrees; only **9** rows reach at least 150 degrees. The first 0–6 s has median 138.24 degrees, followed by medians of 96.22 for 6–18 s, 101.16 for 18–30 s, and 107.53 for 30–37.537 s. Absolute successive angle changes have median **8.52 degrees**, 95th percentile **29.15 degrees**, and maximum **50.85 degrees**. The largest change is from **128.08 to 77.23 degrees in 178 ms**, between 35.060 and 35.238 s. Landmark minimum visibility never drops below 0.779814.

**Inference:** the range is anatomically possible for a moving, bent human arm; it is not by itself proof that MediaPipe confused joints. It is also not evidence of consistently straight compression elbows. The fast, large changes warrant checking landmark placement, occlusion, and arm switching, but real repositioning remains possible because the activity is unlabelled. Visibility is model confidence, not proof of correct anatomical identity.

`PoseMath.elbowDegrees()` uses aspect-corrected image-plane shoulder–elbow–wrist geometry, not an independently measured anatomical angle. `PoseEngine` chooses the side with the larger summed shoulder/elbow/wrist/hip visibility **per frame**. This session-format CSV records neither the chosen side nor those landmark coordinates or individual confidences. **Left versus right cannot be recovered, and neither can near versus far arm or whether the chosen side changed.** Claims of a particular arm being tracked, swapped joints, or a consistent side are unsupported by this file.

## Smallest changes to consider, ranked — not implemented

There is no labelled compression count or verified arm identity here, so no accuracy-improving threshold change is established.

1. **Expose existing rejection diagnostics without changing acceptance.** Record the first failed gate, actual span/row count, maximum gap, RMS, selected-lag strength and recent correlation alongside selected arm identity in a lab/debug trace. Keep unavailable rate unavailable. This is the smallest engine-side diagnostic change: it distinguishes under-sampling from motion/pose problems without making additional rates appear.
2. **Make continuity explicit before mixing samples.** If a subsequent side-labelled trace demonstrates side changes, retain one selected arm through a rate window or reset the rate window on a confirmed side change, instead of correlating an unknown mixture. This is conditional, not a claim that switching caused this trace. Pair any interpolation change with explicit gap-quality reporting; do not equate a synthesized 30 Hz grid with 30 observed frames/s or copy the desktop's incompatible fixed gap cap into Android.
3. **Evaluate a shorter rate-history window using the existing constructor, not looser confidence gates.** The four-second diagnostic isolates coherent short stretches that the six-second history mixes with other motion. This suggests a candidate experiment, not a demonstrated fix: shorter history also loses frequency discrimination and can make irregular motion look rhythmic. Preserve stillness, correlation, recent-beat, and tracking checks until human-counted, activity-labelled traces justify a change. Do not lower the FPS gate alone: the measured counterfactual adds no accepted readings.

Separate tooling repair, not an engine change: keep bracketing source rows around requested desktop intervals and report coverage/gap rejection separately from “no periodic motion”; make the offline sampling-quality policy explicit rather than implicitly assuming 30 Hz observations. Do not bypass gap checks merely to print a rate.

What remains unknown is the actual compression timeline/count, the tracked anatomical side, and landmark correctness. A synchronized video or side-labelled raw-landmark trace with human annotations is needed to resolve those questions; this investigation did not acquire one or access a phone. No new UI text or localization keys are required by this documentation-only change.
