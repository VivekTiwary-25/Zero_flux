# Replay 13–15: Practise voice and Emergency word

Tested commit: `64de3c3ccac3693c58ab44a686ce21edfc3ba3b0` (`origin/cpr-coach`).
Both timeline tests ran for each recording. All three Gradle runs succeeded.
These are replay screen states and clip-start events, not a device or listening check.

## Commands
From this checkout, in PowerShell:
```powershell
git fetch origin cpr-coach
New-Item -ItemType Directory -Force ../ref-t261031-0429-800d | Out-Null
git archive --format=tar -o ../ref-t261031-0429-800d/reference.tar FETCH_HEAD cpr-coach
tar -xf ../ref-t261031-0429-800d/reference.tar -C ../ref-t261031-0429-800d
git rev-parse FETCH_HEAD
Set-Location ../ref-t261031-0429-800d/cpr-coach
$env:ANDROID_HOME="$env:LOCALAPPDATA/Android/Sdk"
$env:CPR_TRACE="tools/testdata/real-cph2467-13.csv"
./gradlew.bat testDebugUnitTest --tests '*TraceTimelineTest*' --tests '*EmergencyTimelineTest*' --rerun-tasks
$env:CPR_TRACE="tools/testdata/real-cph2467-14.csv"
./gradlew.bat testDebugUnitTest --tests '*TraceTimelineTest*' --tests '*EmergencyTimelineTest*' --rerun-tasks
$env:CPR_TRACE="tools/testdata/real-cph2467-15.csv"
./gradlew.bat testDebugUnitTest --tests '*TraceTimelineTest*' --tests '*EmergencyTimelineTest*' --rerun-tasks
```
`CPR_TRACE` was an environment variable, not `-D`. `--rerun-tasks` forced fresh replays.
Evidence: `app/build/trace-timeline.txt` and `app/build/emergency-timeline.txt`, read after each run before overwrite.

## Reading the blocks
Times are seconds from the first CSV frame. Practise setup precedes 0; `countdown` starts at -3 in all runs.
Issue share counts the last 250 ms sample in each second whose actual sample time lies in the labelled half-open block. It is not a duration percentage.
Both modes had identical sampled rates and posture issues. Shares include unknown samples; no issue does not imply good posture.
Rates are displayed compressions/minute, not measured ground truth. `--` means no displayed rate. P = Practise; E = Emergency.
E word shorthand: CALL/HANDS/PUSH = `em_step_call`/`em_step_hands`/`em_step_push`; KEEP/FASTER/GOOD = `em_word_keep_pushing`/`em_word_faster`/`em_word_good`.
ARMS = `posture_straighten_arms`; SHOULDERS = `posture_shoulders_over_hands`. Word ranges are exact 250 ms transitions, clipped to each block.
E always speaks `em_call` 0.00, `em_hands` 2.60, `em_push` 4.77, and `em_aed` 30.00. Runs 14 and 15 also speak `em_continue` 60.00.
Those scripted events are in addition to the block-specific voice events below. “None” means no additional clip starts in that block.
Labels come from `tools/testdata/labels.csv` and task context. Picture labels are assistant-read, not human-verified; 15's lean is team-unconfirmed. Recording 13 has no posture label.

## Recording 13 (ends 44.45; final read 44.50)
- **Pushing ~4–36:** Issues 0/32. Rate 81–107 on 24 samples; `--` on 8. PAUSED at samples 4.75–7.75, 22.75–23.75, 29.75–30.75 despite pushing.
  P voice: `push_faster` 12.00; `good` 35.00. E word: HANDS 4–5, PUSH 5–7.75, KEEP 7.75–10, FASTER 10–25.75, GOOD 25.75–36. E voice: `push_faster` 12.00.
- **Stopped ~36–end:** Issues 0/9; posture still good at 36.75–38.75, then unknown. Rate 101–103 at those first three samples; then `--`. PAUSED 39.75–41.75; WARMING 42.75–44.50.
  P voice: `keep_going` 41.75. E word: GOOD 36–39.50, KEEP 39.50–44.50. E voice: `good` 36.00; `keep_going` 42.00.

## Recording 14 (ends 63.19; final read 63.25)
- **Straight 3–24:** Issues 0/21; posture good 20/21, unknown at 17.75. Rate 105–120 on 20 samples; PAUSED/`--` at 3.75, then BEATING.
  P voice: `good` 7.75. E word: HANDS 3–5, PUSH 5–5.75, GOOD 5.75–24. E voice: `good` 10.75.
