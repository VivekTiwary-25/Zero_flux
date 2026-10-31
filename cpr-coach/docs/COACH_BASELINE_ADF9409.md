# Coach timing baseline on recordings 10–15 (commit adf9409)

Tested commit: `adf940947fbca2fe083615955ac1a70111551624`.
Both timeline unit tests (`TraceTimelineTest` and `EmergencyTimelineTest`) ran inside a reference checkout (`../ref-t261031-0524-4f8f/cpr-coach`) for recordings 10, 11, 12, 13, 14, and 15 with `--rerun-tasks`.
Times are seconds from the first CSV frame ($t=0.00\text{ s}$).
In Practise, setup countdown ends at $t=0.00\text{ s}$ (`countdown` at $-3.00\text{ s}$).
Timeline samples count the last 250 ms tick in each 1-second window $[t, t+1)$ whose sample timestamp lies in the block.
Emergency word intervals use exact 250 ms transition boundaries $[start, end)$.
Labels are taken from `tools/testdata/labels.csv`. Recordings 10, 11, and 13 have no entries in `labels.csv`; their timelines are reported for their full session and motion stretches.

---

## 1. Summary tables

### A. Posture error reaction latency (bent and lean blocks)
Seconds from block start ($t_{start}$) to Emergency word transition ($t_{word}$) and spoken voice clip start ($t_{voice}$).

| Recording | Labelled block | Label | Source | $t_{start}$ | Emergency word | $t_{word}$ | Latency to word | Spoken phrase | Practise voice ($t_{voice}$) | Emergency voice ($t_{voice}$) | Latency to voice |
|---|---|---|---|---:|---|---:|---:|---|---:|---:|---:|
| 12 | 18.0–29.0 s | bent | team_unconfirmed | 18.00 s | `posture_straighten_arms` | 19.75 s | **1.75 s** | `elbows_straight` | 20.75 s | 20.75 s | **2.75 s** |
| 15 | 38.2–43.4 s | bent | pictures | 38.20 s | `posture_straighten_arms` | 41.25 s | **3.05 s** | `elbows_straight` | 42.25 s | 42.25 s | **4.05 s** |
| 15 | 54.0–61.5 s | bent | pictures | 54.00 s | `posture_straighten_arms` | 56.75 s | **2.75 s** | `elbows_straight` | 57.75 s | 57.75 s | **3.75 s** |
| 15 | 65.0–69.5 s | leaning | team_unconfirmed | 65.00 s | `posture_shoulders_over_hands` | 67.00 s | **2.00 s** | `shoulders_over_hands` | 68.00 s | 68.00 s | **3.00 s** |

*Note:* In all four blocks, Practise and Emergency trigger spoken posture corrections at identical timestamps.

---

### B. Posture word persistence into straight blocks
Seconds an on-screen posture instruction word remains after a straight block starts.

| Recording | Preceding block | Following straight block | Straight start ($t_{straight}$) | Posture word | Word end ($t_{end}$) | Persistence past straight start |
|---|---|---|---:|---|---:|---:|
| 15 | 38.2–43.4 s (bent) | 44.0–53.4 s (straight) | 44.00 s | `posture_straighten_arms` | 46.25 s | **2.25 s** |

*Additional unlabelled block transitions:*
- 12: Bent block ends at 29.00 s; `posture_straighten_arms` persists to 29.75 s (**0.75 s** past bent end) before switching to `em_word_slower`.
- 15: Second bent block ends at 61.50 s; `posture_straighten_arms` persists to 65.00 s (**3.50 s** past bent end) before switching to `em_word_keep_pushing`.
- 15: Lean block ends at 69.50 s; `posture_shoulders_over_hands` persists to 70.25 s (**0.75 s** past lean end) before switching to `em_word_keep_pushing`.

---

### C. Spoken `keep_going` prompts and pause timing
Every spoken `keep_going` event across all runs, compared to the preceding stop in chest compressions.

