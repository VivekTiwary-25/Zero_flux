# CPR Coach: two-minute demo

## Before the clock starts

- Use demo build `a99cd63` in English. Grant camera access, finish the first-use walkthrough, and check sound before the show. Then return to the Android home screen, with the app icon in view.
- Use a cushion, never a person. One person speaks, one pushes, and one holds the phone still at the rescuer's side with the arms in view and taps controls.
- Turn media sound up, disconnect headphones/Bluetooth, leave the mic off, keep the screen on, and have a spare phone ready. Rehearse the taps and time marks.
- Use a demo phone with no saved private contact. Show the blank Emergency contact form; do not save or pick a contact. Never press Call in the dialer.
- Home has the **CPR coach** logo at top left and the English button **Practice**. Outside the camera screens, buttons have a raised look; panels use dark glass or a soft raised light style. Do not describe camera panels as glass or blurred.
- The OpenAI teaching pictures ship in the guide, walkthrough and Emergency opening. The team reports that the image worker and boss checked them and the team lead approved them. This was not a medical-staff review; code cannot prove who made or approved them.
- The team reports that `a99cd63` passed phone tests on 31 Oct 2026 and that Hindi, Kannada, English and Gujarati were tried and worked well. This was a team try, not formal translation or medical review. Tamil, Telugu, Malayalam, Marathi, Bengali and Odia remain unreviewed.

## Live run: 0:00–2:00 (120 seconds total)

Start the clock with the app icon in view. The words in quotes are the presenter's lines. Each part includes taps, screen changes and app sound. Move on at each mark; do not wait for a rate or force a cue.

| Time | Seconds | Screen and action | Say |
|---|---:|---|---|
| 0:00–0:16 | 16 | Tap the CPR Coach app icon. Show Home; point to the top-left CPR coach logo, Practice button and raised controls. | “CPR Coach is a practice tool, not a medical device. In real danger call 112, follow the dispatcher, and never delay care for this app. We use a cushion, never a person.” |
| 0:16–0:34 | 18 | Open CPR guide. Show the shipped picture on step 1. Tap Next step, then the **?** by the step 2 picture caption to open the AED pop-up. Close it and return Home. | “Five steps explain adult CPR. These are teaching pictures, not camera checks. Step two has an AED pop-up: a portable box that can give a shock. Follow its prompts and the dispatcher.” |
| 0:34–0:44 | 10 | Open Emergency contact. Show the blank name and phone fields. Dismiss without saving; return Home. | “Saving a contact does not call anyone. In Emergency, its button only opens the dialer. We leave this blank.” |
| 0:44–0:54 | 10 | Open Dashboard. Point to **Testing data** and **Synthetic data** tags; return Home. | “These tagged samples are test and made-up data, not proof from real users or real care.” |
| 0:54–1:12 | 18 | Tap **Practice**. Frame the arms from the side, tap Start when ready, and push on the cushion. Tap Stop, then Home on the session screen. | “Practice estimates pace and arm posture. It can miss readings. It does not measure depth, force, chest recoil or hand placement, and gives no overall CPR score.” |
| 1:12–1:50 | 38 | Tap Emergency. Keep the phone still and push to the beat. Let the opening voice play; speak in a gap. The AED reminder is due 30 seconds after the audio sequence starts; queued sound may delay it. Point to it while it plays, without waiting past this part. Near the end tap Call 112, show the dialer, then press Back without pressing Call. Return to the app. | “Emergency starts steps and a beat without waiting for the camera. It now talks more, with short cues when readings and sound gaps allow. Call 112 opens the dialer, not a call.” |
| 1:50–1:56 | 6 | Hold to stop. Show **For the ambulance crew**; read only what is on screen. | “A dash means not measured, not zero. These are estimates, not a clinical score.” |
| 1:56–2:00 | 4 | Keep the summary visible. End at 2:00. | “Call 112. Follow the dispatcher. Never delay care.” |
| **Total** | **120** | | |

## Emergency words and timing

