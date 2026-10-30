#!/usr/bin/env python3
"""Compare CPR trace signals in six-second windows (requires NumPy).

Usage: python compare_signals.py trace.csv [--count N --seconds S]
See docs/RATE_NEXT_STEPS.md for the frozen confidence/selection policy.
"""

import argparse
import csv
import math
import sys
from dataclasses import dataclass
from pathlib import Path

import numpy as np

WINDOW_SECONDS = 6
MIN_RATE = 60
MAX_RATE = 170
# Existing RateEstimator settings, not measured properties of an input file.
MIN_FPS = 8
MIN_CORRELATION = 0.35
MIN_RECENT_CORRELATION = 0.5
RECENT_SECONDS = 1.5
DETREND_SECONDS = 1
OCTAVE_PREFERENCE = 0.6


@dataclass
class Candidate:
    name: str
    units: str
    values: np.ndarray


def number(row, key):
    try:
        value = float(row.get(key, ""))
        return value if math.isfinite(value) else math.nan
    except (TypeError, ValueError):
        return math.nan


def read_trace(path):
    with path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        fields = set(reader.fieldnames or ())
        if "t_ms" not in fields:
            raise ValueError("CSV requires a t_ms column")
        rows = list(reader)
    if not rows:
        raise ValueError("CSV contains no frames")
    times = np.array([number(row, "t_ms") for row in rows])
    if not np.all(np.isfinite(times)) or np.any(np.diff(times) <= 0):
        raise ValueError("t_ms must be finite and strictly increasing; do not mix sessions")
    times = (times - times[0]) / 1000
    candidates = []
    unavailable = []

    def column(key):
        return np.array([number(row, key) for row in rows])

    definitions = (
        ("wrist_minus_hip", ("wrist_y", "hip_y")),
        ("shoulder_minus_hip", ("shoulder_y", "hip_y")),
        ("shoulder_alone", ("shoulder_y",)),
        ("wrist_alone", ("wrist_y",)),
        ("elbow_angle", ("elbow_deg",)),
    )
    for name, required in definitions:
        if set(required) <= fields:
            values = column(required[0])
            if len(required) == 2:
                values = values - column(required[1])
            units = "degrees" if name == "elbow_angle" else "image_height"
        elif name == "wrist_minus_hip" and "wrist_signal" in fields:
            # Stored signal is hip-minus-wrist / torso. Sign does not affect ACF.
            values = -column("wrist_signal")
            units = "torso_lengths (negated wrist_signal fallback)"
        else:
            missing = ", ".join(key for key in required if key not in fields)
            unavailable.append(f"{name}: missing {missing}")
            continue
        if not np.any(np.isfinite(values)):
            unavailable.append(f"{name}: no finite samples")
        else:
            candidates.append(Candidate(name, units, values))
    sides = [row.get("side", "").strip() for row in rows] if "side" in fields else None
    return times, candidates, unavailable, sides


def correlation(first, second):
    denominator = float(np.linalg.norm(first) * np.linalg.norm(second))
    return float(np.dot(first, second) / denominator) if denominator else 0.0


def detrend(values, step):
    # Center first so a constant signal remains exactly zero after subtraction.
    centered = values - values[0]
    half = int(round(DETREND_SECONDS / step / 2))
    prefix = np.concatenate(([0.0], np.cumsum(centered)))
    indices = np.arange(len(values))
    left = np.maximum(0, indices - half)
    right = np.minimum(len(values), indices + half + 1)
    return centered - (prefix[right] - prefix[left]) / (right - left)


def detect_beat(grid, samples):
    # Fractional lags avoid quantizing rates to the measured (often low) FPS.
    rates = np.arange(MAX_RATE, MIN_RATE - 1, -1, dtype=float)
    strengths = []
    for rate in rates:
        period = 60 / rate
        mask = grid >= grid[0] + period
        delayed = np.interp(grid[mask] - period, grid, samples)
        strengths.append(correlation(samples[mask], delayed))
    peaks = [i for i, strength in enumerate(strengths)
             if strength > 0
             and (i == 0 or strength > strengths[i - 1])
             and (i == len(strengths) - 1 or strength >= strengths[i + 1])]
    if not peaks:
        return None, None, None
    best = max(strengths[i] for i in peaks)
    chosen = next(i for i in peaks if strengths[i] >= best * OCTAVE_PREFERENCE)
    rate = float(rates[chosen])
    recent = grid >= grid[-1] - RECENT_SECONDS
    delayed = np.interp(grid[recent] - 60 / rate, grid, samples)
    return rate, strengths[chosen], correlation(samples[recent], delayed)


