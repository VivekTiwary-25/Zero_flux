# One-camera posture: measured limits and proposed gates

**2D cannot certify straight arms or vertical loading.** `ElbowGate` requires **<100°** to enter bent: even perfect side-on 130° misses. Visibility is not accuracy; an occluded far shoulder means **unknown**, not automatic side-on acceptance.

**Synthetic experiment:** external Python/NumPy pinhole model, seed 20261031. Assumed kneeling geometry: upper/forearm .30/.26 m, shoulder/hip widths .36/.28 m, hip .28 m behind/.40 m below shoulder, wrist height .30 m. True angles 130°/170°, sagittal offset/arm length `q=0/.30`; sagittal/depth-plane bends. Factorial yaw 0–60°/10°, distance 1/2/3 m, camera height .3/.8/1.3 m, pitch −20/0/+20° relative to aiming at height .60 m, roll −10/0/+10°; 200 noise draws/configuration (16,200/class/yaw), conditional on visibility.

**Measured noise:** 12,543 CSV rows; 10,746 same-side triples, four visibilities ≥.8, gaps ≤100 ms. Aspect inferred from stored angles: .75 except session 10, 4/3; maximum reconstruction error .0005°. Median cadence 43–45 ms, not `frame_gap_ms=30`. Coordinate MAD first-difference jitter spans .30–5.44% arm length/session; `Δ²/√6` noise proxy .16–2.66%. Bootstrap centered residual vectors preserves within-frame covariance, **not temporal correlation**; motion contaminates this proxy.

**Ungated single-frame results, sagittal bends.** Bent `<150°`; projected-straight `≥165°`; between = unknown. Lean `|q₂D|>.20`, on 170° arms. FA = alarm on straight/aligned; miss = no alarm on bent/leaning, **including abstentions**. These are simulated rates, not recording accuracy.

|Yaw|Median angle error, 130°/170°|Bent FA/miss %|Median measured q, true .30|Lean FA/miss %|
|---:|---:|---:|---:|---:|
|0°|+0.3/+0.0°|0.02/0.02|.300|1.32/29.61|
|10°|+0.5/+0.2°|0.02/0.01|.296|2.88/29.55|
|20°|+2.3/+0.7°|0.03/0.01|.284|6.57/29.74|
|30°|+5.8/+1.6°|0.01/0.06|.263|9.31/31.35|
|40°|+11.0/+2.7°|0.01/0.40|.231|11.94/34.85|
|50°|+17.9/+4.2°|0.01/17.75|.192|13.96/56.19|
|60°|+26.1/+5.8°|0.00/88.47|.142|15.56/66.41|

**No universal yaw cutoff.** Sagittal 130°/170° remain separated at 60° (5th–95th: 145–160° versus 173–178°), despite the 150° threshold failing. Depth-plane 130°/170° both project to median ≈179° at **0° yaw**: already indistinguishable. True offset .30 produces .10–.50 at 0° and .02–.33 at 60° (5th–95th), overlapping aligned posture: elevation/roll confounds horizontal alignment.

**Yaw proxy:** aspect-corrected **horizontal** shoulder gap/torso is best available, not calibrated yaw. With known model proportions, noiseless yaw MAE over 0–30° is shoulders/hips/both **2.8/7.4/4.4°**; at 60°: **21.0/16.5/18.3°**. No universal winner; combining does not necessarily help. Far hip exists upstream, not in CSV. Proposed ratio **≤.25** rejects 47% at 20°, 99% at 30°; existing .35 is not calibrated 25°. Twist, proportions, roll and occlusion prevent universal conversion.

**Proposed check order:** finite nondegenerate, aspect-corrected geometry → near-joint visibility ≥.8/far shoulder ≥.5 → shoulder ratio ≤.25 → causal **200 ms median**, ≥3 samples, reset on side change/tracking loss → `<150°` bent/`≥165°` projected-straight/otherwise unknown. Never certify good from projected-straight alone. Five samples span 172 ms; synthetic 110/min, 25 mm common vertical motion and a 130→170° step gave 86 ms median detection lag, versus 516/731 ms for 25/35 samples—not an established optimal window. Offset ≤.10/>.20 are candidate neutral/lean bands **only after gravity/view calibration**; otherwise withhold leaning feedback.

**Recordings [INFERENCE]:** filtered elbow 5th/median/95th, sessions 1→12: `119/142/164; 96/140/169; 112/155/170; 157/166/173; 126/168/172; 158/166/174; 165/176/180; 126/177/180; 163/168/174; 148/173/179; 168/177/180; 101/166/178`. Median absolute offsets: `.061/.217/.042/.141/.136/.037/.087/.124/.066/.192/.130/.216`. All remain compatible with **0–60° yaw** given unknown true posture. Sessions 10/11/12 are side-on-flagged 100/99.4/100%: near-side view **or far-shoulder bypass**, not measured yaw. Others lack that flag; all lack bilateral gaps, camera orientation and posture labels.

**3D and remaining evidence:** [MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android) supplies hip-centered metric world landmarks; this app does not consume/record them. A correct reconstructed 3D angle removes projection error [INFERENCE], but [GHUM authors](https://blog.tensorflow.org/2021/08/3d-pose-detection-with-mediapipe-blazepose-ghum-tfjs.html) explicitly describe monocular depth ambiguity; the [model card](https://developers.google.com/static/ml-kit/images/vision/pose-detection/pose_model_card.pdf) evaluates 2D, not CPR elbow angles. Only synchronized labelled angle/offset/yaw recordings can settle actual false alarms/misses, correlated tracking bias, optimal windows, thresholds and world-3D benefit. No app changes or device access; Python geometric invariants and compression scenario passed; Android not compiled.
