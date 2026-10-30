#!/usr/bin/env python3
"""Summarize CPR Coach session and lab CSV traces."""

import argparse
import csv
import math
import sys
from pathlib import Path

import numpy as np

SAMPLE_HZ = 30
WINDOW_SECONDS = 6
MOVING_AVERAGE_SECONDS = 1
MIN_RATE = 60
MAX_RATE = 170
OCTAVE_PREFERENCE = 0.6


def number(row, key):
    value = row.get(key, "")
    if value is None or not value.strip():
        return math.nan
    try:
        result = float(value)
    except ValueError:
        return math.nan
    return result if math.isfinite(result) else math.nan


def read_trace(path):
    with path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        fields = set(reader.fieldnames or ())
        session = {"t_ms", "wrist_signal", "elbow_deg", "min_vis", "latency_ms"}
        lab = {"t_ms", "side", "sh_v", "el_v", "wr_v", "hip_v", "elbow_deg", "wrist_signal", "latency_ms"}
        if session <= fields:
            lab_format = False
        elif lab <= fields:
            lab_format = True
        else:
            raise ValueError("CSV header is neither a session trace nor a lab trace")

        rows = []
        for row in reader:
            time_ms = number(row, "t_ms")
            if not math.isfinite(time_ms):
                continue
            if lab_format:
                visibility = [number(row, name) for name in ("sh_v", "el_v", "wr_v", "hip_v")]
                min_vis = min(visibility) if all(math.isfinite(v) for v in visibility) else math.nan
            else:
                min_vis = number(row, "min_vis")
            rows.append((time_ms, number(row, "wrist_signal"), number(row, "elbow_deg"), min_vis, number(row, "latency_ms")))
    if not rows:
        raise ValueError("CSV contains no timestamped frames")
    rows.sort(key=lambda item: item[0])
    return np.asarray(rows, dtype=float)


def beat_rate(times_ms, signal):
    """Return a rate from one complete six-second, evenly resampled signal window."""
    start = times_ms[0]
    sample_times = start + np.arange(WINDOW_SECONDS * SAMPLE_HZ + 1) * (1000 / SAMPLE_HZ)
    if sample_times[-1] > times_ms[-1]:
        return None
    # Do not bridge tracking gaps: every resampled point must be bracketed by valid signal values.
    valid = np.isfinite(signal)
    valid_times = times_ms[valid]
    valid_signal = signal[valid]
    if len(valid_times) < 2 or sample_times[0] < valid_times[0] or sample_times[-1] > valid_times[-1]:
        return None
    indices = np.searchsorted(valid_times, sample_times, side="right")
    right = np.minimum(indices, len(valid_times) - 1)
    left = np.maximum(indices - 1, 0)
    # Empty-signal rows create gaps. A window is usable only where adjacent source samples are close enough
    # to bracket the resampled samples; longer intervals are left unavailable rather than fabricated.
    if np.any(valid_times[right] - valid_times[left] > (1000 / SAMPLE_HZ) * 2):
        return None
    samples = np.interp(sample_times, valid_times, valid_signal)

    half = SAMPLE_HZ // 2
    prefix = np.concatenate(([0.0], np.cumsum(samples)))
    detrended = np.empty_like(samples)
    for i in range(len(samples)):
        a = max(0, i - half)
        b = min(len(samples), i + half + 1)
        detrended[i] = samples[i] - (prefix[b] - prefix[a]) / (b - a)

    minimum_lag = math.ceil(SAMPLE_HZ * 60 / MAX_RATE)
    maximum_lag = math.floor(SAMPLE_HZ * 60 / MIN_RATE)
    correlations = np.zeros(maximum_lag + 1)
    for lag in range(minimum_lag, maximum_lag + 1):
        first = detrended[:-lag]
        second = detrended[lag:]
        denominator = math.sqrt(float(np.dot(first, first) * np.dot(second, second)))
        if denominator > 1e-9:
            correlations[lag] = float(np.dot(first, second)) / denominator
    peaks = [lag for lag in range(minimum_lag, maximum_lag) if correlations[lag] > correlations[lag - 1] and correlations[lag] >= correlations[lag + 1]]
    if not peaks:
        return None
    best = max(correlations[lag] for lag in peaks)
    lag = next((candidate for candidate in peaks if correlations[candidate] >= best * OCTAVE_PREFERENCE), None)
    if lag is None:
        return None
    a, b, c = correlations[lag - 1:lag + 2]
    denominator = a - 2 * b + c
    refined_lag = lag if abs(denominator) < 1e-9 else lag + 0.5 * (a - c) / denominator
    return SAMPLE_HZ * 60 / refined_lag if refined_lag > 0 else None


def summarize(data, count=None, seconds=None):
    times = data[:, 0]
    duration = (times[-1] - times[0]) / 1000
    intervals = np.diff(times)
    fps = 1000 / intervals[intervals > 0] if np.any(intervals > 0) else np.array([])
    latency = data[:, 4][np.isfinite(data[:, 4])]
    visibility = data[:, 3][np.isfinite(data[:, 3])]
    elbow = data[:, 2][np.isfinite(data[:, 2])]
    rates = []
    # One-second strides cover each possible completed six-second section without counting overlapping frames as observations.
    for offset in range(0, max(0, int(duration - WINDOW_SECONDS) + 1)):
        left = times[0] + offset * 1000
        right = left + WINDOW_SECONDS * 1000
        mask = (times >= left) & (times <= right)
        window = data[mask]
        rate = beat_rate(window[:, 0], window[:, 1]) if len(window) else None
        if rate is not None:
            rates.append(rate)

    print(f"Duration: {duration:.2f} s")
    print(f"FPS: median {np.median(fps):.2f}; 5th percentile {np.percentile(fps, 5):.2f}" if len(fps) else "FPS: unavailable")
    print(f"Latency: median {np.median(latency):.2f} ms; 95th percentile {np.percentile(latency, 95):.2f} ms" if len(latency) else "Latency: unavailable")
    print(f"Frames with min visibility >= 0.5: {np.mean(visibility >= 0.5) * 100:.2f}%" if len(visibility) else "Frames with min visibility >= 0.5: unavailable")
    print(f"Elbow angle: median {np.median(elbow):.2f} deg; range {np.min(elbow):.2f}–{np.max(elbow):.2f} deg" if len(elbow) else "Elbow angle: unavailable")
    print(f"Compression rate: {np.median(rates):.2f}/min" if rates else "Compression rate: no usable beat")
    if count is not None and seconds is not None:
        human_rate = count / seconds * 60
        if rates:
            difference = float(np.median(rates)) - human_rate
            print(f"Human-counted rate: {human_rate:.2f}/min")
            print(f"Difference (app - human): {difference:+.2f}/min")
        else:
            print(f"Human-counted rate: {human_rate:.2f}/min")
            print("Difference (app - human): unavailable (no usable beat)")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--count", type=int)
    parser.add_argument("--seconds", type=float)
    args = parser.parse_args()
    if (args.count is None) != (args.seconds is None):
        parser.error("--count and --seconds must be provided together")
    if args.count is not None and (args.count < 0 or args.seconds <= 0):
        parser.error("--count must be nonnegative and --seconds must be positive")
    try:
        summarize(read_trace(args.csv), args.count, args.seconds)
    except (OSError, ValueError) as error:
        print(f"analyze_trace.py: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
