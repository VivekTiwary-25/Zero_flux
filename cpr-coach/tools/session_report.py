#!/usr/bin/env python3
"""Summarize timing, pose availability, side, and device metrics in a session trace."""

import argparse
import csv
import math
import re
import sys
from collections import Counter
from pathlib import Path

import numpy as np

POSE_COLUMNS = (
    "shoulder_x", "shoulder_y", "elbow_x", "elbow_y",
    "wrist_x", "wrist_y", "hip_x", "hip_y",
)
LOG_FIELDS = ("camera_frames_per_s", "analysis_size", "results_per_s")


def number(row, key):
    value = row.get(key, "")
    if value is None or not str(value).strip():
        return math.nan
    try:
        result = float(value)
    except (TypeError, ValueError):
        return math.nan
    return result if math.isfinite(result) else math.nan


def read_trace(path):
    rows = []
    with path.open(newline="", encoding="utf-8-sig") as source:
        reader = csv.DictReader(source)
        required = {"t_ms", "latency_ms"}
        if not reader.fieldnames or not required.issubset(reader.fieldnames):
            raise ValueError("CSV must include t_ms and latency_ms columns")
        for row in reader:
            t_ms = number(row, "t_ms")
            if math.isfinite(t_ms):
                row["_t_ms"] = t_ms
                rows.append(row)
    rows.sort(key=lambda row: row["_t_ms"])
    return rows


def percentile(values, q):
    values = np.asarray([value for value in values if math.isfinite(value)], dtype=float)
    return float(np.percentile(values, q)) if values.size else math.nan


def display(value, suffix=""):
    return f"{value:.2f}{suffix}" if math.isfinite(value) else "unavailable"


def has_pose(row):
    # New traces carry explicit landmarks; older traces only carry min_vis.
    if any(str(row.get(key, "") or "").strip() for key in POSE_COLUMNS):
        return all(math.isfinite(number(row, key)) for key in POSE_COLUMNS)
    visibility = number(row, "min_vis")
    return math.isfinite(visibility) and visibility > 0


def print_runs(rows, field, label, formatter):
    run_start = None
    run_value = None
    run_last = None
    for row in rows:
        value = row.get(field, "")
        value = value.strip() if value is not None else ""
        normalized = value if value else "unavailable"
        if run_start is None:
            run_start, run_value = row["_t_ms"], normalized
        elif normalized != run_value:
            print(f"  {label}: {formatter(run_value)} from {run_start:.0f} to {run_last:.0f} ms")
            run_start, run_value = row["_t_ms"], normalized
        run_last = row["_t_ms"]
    if run_start is not None:
        print(f"  {label}: {formatter(run_value)} from {run_start:.0f} to {run_last:.0f} ms")


def side_metrics(rows):
    sides = [str(row.get("side", "") or "").strip().lower() for row in rows]
    sides = [side for side in sides if side]
    counts = Counter(sides)
    print("Side share: " + (", ".join(f"{side} {count / len(sides) * 100:.1f}%" for side, count in sorted(counts.items())) if sides else "unavailable"))
    switches = sum(left != right for left, right in zip(sides, sides[1:]))
    print(f"Side switches: {switches if sides else 'unavailable'}")


def read_logcat(path):
    values = {key: [] for key in LOG_FIELDS if key != "analysis_size"}
    sizes = []
    numeric = re.compile(r"\b(camera_frames_per_s|results_per_s)=([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)")
    size_pattern = re.compile(r"\banalysis_size=(\d+)x(\d+)")
    with path.open(encoding="utf-8", errors="replace") as source:
        for line in source:
            if "CPR_POSE" not in line:
                continue
            for key, raw in numeric.findall(line):
                value = float(raw)
                if math.isfinite(value):
                    values[key].append(value)
            size_match = size_pattern.search(line)
            if size_match:
                sizes.append((float(size_match.group(1)), float(size_match.group(2))))
    return values, sizes


def report_logcat(path):
    values, sizes = read_logcat(path)
    medians = {key: percentile(items, 50) for key, items in values.items()}
    size = (
        f"{percentile([item[0] for item in sizes], 50):.0f}x{percentile([item[1] for item in sizes], 50):.0f}"
        if sizes else "unavailable"
    )
    print(f"CPR_POSE medians: camera_frames_per_s={display(medians['camera_frames_per_s'])}, analysis_size={size}, results_per_s={display(medians['results_per_s'])}")
    camera, results = medians["camera_frames_per_s"], medians["results_per_s"]
    if math.isfinite(camera) and math.isfinite(results) and camera > 0 and results > 0:
        if results < camera:
            print("Frame limit: processing (results/s is below camera frames/s).")
        elif results == camera:
            print("Frame limit: camera supply (results/s matches camera frames/s).")


def summarize(rows, logcat=None):
    if not rows:
        print("No timestamped frames.")
        return
    start, end = rows[0]["_t_ms"], rows[-1]["_t_ms"]
    duration_s = max(0.0, (end - start) / 1000)
    print(f"Duration: {display(duration_s, ' s')}")
    all_count = len(rows)
    pose_rows = [row for row in rows if has_pose(row)]
    divisor = duration_s if duration_s > 0 else math.nan
    print(f"Frame rate: all rows {display(all_count / divisor, ' fps')}; rows with pose {display(len(pose_rows) / divisor, ' fps')}")
    print("Frames per 10 seconds (all rows / with pose):")
    if duration_s == 0:
        print("  unavailable (zero-duration trace)")
    else:
        window_count = max(1, math.ceil(duration_s / 10))
        for window in range(window_count):
            left = start + window * 10000
            right = min(end, left + 10000)
            span = (right - left) / 1000
            in_window = [row for row in rows if left <= row["_t_ms"] < right or (window == window_count - 1 and row["_t_ms"] == end)]
            count_pose = sum(has_pose(row) for row in in_window)
            denominator = span if span > 0 else math.nan
            print(f"  {left:.0f}-{right:.0f} ms: {display(len(in_window) / denominator, ' fps')} / {display(count_pose / denominator, ' fps')}")

    gaps = [number(row, "frame_gap_ms") for row in rows]
    gaps = [value for value in gaps if math.isfinite(value)]
    print("Frame gaps (ms): " + (f"median {display(percentile(gaps, 50))}; p95 {display(percentile(gaps, 95))}; worst {display(max(gaps))}; over 275 ms {sum(value > 275 for value in gaps) / len(gaps) * 100:.1f}%" if gaps else "unavailable"))
    latencies = [number(row, "latency_ms") for row in rows]
    print(f"Latency (ms): median {display(percentile(latencies, 50))}; p90 {display(percentile(latencies, 90))}")
    print("Frame-gap changes:")
    print_runs(rows, "frame_gap_ms", "frame_gap_ms", lambda value: value)
    print("Thermal status changes:")
    print_runs(rows, "thermal_status", "thermal_status", lambda value: value)
    print("Battery temperature changes:")
    print_runs(rows, "battery_temp_c", "battery_temp_c", lambda value: value)
    side_metrics(rows)
    if logcat is not None:
        report_logcat(logcat)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trace", type=Path)
    parser.add_argument("--logcat", type=Path)
    args = parser.parse_args(argv)
    try:
        summarize(read_trace(args.trace), args.logcat)
    except (OSError, ValueError, csv.Error) as error:
        print(f"session_report: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