- **Rest 24.7–33.5:** Issues 4/9 (44%): shoulders 26.75, bent elbows 27.75–29.75. All 9 samples PAUSED with rate `--`.
  P voice: `keep_going` 27.50. E word: GOOD 24.70–24.75, KEEP 24.75–33.50. E voice: `keep_going` 27.50; no spoken posture correction.
- **Phone dropped ~33.5–end:** Issues 0/31, all unknown posture. All samples LOST with rate `--`, starting 33.75.
  P voice: none. E word: KEEP throughout. E voice: only scripted `em_continue` 60.00; no drop-specific warning.

## Recording 15 (ends 71.45; final read 71.50)
- **Straight 3.5–32:** Issues 0/29, all posture good. Rate 95–111 on 28 samples; PAUSED/`--` at 3.75.
  P voice: `good` 8.00. E word: HANDS 3.5–5, PUSH 5–6, GOOD 6–26.25, KEEP 26.25–27.50, GOOD 27.50–32. E voice: `good` 10.75.
- **Reposition 32.3–34 (unknown posture):** Issues 0/2. BEATING/105 at 32.75, PAUSED/`--` at 33.75.
  P voice: none. E word: GOOD 32.3–33, KEEP 33–34. E voice: none.
- **Straight 34.5–37.3:** Issues 0/3, all posture good. PAUSED/`--` at 34.75 and 35.75; BEATING/95 at 36.75.
  P voice: `keep_going` 35.00. E word: KEEP throughout. E voice: `keep_going` 35.00. The separate unknown 37.4–38 gap has 0/1 issues, rate 93; E changes KEEP to FASTER at 37.50, with neither mode speaking.
- **Bent 38.2–43.4:** Bent issue 4/5 (80%), at 39.75–42.75; unknown at 38.75. Rate 91 on 2 samples; PAUSED/`--` on 3.
  P voice: `elbows_straight` 42.25. E word: FASTER 38.2–40, KEEP 40–41.25, ARMS 41.25–43.4. E voice: `elbows_straight` 42.25.
- **Straight 44–53.4:** Bent issue 1/9 (11%), at 44.75; remaining samples posture good. BEATING throughout; rate 103–112.
  P voice: none. E word: ARMS 44–46.25, GOOD 46.25–53.4. E voice: none.
- **Bent 54–61.5:** Bent issue 6/7 (86%), at 55.75–60.75; unknown at 54.75. Rate 101–113 on 3 samples; PAUSED/`--` on 4.
  P voice: `elbows_straight` 57.75. E word: GOOD 54–55, KEEP 55–56.75, ARMS 56.75–61.5. E voice: `elbows_straight` 57.75.
- **Lean back ~65–69.5:** Shoulder issue 3/4 (75%), at 66.75–68.75; unknown at 65.75. All samples PAUSED with rate `--`.
  P voice: `shoulders_over_hands` 68.00. E word: KEEP 65–67, SHOULDERS 67–69.5. E voice: `shoulders_over_hands` 68.00.

## Mismatches and missing reactions
- 13: False PAUSED gaps during labelled pushing. After the stop, rate and E GOOD persist to 39.50; E even speaks `good` at 36.00. P does not speak a resume cue until 41.75; E until 42.00.
- 14: Posture issues appear during rest, not labelled compression posture; do not count these as proven bent/shoulder label errors. After the drop, tracking reacts (LOST), but P has no voice reaction and E keeps the generic KEEP instruction through 63.25, including scripted speech at 60.00.
- 15: Restart is missed at 34.75–35.75; both modes say `keep_going` at 35.00 despite the straight pushing block. Bent blocks have PAUSED/rate gaps. E initially shows FASTER rather than ARMS in the first bent block, and GOOD 54–55 in the second. ARMS arrives 3.05 s and 2.75 s after those bent block starts; speech arrives after 4.05 s and 3.75 s.
- 15: Bent posture remains at 44.75 inside the straight block; E ARMS persists until 46.25 (2.25 s into straight). Lean gets a shoulder reaction, but only at 67.00 on E and 68.00 in speech. Every labelled posture-error block eventually receives a matching spoken correction; none is wholly missed.
- No reference cadence is labelled. FASTER and numeric-rate accuracy cannot be judged from these labels alone.
