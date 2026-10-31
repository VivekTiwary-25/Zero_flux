# CPR Coach: two-minute demo

## Before the clock starts

- Use the English demo build. Confirm CPR guide, Emergency contact and Dashboard are present; rehearse on the stage build.
- Use a cushion, never a person. Clear space for the person pushing. One person speaks, one pushes, one holds the phone still at the rescuer's side with the arms in view and taps controls.
- Turn media sound up, disconnect headphones/Bluetooth, leave the mic off, keep the screen on, and have a spare phone ready. Dry-run camera access and sound.
- Do not expose private contact details. Open the Emergency contact form but do not save a contact or call anyone.
- For a lean, push firm and steady, keep it short, and keep the phone still. Recording 16 documents a known problem: weak, uneven pushing can lose its rate and show Pause while pushing continues. This is not proof that pushing stopped; do not force a cue or conceal a missed cue.

## Live run: 0:00–2:00 (120 seconds total)

The quoted words are the presenter's lines. The time includes taps, transitions, and app audio. Move on at each time mark; do not wait for a rate or cue.

| Time | Seconds | Screen and action | Say |
|---|---:|---|---|
| 0:00–0:16 | 16 | Launch from the Android home screen; show Home. | “CPR Coach is a practice tool, not a medical device. In a real emergency, call 112 and follow the dispatcher. Never delay care for this app. We use a cushion, never a person, and will not make a call.” |
| 0:16–0:30 | 14 | Open CPR guide. Show step 1, tap Next once to step 2 and show its AED explainer pop-up if available; dismiss and return Home. | “The guide gives five short steps for adult CPR. Step 2 explains an AED: an automated external defibrillator, a portable device that can deliver a shock and gives spoken instructions. It does not replace the dispatcher.” |
| 0:30–0:42 | 12 | Open Emergency contact; point at blank name and phone fields. Close and return Home without entry. | “You can save an emergency contact here. We leave the form blank; saving a contact does not call anyone.” |
| 0:42–0:54 | 12 | Open Dashboard. Point out “Testing data” and “Synthetic data” labels; return Home. | “These tagged examples are testing and made-up data, not real-user evidence. They do not prove the app works in real care.” |
| 0:54–1:14 | 20 | Tap Practise. Frame arms from the side; start when ready and push on cushion. If time, brief lean with firm steady pushes, phone still. Stop and return Home. | “Practise uses arm motion to estimate pace and show posture or pause cues. It does not measure depth or hand placement and gives no overall CPR score.” |
| 1:14–1:46 | 32 | Tap Emergency. Keep camera on and phone still; push to beat. Let opening voice play; speak in a gap. In final seconds tap Call 112 to display dialer, then cancel with Back without pressing Call. Return to app. | “Emergency gives spoken steps and a beat without waiting for camera tracking. Camera-based cues depend on readings. This button opens the dialer; we cancel it. No call.” |
| 1:46–1:52 | 6 | Hold Stop. Show “For the ambulance crew”; do not invent values. | “A dash means not measured, not zero.” |
| 1:52–2:00 | 8 | Keep summary visible. End at 2:00. | “This is a practice tool, not a medical device. In real danger call 112 and follow the dispatcher. Never delay care.” |

## If a cue or sound is missing

- If Practise loses the arms or gives no rate, say: “It cannot get a reading; we will not guess.” Do not claim a pause is real while pushes continue.
- If Emergency loses tracking, its beat and spoken steps continue. Do not promise a correction or rate.
- Corrections, praise and reminders depend on readings and playback gaps. Do not promise a set count, fixed delay, or cue on demand; this short demo will not reach all reminders.
- If sound fails, state that; use the spare phone if needed. Do not pretend audio played.
- If a screen is absent from the stage build, say so; do not pass off another screen as the feature.
- If the dialer opens, cancel with Back. Never press Call or place a call during the demo.

## Limits for questions

- No depth, force, chest recoil or hand-placement measurement. No overall CPR score, certification or clinical proof. Session values are readings available during that run, not a clinical score; a dash means not measured.
- Quote no accuracy or speed figure. Rate accuracy on a real person is not clinically proven. Recording 16 shows a known rate-dropout/pause problem during weak or uneven pushing; the proposed estimator and pause-rule changes were held back because they introduced other errors.
- Guide images are illustrations, not camera checks of hand placement.
- Use English for the demo. Translations noted in `cpr-coach/docs/STRINGS_UNREVIEWED.md` are machine-translated or copied prototype text and have not had human review; do not call them checked.
- Dashboard “Testing data” and “Synthetic data” entries are examples, not evidence of real users or lives saved. Real saved sessions have no sample tag.
- In real care, call 112 and follow the dispatcher. Never delay care to set up the camera or app. Never push on a person for a demo.

## Source notes

Claims checked against `origin/takeover/t5-final` app code and docs, and the AED wording in `origin/takeover/t8-aed:cpr-coach/app/src/main/assets/strings/en.json`. The upstream `origin/cpr-coach` demo and Q&A informed this rewrite. The guide code has step 2 text but no AED popup in `GuideScreen.kt`; therefore only mention/show a popup if it is present in the actual stage build. This script is not proof of a phone run; rehearse on stage build before use.
