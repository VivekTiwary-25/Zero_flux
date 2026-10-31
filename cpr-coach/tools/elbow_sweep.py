#!/usr/bin/env python3
"""Replay LandmarkGuard-filtered elbow angles and sweep the bent-read timing.

Run from the cpr-coach directory: python tools/elbow_sweep.py
"""
import argparse
import csv
import math
import statistics
import os
from collections import deque
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = Path(os.environ.get("CPR_DATA_DIR", ROOT / "tools/testdata"))

# Values copied from LandmarkGuard and PostureJudge at adf9409.
GUARD = dict(min_vis=.5, min_torso=.05, max_gap=1000, jump_x=.25,
             jump_x_per_s=.05, jump_x_max=.4, jump_dt_cap=300,
             elbow_share=.7, jump_y=.6, drift_x=.3, reference_ms=1500,
             min_reference=5, bone_min=.6, bone_max=1.5, facing_gap=.3,
             behind_wrist=.2, behind_elbow=.5, above_shoulder=.15,
             reanchor_ms=350, reanchor_min=4, reanchor_spread=.3,
             force_ms=400, majority_ms=2000, majority_ratio=3)


def med(vals):
    return statistics.median(vals)


def load_frames(path):
    with path.open(newline="", encoding="utf-8-sig") as f:
        rows = list(csv.DictReader(row for row in f if row.strip() and not row.lstrip().startswith("#")))
    start = int(rows[0]["t_ms"])
    return [{**r, "t": int(r["t_ms"]) - start} for r in rows]


class LandmarkGuard:
    """Port of LandmarkGuard.check; rejected frames never enter the judge."""
    def __init__(self):
        self.reset()

    def reset(self):
        self.ref = None
        self.slow = deque()
        self.bones = deque()
        self.rejected = []
        self.pool = []
        self.accepted = deque()
        self.facing = 0
        self.near_right = None

    def check(self, r):
        num = lambda key: float(r[key])
        required = ("shoulder_vis", "elbow_vis", "wrist_vis", "hip_vis", "shoulder_x", "shoulder_y",
                    "elbow_x", "elbow_y", "wrist_x", "wrist_y", "hip_x", "hip_y")
        if any(not r[key] for key in required):
            return True
        aspect = 1.0  # traces contain normalized image coordinates; replay uses aspect 1.0.
        if min(num("shoulder_vis"), num("elbow_vis"), num("wrist_vis"), num("hip_vis")) < GUARD["min_vis"]:
            return True
        t = r["t"]
        side = r["side"]
        if ((self.near_right is not None and side != self.near_right) or
                (self.ref is not None and (t <= self.ref["t"] or t-self.ref["t"] > GUARD["max_gap"]))):
            self.reset()
        self.near_right = side
        hx, hy = num("hip_x")*aspect, num("hip_y")
        sx, sy = num("shoulder_x")*aspect, num("shoulder_y")
        ex0, ey0 = num("elbow_x")*aspect, num("elbow_y")
        wx0, wy0 = num("wrist_x")*aspect, num("wrist_y")
        torso = math.hypot(sx-hx, sy-hy)
        if torso < GUARD["min_torso"]:
            return True
        gap = (sx-hx)/torso
        if abs(gap) > GUARD["facing_gap"]:
            self.facing = 1 if gap > 0 else -1
        wx, wy = (wx0-hx)/torso, (wy0-hy)/torso
        ex, ey = (ex0-hx)/torso, (ey0-hy)/torso
        shx, shy = (sx-hx)/torso, (sy-hy)/torso
        upper, fore = math.hypot(shx-ex, shy-ey), math.hypot(wx-ex, wy-ey)
        now = {"t": t, "wx": wx, "wy": wy, "ex": ex}
        reason = None
        if self.ref is not None:
            dt = min(t-self.ref["t"], GUARD["jump_dt_cap"])
            limit = min(GUARD["jump_x_max"], GUARD["jump_x"]+GUARD["jump_x_per_s"]*dt/1000)
            if (abs(wx-self.ref["wx"]) > limit or abs(ex-self.ref["ex"]) > limit*GUARD["elbow_share"] or
                    abs(wy-self.ref["wy"]) > GUARD["jump_y"]):
                reason = "reject"
        if reason is None and len(self.slow) >= GUARD["min_reference"]:
            if abs(wx-med([x["wx"] for x in self.slow])) > GUARD["drift_x"] or abs(ex-med([x["ex"] for x in self.slow])) > GUARD["drift_x"]:
                reason = "reject"
        if reason is None and len(self.bones) >= GUARD["min_reference"]:
            if not GUARD["bone_min"] <= upper/med([x[1] for x in self.bones]) <= GUARD["bone_max"] or not GUARD["bone_min"] <= fore/med([x[2] for x in self.bones]) <= GUARD["bone_max"]:
                reason = "reject"
        if reason is None and self.facing and ((wx-shx)*self.facing < -GUARD["behind_wrist"] or (ex-shx)*self.facing < -GUARD["behind_elbow"]):
            reason = "reject"
        if reason is None and wy < shy-GUARD["above_shoulder"]:
            reason = "reject"
        if reason is None:
            self._accept(now, upper, fore)
            return True
        self.rejected.append(now)
        self.pool.append(now)
        self.pool = [x for x in self.pool if t-x["t"] <= GUARD["majority_ms"]]
        while self.accepted and t-self.accepted[0] > GUARD["majority_ms"]:
            self.accepted.popleft()
        blind = t-(self.ref["t"] if self.ref else self.rejected[0]["t"])
        if blind >= GUARD["reanchor_ms"]:
            mx, my = med([x["wx"] for x in self.rejected]), med([x["wy"] for x in self.rejected])
            agree = len(self.rejected) >= GUARD["reanchor_min"] and all(abs(x["wx"]-mx) < GUARD["reanchor_spread"] and abs(x["wy"]-my) < 2*GUARD["reanchor_spread"] for x in self.rejected)
            if agree or blind >= GUARD["force_ms"]:
                self.slow.clear(); self.bones.clear(); self.pool.clear(); self.accepted.clear()
                self._accept(now, upper, fore)
                return True
            self.rejected = [x for x in self.rejected if t-x["t"] <= GUARD["force_ms"]]
        if len(self.pool) >= GUARD["reanchor_min"] and len(self.pool) >= GUARD["majority_ratio"]*len(self.accepted) and t-self.pool[0]["t"] >= GUARD["majority_ms"]*3//4:
            mx, my = med([x["wx"] for x in self.pool]), med([x["wy"] for x in self.pool])
            if all(abs(x["wx"]-mx) < GUARD["reanchor_spread"] and abs(x["wy"]-my) < 2*GUARD["reanchor_spread"] for x in self.pool):
                self.slow.clear(); self.bones.clear(); self.pool.clear(); self.accepted.clear()
                self._accept(now, upper, fore)
                return True
        return False

    def _accept(self, now, upper, fore):
        self.ref = now
        self.rejected.clear()
        self.accepted.append(now["t"])
        self.slow.append(now)
        while now["t"]-self.slow[0]["t"] > GUARD["reference_ms"]: self.slow.popleft()
        self.bones.append((now["t"], upper, fore))
        while now["t"]-self.bones[0][0] > GUARD["reference_ms"]: self.bones.popleft()


