# Landmark plausibility study

## Result

No setting meets every target. The sweep contains 144 settings and 16,439 frames from recordings 1–15.
The best setting meets the straight-rejection and angle-separation targets. It fails the run-length target.
These are diagnostic rankings, not production recommendations.

Reference: `origin/cpr-coach`, fetched and archived at `967e155b99f078e8713877db667854feed47a780`.
No Kotlin guard code was used. The study uses only Python 3's standard library.

Command run from the checkout root:

```sh
python3 cpr-coach/tools/guard_study.py --data-dir ../ref-t261031-0334-7acf/cpr-coach/tools/testdata --reference 967e155b99f078e8713877db667854feed47a780
```

The command emits JSON. `results` contains every setting and every recording, in rank order.
Each labelled segment includes rejection share, raw and accepted angle distributions, and rolling-offset distributions.
`best_recording15_rolling_series` contains the complete timestamped rolling-offset series for each labelled segment of recording 15.
The tables below are rounded. Selection uses unrounded values.

## Method

- Scale image x by 4/3. Leave y unchanged. Do not use `elbow_deg`, world coordinates, visibility, or `side_on`.
- Subtract the current hip from each joint. Divide by the current shoulder-to-hip distance.
- Compare wrist and elbow horizontal positions with the **last accepted** frame. Reject if either absolute change exceeds L.
- Measure upper-arm and forearm lengths in torso units. Compare each with its own median over accepted frames in the previous 2 seconds. Include the exact 2-second boundary. Reject outside the selected ratio window. Skip this comparison if no accepted baseline remains.
- Reject a wrist above the shoulder: `wy < sy`. Image y points down.
- Reject a wrist on the hip side of the shoulder: `(wx-sx)*(hx-sx) > 0`. Equality is allowed. These two anatomical gates have no tolerance.
- Reject missing, nonfinite, or degenerate poses. They remain in the denominator. They cannot seed a reference.
- After rejection, require mutually consistent, anatomically valid poses for at least R ms. Every pair must satisfy L and the bone-ratio window in both directions. A conflicting pose starts a new candidate run. A missing pose, anatomical failure, or timestamp gap greater than R breaks candidate continuity.
- Accept the frame that completes this stable run. Replace the last accepted reference and restart the accepted bone history from that frame. Earlier rejected candidates remain rejected.
- Any change in `side`, including a blank side, clears the reference, bone history, candidate run, and offset history. The first anatomically valid pose on the new side seeds the reference.

L is 0.10, 0.15, 0.20, 0.25, 0.30, or 0.35 torso lengths.
Bone windows are 0.90–1.10, 0.85–1.15, 0.75–1.25, or 0.65–1.35 times the accepted median.
R is 250, 300, 350, 400, 450, or 500 ms.

Rejection share is a frame fraction, not a time fraction.
A rejected frame owns the interval to the next timestamp. The final frame owns one median frame interval.
The longest run includes missing poses and unlabelled times. It is not capped at R.
There is no interpolation across gaps.

Labels use seconds from each recording's first row. Both interval endpoints are inclusive.
Only picture/human straight labels set the straight-rejection target. This includes recording 14 at 3–24 s and all three straight segments in 15.
Recording 12's bent label and 15's leaning label are provisional. Unknown and unlabelled times do not set posture targets.
Picture labels were read by an assistant from saved frames, not confirmed by a person.

The elbow angle is the 2D angle at the elbow, recomputed from the aspect-corrected shoulder, elbow, and wrist.
Arm length for the offset is upper-arm length plus forearm length in the same coordinates.
The signed offset is `(wx-sx)*dir/armLength`, with `dir=+1` when `wx>=hx`, otherwise `-1`.
Use a trailing 1-second median of accepted offsets. Include the exact 1-second boundary.
Do not reset at label boundaries. The segment summaries sample this series only at accepted frames.
The JSON also includes the unfiltered rolling series and its summaries.

For ranking, “clearly below” means at least a 10° gap between the lowest straight-segment median and the highest bent-segment median in recording 15.
Rank first by the number of failed targets. Then use summed normalized target excess, larger angle gap, fewer rejected frames, and setting ID.
Strict targets are rejection **<5%** and longest run **<450 ms**. The declared angle target is gap **≥10°**.

## Best three

All three use L=0.15 and a bone window of 0.85–1.15.
“Worst straight” is the maximum over the four picture-labelled straight intervals.
“Gap” uses the accepted segment medians in recording 15.

