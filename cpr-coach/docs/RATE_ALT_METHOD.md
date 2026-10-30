# Rate alternatives: no clear winner

**Keep autocorrelation.** Peak counting increases availability but falsely reports a rate with nobody present. Spectral estimation does not beat matched-window autocorrelation on availability or generated-noise rejection.

**Run:** Python replay of `real-cph2467-1..9.csv` and `negative-cph2467-nobody.csv`, reference revision `f1abec2519e232ca020e6743edc241fd92e10f31`. Recorded wrist signal; finite signal/elbow; visibility ≥0.5; reset after >500 ms between good samples; reads every 250 ms; no display holds/smoothing. The faithful `RateEstimator.kt` port includes edge thresholds, octave preference and pooled recent correlation. Scalar/vector implementations agreed on 983 windows, maximum difference 2.85e-14/min. Not compiled; no app changes, adb or phone access.

**No human count or start/stop labels exist: true accuracy, pushing coverage and onset latency are unknowable.** Availability uses the app's independent motion proxy: 500 ms linear-detrended wrist RMS, ≥3 frames, >0.04 on / >0.025 off (torso lengths). This includes transitions/non-pushing motion; warm-up counts as unavailable. Overlapping windows are not independent. Metronome agreement is not accuracy.

| Method | Rates / 1,279 motion slots | Median /min | Paired difference¹ /min | Temporal change² /min | Onset delay³ |
|---|---:|---:|---:|---:|---:|
| Original ACF, 6 s | 449 (35.1%) | 109.86 | — | 1.94 | 4.25 s; 6/6 |
| ACF, 4 s | 558 (43.6%) | 109.80 | 1.12 | 2.63 | 3.63 s; 6/6 |
| Spectral, 4 s | 480 (37.5%) | 111.00 | 1.75 | 2.00 | 2.25 s; 5/6 |
| Peaks, 4 s | 687 (53.7%) | 109.76 | 1.78 | 5.90 | 1.75 s; 6/6 |

¹ Median absolute difference from original ACF on 434/376/410 paired motion slots. ² 95th-percentile absolute change between adjacent rated motion slots. ³ Detected-only median: six ≥4 s motion bouts after ≥2 s observed quiet, closing ≤1 s motion gaps; excludes recording-start motion. Spectral misses one: not a latency win.

Nobody: 288 slots, 64 tracked, only 17 past warm-up. Both ACFs/spectral: zero rates; peaks: **105.88/min at 17.0 s**. Limited eligible coverage weakens specificity evidence.

Generated-noise false-rate shares; each denominator 3,280 post-warm-up slots:

| Noise | ACF6 | ACF4 | Spectral4 | Peaks4 |
|---|---:|---:|---:|---:|
| White | 0.00% | 0.21% | 0.00% | 19.82% |
| AR(1) | 2.23% | 5.52% | 4.12% | 18.23% |
| Random walk | 1.40% | 3.96% | 3.81% | 17.53% |

**Experimental constants, not measured physiology:** alternatives share 3 s minimum history, ≥6 frames/s, engine-configured 30 Hz interpolation, centered 31-point mean subtraction, recent 1.5 s RMS ≥0.015. Spectral: Hann DFT over 60–170/min, step 1/min; `P(f)=2|Σwx·exp(-i2πft)|²/(Σw·Σwx²)`; require `P(f)≥.45`, `P(f)+P(2f)≥.65`; lowest local peak of `P(f)+.5P(2f)` within 80% of best eligible peak; recent fundamental power ≥.35. Peaks: `[1,4,6,4,1]/16` smoothing, separation ≥10 samples, prominence ≥max(.03,.5·window RMS), ≥4 peaks, all intervals within 60–170/min, interval CV ≤.20, last peak age ≤1.5 median intervals; rate =60/mean interval.

Noise: seed 2467, 20 trials on recording 6 timestamps; shared Gaussian innovations/trial; white SD .1, AR coefficient .9/stationary SD .1, walk step SD .03. Python 3.12.10 / NumPy 2.5.1 / SciPy 1.18.0. Generated 110/min sine/harmonic controls were detected by all methods. **Next:** synchronized human counts, pushing/pause labels and held-out negatives—not an alternative Kotlin port.
