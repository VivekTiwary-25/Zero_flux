#!/usr/bin/env python3
"""Score a CPR Coach phone-test trace as a replay estimate.

Usage: python tools/score_phone_test.py SESSION.csv [--offset SECONDS]

Offset is the holder's stopwatch-zero time, in seconds after the first CSV
frame. Expected test blocks are shifted by that amount onto trace time. This
uses trace landmarks and the offline six-second beat estimator; it cannot
recover voice clip starts, which are not stored in session CSVs.
"""

import argparse
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import analyze_trace  # noqa: E402
import session_report  # noqa: E402

ASPECT = 4 / 3
BLOCKS = (
    (0, 25, "straight"),
    (25, 40, "bent elbows"),
    (40, 55, "straight"),
    (55, 70, "stop/rest"),
    (70, 85, "straight"),
    (85, 100, "lean back"),
    (100, 110, "lean forward"),
    (110, 120, "session end"),
)


def median(values):
    values = [v for v in values if math.isfinite(v)]
    return float(np.median(values)) if values else math.nan


def fmt(value, digits=1):
    return f"{value:.{digits}f}" if math.isfinite(value) else "--"


def window_rate(times, signal):
    """Resample one six-second window; reject gaps longer than three frame periods."""
    valid = np.isfinite(signal)
    times, signal = times[valid], signal[valid]
    if len(times) < 2 or np.max(np.diff(times)) > 100:
        return None
    sample_times = times[0] + np.arange(181) * (1000 / 30)
    if sample_times[-1] > times[-1]:
        return None
    sampled = np.interp(sample_times, times, signal)
    return analyze_trace.beat_rate(sample_times, sampled)


