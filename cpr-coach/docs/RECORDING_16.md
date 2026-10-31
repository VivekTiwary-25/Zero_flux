# Recording 16: phone test of build e1b489f

`tools/testdata/real-cph2467-16.csv`. CPH2467, 31 Oct 2026, 05:36:50-05:38:59, one 2-minute Practise session, 2,692 frames, 236 pictures. Picture sheets are in `tools/traces/run-1031b/sheets`. Times are seconds from the first CSV row. Live time is about 3.3 s less.

## Labels
The lean and the last straight block are the tester's account: lean about 85-100 s on the stopwatch (trace 88.3-103.3), firm and weak pushing in turn during it, straight again from about 100 s (trace 103.3). The tester saw the lean on screen about 3 s in, then the app paused. The other blocks were read from the saved pictures by the coding assistant.

| Trace time | Label | Replay |
|---|---|---|
| 5-30.5 | Straight | BEATING, about 104/min |
| 30.5-44.5 | Bent; a short straight dip near 40 | ELBOWS_BENT 32-39 |
| 45-58.5 | Straight | BEATING, about 108/min |
| 59-78 | Stopped | PAUSED, no rate |
| 79-88 | Straight | BEATING, about 110/min |
| 88.3-103.3 | Lean | BEATING to 92, then PAUSED most of the time, **no lean flagged** |
| 103.3-123 | Straight | **Lean flagged 105-119**, PAUSED from 108 |

**Unexplained:** the replay's lean and the tester's lean are about 15 s apart. The pictures also show the arms more slanted from about 104 s to the end than before it. The replay uses the same code as the phone, except for the camera's real aspect. So either the replay differs from the phone here or the stopwatch times are off. Don't use this recording's lean blocks to tune posture until this is settled.

The phone was moved sharply at about 102-108 s: the background shifts and the pictures blur.

## Why it paused during the lean
It is rate dropout. It is not the pause rule or the frame filter.

- **Frame filter:** 8 rejected frames in the whole session.
- **Pause rule:** PAUSED is simply what the app shows when the estimator has no beat and the 2 s rate hold has run out. The coach's pause rule only decides whether Keep going plays.
- **Rate estimator, 93-123 s, 121 reads:** 63 failed recent correlation, 41 failed overall correlation, 15 gave a rate, 1 failed the movement floor, 1 found no peak.
- **The hold can't help:** `moving` was true on only 17% of reads from 104 s on, so the hold rarely applies.

Pushing went on but got weaker and less even. Wrist peaks per block, an independent count from the signal:

| Trace time | Peaks | Pace from peaks | Signal size (RMS) |
|---|---:|---:|---:|
| 5-30 straight | 44 | 106 | 0.045 |
| 80-92 straight | 22 | 110 | 0.042 |
| 93-104 | 18 | 98 | 0.037 |
| 104-108 phone moved | 6 | 90 | 0.131 |
| 108-114 | 7 | 70 | 0.021 |
| 114-123 | 17 | 113 | 0.030 |

The phone's movement at 104-108 s swamps the signal and stays in the 6 s window until about 114 s. After that the pushing is regular at about 113/min, and the estimator still rejects it (overall correlation 0.10-0.34 against the 0.35 limit). That last stretch is a real miss.

A false Keep going also played at 102.75 s in replay. The peaks show pushing at the time.

## The held-back rate change: not applied
Settings as in `RATE_BENT_LEAN.md`. "Peak pace" is wrist peaks over the previous 6 s, with at least 6 peaks. It is not a counted truth.

| Check | Now | With the change |
|---|---:|---:|
| 16 lean 104-123: reads showing a rate, of 77 | 13 | 45 |
| 16 unclear 93-104: reads showing a rate, of 45 | 12 | 34 |
| 16 real stop 62-76: reads showing a rate | 0 | 0 |
| 14 rest, nobody: reads showing a rate | 0 | 0 |
| 16 restart 76-82: rates shown | 113-116 | 117-127 |
| 16 stop 58-62: reads showing a rate, of 17 | 8 | 10 |
| All recordings: rate more than 10/min from peak pace | 15.1% of 1,538 | 25.3% of 2,054 |
| All recordings: rate more than 20/min from peak pace | 5.3% | 9.3% |
| 16: reads more than 10/min off | 14 | 45 |
| 12: reads outside 100-120 | 52% | 80% |

It shows more rates, but more of them are wrong, and the app would give more false Faster and Slower lines. It also reports a stop later and spikes on restart. The condition was that pace counting stays correct on all recordings, and it does not, so it stays out.

## Coach voice on this recording
With the elbow patch, the elbow line is due 3.25 s after the bend starts, in replay; it was 4.25 s. The tester heard about 3 s on the phone.
