#!/usr/bin/env python3
"""Rate/display replay, not an accuracy evaluator (no inferred truth labels).

Timing/numeric conventions follow EmergencyWordReplayTest: float32 4/3 aspect,
row order, stored double wrist_signal, and first-through-last-row 250ms ticks.
Unlike that test's null side, recorded R/L sides reach the guard, as in the app.
Coordinates/visibility are rounded to Kotlin Float before guard calculations.
Stored elbow_deg is used only for the finite-value gate; posture is not replayed.
Guard.reset deliberately retains nearRight and counters, and LiveAnalyzer clears
signals without clearing lastGood/lastSeen/lastRatedFrame, as the source does.
The baseline follows reference adf940947fbca2fe083615955ac1a70111551624,
including stale accepted-frame suppression and accepted-frame-dated rate holds.
NumPy reductions may differ from Kotlin's sequential sums at floating-point eps.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
from collections import Counter, deque
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np


@dataclass(frozen=True)
class Config:
    window_ms: int = 6000
    min_data_ms: int = 3000
    min_amplitude: float = 0.015
    min_correlation: float = 0.35
    min_recent_correlation: float = 0.4
    edge_min_correlation: float = 0.7
    octave_preference: float = 0.6
    still_window_ms: int = 1500
    recent_cycles: int = 4
    detrend_half: int = 15
    global_clip: float = 0.0
    signed_sqrt: bool = False
    diagnostic_all_gates: bool = False


def load(path):
    """Load CSV to plain frame dictionaries without changing timestamps/order."""
    frames = []
    with open(path, newline='', encoding='utf-8-sig') as stream:
        for row in csv.DictReader(stream):
            t = int(row['t_ms'])
            frame = dict(time_ms=t, arm=None, sample=None,
                         aspect=float(np.float32(np.float32(4) / np.float32(3))),
                         near_right=None)
            if row.get('wrist_signal', '').strip() and row.get('shoulder_x', '').strip():
                frame['arm'] = {f'{joint}_{axis}': float(np.float32(row[f'{joint}_{axis}']))
                                for joint in ('shoulder', 'elbow', 'wrist', 'hip')
                                for axis in ('x', 'y', 'vis')}
                frame['sample'] = dict(signal=float(row['wrist_signal']),
                                       elbow=float(row['elbow_deg']),
                                       visibility=float(np.float32(row['min_vis'])))
                side = row.get('side', '').strip()
                frame['near_right'] = True if side == 'R' else False if side == 'L' else None
            frames.append(frame)
    return frames


def phantom_sensitivity(frames):
    """Offline sensitivity only, not a deployable landmark classifier.

    Find under-elbow runs touching a rapid wrist-only relocation. Geometry here
    uses unscaled image coordinates to reproduce the documented diagnostic mask.
    Future samples can mark earlier frames; this is deliberately noncausal.
    """
    n = len(frames)
    coords = np.full((n, 3, 2), np.nan)
    for i, frame in enumerate(frames):
        if frame['arm'] is not None:
            coords[i] = [[frame['arm'][j + '_' + a] for a in ('x', 'y')]
                         for j in ('shoulder', 'elbow', 'wrist')]
    upper = np.linalg.norm(coords[:, 1] - coords[:, 0], axis=1)
    fore = np.linalg.norm(coords[:, 2] - coords[:, 1], axis=1)
    with np.errstate(divide='ignore', invalid='ignore'):
        horizontal = abs(coords[:, 2, 0] - coords[:, 1, 0]) / upper
        under = (horizontal < .20) & (fore / upper < .90)
        separated = horizontal >= .30
    dt = np.diff([f['time_ms'] for f in frames])
    wrist_jump = np.linalg.norm(np.diff(coords[:, 2], axis=0), axis=1)
    elbow_jump = np.linalg.norm(np.diff(coords[:, 1], axis=0), axis=1)
    events = ((dt > 0) & (dt <= 120) & (wrist_jump > .035) &
              (elbow_jump < .020) &
              ((under[:-1] & separated[1:]) | (separated[:-1] & under[1:])))
    touched = np.zeros(n, dtype=bool)
    for i in np.flatnonzero(events):
        touched[i if under[i] else i + 1] = True
    mask = np.zeros(n, dtype=bool)
    i = 0
    while i < n:
        if not under[i]:
            i += 1
            continue
        end = i + 1
        while end < n and under[end]:
            end += 1
        if touched[i:end].any():
            mask[i:end] = True
        i = end
    return [dict(f, phantom_mask=bool(m)) for f, m in zip(frames, mask)]


class Guard:
    def __init__(self):
        self.near_right = None
        self.judged = self.rejects = 0
        self.reset()

    def reset(self):
        self.ref = None
        self.slow, self.bones, self.rejected, self.pool, self.accepted = [], [], [], [], []
        self.facing = 0

    def accept(self, now, upper, fore):
        self.ref = now
        self.rejected.clear()
        self.accepted.append(now[0])
        self.slow.append(now)
        self.slow = [x for x in self.slow if now[0] - x[0] <= 1500]
        self.bones.append((now[0], upper, fore))
        self.bones = [x for x in self.bones if now[0] - x[0] <= 1500]

    @staticmethod
    def agrees(rows):
        mx = sum(r[1] for r in rows) / len(rows)
        my = sum(r[2] for r in rows) / len(rows)
        return all(abs(r[1] - mx) < .3 and abs(r[2] - my) < .6 for r in rows)

    def check(self, frame):
        a, aspect, t = frame['arm'], frame['aspect'], frame['time_ms']
        if a is None or not math.isfinite(aspect) or aspect <= 0:
            return 'ACCEPT'
        vis = [a[j + '_vis'] for j in ('shoulder', 'elbow', 'wrist', 'hip')]
        if float(np.min(vis)) < .5 or not all(math.isfinite(a[j + '_' + c])
                for j in ('shoulder', 'elbow', 'wrist', 'hip') for c in ('x', 'y')):
            return 'ACCEPT'
        verdict = 'ACCEPT'
        side = frame['near_right']
        switched = side is not None and self.near_right is not None and side != self.near_right
        if switched or (self.ref is not None and (t <= self.ref[0] or t - self.ref[0] > 1000)):
            if switched:
                verdict = 'SIDE_SWITCH'
            self.reset()
        if side is not None:
            self.near_right = side
        hx, hy = a['hip_x'] * aspect, a['hip_y']
        sx, sy = a['shoulder_x'] * aspect, a['shoulder_y']
        torso = math.hypot(sx - hx, sy - hy)
        if torso < .05:
            return verdict
        shoulder_x, shoulder_y = (sx - hx) / torso, (sy - hy) / torso
        if abs(shoulder_x) > .3:
            self.facing = 1 if shoulder_x > 0 else -1
        wx, wy = (a['wrist_x'] * aspect - hx) / torso, (a['wrist_y'] - hy) / torso
        ex, ey = (a['elbow_x'] * aspect - hx) / torso, (a['elbow_y'] - hy) / torso
        upper, fore = math.hypot(shoulder_x - ex, shoulder_y - ey), math.hypot(wx - ex, wy - ey)
        now = (t, wx, wy, ex)
        self.judged += 1
        reason = None
        if self.ref is not None:
            r = self.ref
            limit = min(.4, .25 + .05 * min(t - r[0], 300) / 1000)
            if abs(wx - r[1]) > limit or abs(ex - r[3]) > limit * .7 or abs(wy - r[2]) > .6:
                reason = 'JUMP'
        if reason is None and len(self.slow) >= 5:
            if abs(wx - float(np.median([r[1] for r in self.slow]))) > .3 or abs(ex - float(np.median([r[3] for r in self.slow]))) > .3:
                reason = 'DRIFT'
        if reason is None and len(self.bones) >= 5:
            u, f = float(np.median([r[1] for r in self.bones])), float(np.median([r[2] for r in self.bones]))
            ur = upper / u if u else (math.inf if upper else math.nan)
            fr = fore / f if f else (math.inf if fore else math.nan)
            if not (.6 <= ur <= 1.5 and .6 <= fr <= 1.5):
                reason = 'BONE_LENGTH'
        if reason is None and self.facing and ((wx - shoulder_x) * self.facing < -.2 or (ex - shoulder_x) * self.facing < -.5):
            reason = 'CROSSED'
        if reason is None and wy < shoulder_y - .15:
            reason = 'CROSSED'
        if reason is None:
            self.accept(now, upper, fore)
            return verdict
        self.rejects += 1
        self.rejected.append(now)
        self.pool.append(now)
        self.pool = [r for r in self.pool if t - r[0] <= 2000]
        self.accepted = [v for v in self.accepted if t - v <= 2000]
        blind = t - (self.ref[0] if self.ref is not None else self.rejected[0][0])
        reanchor = False
        if blind >= 350:
            reanchor = (len(self.rejected) >= 4 and self.agrees(self.rejected)) or blind >= 400
            if not reanchor:
                self.rejected = [r for r in self.rejected if t - r[0] <= 400]
        if not reanchor and len(self.pool) >= 4 and len(self.pool) >= 3 * len(self.accepted) and t - self.pool[0][0] >= 1500:
            reanchor = self.agrees(self.pool)
        if reanchor:
            self.rejects -= 1
            self.slow.clear()
            self.bones.clear()
            self.pool.clear()
            self.accepted.clear()
            self.accept(now, upper, fore)
            return 'REANCHOR'
        return reason


def estimate(times, values, config=Config(), *, scalar=False):
    """Stateless estimator diagnostic; scalar=True uses independent scalar DSP loops.

    Inputs are the current already-trimmed estimator window (original times).
    diagnostic_all_gates evaluates later gates even after stillness rejection.
    """
    c = config
    d = dict(state='WARMING', reject=None, recent_rms=None, lag=None,
             global_acf=None, recent_correlation=None, raw_rate=None,
             count=len(times), span_ms=times[-1] - times[0] if len(times) else 0)
    if len(times) < 2 or d['span_ms'] < c.min_data_ms:
        d['reject'] = 'warming_data'
        return d
    if len(times) * 1000 // d['span_ms'] < 6:
        d['reject'] = 'warming_fps'
        return d
    n = d['span_ms'] * 30 // 1000 + 1
    half = c.detrend_half
    if scalar:
        samples, j = [], 0
        for i in range(n):
            t = times[0] + i * 1000.0 / 30
            while j < len(times) - 2 and times[j + 1] < t:
                j += 1
            f = max(0., min(1., (t - times[j]) / (times[j + 1] - times[j])))
            samples.append(values[j] + (values[j + 1] - values[j]) * f)
        prefix = [0.]
        for v in samples:
            prefix.append(prefix[-1] + v)
        x = [v - (prefix[min(n, i + half + 1)] - prefix[max(0, i - half)]) / (min(n, i + half + 1) - max(0, i - half)) for i, v in enumerate(samples)]
    else:
        samples = np.interp(times[0] + np.arange(n) * (1000.0 / 30), times, values)
        prefix = np.concatenate(([0.], np.cumsum(samples)))
        idx = np.arange(n)
        lo, hi = np.maximum(0, idx - half), np.minimum(n, idx + half + 1)
        x = samples - (prefix[hi] - prefix[lo]) / (hi - lo)
    recent = min(c.still_window_ms * 30 // 1000, n)
    rms = math.sqrt(sum(float(v) ** 2 for v in x[-recent:]) / recent)
    d['recent_rms'] = rms
    failed = 'still' if rms < c.min_amplitude else None
    if failed and not c.diagnostic_all_gates:
        d.update(state='NoBeat', reject=failed)
        return d
    if c.signed_sqrt:
        # Experimental only: preserve the amplitude gate's original units.
        x = [math.copysign(math.sqrt(abs(v)), v) for v in x] if scalar else np.sign(x) * np.sqrt(abs(x))
    global_x = x
    if c.global_clip > 0:
        # Limit transition residuals only in period selection. Stop detection
        # still sees the original amplitudes and recent waveform.
        if scalar:
            magnitudes = sorted(abs(v) for v in x)
            mid = len(magnitudes) // 2
            median_abs = (magnitudes[mid] if len(magnitudes) % 2 else
                          (magnitudes[mid - 1] + magnitudes[mid]) / 2)
            bound = c.global_clip * median_abs
            global_x = [max(-bound, min(bound, v)) for v in x]
        else:
            bound = c.global_clip * float(np.median(np.abs(x)))
            global_x = np.clip(x, -bound, bound)
        d['global_clip_bound'] = bound
    r = [0.] * (min(31, n - 1) + 1)
    for lag in range(9, len(r)):
        if scalar:
            cross = sum(global_x[i] * global_x[i + lag] for i in range(n - lag))
            a = sum(global_x[i] * global_x[i] for i in range(n - lag))
            b = sum(global_x[i + lag] * global_x[i + lag] for i in range(n - lag))
        else:
            aa, bb = global_x[:-lag], global_x[lag:]
            cross, a, b = float(aa @ bb), float(aa @ aa), float(bb @ bb)
        den = math.sqrt(a * b)
        r[lag] = cross / den if den > 1e-9 else 0.
    peaks = [lag for lag in range(10, min(30, len(r) - 2) + 1) if r[lag] > r[lag - 1] and r[lag] >= r[lag + 1]]
    best = max((r[lag] for lag in peaks), default=0.)
    if best <= 0:
        d.update(state='NoBeat', reject=failed or 'nopeak')
        return d
    lag = next(lag for lag in peaks if r[lag] >= best * c.octave_preference)
    d.update(lag=lag, global_acf=r[lag])
    threshold = c.edge_min_correlation if lag <= 13 or lag >= 27 else c.min_correlation
    if r[lag] < threshold:
        failed = failed or 'corr'
    recent_n = c.still_window_ms * 30 // 1000
    start = n - recent_n
    rc = 1.
    if start - lag >= 0:
        cross = b = 0.
        a = sum(float(x[i]) ** 2 for i in range(start, n))
        max_offset = min(start, c.recent_cycles * lag)
        for offset in range(lag, max_offset + 1, lag):
            for i in range(start, n):
                cross += x[i] * x[i - offset]
                b += x[i - offset] * x[i - offset]
        den = math.sqrt(a * (max_offset // lag) * b)
        rc = float(cross / den) if den > 1e-9 else 0.
    d['recent_correlation'] = rc
    if rc < c.min_recent_correlation:
        failed = failed or 'recent'
    den = r[lag - 1] - 2 * r[lag] + r[lag + 1]
    refined = float(lag) if abs(den) < 1e-9 else lag + .5 * (r[lag - 1] - r[lag + 1]) / den
    d['candidate_rate'] = 1800. / refined
    d.update(state='NoBeat' if failed else 'Beat', reject=failed,
             raw_rate=None if failed else 1800. / refined)
    return d


def scalar_estimate(times, values, config=Config()):
    """Scalar reference DSP for comparisons, independent from NumPy DSP operations."""
    return estimate(times, values, config, scalar=True)


class RateEstimator:
    """Stateful estimator; times/values expose accepted, retained source samples."""

    def __init__(self, config=Config()):
        self.config = config
        self.times, self.values = [], []

    def reset(self):
        self.times.clear()
        self.values.clear()

    def add(self, time_ms, signal):
        if self.times and time_ms <= self.times[-1]:
            return
        self.times.append(time_ms)
        self.values.append(signal)
        while self.times and time_ms - self.times[0] > self.config.window_ms:
            self.times.pop(0)
            self.values.pop(0)

    def read(self):
        return estimate(self.times, self.values, self.config)


class Analyzer:
    def __init__(self, config):
        self.config, self.guard = config, Guard()
        self.rate = RateEstimator(config)
        self.times, self.values = self.rate.times, self.rate.values
        self.last_good = self.last_seen = self.last_rated = None
        self.held_at = 0
        self.clear()

    def clear(self):
        self.rate.reset()
        self.wrists = []
        self.history = deque(maxlen=5)
        self.moving, self.held = False, None
        self.guard.reset()

    def add(self, frame):
        t = frame['time_ms']
        if frame.get('phantom_mask'):
            if self.last_seen is None or t > self.last_seen:
                self.last_seen = t
            return 'PHANTOM_SENSITIVITY'
        verdict = self.guard.check(frame)
        if verdict in ('JUMP', 'DRIFT', 'BONE_LENGTH', 'CROSSED'):
            if self.last_seen is None or t > self.last_seen:
                self.last_seen = t
            return verdict
        s = frame['sample']
        if s is None or not s['visibility'] >= .5 or not math.isfinite(s['signal']) or not math.isfinite(s['elbow']):
            return verdict
        if self.last_good is not None and (t - self.last_good > 500 or t < self.last_good):
            self.clear()
        self.last_good = t
        self.rate.add(t, s['signal'])
        self.wrists.append((t, s['signal']))
        self.wrists = [r for r in self.wrists if t - r[0] <= 500]
        return verdict

    def read(self, now):
        last = self.last_good
        seen = self.last_seen
        seen_recently = last is not None and seen is not None and now - last <= 1500 and now - seen <= 500 and seen >= last
        base = dict(time_ms=now, tracking='LOST', moving=False, shown_rate=None,
                    raw_rate=None, held=False, moving_rms=None, est=None)
        if last is None or (now - last > 500 and not seen_recently):
            self.clear()
            return base
        if not self.wrists:
            return base
        if now - last > 500:
            base['tracking'] = 'WARMING'
            return base
        recent = [r for r in self.wrists if r[0] > now - 500]
        rms = None
        if len(recent) >= 3:
            x = np.array([r[0] - recent[0][0] for r in recent], dtype=float)
            y = np.array([r[1] for r in recent])
            mx, my = float(x.mean()), float(y.mean())
            dx = x - mx
            sxx = float(dx @ dx)
            slope = float(dx @ (y - my)) / sxx if sxx > 1e-9 else 0.
            residual = y - (my + slope * dx)
            rms = math.sqrt(float(residual @ residual) / len(recent))
            self.moving = rms > (.02 if self.moving else .03)
        d = estimate(self.times, self.values, self.config)
        base.update(moving=self.moving, moving_rms=rms, est=d, raw_rate=d['raw_rate'])
        if d['state'] == 'WARMING':
            base['tracking'] = 'WARMING'
        elif d['state'] == 'NoBeat':
            self.history.clear()
            if self.moving and self.held is not None and 0 <= now - self.held_at <= 2000:
                base.update(tracking='BEATING', shown_rate=self.held, held=True)
            else:
                self.held = None
                base['tracking'] = 'PAUSED'
        else:
            if self.last_rated != last:
                self.last_rated = last
                self.history.append(d['raw_rate'])
            smoothed = float(np.median(self.history)) if self.history else d['raw_rate']
            self.held = math.floor(smoothed + .5)
            self.held_at = last
            base.update(tracking='BEATING', shown_rate=self.held)
        return base


def replay(frames, config=Config()):
    """Replay all frame additions before each 250ms tick; return JSON-safe records."""
    if not frames:
        return []
    if isinstance(config, dict):
        config = Config(**config)
    analyzer, records, i, previous_rejects = Analyzer(config), [], 0, 0
    for tick in range(frames[0]['time_ms'], frames[-1]['time_ms'] + 1, 250):
        verdicts = Counter()
        while i < len(frames) and frames[i]['time_ms'] <= tick:
            verdicts[analyzer.add(frames[i])] += 1
            i += 1
        record = analyzer.read(tick)
        record.update(time_s=(tick - frames[0]['time_ms']) / 1000,
                      guard_rejects=analyzer.guard.rejects,
                      guard_rejects_tick=analyzer.guard.rejects - previous_rejects,
                      guard_verdicts=dict(verdicts), guard_judged=analyzer.guard.judged)
        previous_rejects = analyzer.guard.rejects
        records.append(record)
    return records


def summarize(records):
    n = len(records)
    shown = [r['shown_rate'] for r in records if r['shown_rate'] is not None]
    diagnostics = [r['est'] for r in records if r['est']]
    def distribution(key):
        values = [d[key] for d in diagnostics if d.get(key) is not None]
        return dict(min=min(values), median=float(np.median(values)), max=max(values)) if values else None
    return dict(ticks=n,
                tracking=dict(Counter(r['tracking'] for r in records)),
                moving_ticks=sum(r['moving'] for r in records),
                shown_ticks=len(shown), shown_fraction=len(shown) / n if n else None,
                held_ticks=sum(r['held'] for r in records),
                raw_beat_ticks=sum(r['raw_rate'] is not None for r in records),
                shown_median=float(np.median(shown)) if shown else None,
                rejects=dict(Counter(r['est']['reject'] for r in records if r['est'] and r['est']['reject'])),
                guard_rejects=sum(r['guard_rejects_tick'] for r in records),
                recent_rms=distribution('recent_rms'),
                global_acf=distribution('global_acf'),
                recent_correlation=distribution('recent_correlation'))


WINDOWS_15 = {'straight_1': (3.5, 32.), 'straight_2': (44., 53.4),
              'bent_1': (38.2, 43.4), 'bent_2': (54., 61.5),
              'lean': (65., 69.5), 'outage_1': (39.5, 43.5),
              'outage_2': (57.5, 59.), 'outage_3': (64., 69.5)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', type=Path, required=True)
    for name in ('window_ms', 'min_data_ms', 'still_window_ms', 'recent_cycles', 'detrend_half'):
        parser.add_argument('--' + name.replace('_', '-'), type=int, default=getattr(Config(), name))
    for name in ('min_amplitude', 'min_correlation', 'min_recent_correlation', 'edge_min_correlation', 'octave_preference', 'global_clip'):
        parser.add_argument('--' + name.replace('_', '-'), type=float, default=getattr(Config(), name))
    parser.add_argument('--diagnostic-all-gates', action='store_true', help='Evaluate later gates despite stillness; first rejection remains authoritative')
    parser.add_argument('--signed-sqrt', action='store_true',
                        help='Experimental signed square root before correlation, after amplitude gate')
    parser.add_argument('--records', action='store_true', help='Include per-tick diagnostics in JSON')
    parser.add_argument('--phantom-sensitivity', action='store_true',
                        help='Apply the offline diagnostic wrist mask to alternate only')
    args = parser.parse_args()
    alternate = Config(**{key: getattr(args, key) for key in asdict(Config())})
    baseline = Config(diagnostic_all_gates=args.diagnostic_all_gates)
    report = dict(assumptions=__doc__, baseline_config=asdict(baseline), alternate_config=asdict(alternate), recordings={})
    names = [f'real-cph2467-{i}.csv' for i in range(1, 16)] + ['negative-cph2467-nobody.csv']
    for name in names:
        frames = load(args.data_dir / name)
        result = {}
        for variant, config in (('baseline', baseline), ('alternate', alternate)):
            source = phantom_sensitivity(frames) if variant == 'alternate' and args.phantom_sensitivity else frames
            records = replay(source, config)
            entry = dict(summary=summarize(records),
                         phantom_excluded_frames=sum(bool(f.get('phantom_mask')) for f in source))
            windows = WINDOWS_15 if name == 'real-cph2467-15.csv' else {'pause': (24.7, 33.5)} if name == 'real-cph2467-14.csv' else {}
            entry['windows'] = {label: dict(start_s=start, end_s=end,
                **summarize([r for r in records if start <= r['time_s'] <= end])) for label, (start, end) in windows.items()}
            if args.records:
                entry['records'] = records
            result[variant] = entry
        report['recordings'][name] = result
    print(json.dumps(report, indent=2, allow_nan=False))


if __name__ == '__main__':
    main()
