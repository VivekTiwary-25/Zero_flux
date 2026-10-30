"""Benchmark the MediaPipe pose model sizes on a phone (debug build only).

For each of lite, full, heavy: launch the app with `--es pose_model <name>`, give it 40 s on the
framing screen, pull the CPR_POSE stats lines from logcat and print one table row per model:
results per second, latency median and p90, thermal status.

Lite and heavy are read by the debug build from
/sdcard/Android/data/com.zeroflux.cprcoach/files/models/. This script downloads them into
tools/pose_models/ (next to this file) when missing and pushes them with adb. Full ships in assets.

The app has no deep link to the framing screen, so for each model either pass --tap X,Y (screen
pixels of the Practise button on the home screen) or let the script ask you to open it by hand.
Keep the same person in frame for every model, or the numbers are not comparable.

    python tools/bench_pose.py --serial R58M123ABC --tap 540,1400
    python tools/bench_pose.py --download-only
"""
from __future__ import annotations

import argparse
import re
import statistics
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

PACKAGE = "com.zeroflux.cprcoach"
DEVICE_DIR = f"/sdcard/Android/data/{PACKAGE}/files/models"
HERE = Path(__file__).resolve().parent
MODEL_DIR = HERE / "pose_models"
BASE = "https://storage.googleapis.com/mediapipe-models/pose_landmarker"
URLS = {
    "lite": f"{BASE}/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task",
    "heavy": f"{BASE}/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task",
}
MODELS = ("lite", "full", "heavy")
STAT = re.compile(r"(\w+)=(\S+)")


def download(name: str) -> Path:
    target = MODEL_DIR / f"pose_landmarker_{name}.task"
    if target.is_file() and target.stat().st_size > 1_000_000:
        return target
    MODEL_DIR.mkdir(parents=True, exist_ok=True)
    print(f"downloading {name} from {URLS[name]}")
    partial = target.with_suffix(".part")
    with urllib.request.urlopen(URLS[name], timeout=60) as response, open(partial, "wb") as out:
        while chunk := response.read(1 << 20):
            out.write(chunk)
    head = partial.read_bytes()[:8]
    if partial.stat().st_size < 1_000_000 or b"PK" not in head:  # .task files are zip archives, sometimes with padding first
        partial.unlink()
        raise SystemExit(f"{name}: download is not a model file (size or format wrong)")
    partial.replace(target)
    return target


def adb(serial: str | None, *args: str, check: bool = True) -> str:
    cmd = ["adb"] + (["-s", serial] if serial else []) + list(args)
    done = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if check and done.returncode != 0:
        raise SystemExit(f"adb failed: {' '.join(args)}\n{done.stdout}{done.stderr}")
    return done.stdout


def parse_stats(log: str, model: str) -> list[dict[str, float]]:
    rows = []
    for line in log.splitlines():
        if "pose delegate=" not in line:
            continue
        fields = dict(STAT.findall(line))
        if fields.get("model") != model:
            continue
        try:
            rows.append({
                "rps": float(fields["results_per_s"]),
                "med": float(fields["latency_median_ms"]),
                "p90": float(fields["latency_p90_ms"]),
                "thermal": float(fields["thermal_status"]),
                "gap": float(fields["frame_gap_ms"]),
            })
        except (KeyError, ValueError):
            continue
    return rows


def summarise(rows: list[dict[str, float]]) -> dict[str, float] | None:
    if not rows:
        return None
    return {
        "rps": statistics.fmean(r["rps"] for r in rows),
        "med": statistics.median(r["med"] for r in rows),
        "p90": statistics.median(r["p90"] for r in rows),
        "thermal": max(r["thermal"] for r in rows),
        "gap": max(r["gap"] for r in rows),
        "n": len(rows),
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", help="adb serial; required unless exactly one phone is attached")
    ap.add_argument("--seconds", type=int, default=40, help="time on the framing screen per model")
    ap.add_argument("--tap", help="X,Y screen tap that opens the framing screen from the home screen")
    ap.add_argument("--models", nargs="+", choices=MODELS, default=list(MODELS))
    ap.add_argument("--download-only", action="store_true")
    args = ap.parse_args()

    for name in URLS:
        if name in args.models:
            download(name)
    if args.download_only:
        return 0

    adb(args.serial, "get-state")
    adb(args.serial, "shell", "mkdir", "-p", DEVICE_DIR)
    for name in URLS:
        if name in args.models:
            path = MODEL_DIR / f"pose_landmarker_{name}.task"
            print(f"pushing {path.name}")
            adb(args.serial, "push", str(path), f"{DEVICE_DIR}/{path.name}")

    results: dict[str, dict[str, float] | None] = {}
    for name in args.models:
        adb(args.serial, "shell", "am", "force-stop", PACKAGE)
        adb(args.serial, "logcat", "-c")
        adb(args.serial, "shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity", "--es", "pose_model", name)
        time.sleep(3)
        if args.tap:
            adb(args.serial, "shell", "input", "tap", *args.tap.split(","))
        else:
            input(f"[{name}] open the framing screen with the person in frame, then press Enter... ")
        print(f"[{name}] measuring for {args.seconds}s")
        time.sleep(args.seconds)
        log = adb(args.serial, "logcat", "-d", "-s", "CPR_POSE:I")
        rows = parse_stats(log, name)
        results[name] = summarise(rows)
        if results[name] is None:
            print(f"[{name}] no stats lines with model={name}; check the model file and the screen")

    adb(args.serial, "shell", "am", "force-stop", PACKAGE)
    print()
    print(f"{'model':<7}{'results/s':>10}{'lat med ms':>12}{'lat p90 ms':>12}{'thermal':>9}{'max gap':>9}{'lines':>7}")
    for name, r in results.items():
        if r is None:
            print(f"{name:<7}{'no data':>10}")
        else:
            print(f"{name:<7}{r['rps']:>10.1f}{r['med']:>12.0f}{r['p90']:>12.0f}{r['thermal']:>9.0f}{r['gap']:>9.0f}{r['n']:>7.0f}")
    print("\nA model that fell back to full logs 'pose_model=... missing' in logcat; its row would then be absent.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
