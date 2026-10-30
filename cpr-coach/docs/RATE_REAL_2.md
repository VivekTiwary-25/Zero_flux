# Real rate rejection: measured replay

**Single-change recommendation: 6→4-second window; keep the normalized wrist signal and all gates.** Accepted reads increased 2,195→2,775 (+26.4%), improving eight recordings; nobody remained zero. This is replay evidence, not accuracy.

## Method and rejection ledger

Source `6892ced`; all ten CSVs. External faithful Python `RateEstimator.kt` replay: original timestamps/gates, read every finite frame, no `LiveAnalyzer` filtering/resets. Independent scalar check: 19,850 reads, zero gate mismatches. Scripts and timestamped gate ledgers: sibling `ref-t261031-0045-0573/` (paths in swarm report).

Default 6s, stored signal: **first failure** per sliding-window read. W=insufficient span, D=density, A=amplitude, C=correlation (including no peak), R=recent correlation, B=Beat. Counts are **reads, not compressions**.

| Recording | W | D | A | C | R | B (6s) | B (4s) |
|---|---:|---:|---:|---:|---:|---:|---:|
| real-1 | 44 | 15 | 0 | 26 | 0 | 0 | 0 |
| real-2 | 45 | 0 | 146 | 567 | 46 | 60 | 79 |
| real-3 | 67 | 0 | 0 | 1,907 | 242 | 76 | 180 |
| real-4 | 6 | 31 | 0 | 306 | 182 | 154 | 198 |
| real-5 | 55 | 21 | 18 | 195 | 172 | 800 | 852 |
| real-6 | 71 | 0 | 0 | 208 | 413 | 281 | 382 |
| real-7 | 71 | 0 | 23 | 278 | 441 | 385 | 466 |
| real-8 | 68 | 0 | 0 | 106 | 28 | 98 | 122 |
| real-9 | 68 | 0 | 0 | 489 | 280 | 341 | 496 |
| nobody | 66 | 0 | 0 | 337 | 13 | 0 | 0 |

Correlation/recent correlation dominate: 4,082/1,804 failures versus amplitude/density 187/67. Removing recent correlation would admit 13 negative reads.

## Signal/window comparison

Cells: **port Beat reads / `compare_signals.py` confident windows**. Denominators: 8,830 frames; tool 144/107/83/71 complete, non-overlapping windows at 3/4/5/6s, anchored at recording start. Ran its CLI; varied only imported `WINDOW_SECONDS`. Tool adds coverage/side/gap checks, uses different correlation and no amplitude floor—not an Android replay.

| Raw image-height signal | 3s | 4s | 5s | 6s |
|---|---:|---:|---:|---:|
| wrist − hip | 39 / 55 | 1,061 / 33 | 911 / 27 | 781 / 22 |
| shoulder − hip | 48 / 71 | 1,791 / 45 | 1,649 / 37 | 1,504 / 29 |
| shoulder alone | 48 / 68 | 1,749 / 41 | 1,536 / 32 | 1,416 / 32 |
| wrist alone | 44 / 53 | 1,513 / 29 | 1,301 / 28 | 1,140 / 22 |

**Coverage and stability disagree.** Shoulder−hip/4s wins raw-port coverage; tool shoulder−hip/3s has most confident windows but adjacent-rate-change P90=44/min. Prefer shoulder-alone/4s for a counted experiment: median 110/min, P10–P90=104–118; adjacent-change median/P90=3/9.6 per minute (23 within-record pairs), versus shoulder−hip/4s at 4/28.5.

Limits: 3s port leaves 8,677/8,830 reads warming: retained span rarely meets the unchanged 3s minimum. Raw-coordinate amplitude floors are unit-mismatched, not deployable tuning. **Negative tool beats: zero throughout.** Port false reads: raw wrist−hip 1/2 at 3s/4s; normalized wrist 1 at 3s; all others zero. Overlapping reads are dependent. Without synchronized human counts/pushing labels, actual beats, misses, rate error and which rejections were mid-push are unknowable; metronome ≠ truth. No app changes, Android compilation, adb or phone access.