- The English AED reminder text is exactly **“Ask someone to bring an AED (the box that can restart the heart)”**. It is shown while the `em_aed` clip plays, not just because the elapsed clock reaches 30 seconds. The voice clip and the on-screen line need not use the same words.
- As set out in [EMERGENCY_TALK.md](EMERGENCY_TALK.md), Emergency observes readings every 0.25 seconds, even while a clip plays. A rate word has a 0.5-second screen hold. A spoken rate correction needs 1.5 seconds of steady readings, a 4-second adaptive-speech cooldown, and a free playback slot. These are code rules, not a promised end-to-end response time.
- Good can follow a cleared spoken correction at the next eligible gap. Later Good cues use a 10-second interval, subject to readings and playback. While pushing is detected, filler cues alternate after at least 8 seconds of silence measured from the end of the last clip. They do not play on demand.
- Scripted steps have priority. AED is due at 30 seconds, Continue at 60 seconds, and Swap every 2 minutes after the audio sequence starts; busy playback can delay them. This short Emergency segment will not reach Continue or Swap.

## If a reading or sound is missing

- If Practice has no rate, say: “It cannot get a reading; we will not guess.” Movement can continue without a locked rate. Do not claim a pause is real just because a rate drops out.
- Emergency's scripted steps and beat do not wait for camera readings. Do not promise a rate, correction or fixed cue count.
- If sound fails, state that; use the spare phone if needed. Do not pretend a line played.
- If an expected screen fails to appear, state that; do not pass off another screen as the feature.
- Cancel the dialer with Back. Never press Call or place a call during the demo.

## Limits for questions

- No depth, force, chest recoil or hand-placement measurement; no overall CPR score, certification or claim of clinical proof. Quote no accuracy or phone-latency figure.
- Guide and hand pictures teach a pose; they do not show that the camera can check hand placement. Team image approval is not medical review.
- Four tried languages do not mean ten reviewed languages. The phone-pass and language reports are team reports, not formal validation.
- Dashboard tags mark samples. Real saved Practice sessions have no sample tag. Samples are not evidence of real users or lives saved.
- In real care, call 112 and follow the dispatcher. Never delay care to set up the camera or app.

## Source notes

Feature claims use `app/src/main` on the fetched `origin/cpr-coach` source, which matches demo build `a99cd63`. In this list, `ui/`, `engine/` and bare Kotlin file names are short paths under `java/com/zeroflux/cprcoach/`; asset and resource paths start at `app/src/main`:

- `assets/strings/en.json:4,64-86,128-140`: Practice spelling, summary labels, exact AED reminder, guide scope, sample tags and pop-up text.
- `java/com/zeroflux/cprcoach/ui/HomeScreen.kt:115-162,202-268` and `ui/Theme.kt:179-256`: logo, Home controls, raised buttons and non-camera panel styles.
- `ui/GuideScreen.kt:160-193,228-284,423-470`: five steps, shipped pictures and step 2 AED pop-up. `ui/WalkthroughScreen.kt:120-124,260-263` and `ui/EmergencyScreen.kt:140-145` use the other teaching pictures.
- `ui/EmergencyContactDialog.kt:35-68`, `AppRoot.kt:226-245` and `EmergencyHost.kt:61-80,237-240`: contact form/save and dialer-only call actions.
- `ui/DashboardLogic.kt:77-98,116-125` and `ui/DashboardScreen.kt:553-563`: untagged saved sessions, sample clearing and visible tags.
- `PractiseHost.kt:270-286,377-385`, `engine/LiveAnalyzer.kt:135-162`, `engine/SessionMetrics.kt:7-15` and `ui/ScorecardScreen.kt:118-137`: Practice controls and limited measured values.
- `EmergencyHost.kt:174-209,226-230,366-439`, `engine/EmergencyCoach.kt:28-44,99-126,169-176`, `engine/CoachPolicy.kt:86-99,219-229` and `engine/EmergencySequence.kt:18-62`: audio independence, speech rules, displayed reminder and due times. `ui/EmergencyScreen.kt:264-268,309-317` shows missing summary values as dashes.

This docs edit did not run the app or repeat phone tests. Phone pass, four-language use, OpenAI origin and human image-check/approval roles are team reports, not facts proved by code. Timing rules do not prove device or clinical performance.
