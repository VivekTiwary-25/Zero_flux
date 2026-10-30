#!/usr/bin/env python3
"""Independent, stdlib-only landmark guard replay. Emit the complete sweep as JSON.

Example:
    python3 cpr-coach/tools/guard_study.py --data-dir PATH/TO/testdata

No app implementation, elbow_deg, visibility, side_on, or world coordinates are used.
"""

import argparse
from bisect import bisect_left, insort
from collections import Counter, deque
import csv
from dataclasses import dataclass
import itertools
import json
import math
from pathlib import Path
import statistics
import sys


ASPECT = 4.0 / 3.0
BONE_MS = 2000
OFFSET_MS = 1000
CLEAR_GAP_DEG = 10.0
JOINTS = ("shoulder", "elbow", "wrist", "hip")


class Window:
    """Timestamped vectors with exact medians; never interpolate missing frames."""

    def __init__(self, width_ms, dimensions):
        self.width_ms = width_ms
        self.entries = deque()
        self.columns = [[] for _ in range(dimensions)]

    def expire(self, now):
        while self.entries and self.entries[0][0] < now - self.width_ms:
            _, values = self.entries.popleft()
            for column, value in zip(self.columns, values):
                column.pop(bisect_left(column, value))

    def add(self, now, values):
        self.entries.append((now, values))
        for column, value in zip(self.columns, values):
            insort(column, value)

    def median(self):
        if not self.entries:
            return None
        n = len(self.entries)
        return tuple((column[(n - 1) // 2] + column[n // 2]) / 2
                     for column in self.columns)


@dataclass(frozen=True)
class Frame:
    t: int
    side: str
    points: object
    bones: object
    angle: object
    offset: object
    anatomical: object


@dataclass(frozen=True)
class Setting:
    jump: float
    bone_delta: float
    reanchor_ms: int

    @property
    def name(self):
        return "L%.2f_B%.2f-%.2f_R%d" % (
            self.jump, 1 - self.bone_delta, 1 + self.bone_delta, self.reanchor_ms)

    def describe(self):
        return {"id": self.name, "jump_torso": self.jump,
                "bone_ratio_min": 1 - self.bone_delta,
                "bone_ratio_max": 1 + self.bone_delta,
                "reanchor_ms": self.reanchor_ms}


def load_recording(path):
    frames = []
    with path.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        required = {"t_ms", "side"} | {
            joint + axis for joint in JOINTS for axis in ("_x", "_y")}
        missing = required - set(reader.fieldnames or [])
        if missing:
            raise ValueError("%s: missing columns %s" % (path, sorted(missing)))
        origin = None
        for line, row in enumerate(reader, 2):
            t = int(row["t_ms"])
            if origin is None:
                origin = t
            t -= origin
            if frames and t <= frames[-1].t:
                raise ValueError("%s:%d: timestamps must strictly increase" % (path, line))
            side = row["side"].strip()
            try:
                points = tuple((float(row[j + "_x"]) * ASPECT, float(row[j + "_y"]))
                               for j in JOINTS)
                if not all(math.isfinite(v) for p in points for v in p):
                    raise ValueError("nonfinite point")
                shoulder, elbow, wrist, hip = points
                torso = math.dist(shoulder, hip)
                upper = math.dist(shoulder, elbow)
                fore = math.dist(elbow, wrist)
                if min(torso, upper, fore) <= 0 or not side:
                    raise ValueError("degenerate geometry or missing side")
                ux, uy = shoulder[0] - elbow[0], shoulder[1] - elbow[1]
                vx, vy = wrist[0] - elbow[0], wrist[1] - elbow[1]
                angle = math.degrees(math.atan2(abs(ux * vy - uy * vx), ux * vx + uy * vy))
                direction = 1 if wrist[0] >= hip[0] else -1
                offset = (wrist[0] - shoulder[0]) * direction / (upper + fore)
                anatomical = []
                if (wrist[0] - shoulder[0]) * (hip[0] - shoulder[0]) > 0:
                    anatomical.append("behind_shoulder")
                if wrist[1] < shoulder[1]:
                    anatomical.append("above_shoulder")
                relative = tuple(((x - hip[0]) / torso, (y - hip[1]) / torso)
                                 for x, y in points[:3])
                frame = Frame(t, side, relative, (upper / torso, fore / torso),
                              angle, offset, tuple(anatomical))
            except (ValueError, TypeError, OverflowError):
                frame = Frame(t, side, None, None, None, None, ("invalid_pose",))
            frames.append(frame)
    if len(frames) < 2:
        raise ValueError("%s: need at least two frames" % path)
    return frames


def load_labels(path):
    with path.open(newline="", encoding="utf-8-sig") as stream:
        rows = csv.DictReader(line for line in stream if line.strip() and not line.lstrip().startswith("#"))
        labels = []
        for row in rows:
            start, end = float(row["start_s"]), float(row["end_s"])
            if not math.isfinite(start) or not math.isfinite(end) or end < start:
                raise ValueError("invalid label interval")
            labels.append({"file": row["file"], "start_s": start, "end_s": end,
                           "label": row["label"], "source": row["source"]})
        return labels


def compatible_bones(bones, baseline, delta):
    return all((1 - delta) * b <= v <= (1 + delta) * b
               for v, b in zip(bones, baseline))


def horizontal(frame):
    return frame.points[1][0], frame.points[2][0]  # elbow, wrist, hip-relative / torso


def replay(frames, setting):
    last = None
    side = None
    history = Window(BONE_MS, 2)
    offsets = Window(OFFSET_MS, 1)
    candidate = []
    accepted, rolling = [], []
    reasons = Counter()
    reanchors = side_resets = 0
    for frame in frames:
        if frame.side != side:
            if side is not None:
                side_resets += 1
            side = frame.side
            last = None
            history = Window(BONE_MS, 2)
            offsets = Window(OFFSET_MS, 1)
            candidate = []
        history.expire(frame.t)
        offsets.expire(frame.t)
        why = list(frame.anatomical)
        if not why and last is not None:
            if any(abs(a - b) > setting.jump for a, b in zip(horizontal(frame), horizontal(last))):
                why.append("horizontal_jump")
            baseline = history.median()
            if baseline is not None and not compatible_bones(frame.bones, baseline, setting.bone_delta):
                why.append("bone_ratio")
        ok = not why
        if not ok:
            if frame.anatomical:
                candidate = []  # Missing/anatomically impossible poses cannot re-anchor.
            else:
                # Every pair must agree, not only adjacent rejected samples. This
                # prevents slow drift or alternating clusters from bridging L.
                agrees = candidate and frame.t - candidate[-1].t <= setting.reanchor_ms
                if agrees:
                    h = horizontal(frame)
                    for prior in candidate:
                        if (any(abs(a - b) > setting.jump for a, b in zip(h, horizontal(prior)))
                                or not compatible_bones(frame.bones, prior.bones, setting.bone_delta)
                                or not compatible_bones(prior.bones, frame.bones, setting.bone_delta)):
                            agrees = False
                            break
                if not agrees:
                    candidate = []
                candidate.append(frame)
                if frame.t - candidate[0].t >= setting.reanchor_ms:
                    ok = True
                    reanchors += 1
                    history = Window(BONE_MS, 2)
        if ok:
            last = frame
            history.add(frame.t, frame.bones)
            offsets.add(frame.t, (frame.offset,))
            candidate = []
        else:
            reasons.update(why)
        accepted.append(ok)
        value = offsets.median()
        rolling.append(value[0] if value is not None else None)
    return {"accepted": accepted, "rolling": rolling, "reasons": dict(reasons),
            "reanchors": reanchors, "side_resets": side_resets}


def quantile(sorted_values, fraction):
    position = (len(sorted_values) - 1) * fraction
    lo, hi = math.floor(position), math.ceil(position)
    return sorted_values[lo] + (sorted_values[hi] - sorted_values[lo]) * (position - lo)


def distribution(values):
    values = sorted(v for v in values if v is not None)
    if not values:
        return {"median": None, "p10": None, "p90": None, "min": None, "max": None}
    return {"median": statistics.median(values), "p10": quantile(values, 0.1),
            "p90": quantile(values, 0.9), "min": values[0], "max": values[-1]}


def raw_rolling(frames):
    window = Window(OFFSET_MS, 1)
    side = None
    values = []
    for frame in frames:
        if frame.side != side:
            window = Window(OFFSET_MS, 1)
            side = frame.side
        window.expire(frame.t)
        if frame.offset is not None:
            window.add(frame.t, (frame.offset,))
        value = window.median()
        values.append(value[0] if value is not None else None)
    return values


def longest_run(frames, accepted):
    """A rejected sample owns [its t, next t); last sample owns a median step."""
    step = statistics.median(b.t - a.t for a, b in zip(frames, frames[1:]))
    longest = 0
    start = None
    for i, (frame, ok) in enumerate(zip(frames, accepted)):
        if ok:
            start = None
        else:
            if start is None:
                start = frame.t
            end = frames[i + 1].t if i + 1 < len(frames) else frame.t + step
            longest = max(longest, end - start)
    return longest


def summarize(frames, result, labels, raw_offsets):
    mask = result["accepted"]
    available = [frame.points is not None for frame in frames]
    report = {"frames": len(frames), "rejected_frames": mask.count(False),
              "rejected_share": mask.count(False) / len(frames),
              "longest_rejected_run_ms": longest_run(frames, mask),
              "unavailable_frames": available.count(False),
              "longest_unavailable_run_ms": longest_run(frames, available),
              "anatomical_or_unavailable_run_lower_bound_ms": longest_run(
                  frames, [not frame.anatomical for frame in frames]),
              "rejection_reasons": result["reasons"], "reanchors": result["reanchors"],
              "side_resets": result["side_resets"], "segments": []}
    for label in labels:
        indices = [i for i, f in enumerate(frames) if label["start_s"] * 1000 <= f.t <= label["end_s"] * 1000]
        kept = [i for i in indices if mask[i]]
        segment = dict(label)
        segment.update({"frames": len(indices), "accepted_frames": len(kept),
                        "rejected_share": 1 - len(kept) / len(indices) if indices else None,
                        "accepted_elbow_deg": distribution(frames[i].angle for i in kept),
                        "raw_elbow_deg": distribution(frames[i].angle for i in indices),
                        "accepted_offset_rolling_1s": distribution(result["rolling"][i] for i in kept),
                        "raw_offset_rolling_1s": distribution(raw_offsets[i] for i in indices)})
        report["segments"].append(segment)
    return report


def evaluate(recordings):
    straight = [s for r in recordings for s in r["segments"]
                if s["label"] == "straight" and s["source"] in ("pictures", "human")]
    r15 = recordings[14]
    straight15 = [s["accepted_elbow_deg"]["median"] for s in r15["segments"] if s["label"] == "straight"]
    bent15 = [s["accepted_elbow_deg"]["median"] for s in r15["segments"] if s["label"] == "bent"]
    gap = None
    if straight15 and bent15 and all(x is not None for x in straight15 + bent15):
        gap = min(straight15) - max(bent15)
    max_straight = max((s["rejected_share"] if s["rejected_share"] is not None else 1) for s in straight)
    longest = max(r["longest_rejected_run_ms"] for r in recordings)
    passes_straight = max_straight < 0.05
    passes_runs = longest < 450
    passes_gap = gap is not None and gap >= CLEAR_GAP_DEG
    return {"max_straight_rejected_share": max_straight,
            "max_rejected_run_ms": longest, "bent_straight_gap_deg": gap,
            "passes_straight": passes_straight, "passes_runs": passes_runs,
            "passes_angle_gap": passes_gap,
            "meets_all_targets": passes_straight and passes_runs and passes_gap}


def rank(result):
    # A numerical definition of "clearly below" is declared before the sweep.
    score = result["targets"]
    violations = sum(not score[k] for k in ("passes_straight", "passes_runs", "passes_angle_gap"))
    gap = score["bent_straight_gap_deg"]
    excess = (max(0, score["max_straight_rejected_share"] / 0.05 - 1)
              + max(0, score["max_rejected_run_ms"] / 450 - 1)
              + (max(0, (CLEAR_GAP_DEG - gap) / CLEAR_GAP_DEG) if gap is not None else 1000))
    return (violations, excess, -(gap if gap is not None else -1000),
            sum(r["rejected_frames"] for r in result["recordings"]), result["setting"]["id"])


def run(data_dir, reference):
    labels = load_labels(data_dir / "labels.csv")
    data = []
    for number in range(1, 16):
        name = "real-cph2467-%d.csv" % number
        frames = load_recording(data_dir / name)
        data.append((frames, [label for label in labels if label["file"] == name], raw_rolling(frames)))
    settings = [Setting(jump / 100, delta / 100, reanchor)
                for jump, delta, reanchor in itertools.product(
                    range(10, 36, 5), (10, 15, 25, 35), range(250, 501, 50))]
    results = []
    for setting in settings:
        reports = []
        for number, (frames, segments, offsets) in enumerate(data, 1):
            report = summarize(frames, replay(frames, setting), segments, offsets)
            report["recording"] = number
            reports.append(report)
        results.append({"setting": setting.describe(), "targets": evaluate(reports), "recordings": reports})
    results.sort(key=rank)
    best = results[0]
    best_setting = next(s for s in settings if s.name == best["setting"]["id"])
    frames, segments, offsets = data[14]
    result15 = replay(frames, best_setting)
    series = []
    for label in segments:
        series.append({"segment": label, "samples": [
            {"t_s": frame.t / 1000, "accepted": result15["accepted"][i],
             "raw_offset_rolling_1s": offsets[i],
             "accepted_offset_rolling_1s": result15["rolling"][i]}
            for i, frame in enumerate(frames)
            if label["start_s"] * 1000 <= frame.t <= label["end_s"] * 1000]})
    return {"reference": reference, "data_dir": str(data_dir), "settings_count": len(settings),
            "clear_gap_deg": CLEAR_GAP_DEG, "passing_settings": sum(r["targets"]["meets_all_targets"] for r in results),
            "best_three_ids": [r["setting"]["id"] for r in results[:3]],
            "results": results, "best_recording15_rolling_series": series}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data-dir", required=True, type=Path)
    parser.add_argument("--reference", default="unspecified", help="extracted git revision, for provenance")
    args = parser.parse_args()
    try:
        report = run(args.data_dir, args.reference)
    except (OSError, ValueError, KeyError) as error:
        parser.exit(2, "guard_study: %s\n" % error)
    json.dump(report, sys.stdout, indent=2, allow_nan=False)
    print()


if __name__ == "__main__":
    main()