| Rank | R (ms) | Worst straight rejected | Longest run, all recordings (ms) | 15 rejected | 15 longest run (ms) | Gap (degrees) | All targets |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 400 | 4.90% | 30078.0 | 18.22% | 1810.0 | 23.99 | No |
| 2 | 350 | 4.90% | 30078.0 | 17.47% | 1728.5 | 23.90 | No |
| 3 | 300 | 4.90% | 30078.0 | 17.22% | 1728.5 | 23.76 | No |

90 settings meet the straight-rejection target. 134 meet the angle-gap target. None meet the run target.
The ranking favors R=400's slightly larger separation. R=300 rejects fewer frames in 15, but still misses the run target badly.

## Best setting by recording

Setting: `L0.15_B0.85-1.15_R400`.
These numbers include every timestamped row, not only labelled or tracked poses.

| Recording | Frames | Rejected | Longest rejected run (ms) |
| --- | ---: | ---: | ---: |
| 1 | 315 | 97.14% | 6057.0 |
| 2 | 865 | 87.40% | 16314.0 |
| 3 | 2362 | 49.87% | 6271.0 |
| 4 | 943 | 63.10% | 7391.0 |
| 5 | 1509 | 30.55% | 4567.0 |
| 6 | 973 | 34.33% | 1620.0 |
| 7 | 1198 | 5.68% | 1412.0 |
| 8 | 300 | 45.00% | 1667.0 |
| 9 | 1228 | 18.97% | 3197.0 |
| 10 | 999 | 4.80% | 456.0 |
| 11 | 710 | 31.69% | 1745.0 |
| 12 | 1141 | 28.40% | 13562.0 |
| 13 | 898 | 35.08% | 2161.0 |
| 14 | 1401 | 53.03% | 30078.0 |
| 15 | 1597 | 18.22% | 1810.0 |

## Recording 15 segments, best setting

Offset cells show the median of the trailing 1-second series, with its 10th–90th percentiles.
The angle is the median among accepted frames. All rows are shown, including unknown intervals.

| Seconds | Label | Accepted/total | Rejected | Angle (degrees) | Rolling signed offset, median (p10–p90) |
| --- | --- | ---: | ---: | ---: | ---: |
| 3.5–32 | straight | 645/654 | 1.38% | 174.41 | 0.125 (0.067–0.177) |
| 32.3–34 | unknown | 6/38 | 84.21% | 150.77 | 0.316 (0.233–0.381) |
| 34.5–37.3 | straight | 59/60 | 1.67% | 173.94 | 0.209 (0.202–0.242) |
| 37.4–38 | unknown | 1/14 | 92.86% | 176.68 | 0.193 (0.193–0.193) |
| 38.2–43.4 | bent | 110/116 | 5.17% | 138.18 | 0.253 (0.239–0.294) |
| 44–53.4 | straight | 194/204 | 4.90% | 174.28 | 0.162 (0.135–0.178) |
| 54–61.5 | bent | 76/158 | 51.90% | 149.95 | 0.377 (0.360–0.405) |
| 65–69.5 | leaning, provisional | 55/100 | 45.00% | 157.78 | 0.303 (0.272–0.322) |

The difficult 54–61.5 s block improves from a raw median of 154.69° to 149.95°.
Its accepted p10–p90 angles are 140.42–160.05°. Its accepted maximum is 169.02°.
The guard does not prove that every phantom wrist is removed.
The raw rolling-offset median is 0.318 there, versus 0.377 after filtering.
The first bent block has a raw median angle of 136.66°, versus 138.18° accepted.

## Limits and checks

Recording 14 has 672 unavailable poses. Its longest missing-only run is 19,283 ms.
Missing poses plus the fixed anatomical gates force a 30,078 ms rejection run there, regardless of the swept parameters.
Recording 15 has no missing poses. Its fixed anatomical gates alone force a 718.5 ms run.
Thus excluding missing rows would not by itself meet the literal everywhere target.
The study does not bypass those gates to make the score pass.
Most earlier recordings have no independent posture labels. Their rejected shares are not false-rejection estimates.

The actual CLI completed successfully and emitted parseable JSON for all 144 settings, all 15 recordings, and all eight segments in 15.
Throwaway smoke checks covered stable re-anchoring, alternating clusters, side resets, anatomical rejection, last-accepted references, uncontaminated bone history, timestamp gaps, and the exact 2-second median boundary.
A separate dot-product angle calculation matched the replay's aspect-scaled angle.
No permanent test scaffolding or app changes were added.
