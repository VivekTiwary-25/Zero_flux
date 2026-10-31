# Weak regular pushing rate study

## Finding

Recording 16's trace-time 114–123 s contains real regular pushing: the independent wrist-peak check finds 17 peaks (about 113/min; RMS 0.030). The replay's six-second window still contains the disrupted 108–114 s segment, after the phone movement at 104–108 s. That window dilutes periodicity: between 114 and 116.5 s, candidates are mostly 113–119/min but selected global ACF is only 0.076–0.206; recent-cycle correlation is often below 0.4 as well. Recent RMS is only 0.022–0.030, but remains above the estimator's 0.015 amplitude floor. A later narrow opportunity at 118.25 s has a 113.65/min candidate, global ACF 0.307, recent correlation 0.418, and RMS 0.028: only the global 0.35 correlation gate rejects it.

The weak signal and window contamination explain the miss; lowering a gate globally is not warranted. I tested a conditional fallback: only when the ordinary correlation gate is the first rejection, the candidate is within 8/min of a previously locked rate, and that lock is at most 3 s old, retry with a lower global correlation limit. Recent-correlation, edge-correlation, amplitude, display, and stop logic remain unchanged. The experiment script is `tools/rate_weak_regular.py`; the exact copied replay and peak-check helpers are `tools/rbl17.py` and `tools/peaks.py`.

Reproduce with Python 3 and NumPy:

```sh
python cpr-coach/tools/rate_weak_regular.py --data-dir <path-to-testdata>
python cpr-coach/tools/peaks.py <path-to-testdata>
```

The first command evaluates conditional floors 0.30, 0.25 and 0.20 in sequence.

## Python replay results

Each threshold changes only the conditional global-correlation floor; baseline is 0.35. The tested lower floors all produced the same output:

| Conditional floor | 16, 93–104 | 16, 104–123 | 16, 114–123 | 16 stop 62–76 | 14 stop 25.5–33.5 | 13 stop 38–44 | Nobody | 16 restart 76–82 |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Baseline | 12 | 13 | 1 | 0 | 0 | 6 | 0 | 113–116 |
| 0.30 | 12 | 16 | 4 | 0 | 0 | 6 | 0 | 113–116 |
| 0.25 | 12 | 16 | 4 | 0 | 0 | 6 | 0 | 113–116 |
| 0.20 | 12 | 16 | 4 | 0 | 0 | 6 | 0 | 113–116 |

Counts are displayed-rate reads, inclusive time bounds. The tested conditional change adds three displayed reads in 104–123, including three in the previously nearly blank 114–123 interval; one fresh conditional estimate at 118.25 s also enables the existing display hold briefly. No tested stop or nobody interval gains a rate. Restart rate range is unchanged.

Across recordings 2–16, with the independent peak pace comparison and its six-second/six-peak eligibility rule, baseline is 232/1,538 reads more than 10/min from peak pace (15.1%) and 82/1,538 more than 20/min (5.3%). The 0.30 conditional variant is 232/1,547 (15.0%) and 82/1,547 (5.3%). The added eligible reads do not increase either error share. The independent baseline run also reproduced recording 16's peak blocks: 93–104 s, 18 peaks/~98/min; 104–108 s, 6/~90/min with RMS 0.131; 108–114 s, 7/~70/min; 114–123 s, 17/~113/min.

## Recommendation

The 0.30 conditional limit qualifies against the requested replay criteria: it improves 114–123 s, adds no shown rates in any tested stop/nobody interval, and raises neither all-recording peak-error percentage. It is the narrowest tested relaxation with the same benefit. This is Python-port evidence only, not Kotlin behavior, independent clinical validation, or a shipping recommendation.