def analyze_window(times, candidate, sides, start, end):
    valid = np.isfinite(candidate.values)
    valid_times = times[valid]
    values = candidate.values[valid]
    inside = (valid_times >= start) & (valid_times < end)
    fps = int(np.count_nonzero(inside)) / WINDOW_SECONDS
    result = dict(fps=fps, gap=None, rms=None, rate=None, strength=None,
                  recent=None, half_gap=None, confident=False, reason="coverage")
    if len(valid_times) < 2 or valid_times[0] > start or valid_times[-1] < end:
        return result
    # Retain actual source brackets, not just rows clipped inside the window.
    left = max(0, int(np.searchsorted(valid_times, start, side="right")) - 1)
    right = int(np.searchsorted(valid_times, end, side="left"))
    source_times = valid_times[left:right + 1]
    source_values = values[left:right + 1]
    gaps = np.diff(source_times)
    result["gap"] = float(np.max(gaps))
    # This is an observed timing-derived grid, not an asserted camera sample rate.
    intervals = max(1, math.ceil(WINDOW_SECONDS / float(np.median(gaps))))
    grid = np.linspace(start, end, intervals + 1)
    samples = detrend(np.interp(grid, source_times, source_values), grid[1] - grid[0])
    result["rms"] = float(np.sqrt(np.mean(samples * samples)))
    rate, strength, recent = detect_beat(grid, samples)
    result.update(rate=rate, strength=strength, recent=recent)
    reasons = []
    if fps < MIN_FPS:
        reasons.append("density")
    if sides is not None:
        a = max(0, int(np.searchsorted(times, start, side="right")) - 1)
        b = int(np.searchsorted(times, end, side="left"))
        window_sides = sides[a:b + 1]
        if any(not side for side in window_sides) or len(set(window_sides)) != 1:
            reasons.append("side")
    if rate is None:
        reasons.append("no_positive_peak")
    else:
        result["half_gap"] = result["gap"] > (60 / rate) / 2
        if result["half_gap"]:
            reasons.append("half_period_gap")
        if strength < MIN_CORRELATION:
            reasons.append("correlation")
        if recent < MIN_RECENT_CORRELATION:
            reasons.append("recent")
    result["confident"] = not reasons
    result["reason"] = ",".join(reasons) if reasons else "beat"
    return result


def display(value, places=3):
    return "n/a" if value is None else f"{value:.{places}f}"


def summarize(times, candidates, unavailable, sides, count=None, seconds=None):
    duration = float(times[-1])
    windows = math.floor(duration / WINDOW_SECONDS)
    print(f"Duration: {duration:.3f} s; complete non-overlapping windows: {windows}; "
          f"omitted tail: {duration - windows * WINDOW_SECONDS:.3f} s")
    print("FPS = finite candidate rows in [start,end) / 6 s; gaps include boundary brackets.")
    print("Grid timing is measured; rates are diagnostic until confidence gates pass.")
    print("Confidence: FPS>=8, ACF>=0.35, recent>=0.5, no gap>half period; "
          "stable known side if recorded. No cross-unit amplitude floor.")
    print("Visibility columns are not gated; confirm joint identity in video.")
    for message in unavailable:
        print(f"Unavailable: {message}")
    human_rate = count / seconds * 60 if count is not None else None
    if human_rate is not None:
        print(f"Human reference: {human_rate:.2f}/min; must cover this same steady interval.")
    for candidate in candidates:
        print(f"\n{candidate.name} [RMS units: {candidate.units}]")
        print("start_s end_s fps max_gap_s rms best_rate/min acf recent gap>half_period confident reason")
        confident_rates = []
        for index in range(windows):
            start = index * WINDOW_SECONDS
            result = analyze_window(times, candidate, sides, start, start + WINDOW_SECONDS)
            half_gap = "n/a" if result["half_gap"] is None else ("yes" if result["half_gap"] else "no")
            print(f"{start:.1f} {start + WINDOW_SECONDS:.1f} {result['fps']:.2f} "
                  f"{display(result['gap'])} {display(result['rms'], 6)} "
                  f"{display(result['rate'], 1)} {display(result['strength'])} "
                  f"{display(result['recent'])} {half_gap} "
                  f"{'yes' if result['confident'] else 'no'} {result['reason']}")
            if result["confident"]:
                confident_rates.append(result["rate"])
        median = float(np.median(confident_rates)) if confident_rates else None
        share = f"{len(confident_rates) / windows:.1%}" if windows else "n/a"
        print(f"Summary {candidate.name}: confident {len(confident_rates)}/{windows} ({share}); "
              f"median confident rate {display(median, 1)}/min")
        if human_rate is not None:
            error = median - human_rate if median is not None else None
            print(f"Error (median - human): {display(error, 2)}/min; "
                  f"absolute error: {display(abs(error) if error is not None else None, 2)}/min")
    if not candidates:
        print("No computable candidates.")
    if not windows:
        print("No complete six-second windows; no rate summary is available.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--count", type=int)
    parser.add_argument("--seconds", type=float)
    args = parser.parse_args()
    if (args.count is None) != (args.seconds is None):
        parser.error("--count and --seconds must be provided together")
    if args.count is not None and (args.count < 0 or not math.isfinite(args.seconds) or args.seconds <= 0):
        parser.error("--count must be nonnegative and --seconds must be finite and positive")
    try:
        times, candidates, unavailable, sides = read_trace(args.csv)
        summarize(times, candidates, unavailable, sides, args.count, args.seconds)
    except (OSError, ValueError, csv.Error) as error:
        print(f"compare_signals.py: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