| Recording | Spoken time | Mode | Preceding real stop / pause start | Time since real stop | Tracking PAUSED at | Time since PAUSED | Cause / Reason for delay |
|---|---:|---|---:|---:|---:|---:|---|
| 10 | 5.25 s | Practise | 0.00 s (countdown end / hesitation) | 5.25 s | 3.00 s | 2.25 s | Hesitation before initial pushing (pushes at ~8 s). Emergency voice muted by scripted intro. |
| 10 | 29.25 s | Practise & Emergency | 27.00 s (end of push block) | 2.25 s | 27.00 s | 2.25 s | Prompt prompt after ~2 s still pause following 26–27 s pushing. |
| 11 | 31.50 s | Practise | 29.00 s (end of push block) | 2.50 s | 29.00 s | 2.50 s | Prompt prompt after ~2 s still pause. Emergency voice muted by scripted `em_aed` (30.00–33.74 s). |
| 12 | 32.75 s | Practise | 30.00 s (end of bent pushing block) | 2.75 s | 30.00 s | 2.75 s | Prompt prompt after ~2 s still pause. Emergency voice muted by scripted `em_aed` (30.00–33.74 s). |
| 12 | 44.25 s | Practise & Emergency | 39.00 s (end of final push block) | 5.25 s | 39.00 s | 5.25 s | Rescuer stopped at 39 s, but movement and bent-elbow posture readings at 41–43 s delayed stillness confirmation. |
| 13 | 41.75 s | Practise | ~36.00 s (rescuer stopped pushing) | 5.75 s | 39.00 s | 2.75 s | Tracking held rate to 38.75 s; PAUSED entered at 39.00 s; prompt at 41.75 s. Emergency lost to WARMING at 42.75 s. |
| 14 | 27.50 s | Practise & Emergency | 24.70 s (real rest block start) | 2.80 s | 24.00 s | 3.50 s | Real rest 24.7–33.5 s. First still reads confirmed pause; prompted 2.80 s after rest began. |
| 15 | 35.00 s | Practise & Emergency | 32.30 s (reposition start; straight ended 32.0 s) | 2.70 s | 33.00 s | 2.00 s | Reposition 32.3–34.0 s. Prompted 2.70 s after reposition began, firing inside the 34.5–37.3 s straight block. |

---

### D. Posture reads on straight stretches (false positives)
Verification of whether `ELBOWS_BENT` or `SHOULDERS_NOT_OVER_HANDS` occurs during labelled straight stretches.

| Recording | Straight stretch | Total 1-s samples | Samples with ELBOWS_BENT | Samples with SHOULDERS_NOT_OVER_HANDS | False detection details |
|---|---|---:|---:|---:|---|
| 11 | Full run (0.0–32.4 s) | 33 | 0 (0%) | 0 (0%) | Clean. Posture good on 31 samples, unknown on 2 (0.75, 27.75). |
| 14 | 3.0–24.0 s (pictures) | 21 | 0 (0%) | 0 (0%) | Clean. Posture good on 20 samples, unknown on 1 (17.75). |
| 15 | 3.5–32.0 s (pictures) | 29 | 0 (0%) | 0 (0%) | Clean. Posture good on all 29 samples. |
| 15 | 34.5–37.3 s (pictures) | 3 | 0 (0%) | 0 (0%) | Clean. Posture good on all 3 samples. |
| 15 | 44.0–53.4 s (pictures) | 9 | **1 (11%)** | 0 (0%) | **False ELBOWS_BENT at 44.75 s** (median elbow angle 174.8°, `posture_good=false`). Remaining 8 samples posture good. |

---

## 2. Per-recording details

### Recording 10 (frames=999, duration=49.43 s, final tick/read=49.50 s)
*No labelled blocks in `tools/testdata/labels.csv`.*

