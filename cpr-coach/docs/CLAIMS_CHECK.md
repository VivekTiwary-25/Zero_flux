# Claims check: numbers in the pitch and demo scripts

Rule for this project: never invent numbers. Any accuracy or latency shown must be really measured.
Checked 2026-10-31 against ref copy of branch cpr-coach (FETCH_HEAD 4e46cfd8).
Scope: every number or measurable claim in PITCH.md, DEMO_SCRIPT.md, DEMO_2MIN.md, JUDGE_QA.md, HOW_IT_WORKS.md.
Line numbers are from the ref copy. NOT FOUND rows come first.

## NOT FOUND (no measurement doc backs these)

| # | Claim | File and line | Evidence status |
|---|---|---|---|
| 1 | No accuracy figure for compression rate. Counted runs are not done. | PITCH.md:29; DEMO_SCRIPT.md:105,111,200; DEMO_2MIN.md:47,90; JUDGE_QA.md:9-10,75; HOW_IT_WORKS.md:26 | NOT FOUND. MEASUREMENTS.md table has zero counted rows. ACCURACY_TEST.md is an unrun procedure. |
| 2 | No latency or feedback-speed figure. | PITCH.md:29; DEMO_SCRIPT.md:201; DEMO_2MIN.md:92; JUDGE_QA.md:12-13; HOW_IT_WORKS.md:26 | NOT FOUND. Nothing to corroborate, which is what the scripts say. |
| 3 | Rate detection on a real person is not proven. The one real recording gave no usable beat. | PITCH.md:29; DEMO_SCRIPT.md:200; DEMO_2MIN.md:49; JUDGE_QA.md:10,75 | NOT FOUND as proven accuracy. REAL_TRACE_1.md analyses that recording (294 frames, no sustained verified rate). RATE_*.md are replays and simulations with no human-count truth. |
| 4 | The redesigned Emergency screen has not yet run with a real person. | PITCH.md:29; JUDGE_QA.md:73-75; DEMO_2MIN.md:108 | NOT FOUND. Stated as unproven in the scripts themselves. |
| 5 | Connected-call audio behaviour is unproven. | PITCH.md:29; JUDGE_QA.md:73-75 | NOT FOUND. CALL_AUDIO_NOTES.md is design notes plus an unrun phone test. M0 notes in MEASUREMENTS.md are mode observations only, unverified by ear. |
| 6 | Fully offline. Pose, speech and clips run on the phone. No network. | PITCH.md:21; DEMO_SCRIPT.md:203; DEMO_2MIN.md:96; JUDGE_QA.md:59-60; HOW_IT_WORKS.md:8,13,22 | NOT FOUND. No network-capture measurement exists. Code-level only (SPEC_CHECK.md). |
| 7 | One camera cannot measure depth, force, recoil or hand placement. The app does not try. | PITCH.md:29; DEMO_SCRIPT.md:202; DEMO_2MIN.md:93-94; JUDGE_QA.md:16; HOW_IT_WORKS.md:26 | NOT FOUND by design. No doc attempts it. POSTURE_GEOMETRY.md gives synthetic 2D limits only. |
| 8 | Spoken coaching verified only for a subset. Other languages are unchecked drafts. | PITCH.md:19; DEMO_SCRIPT.md:206,219; DEMO_2MIN.md:100; JUDGE_QA.md:5,57,73-75 | NOT FOUND for human check. CLIP_STATUS.md says "checked by a person" is NO for every clip. |
| 9 | Live behaviours: one-tap Emergency with instant beat and steps, rate only after steady readings, one correction at a time, scorecard rows, ambulance summary with dashes. | PITCH.md:25; DEMO_SCRIPT.md:92,111,117-124; DEMO_2MIN.md:47,49; JUDGE_QA.md:19,24,29-30 | NOT FOUND on a phone. STATUS.md marks live session, scorecard and voice/metronome sound NOT DONE. PHONE_TEST.md boxes are unfilled. |
| 10 | 100-120 per minute target. Two-minute session. 3-2-1 countdown. Swap every 2 minutes. | PITCH.md:11; DEMO_SCRIPT.md:80,84,111,150; DEMO_2MIN.md:31; HOW_IT_WORKS.md:10-11 | NOT FOUND as measured values. These are guideline targets and design constants, not results. Clip files for countdown, swap and AED exist (see measured rows). |
| 11 | Script timestamps (0:00-0:20, 1:25, and so on). Two phones, Android 11 or newer. | PITCH.md:3,5; DEMO_SCRIPT.md:9; DEMO_2MIN.md:11,43-51 | N/A. PITCH.md:3 says times are guides. Phone items are test prerequisites, not results. |
| 12 | Listening mic mutes coaching voice. Metronome keeps playing. | DEMO_SCRIPT.md:40,177; DEMO_2MIN.md:36 | NOT FOUND on a phone. Code review only (REVIEW_AUDIO_MUTE.md). |