def replay(path):
    guard = LandmarkGuard()
    out = []
    for r in load_frames(path):
        if not guard.check(r):
            continue
        if any(not r[key] for key in ("elbow_deg", "elbow_vis", "shoulder_vis", "wrist_vis", "hip_vis")):
            continue
        if (float(r["elbow_vis"]) < .5 or float(r["shoulder_vis"]) < .5 or float(r["wrist_vis"]) < .5 or
                float(r["hip_vis"]) < .5 or not int(r["side_on"])):
            continue
        out.append((r["t"], min(180., max(0., float(r["elbow_deg"])))))
    return out


def read_labels():
    labels = {}
    with (DATA / "labels.csv").open(encoding="utf-8-sig") as f:
        rows = (line for line in f if line.strip() and not line.lstrip().startswith("#"))
        for r in csv.DictReader(rows):
            labels.setdefault(r["file"], []).append((float(r["start_s"]), float(r["end_s"]), r["label"]))
    return labels


def run_judge(frames, sustain, window, margin):
    win = deque()
    active = False
    since = None
    required = sustain
    reads = []
    for t, angle in frames:
        win.append((t, angle))
        while len(win) > 1 and t-win[0][0] > window*1000: win.popleft()
        if t-win[0][0] < 600:  # PostureJudge MIN_MEDIAN_SPAN_MS
            reads.append((t, False))
            continue
        value = med([x[1] for x in win])
        cond = value >= 153.0 if active else value < 145.0
        if cond:
            if since is None:
                since = t
                required = max(.2, sustain-.3) if not active and value < 145.0-margin else sustain
            if t-since >= required*1000:
                active = not active
                since = None
        else:
            since = None
        reads.append((t, active))
    return reads