- **Full run (0.00–49.50 s):**
  - Timeline samples: 50 total (1.75–19.75 s and 24.75–49.50 s `posture_good=true`; 0.75 s and 20.75–23.75 s unknown).
  - Posture issues: 0 samples with `ELBOWS_BENT`, 0 samples with `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: `keep_going` 5.25 s, `keep_going` 29.25 s (setup: `countdown` -3.00 s).
  - Emergency voice starts: `em_call` 0.00 s, `em_hands` 2.60 s, `em_push` 4.77 s, `good` 11.50 s, `keep_going` 29.25 s, `em_aed` 30.41 s.
  - Emergency word transitions:
    - 0.00–2.75 s: `em_step_call`
    - 2.75–5.00 s: `em_step_hands`
    - 5.00–7.75 s: `em_step_push`
    - 7.75–9.50 s: `em_word_keep_pushing`
    - 9.50–20.00 s: `em_word_good`
    - 20.00–37.50 s: `em_word_keep_pushing`
    - 37.50–40.50 s: `em_word_slower`
    - 40.50–45.75 s: `em_word_good`
    - 45.75–49.50 s: `em_word_keep_pushing`

---

### Recording 11 (frames=710, duration=32.43 s, final tick/read=32.50 s)
*No labelled blocks in `tools/testdata/labels.csv`.* Rescuer pushes straight throughout.

- **Full run (0.00–32.50 s):**
  - Timeline samples: 33 total (31 `posture_good=true`; unknown at 0.75 s and 27.75 s).
  - Posture issues: 0 samples with `ELBOWS_BENT`, 0 samples with `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: `good` 8.25 s, `keep_going` 31.50 s (setup: `countdown` -3.00 s).
  - Emergency voice starts: `em_call` 0.00 s, `em_hands` 2.60 s, `em_push` 4.77 s, `good` 10.75 s, `em_aed` 30.00 s.
  - Emergency word transitions:
    - 0.00–2.75 s: `em_step_call`
    - 2.75–5.00 s: `em_step_hands`
    - 5.00–6.25 s: `em_step_push`
    - 6.25–13.50 s: `em_word_good`
    - 13.50–18.75 s: `em_word_keep_pushing`
    - 18.75–29.50 s: `em_word_good`
    - 29.50–32.50 s: `em_word_keep_pushing`

---

### Recording 12 (frames=1141, duration=52.26 s, final tick/read=52.50 s)

