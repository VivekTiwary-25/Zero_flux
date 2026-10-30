# Evidence status draft for judges

Evidence reflects documented tests, not feature intent. PHONE means a real device; REPLAY means real-recording replay; SYNTHETIC means made-up frames or state sequences only. Earlier status notes are limited and may not describe the current build.

| Feature | Evidence | Source doc/commit |
|---|---|---|
| Home, language picker, walkthrough, camera/framing screens | PHONE — RMX3392; earlier screen smoke only. Does not prove current-build details or camera tracking. | `docs/STATUS.md` (older report); `docs/EMULATOR_RUN.md` (emulator only) |
| Pose tracking | PHONE — RMX3392 outdoor hand-held check recorded one elbow reading; not sustained tracking validation. | `docs/MEASUREMENTS.md` (M0, 30 Oct) |
| Compression rate | REPLAY — real-recording estimator replays produce some candidate reads; not ground-truth accuracy. Counted accuracy and reliable rate display remain NOT DONE. | `docs/RATE_REAL_2.md`; `docs/REAL_TRACE_1.md`; `docs/MEASUREMENTS.md` |
| Ghost-wrist rejection | REPLAY — tests/replay evidence for the guard, but separate review found failure cases; no blanket rejection claim or phone validation. | `e50a11a`; `c578e2f`; `docs/REVIEW_LANDMARK_GUARD.md` |
| Bent elbows | REPLAY — posture replay against recordings; some labels are unconfirmed, so this is not validated detection. | `docs/POSTURE_VALIDATION.md`; `6cf3409` |
| Lean | SYNTHETIC — lean-direction test uses synthetic frames; no labelled lean recording, thresholds remain unmeasured guesses. | `d35f114`; `docs/POSTURE_VALIDATION.md` |
| Pause / “Keep going” | SYNTHETIC — state-sequence tests cover stillness and re-arming; no documented phone demonstration. | `4dfb3b6`; `1c3aa4d`; `docs/NIGHT_DECISIONS.md` |
| Voice clips per language (machine checked) | NOT DONE for human/on-device confirmation. Automated ASR/format checks covered 150 clips across 10 languages; flags remain, and ASR does not verify language quality. | `docs/CLIP_CHECK_0400.md`; `4100605` |
| Voice clips, listened to by a person | NOT DONE — docs say nobody has listened to any clip. | `docs/CLIP_STATUS.md`; `docs/JUDGE_QA.md` |
| Emergency instruction word | SYNTHETIC — policy/replay tests cover word selection; redesigned Emergency with a real rescuer is explicitly unproven. | `a904d2d`; `17d20eb`; `docs/DEMO_2MIN.md` |
| Scorecard | NOT DONE — no documented completed real-phone session or scorecard check; emulator run did not complete Practise. | `docs/PHONE_TEST.md` (unchecked checklist); `docs/EMULATOR_RUN.md` |
| Hands-free commands | NOT DONE — emulator only showed the listening indicator/auto-mute; no command was spoken and confirmed. | `docs/EMULATOR_RUN.md`; `docs/PHONE_TEST.md` (unchecked checklist) |

Do not describe replay or synthetic evidence as phone-tested, and do not quote rate accuracy: real recordings lack synchronized human counts.