## Measured or corroborated

| # | Claim | File and line | Measured in |
|---|---|---|---|
| 13 | The picker lists ten languages in their own script. | PITCH.md:19; DEMO_SCRIPT.md:56,58; DEMO_2MIN.md:100 | CLIP_COVERAGE.md (10 languages, played keys OK). STRINGS_COVERAGE.md (en has 69 keys, each other language 64, same 5 debug keys missing). |
| 14 | Call 112 opens the dialer with 112 filled in and never places the call. | PITCH.md:21; DEMO_SCRIPT.md:117,126,205; DEMO_2MIN.md:98; JUDGE_QA.md:47-48 | STATUS.md (PHONE RMX3392: dialer opened with 112, call never pressed). |
| 15 | The one real recording gave no usable beat. | JUDGE_QA.md:9-10 | REAL_TRACE_1.md (294 frames over 37.537 s, 7.81 fps effective, median latency 144.5 ms, intermittent periodicity only). |
| 16 | Pose throughput single readings (CPH2467 22-24 results/s at 40 ms; RMX3392 22/s at 72 ms; M0 hand-held 26 fps at 73 ms). None of these figures is quoted in the scripts. | Quoted nowhere in the five files | MEASUREMENTS.md (M0 notes, single readings, not accuracy). POSE_SPEED.md (nobody in frame; person-in-frame rate unmeasured). |
| 17 | Machine check of every coaching clip in ten languages (format, duration, transcription). | JUDGE_QA.md:57 (subset verified) | CLIP_CHECK.md and CLIP_CHECK_0400.md (150 clips, 42 flagged, mostly or/ml English copies). Human listening still NO per CLIP_STATUS.md. |
| 18 | English and Hindi are the first-build languages. The app runs end to end on two real phones with no known crash in the latest build. | JUDGE_QA.md:5,57 | STATUS.md (RMX3392 driven over adb, no crashes). Earlier-project speech evidence is outside this repo. |
| 19 | Elbow indicator says straight in teal or bent in amber. The scripts quote no angle numbers. | DEMO_SCRIPT.md:92; DEMO_2MIN.md:31 | POSTURE_VALIDATION.md (replay: bent below 150, straight from 160; the bent block is the recorder's account, partly circular). POSTURE_GEOMETRY.md (synthetic only). |
| 20 | Rate gate and window studies behind the "steady readings" behaviour. The scripts quote no figure from them. | JUDGE_QA.md:9-10,19 (background) | RATE_REAL_2.md, RATE_ALT_METHOD.md, RATE_FPS_STUDY.md, RATE_CHANGE_NOTE.md, RATE_NEXT_STEPS.md, SMOOTHING.md, GUARD_STUDY.md. Replays and simulations only. No human-count truth, so no accuracy. |

## Bottom line

The five files quote no accuracy or latency numbers, and that matches the evidence: the counted-runs table is empty.
The numbers they do state are setup facts (112, ten languages, 100-120 target, two minutes), and each is either corroborated above or labelled a guide, prerequisite or design constant.
Pose-speed and clip-machine-check numbers exist in MEASUREMENTS.md, POSE_SPEED.md and CLIP_CHECK*.md but are correctly not quoted in the scripts.
