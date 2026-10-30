# CPR Coach docs index

Every file in docs/, grouped by use. One plain line each.

## How it works

- [`SPEC.md`](SPEC.md) — Build brief: what the app is, screen behaviour, and build order.
- [`HOW_IT_WORKS.md`](HOW_IT_WORKS.md) — One-page camera-to-voice pipeline and limits, for judges.
- [`STATUS.md`](STATUS.md) — Evidence table of features actually shown to work.
- [`README.md`](README.md) (older) — Earlier index of this folder, replaced by this file.
- [`INSTALL.md`](INSTALL.md) — Steps to build the APK and push models to a team phone.
- [`LINT.md`](LINT.md) — Lint run with crash-level findings ranked first.
- [`SPEC_CHECK.md`](SPEC_CHECK.md) — Section 3 requirements checked against source, met or missing.
- [`DESIGN_AUDIT.md`](DESIGN_AUDIT.md) — Source-only UI review with failures ranked by judge impact.
- [`POSTURE_GEOMETRY.md`](POSTURE_GEOMETRY.md) — Measured 2D limits and proposed posture gates.
- [`POSE_SPEED.md`](POSE_SPEED.md) — Pose frame-rate audit and debug switches.
- [`CALL_AUDIO_NOTES.md`](CALL_AUDIO_NOTES.md) — Audio routing pitfalls between media and call usage.
- [`SMOOTHING.md`](SMOOTHING.md) — Overlay smoothing settings picked from joint replay.

## Testing on phones

- [`PHONE_TEST.md`](PHONE_TEST.md) — Tick-box checklist for a two-person real-phone test.
- [`ACCURACY_TEST.md`](ACCURACY_TEST.md) — Three hand-counted runs and trace analysis procedure.
- [`CONTROLLED_TEST.md`](CONTROLLED_TEST.md) — Timed push and pause blocks with a counter and stopwatch.
- [`NEXT_TEST_0700.md`](NEXT_TEST_0700.md) — Short scripted Practise session for one phone.
- [`MEASUREMENTS.md`](MEASUREMENTS.md) — Table for counted runs plus early single-reading notes.
- [`FILMING.md`](FILMING.md) — How to film a real-person test session.
- [`EMULATOR_RUN.md`](EMULATOR_RUN.md) — Record of one emulator walkthrough and its limits.
- [`LABELS_CHECK.md`](LABELS_CHECK.md) — Machine check of labels.csv against its recordings.

## Measurements and studies

- [`REAL_TRACE_1.md`](REAL_TRACE_1.md) — What the first real wrist recording shows about rate.
- [`RATE_REAL_2.md`](RATE_REAL_2.md) — Replay evidence for the shorter rate window.
- [`RATE_NEXT_STEPS.md`](RATE_NEXT_STEPS.md) — Decision rule for the next rate recording.
- [`RATE_ALT_METHOD.md`](RATE_ALT_METHOD.md) — Alternative rate methods compared, autocorrelation kept.
- [`RATE_FPS_STUDY.md`](RATE_FPS_STUDY.md) — Simulated frame-rate and movement-size effects on rate.
- [`RATE_CHANGE_NOTE.md`](RATE_CHANGE_NOTE.md) — How rate changes can briefly read as paused.
- [`POSTURE_VALIDATION.md`](POSTURE_VALIDATION.md) — Posture judge checks against labels and synthetic tests.
- [`GUARD_STUDY.md`](GUARD_STUDY.md) — Landmark plausibility sweep across settings and frames.

## Reviews

- [`REVIEW_AUDIO_MUTE.md`](REVIEW_AUDIO_MUTE.md) — Why listening can silently consume speech clips.
- [`REVIEW_COACH_VOICE.md`](REVIEW_COACH_VOICE.md) — Voice-rule review with pause and resume bugs.
- [`REVIEW_LANDMARK_GUARD.md`](REVIEW_LANDMARK_GUARD.md) — Independent review finding overlay-hold bugs.
- [`REVIEW_SHOULDERS_LINE.md`](REVIEW_SHOULDERS_LINE.md) — Back-translation check of the shoulders cue.

## Voice and translations

- [`CLIP_CHECK.md`](CLIP_CHECK.md) (older) — First machine clip check, extended by CLIP_CHECK_0400.
- [`CLIP_CHECK_0400.md`](CLIP_CHECK_0400.md) — Newer machine check including the shoulders cue.
- [`CLIP_COVERAGE.md`](CLIP_COVERAGE.md) — Tool that lists playable clip keys across ten languages.
- [`CLIP_STATUS.md`](CLIP_STATUS.md) — Generated-clip record, none checked by a person.
- [`STRINGS_COVERAGE.md`](STRINGS_COVERAGE.md) — Screen-text key coverage across ten languages.
- [`HINDI_REVIEW.md`](HINDI_REVIEW.md) — Machine review of Hindi strings and clips.
- [`KANNADA_REVIEW.md`](KANNADA_REVIEW.md) — Machine review of Kannada strings and clips.
- [`OTHER_REVIEW.md`](OTHER_REVIEW.md) — Machine review of Marathi, Gujarati, Bengali, and Odia text.
- [`SOUTH_REVIEW.md`](SOUTH_REVIEW.md) — Machine review of Tamil, Telugu, and Malayalam text.
- [`VOICE_OPTIONS.md`](VOICE_OPTIONS.md) — Alternative voice engines for natural Hindi and Kannada.

## Demo

- [`DEMO_2MIN.md`](DEMO_2MIN.md) — Two-minute stage run with safety lines and roles.
- [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) — Three-minute stage run with screen meanings and fallbacks.
- [`DEMO_TEST_5MIN.md`](DEMO_TEST_5MIN.md) — Five-minute live behaviour check before staging.
- [`PITCH.md`](PITCH.md) — Spoken two-minute pitch script with timings.
- [`JUDGE_QA.md`](JUDGE_QA.md) — Prepared answers on accuracy, languages, privacy, and scope.
