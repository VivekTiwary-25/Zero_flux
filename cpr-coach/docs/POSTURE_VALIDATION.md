# Posture judge validation

Rule: never look polished while making wrong judgements; if unsure, say less (null).
Code: `engine/PostureJudge.kt`. Replay: `PostureReplayTest` writes `app/build/posture_replay/*.csv`;
`python tools/posture_report.py` adds labels (`tools/testdata/labels.csv`).

## How it decides
1. Joints seen (elbow visibility >= 0.8, others >= 0.6), else null.
2. Side-on over a 1 s window: clearly not (>= 80% frames not side-on) for a further 1 s gives NOT_SIDE_ON (about 1.8 s after the turn); in between gives null; nothing else is judged meanwhile.
3. Elbow angle and shoulder offset (shoulder behind(+)/ahead(-) of the wrist, over shoulder-elbow + elbow-wrist length, aspect-corrected) as 1 s medians (spans at least one compression cycle). Fire needs the condition held 1 s; clearing needs 1 s of the opposite. Good only when elbow median >= 160 and offset small.

## Measured (replay of real recordings, judge fed aspect 0.75 which reproduces the recorded elbow_deg)
- Elbow thresholds: bent below 150, straight from 160, gap = null. Source: recordings 10-12 (controlled test, side_on about 100%, elbow visibility about 0.99). Straight pushing reads 161-178 in 3 s medians; the block believed bent (-12 at 18-29 s) reads 127-153. The old rule (median under 100) could not fire on it.
- Normal pushing in -12 (0-15 s, medians 161-170) and all of -11 stay quiet (asserted in `PostureReplayTest`).
- Per-file shares: see the table printed by `posture_report.py` (rerun it; numbers are not copied here to avoid going stale).

## Not measured / unproven
- The bent block is the recorder's account (`team_unconfirmed` in labels.csv), not checked against video. Agreement on it is provisional and reported separately; 94% there says only that the 150 threshold matches the thought-to-be-bent numbers it was chosen from, which is partly circular.
- Recordings 2 and 3 (older sessions) are flagged bent for about 40% of frames: real bending, a different rescuer style, or 2D/tracking error cannot be told apart without labels. Likewise 10 (10%, a dip at 33-42 s) and 5 (12%).
- Lean (shoulder over wrist): no leaning was ever labelled and no recording flags it. LEAN_ENTER 0.45 / LEAN_EXIT 0.35 are unmeasured guesses, left wide on purpose. Recordings sit at |offset| 0.0-0.25 of arm length.
- NOT_SIDE_ON: no recording has a non-side-on stretch (11 has a few); shares (0.8/0.2) and time are guesses, tested only synthetically.
- Visibility limits, sustain times (1 s), window (1 s) are reasoned, not tuned.
- Older recordings have no side_on column; replay assumes true. The rmx3392 recording has no coordinates and is not replayed.

## Synthetic (PostureJudgeTest, 30 fps, 1.83 Hz compressions with +-8 degrees swing and noise)
Bent to 120: fires about 1.5 s after onset, clears about 1.7 s after fixing. Bent to 140: fires about 1.55 s. Lean: fires about 1.5 s, clears about 1.5 s. Not side-on: fires about 1.8 s, clears about 0.2 s. A 0.5 s glitch and noisy straight pushing never fire.