- **Labelled block 18.0–29.0 s (bent, team_unconfirmed):**
  - Timeline samples: 11 total (18.75 to 28.75 s).
  - Posture issues: 11/11 (100%) samples with `ELBOWS_BENT` (18.75, 19.75, 20.75, 21.75, 22.75, 23.75, 24.75, 25.75, 26.75, 27.75, 28.75 s).
  - Posture issues: 0 samples with `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: `elbows_straight` 20.75 s.
  - Emergency voice starts: `elbows_straight` 20.75 s.
  - Emergency word transitions (clipped to block):
    - 18.00–18.25 s: `em_word_keep_pushing` (full interval 14.00–18.25 s)
    - 18.25–19.75 s: `em_word_good` (full interval 18.25–19.75 s)
    - 19.75–29.00 s: `posture_straighten_arms` (full interval 19.75–29.75 s)

- **Pre-bent stretch (0.00–18.00 s):**
  - Timeline samples: 18 total. 0 `ELBOWS_BENT`, 0 `SHOULDERS_NOT_OVER_HANDS`. Rate 118–124 bpm.
  - Practise voice: `slow_down` 10.75 s.
  - Emergency voice: `em_call` 0.00 s, `em_hands` 2.60 s, `em_push` 4.77 s, `slow_down` 10.75 s.
  - Emergency words: `em_step_call` (0–2.75 s), `em_step_hands` (2.75–5.00 s), `em_step_push` (5.00–7.75 s), `em_word_keep_pushing` (7.75–8.75 s), `em_word_slower` (8.75–14.00 s), `em_word_keep_pushing` (14.00–18.25 s).

- **Post-bent stretch (29.00–52.50 s):**
  - Timeline samples: 24 total.
  - Posture issues: `ELBOWS_BENT` on 6 samples (29.75, 30.75, 41.75, 42.75, 43.75, 44.75 s); `SHOULDERS_NOT_OVER_HANDS` on 8 samples (45.75, 46.75, 47.75, 48.75, 49.75, 50.75, 51.75, 52.50 s).
  - Practise voice: `keep_going` 32.75 s, `keep_going` 44.25 s.
  - Emergency voice: `em_aed` 30.00 s, `keep_going` 44.25 s.
  - Emergency words: `posture_straighten_arms` (29.00–29.75 s), `em_word_slower` (29.75–30.75 s), `em_word_keep_pushing` (30.75–37.75 s), `em_word_slower` (37.75–39.75 s), `em_word_keep_pushing` (39.75–52.50 s).

---

### Recording 13 (frames=898, duration=44.45 s, final tick/read=44.50 s)
*No labelled blocks in `tools/testdata/labels.csv`.*

- **Full run (0.00–44.50 s):**
  - Timeline samples: 45 total (36 `posture_good=true`; 9 unknown/null).
  - Posture issues: 0 samples with `ELBOWS_BENT`, 0 samples with `SHOULDERS_NOT_OVER_HANDS`.
  - Pushing section (~4–36 s): Rate 81–107 bpm. Practise speaks `push_faster` 12.00 s, `good` 35.00 s. Emergency speaks `em_call` 0.00 s, `em_hands` 2.60 s, `em_push` 4.77 s, `push_faster` 12.00 s, `em_aed` 30.00 s, `good` 36.00 s.
  - Stopped section (~36–44.50 s): Rescuer stops at ~36 s; tracking stays BEATING until 38.75 s, PAUSED at 39.75–41.75 s, WARMING at 42.75–44.50 s. Practise speaks `keep_going` 41.75 s. Emergency word transitions from GOOD to `em_word_keep_pushing` at 39.50 s. Emergency stays silent (tracking transitions to WARMING before idle voice queue triggers).
  - Emergency word transitions:
    - 0.00–2.75 s: `em_step_call`
    - 2.75–5.00 s: `em_step_hands`
    - 5.00–7.75 s: `em_step_push`
    - 7.75–10.00 s: `em_word_keep_pushing`
    - 10.00–25.75 s: `em_word_faster`
    - 25.75–39.50 s: `em_word_good`
    - 39.50–44.50 s: `em_word_keep_pushing`

---

### Recording 14 (frames=1401, duration=63.19 s, final tick/read=63.25 s)

- **Labelled block 3.0–24.0 s (straight, pictures):**
  - Timeline samples: 21 total (3.75 to 23.75 s).
  - Posture issues: 0 samples with `ELBOWS_BENT`, 0 samples with `SHOULDERS_NOT_OVER_HANDS`. Posture good on 20/21 samples (unknown at 17.75 s).
  - Practise voice starts: `good` 7.75 s.
  - Emergency voice starts: `em_push` 4.77 s, `good` 10.75 s (scripted `em_call` 0.00 s and `em_hands` 2.60 s precede 3 s).
  - Emergency word transitions (clipped to block):
    - 3.00–5.00 s: `em_step_hands` (full interval 2.75–5.00 s)
    - 5.00–5.75 s: `em_step_push` (full interval 5.00–5.75 s)
    - 5.75–24.00 s: `em_word_good` (full interval 5.75–24.75 s)

- **Unlabelled rest stretch (24.0–33.5 s):**
  - Timeline samples: 9 total (24.75 to 32.75 s). All 9 samples PAUSED with rate `--`.
  - Posture issues: 4/9 (44%) samples with issues: `SHOULDERS_NOT_OVER_HANDS` at 26.75 s; `ELBOWS_BENT` at 27.75, 28.75, 29.75 s.
  - Practise voice starts: `keep_going` 27.50 s.
  - Emergency voice starts: `keep_going` 27.50 s, `em_aed` 30.00 s.
  - Emergency word transitions: `em_word_good` (24.00–24.75 s), `em_word_keep_pushing` (24.75–33.50 s).

- **Unlabelled phone dropped stretch (33.5–63.25 s):**
  - Timeline samples: 30 total (33.75 to 63.25 s). All samples LOST tracking with rate `--`.
  - Posture issues: 0 samples with issues (all unknown posture).
  - Practise voice starts: none.
  - Emergency voice starts: `em_continue` 60.00 s (scripted).
  - Emergency word transitions: `em_word_keep_pushing` throughout (33.50–63.25 s).

---

### Recording 15 (frames=1597, duration=71.45 s, final tick/read=71.50 s)

- **Labelled block 3.5–32.0 s (straight, pictures):**
  - Timeline samples: 29 total (3.75 to 31.75 s).
  - Posture issues: 0 `ELBOWS_BENT`, 0 `SHOULDERS_NOT_OVER_HANDS`. All 29 samples `posture_good=true`.
  - Practise voice starts: `good` 8.00 s.
  - Emergency voice starts: `em_push` 4.77 s, `good` 10.75 s, `em_aed` 30.00 s.
  - Emergency word transitions (clipped to block):
    - 3.50–5.00 s: `em_step_hands` (full interval 2.75–5.00 s)
    - 5.00–6.00 s: `em_step_push` (full interval 5.00–6.00 s)
    - 6.00–26.25 s: `em_word_good` (full interval 6.00–26.25 s)
    - 26.25–27.50 s: `em_word_keep_pushing` (full interval 26.25–27.50 s)
    - 27.50–32.00 s: `em_word_good` (full interval 27.50–33.00 s)

- **Labelled block 32.3–34.0 s (unknown, none):**
  - Timeline samples: 2 total (32.75, 33.75 s). 0 `ELBOWS_BENT`, 0 `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: none.
  - Emergency voice starts: none.
  - Emergency word transitions (clipped to block):
    - 32.30–33.00 s: `em_word_good` (full interval 27.50–33.00 s)
    - 33.00–34.00 s: `em_word_keep_pushing` (full interval 33.00–37.50 s)

