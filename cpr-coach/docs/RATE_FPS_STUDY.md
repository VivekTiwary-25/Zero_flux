# Frame rate and movement size: simulation only

**Simulation, not real-person accuracy.** Reference `53761f456da7`; faithful external Python port of `engine/RateEstimator.kt`, unchanged thresholds. Java-compatible replay passed all 59 cases in the 13 `RateEstimatorTest.kt` methods. Kotlin/Android not compiled.

**Sweep:** 8/10/15/20/25/30 fps × 90/110/130 compressions/min × peak sine amplitudes 0.01/0.02/0.03/0.05/0.1 torso lengths × two noise proxies × two timings. `tools/testdata/real-rmx3392-1.csv`: 294 frames/37.537 s; mean 7.806 fps. R = regular timing; J = recorded intervals (21–464 ms), mean-scaled to target fps and cycled; integer-ms timestamps.

**Noise source uncertain:** residuals mix movement, drift and pose/sensor errors. H = neighbour-interpolation residual divided by `sqrt(1+(1-w)^2+w^2)`, with time-fraction weight `w`; D = residual from centred ±0.5 s sample mean. Centred population SD: **H 0.02381, D 0.03282 torso lengths**. Add ordered residuals cyclically, unscaled, to zero-phase sines; neither proxy is calibrated noise.

Read each second, 6–37 s: **11,520 overlapping windows, 360 settings**. Cells pool rates/proxies (192 windows): **beat % / conditional mean absolute error (/min), first-rejection letters**. — = no returned beats. Pooling hides individual-condition failures.

| Timing, fps | A=0.01 | A=0.02 | A=0.03 | A=0.05 | A=0.1 |
|---|---|---|---|---|---|
| R, 8 | 3.1/34.8 APCR | 8.3/7.9 APCR | 28.6/3.7 APCR | 66.7/2.1 CR | 100.0/1.0 – |
| R, 10 | 0.5/11.4 APCR | 7.8/3.5 APCR | 26.6/2.9 ACR | 68.2/2.9 CR | 99.5/1.0 R |
| R, 15 | 0.0/— ACR | 2.1/1.0 CR | 35.4/1.8 CR | 77.6/1.0 CR | 99.5/0.4 R |
| R, 20 | 0.0/— ACR | 6.8/7.1 CR | 33.9/3.5 CR | 79.2/1.5 CR | 100.0/0.6 – |
| R, 25 | 0.0/— ACR | 2.1/2.8 CR | 30.2/1.4 CR | 83.3/1.1 R | 100.0/0.4 – |
| R, 30 | 0.0/— C | 0.0/— CR | 10.4/1.6 CR | 65.6/1.4 CR | 100.0/0.4 – |
| J, 8 | 3.1/15.8 APCR | 12.5/6.7 APCR | 24.0/7.4 ACR | 49.5/3.3 CR | 74.0/3.0 CR |
| J, 10 | 1.0/7.3 ACR | 5.2/2.6 ACR | 27.1/5.8 ACR | 56.8/3.4 CR | 90.1/2.3 R |
| J, 15 | 0.0/— ACR | 8.9/2.2 APCR | 35.4/1.5 CR | 76.0/1.3 CR | 99.5/0.6 R |
| J, 20 | 0.0/— ACR | 5.2/1.2 CR | 35.9/1.4 CR | 82.8/1.0 CR | 100.0/0.6 – |
| J, 25 | 0.0/— ACR | 6.2/1.1 CR | 38.5/1.1 CR | 81.2/1.1 CR | 100.0/0.5 – |
| J, 30 | 0.0/— ACR | 4.7/1.4 CR | 38.5/1.4 CR | 83.9/1.2 R | 100.0/0.5 – |

**Limiting checks:** every observed first rejection is listed per cell: A = detrended recent-1.5-s RMS <0.015; P = no positive eligible local correlation peak (shortest ≥0.6×best); C = correlation <0.35; R = recent multi-cycle correlation <0.5; – = none. History ≥3 s and integer-density ≥6 fps never rejected. C/R dominated (3,695/2,829 rejections); noise can clear A without a beat.

**Smallest working amplitude:** require ≥95% beats and ≤5/min MAE in *every* rate/proxy/timing combination. **8/10 fps: none through 0.1; 15/20/25/30 fps: 0.1 torso lengths.** Regular timing alone: 0.1 at every fps. More fps was not monotonically better. Worst accepted error: **66.3/min** (J, 10 fps, 130/min, amplitude 0.03, H). Detection is not correctness; no person/visibility/dropout validation is claimed.