def window_values(frames, start_s, end_s):
    win = deque()
    values = []
    for t, angle in frames:
        win.append((t, angle))
        while len(win) > 1 and t-win[0][0] > 1000:
            win.popleft()
        if start_s*1000 <= t < end_s*1000 and t-win[0][0] >= 600:
            values.append(med([x[1] for x in win]))
    return values


def metric_for(reads, intervals):
    values = []
    first = None
    for start, end, label in intervals:
        selected = [(t, state) for t, state in reads if start*1000 <= t < end*1000]
        share = sum(state for _, state in selected)/len(selected) if selected else 0.0
        delay = next(((t/1000-start) for t, state in selected if state and first is None), None) if label == "bent" else None
        if delay is not None: first = delay
        values.append((delay, share))
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sustains", default="0.4,0.5,0.6,0.7,0.8,0.9,1.0")
    parser.add_argument("--windows", default="0.6,0.7,0.8,0.9,1.0")
    parser.add_argument("--margins", default="0,3,6,9,12,15")
    args = parser.parse_args()
    labels = read_labels()
    clips = {f"real-cph2467-{n}.csv": replay(DATA/f"real-cph2467-{n}.csv") for n in range(10,16)}
    straight = {"real-cph2467-10.csv": [(0, clips["real-cph2467-10.csv"][-1][0]/1000, "straight")],
                "real-cph2467-11.csv": [(0, clips["real-cph2467-11.csv"][-1][0]/1000, "straight")],
                "real-cph2467-12.csv": [(0, 18, "straight")],
                "real-cph2467-13.csv": [(0, clips["real-cph2467-13.csv"][-1][0]/1000, "straight")]}
    for name, segments in labels.items():
        straight[name] = [(a,b,l) for a,b,l in segments if l == "straight"]
    bends = {name: [(a,b,l) for a,b,l in rows if l == "bent"] for name, rows in labels.items()}
    settings = []
    for sustain in map(float,args.sustains.split(",")):
        for window in map(float,args.windows.split(",")):
            for margin in map(float,args.margins.split(",")):
                totals = {}
                false = 0
                for name, frames in clips.items():
                    reads = run_judge(frames,sustain,window,margin)
                    totals[name] = (reads, sum(state for t,state in reads if any(a*1000 <= t < b*1000 for a,b,_ in straight.get(name,[]))))
                    false += totals[name][1]
                block = []
                for name, segs in bends.items():
                    for a,b,l in segs:
                        if name in totals:
                            reads = totals[name][0]
                            sel=[(t,s) for t,s in reads if a*1000<=t<b*1000]
                            delay=next((t/1000-a for t,s in sel if s),None)
                            block.append((name,a,delay,sum(s for _,s in sel)/len(sel) if sel else 0))
                settings.append((sustain,window,margin,false,block))
    print("sustain window margin false_straight_samples; bent blocks: file@start delay share")
    for s,w,m,f,blocks in settings:
        report="; ".join(f"{name.replace('real-cph2467-','').replace('.csv','')}@{a:g} {('never' if d is None else f'{d:.2f}s')} {share:.0%}" for name,a,d,share in blocks)
        print(f"{s:.1f} {w:.1f} {m:2.0f} {f:4d}; {report}")
    eligible=[x for x in settings if x[3] == 0 and len(x[4]) == 3 and all(d is not None for _,_,d,_ in x[4])]
    if eligible:
        best=min(eligible,key=lambda x:(max(d for _,_,d,_ in x[4]),x[0],x[1]))
        print(f"\nFastest zero-false setting with all bent blocks detected: sustain={best[0]:.1f}s window={best[1]:.1f}s margin={best[2]:g} degrees; zero added false samples.")
    else:
        print("\nNo zero-false setting detected all three labelled bent blocks.")
    checks = [
        ("10 all", "real-cph2467-10.csv", 0, clips["real-cph2467-10.csv"][-1][0]/1000),
        ("11 all", "real-cph2467-11.csv", 0, clips["real-cph2467-11.csv"][-1][0]/1000),
        ("12 pre-15s", "real-cph2467-12.csv", 0, 15),
        ("13 all", "real-cph2467-13.csv", 0, clips["real-cph2467-13.csv"][-1][0]/1000),
        ("14 straight", "real-cph2467-14.csv", 3, 24),
        ("15 bent 1", "real-cph2467-15.csv", 38.2, 43.4),
        ("15 bent 2", "real-cph2467-15.csv", 54, 61.5),
    ]
    print("\nPort check: 1.0s elbow medians (min, median, max)")
    for label, name, start, end in checks:
        values=window_values(clips[name],start,end)
        if values:
            print(f"{label}: {min(values):.0f}, {med(values):.0f}, {max(values):.0f} degrees")
if __name__ == "__main__":
    main()
