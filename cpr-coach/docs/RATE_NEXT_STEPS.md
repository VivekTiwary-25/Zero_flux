# Rate: next recording decision rule

## Known and unknown

`REAL_TRACE_1.md` reports weak, intermittent wrist periodicity, a 464 ms maximum gap and a 100.76° median elbow angle. Its scalar-only CSV cannot identify the tracked arm, verify joint placement, measure shoulder motion or establish a human compression rate. Cushion motion versus pose error remains unresolved. Visibility is not proof of anatomical correctness.

## Freeze before inspecting the next recording

Record the proposed side, coordinates and visibilities alongside existing scalars, with synchronized video and a human count over the **same steady-compression interval**. Analyze only that interval: `--count N --seconds S` is not per-window truth. Compare identical six-second, non-overlapping windows; report the omitted tail. Check side switches and joint identity in video.

Candidates: wrist−hip, shoulder−hip, shoulder Y, wrist Y and recorded elbow angle. Raw Y uses image-height units; old files use negated `wrist_signal` in torso lengths. Missing aspect ratio prevents equivalent torso normalization or independent angle reconstruction. Never compare RMS across units or synthesize missing columns.

The NumPy tool retains boundary brackets, derives its interpolation grid from median valid-frame intervals and subtracts a centered one-second moving average. Search: normalized lagged correlation at 60–170/min in 1/min steps; shortest-period local peak ≥0.6 of the strongest positive peak. RMS covers the detrended window. **Confident beat** requires full coverage, ≥8 valid frames/s, correlation ≥0.35, last-1.5-second correlation ≥0.5, stable nonblank side if recorded, and **no source gap greater than half the candidate period**, including brackets. Density/correlation settings and octave preference come from the existing estimator. No shared amplitude floor: units differ. This is not an Android replay or an activity classifier; interpolation cannot repair gaps. Rejected candidate rates remain diagnostic.

Eligibility: at least half of complete windows confident; confident-window median within 10/min of the human rate (one compression per six seconds). Replace eligible wrist−hip only for strictly higher confident share and no worse absolute median-rate error; retain wrist on ties. If wrist is ineligible, choose greatest eligible share, then smallest error; ties prefer shoulder−hip, shoulder alone, wrist alone, then elbow. Absolute-position/elbow winners need video confirmation that camera motion or elbow bending is not the counted rhythm. Require the same winner on a separate counted recording before changing the engine. If none qualifies, improve capture/tracking, not thresholds. These are engineering criteria, not validated accuracy claims.

## Verification status

Rule frozen before these runs. Real CSV: six complete windows, 1.537 s omitted; wrist and elbow each confident in 0/6, three coordinate candidates unavailable, maximum gap 0.464 s. No human count or shoulder coordinates: no replacement decision. External synthetic 120/min trace: all five medians 120/min, zero count error, 2/3 windows confident; the 0.450 s dropout rejects the remaining window. A separate synthetic trace yields confident shoulder 120/min and no beat for static wrists. No phone accessed; no engine changes.