def score(path, offset):
    data = analyze_trace.read_trace(path)
    rows = session_report.read_trace(path)
    first_ms = data[0, 0]
    # Offset gives the holder's zero relative to the first CSV frame.
    holder_zero_ms = first_ms + offset * 1000
    relative_ms = data[:, 0] - holder_zero_ms

    poses = [session_report.has_pose(row) for row in rows]
    # Build the same signed, aspect-corrected image-space measure as PostureJudge.
    offsets = []
    for row, has_pose in zip(rows, poses):
        sx, sy, ex, ey, wx, wy, hx = (session_report.number(row, key) for key in (
            "shoulder_x", "shoulder_y", "elbow_x", "elbow_y", "wrist_x", "wrist_y", "hip_x"
        ))
        if not has_pose or not all(math.isfinite(v) for v in (sx, sy, ex, ey, wx, wy, hx)):
            offsets.append(math.nan)
            continue
        sx, ex, wx, hx = sx * ASPECT, ex * ASPECT, wx * ASPECT, hx * ASPECT
        arm_len = math.hypot(sx - ex, sy - ey) + math.hypot(wx - ex, wy - ey)
        offsets.append((wx - sx) * (1 if wx >= hx else -1) / arm_len if arm_len >= 0.05 else math.nan)

    # Personal straight-arm baseline from the first straight teaching block.
    baseline_window = (relative_ms >= 0) & (relative_ms < 6_000) & np.asarray(poses)
    baseline_values = data[baseline_window, 2]
    baseline_elbow = median(baseline_values[np.isfinite(baseline_values) & (baseline_values >= 160)])
    baseline_offset = median([v for v, keep in zip(offsets, baseline_window) if keep])
    bent_below = max(145.0, min(158.0, baseline_elbow - 28.0)) if math.isfinite(baseline_elbow) else 145.0

    print("REPLAY ESTIMATE only; screen state/rate inferred from trace. Voice clip starts: unavailable (not in CSV).")
    print("block (holder s) | state changes / rate changes | tracking | posture issue | elbow median | shoulder offset | rate | issue delay")
    for start, end, label in BLOCKS:
        lo, hi = holder_zero_ms + start * 1000, holder_zero_ms + end * 1000
        idx = (data[:, 0] >= lo) & (data[:, 0] < hi)
        if not np.any(idx):
            print(f"{label} {start}-{end} | no trace coverage")
            continue
        frame_ids = np.flatnonzero(idx)
        block_rows = [rows[i] for i in frame_ids]
        block_pose = np.array([poses[i] for i in frame_ids])
        tracking_share = float(np.mean(block_pose))
        elbows = data[frame_ids, 2]
        elbow_med = median(elbows)
        block_offsets = [offsets[i] for i in frame_ids]
        offset_med = median(block_offsets)
        valid_pose = block_pose & np.isfinite(elbows) & np.array([math.isfinite(x) for x in block_offsets])
        elbow_issue = np.zeros(len(frame_ids), dtype=bool)
        shoulder_issue = np.zeros(len(frame_ids), dtype=bool)
        # One-second trailing medians approximate PostureJudge's rolling measure.
        for j, i in enumerate(frame_ids):
            window = (data[:, 0] >= data[i, 0] - 1000) & (data[:, 0] <= data[i, 0])
            em = median(data[window & np.isfinite(data[:, 2]), 2])
            om = median([v for k, v in enumerate(offsets) if window[k]])
            elbow_issue[j] = math.isfinite(em) and em < bent_below
            shoulder_issue[j] = math.isfinite(om) and (abs(om) > 0.45 or (math.isfinite(baseline_offset) and abs(om - baseline_offset) > 0.19))
        issue = valid_pose & (elbow_issue | shoulder_issue)
        issue_share = float(np.mean(issue[valid_pose])) if np.any(valid_pose) else math.nan
        matching_delay = math.nan
        matching = elbow_issue if label == "bent elbows" else shoulder_issue if label in ("lean back", "lean forward") else np.zeros(len(frame_ids), dtype=bool)
        matching &= valid_pose
        for j in np.flatnonzero(matching):
            matching_delay = (data[frame_ids[j], 0] - lo) / 1000
            break

        # Evaluate one six-second window ending at each trace frame. Report state
        # transition times and rate changes as events, not as separate tiny blocks.
        rates = []
        states = []
        for i in frame_ids:
            t = data[i, 0]
            # The trace has occasional single dropped frames. Interpolate only
            # short gaps, then pass the exact six-second uniform window to beat_rate.
            w = (data[:, 0] >= t - 6100) & (data[:, 0] <= t)
            rate = window_rate(data[w, 0], data[w, 1]) if np.count_nonzero(w) else None
            rates.append(rate if rate is not None else math.nan)
            states.append("LOST" if not poses[i] else "PAUSED" if not math.isfinite(rates[-1]) else "BEATING")
        changes = []
        prev_state = None
        prev_rate = None
        for j, (state, rate) in enumerate(zip(states, rates)):
            shown = round(rate) if math.isfinite(rate) else None
            if state != prev_state:
                changes.append(f"{relative_ms[frame_ids[j]] / 1000:.1f}s {state}")
            if shown != prev_rate:
                changes.append(f"{relative_ms[frame_ids[j]] / 1000:.1f}s rate {shown if shown is not None else '--'}")
            prev_state, prev_rate = state, shown
        if len(changes) > 6:
            changes = changes[:3] + ["…"] + changes[-2:]
        rate_med = median(rates)
        posture_name = "elbow" if np.any(elbow_issue & valid_pose) else "shoulder" if np.any(shoulder_issue & valid_pose) else "none"
        print(f"{label} {start}-{end} | {'; '.join(changes) or 'no changes'} | {tracking_share:.0%} | {issue_share:.0%} {posture_name} | {fmt(elbow_med, 0)}° | {fmt(offset_med, 2)} | {fmt(rate_med, 0)} cpm | {fmt(matching_delay, 1)} s")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path, help="session CSV pulled by tools/pull-traces.ps1")
    parser.add_argument("--offset", type=float, default=0.0, help="holder zero in seconds after the first CSV frame")
    args = parser.parse_args(argv)
    score(args.csv, args.offset)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
