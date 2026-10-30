#!/usr/bin/env python3
"""Write synthetic CPR replay input, NOT measured accuracy, latency, or human performance.

Example (synthetic target rate, not a measurement):
    python3 tools/make_test_trace.py synthetic-replay.csv --rate 110
    adb shell mkdir -p /sdcard/Android/data/com.zeroflux.cprcoach/files/traces
    adb push synthetic-replay.csv /sdcard/Android/data/com.zeroflux.cprcoach/files/traces/
    adb shell am start -S -n com.zeroflux.cprcoach/.MainActivity --es replay synthetic-replay.csv

Use a debug APK, open Practise, skip the walkthrough if needed, wait for Ready,
then tap Start. Countdown, Live, pause/stop, and Scorecard use the real session.
The replay label remains on the scorecard; none of its values are measurements.
The default trace leaves time for navigation, a countdown and the full session.
Replay begins on the framing screen, runs once, and restarts on Swap roles.
EOF loses tracking; the normal session timer still finishes. Invalid input keeps
camera capture disabled. Release APKs ignore the extra and contain no reader.
Remove the extra by starting the activity again with -S and without --es replay.

CSV pose fields are empty during the synthetic out-of-frame stretch. The pause
holds the wrist still while keeping the synthetic arm visible. Latency is always
empty: this tool has not measured inference latency. Do not use generated files
as input to accuracy/latency claims or measurement reports.
"""

import argparse
import csv
import math
from pathlib import Path


HEADER = ("t_ms", "wrist_signal", "elbow_deg", "min_vis", "latency_ms")


def positive_number(value):
    number = float(value)
    if not math.isfinite(number) or number <= 0:
        raise argparse.ArgumentTypeError("must be finite and positive")
    return number


def nonnegative_number(value):
    number = float(value)
    if not math.isfinite(number) or number < 0:
        raise argparse.ArgumentTypeError("must be finite and nonnegative")
    return number


def write_trace(args):
    with args.output.open("w", newline="", encoding="utf-8") as output:
        writer = csv.writer(output)
        writer.writerow(HEADER)
        for frame in range(math.ceil(args.seconds * args.fps) + 1):
            time_ms = round(frame * 1000 / args.fps)
            if time_ms > args.seconds * 1000:
                break
            seconds = time_ms / 1000
            if args.out_start <= seconds < args.out_start + args.out_seconds:
                writer.writerow((time_ms, "", "", "", ""))
                continue
            # All pose values below are synthetic fixtures, never camera output.
            signal_time = args.pause_start if args.pause_start <= seconds < args.pause_start + args.pause_seconds else seconds
            wrist = 0.5 + 0.1 * math.sin(math.tau * args.rate * signal_time / 60)
            writer.writerow((time_ms, f"{wrist:.8f}", "175", "1", ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("output", type=Path, help="output filename must start with synthetic- and end in .csv")
    parser.add_argument("--rate", required=True, type=positive_number, help="synthetic compressions per minute, NOT measured")
    parser.add_argument("--fps", type=positive_number, default=30, help="synthetic input frames per second (default: 30)")
    parser.add_argument("--seconds", type=positive_number, default=150, help="synthetic duration (default: 150)")
    parser.add_argument("--out-start", type=nonnegative_number, default=30, help="out-of-frame start in seconds (default: 30)")
    parser.add_argument("--out-seconds", type=positive_number, default=6, help="out-of-frame duration (default: 6)")
    parser.add_argument("--pause-start", type=nonnegative_number, default=65, help="visible pause start in seconds (default: 65)")
    parser.add_argument("--pause-seconds", type=positive_number, default=10, help="visible pause duration (default: 10)")
    args = parser.parse_args()
    if not args.output.name.startswith("synthetic-") or args.output.suffix != ".csv":
        parser.error("output must be named synthetic-*.csv; generated values are NOT measurements")
    if args.fps > 1000:
        parser.error("--fps must not exceed timestamp resolution (1000 frames/s)")
    if args.rate / 60 >= args.fps / 2:
        parser.error("--fps must exceed twice the synthetic beat frequency")
    out_end = args.out_start + args.out_seconds
    pause_end = args.pause_start + args.pause_seconds
    if out_end > args.seconds or pause_end > args.seconds:
        parser.error("out-of-frame and pause stretches must fit within --seconds")
    if max(args.out_start, args.pause_start) < min(out_end, pause_end):
        parser.error("out-of-frame and visible pause stretches must not overlap")
    if round(out_end * 1000) <= round(args.out_start * 1000) or round(pause_end * 1000) <= round(args.pause_start * 1000):
        parser.error("stretches must exceed timestamp resolution")
    try:
        write_trace(args)
    except OSError as error:
        parser.exit(2, f"make_test_trace.py: {error}\n")
    print(f"Wrote SYNTHETIC replay: {args.output} (not measured data; latency unavailable)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