- **Labelled block 34.5–37.3 s (straight, pictures):**
  - Timeline samples: 3 total (34.75, 35.75, 36.75 s).
  - Posture issues: 0 `ELBOWS_BENT`, 0 `SHOULDERS_NOT_OVER_HANDS`. All 3 samples `posture_good=true`.
  - Practise voice starts: `keep_going` 35.00 s.
  - Emergency voice starts: `keep_going` 35.00 s.
  - Emergency word transitions (clipped to block):
    - 34.50–37.30 s: `em_word_keep_pushing` (full interval 33.00–37.50 s)

- **Labelled block 37.4–38.0 s (unknown, none):**
  - Timeline samples: 1 total (37.75 s). 0 `ELBOWS_BENT`, 0 `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: none.
  - Emergency voice starts: none.
  - Emergency word transitions (clipped to block):
    - 37.40–37.50 s: `em_word_keep_pushing` (full interval 33.00–37.50 s)
    - 37.50–38.00 s: `em_word_faster` (full interval 37.50–40.00 s)

- **Labelled block 38.2–43.4 s (bent, pictures):**
  - Timeline samples: 5 total (38.75 to 42.75 s).
  - Posture issues: 4/5 (80%) samples with `ELBOWS_BENT` (39.75, 40.75, 41.75, 42.75 s; unknown at 38.75 s).
  - Posture issues: 0 `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: `elbows_straight` 42.25 s.
  - Emergency voice starts: `elbows_straight` 42.25 s.
  - Emergency word transitions (clipped to block):
    - 38.20–40.00 s: `em_word_faster` (full interval 37.50–40.00 s)
    - 40.00–41.25 s: `em_word_keep_pushing` (full interval 40.00–41.25 s)
    - 41.25–43.40 s: `posture_straighten_arms` (full interval 41.25–46.25 s)

- **Labelled block 44.0–53.4 s (straight, pictures):**
  - Timeline samples: 9 total (44.75 to 52.75 s).
  - Posture issues: **1/9 (11%) samples with `ELBOWS_BENT` at 44.75 s**; remaining 8 samples `posture_good=true`.
  - Posture issues: 0 `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: none.
  - Emergency voice starts: none.
  - Emergency word transitions (clipped to block):
    - 44.00–46.25 s: `posture_straighten_arms` (full interval 41.25–46.25 s)
    - 46.25–53.40 s: `em_word_good` (full interval 46.25–55.00 s)

- **Labelled block 54.0–61.5 s (bent, pictures):**
  - Timeline samples: 7 total (54.75 to 60.75 s).
  - Posture issues: 6/7 (86%) samples with `ELBOWS_BENT` (55.75, 56.75, 57.75, 58.75, 59.75, 60.75 s; unknown at 54.75 s).
  - Posture issues: 0 `SHOULDERS_NOT_OVER_HANDS`.
  - Practise voice starts: `elbows_straight` 57.75 s.
  - Emergency voice starts: `elbows_straight` 57.75 s, `em_continue` 60.00 s.
  - Emergency word transitions (clipped to block):
    - 54.00–55.00 s: `em_word_good` (full interval 46.25–55.00 s)
    - 55.00–56.75 s: `em_word_keep_pushing` (full interval 55.00–56.75 s)
    - 56.75–61.50 s: `posture_straighten_arms` (full interval 56.75–65.00 s)

- **Labelled block 65.0–69.5 s (leaning, team_unconfirmed):**
  - Timeline samples: 4 total (65.75 to 68.75 s).
  - Posture issues: 0 `ELBOWS_BENT`.
  - Posture issues: 3/4 (75%) samples with `SHOULDERS_NOT_OVER_HANDS` (66.75, 67.75, 68.75 s; unknown at 65.75 s).
  - Practise voice starts: `shoulders_over_hands` 68.00 s.
  - Emergency voice starts: `shoulders_over_hands` 68.00 s.
  - Emergency word transitions (clipped to block):
    - 65.00–67.00 s: `em_word_keep_pushing` (full interval 65.00–67.00 s)
    - 67.00–69.50 s: `posture_shoulders_over_hands` (full interval 67.00–70.25 s)
