# Overlay smoothing: recorded-joint replay

**Recommend `minCutoffHz=0.5`, `beta=20.0`, `derivativeCutoffHz=4.0` for normalized x/y.** Compute posture from confidence-gated **raw** landmarks, then median posture readings: overlay filtering can delay corrections and distort geometry.

Python replayed shoulder/elbow/wrist/hip x/y from all nine `real-cph2467-*.csv` files at actual `t_ms`, matching Kotlin’s filtered-value derivative; missing poses skipped, file/side changes reset, no clamping. Sweep: min `{0.5,1,1.5,2,3,4}`, beta `{0.02,1,5,10,20,40,80}`, derivative `{1,2,4}`: 126 combinations.

Windows, seconds from first row: roughly still `-2` 27.5–29.2, `-7` 38–41; pushing `-5` 4–16, 40–50 and `-6` 24–33. Shoulder-y sinusoid-plus-trend fits measured **108.84, 109.38, 111.60/min**. Jitter: pooled RMS successive-frame 2D displacement, all joints. Lag/gain: filtered-versus-raw phase delay/amplitude retention at fitted frequencies, shoulder/elbow/wrist y. Scored coordinates are in-frame.

| min / beta / derivative | Jitter | Lag median / max (ms) | Median gain |
|---|---:|---:|---:|
| Raw | 0.001941 | 0 / 0 | 100% |
| Defaults: 1.5 / 0.02 / 1 | 0.001174 | 62.0 / 64.0 | 57.7% |
| 0.5 / 10 / 4 | 0.001227 | 37.5 / 44.3 | 82.6% |
| **0.5 / 20 / 4** | **0.001338** | **24.9 / 30.1** | **91.6%** |
| 0.5 / 40 / 4 | 0.001438 | 15.7 / 19.2 | 96.8% |

Recommended settings reduce jitter **31.1%**; maximum filter lag is below the recorded median frame interval (**43 ms**). Unlabeled, visually selected windows include drift, not pure noise. Residual jitter and visibly lag-free performance need phone validation; camera/model/display latency is unmeasured. App unchanged; no Android build, adb or phone use.
