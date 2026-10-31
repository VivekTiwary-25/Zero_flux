# Judge Q&A — CPR Coach

CPR Coach is a CPR practice tool, not a medical device. It does not diagnose, certify skills, or replace dispatchers or instructors. In an emergency call 112, follow the dispatcher and do not delay care for the app. Practice on a cushion, never a person.

## Build and look

**Which build is this? Has it been tried on a phone?**  
This demo is for `a99cd63`. The team reports that it passed phone tests on 31 Oct 2026. That is a team report, not a new test by the docs writer or clinical proof. Its `app/src/main` source matches fetched `origin/cpr-coach`.

**What should judges see on Home?**  
The **CPR coach** logo is at top left. The English button says **Practice**, not “Practise.” Outside the camera screens, buttons have a raised look; panels use dark glass or a soft raised light style. Camera chrome keeps its separate style; do not claim that it uses blur.

## Accuracy and limits

**What does the app measure?**  
It estimates rate, elbow angle and selected posture/pause cues from visible arm motion. It does not measure depth, force, chest recoil or hand placement, and it gives no overall CPR score. A dash or “Not measured” means no value was measured; it is not zero.

**How accurate or fast is the feedback?**  
We make no proven accuracy or phone-latency claim. Camera readings and timing rules can be checked in code; they do not prove clinical performance. Feedback depends on the view of the arms, usable readings and sound gaps.

**Can it miss pushing or a pause?**  
Yes. Movement can continue without a locked rate. The analyzer can briefly hold a prior rate, then return a no-rate state even while motion is seen. That is not proof the rescuer stopped. Do not use a missing rate or a cue as a clinical judgment.

**Why are Dashboard samples tagged?**  
The Dashboard ships examples marked **Testing data** and made-up examples marked **Synthetic data**. Those tags distinguish samples from real saved Practice sessions, which have no sample tag. Clear sample data hides both sample kinds while keeping real saved sessions. Samples are not proof of real users, lives saved or clinical performance.

## How to use it

**Why two modes?**  
Practice provides camera-based feedback and a session summary. Emergency starts spoken steps and a beat without waiting for camera tracking; camera-based cues still need usable readings. In real danger, do not delay care to set up the camera.

**Why does Emergency use short action text instead of a rate number?**  
Emergency shows steps and short action cues. Practice is where live rate readings appear. Neither mode gives an overall CPR quality score.

**Does Emergency talk more now? What are the timings?**  
Yes. The current rules add repeat praise, praise after a cleared correction, and filler cues in long sound gaps. [EMERGENCY_TALK.md](EMERGENCY_TALK.md) describes the change:

| Rule | Current setting and limit |
|---|---|
| Observe camera readings | Every 0.25 seconds, including while sound plays. A stale correction is not saved for later. |
| Rate word on screen | 0.5-second hold; this is not the spoken-cue delay. |
| Spoken rate correction | 1.5 seconds of steady readings; the rate-relock guard runs within that time. |
| Adaptive speech | 4-second cooldown; no active clip or queued scripted step. |
| Good after a spoken correction clears | Next eligible gap, with an in-range beat and no remaining correction; cooldown still applies. |
| Later Good cues | 10-second start-to-start interval, subject to readings and playback. |
| Filler cues while pushing is detected | Alternate keep-going and push cues after at least 8 seconds of silence from the last clip's **end**, not its start. |
| Scripted reminders | AED due at 30 seconds, Continue at 60 seconds, Swap every 2 minutes from audio-sequence start. Scripted clips have priority; busy sound can delay them. |

These are settings, not a guarantee of a line at an exact wall-clock time or a fixed number of lines per minute. Device audio delay is not established by these docs.

**What happens when tracking fails?**  
The Emergency beat and scripted steps do not wait for tracking. Camera-based corrections need readings; do not infer a rate when none is shown.

**What does “For the ambulance crew” show? Why can values be dashes?**  
It shows total elapsed CPR time, average measured rate, pauses and how long rate was measured. Rate and pause values are withheld unless at least 10 seconds of rate were measured. Missing values are dashes, not zero. This is a record of the app's estimates, not a clinical score.

**Why use a cushion?**  
It lets the team show rhythm and arm motion without pushing on a person. A cushion demo does not prove depth, force, chest recoil or clinical skill.

**What is the step 2 AED pop-up?**  
The guide has five adult CPR steps. On step 2, tap the **?** by the picture caption to open the AED pop-up. It explains that an AED is a portable box that can give an electric shock and spoken prompts, and names malls, airports, metro stations and offices as places to find one. Follow the device and dispatcher; the app is not a substitute.

**What is the exact AED reminder in Emergency?**  
The English screen text is **“Ask someone to bring an AED (the box that can restart the heart)”**. It appears while the `em_aed` clip plays. The reminder is due at 30 seconds after the audio sequence starts, but another clip can delay it. The recorded voice and screen text need not use the same words.

**Does Emergency contact or Call 112 place a call?**  
No automatic call is placed. Home opens a contact form; saving only stores the contact. On the Emergency screen, the saved-contact button opens the dialer for that number. Call 112 opens the dialer for 112. The user must then choose whether to call. In the demo, leave contact fields blank and cancel the dialer with Back; never press Call.

**Is this medical advice or proof of CPR skill?**  
No. It is a practice aid, not diagnosis or certification. Call 112, follow the dispatcher and never delay care to use it.

## Shipped pictures

**Are the pictures AI-made? Who approved them?**  
All the teaching pictures ship: five guide pictures, two role pictures and one hand-placement picture. The team reports that they were made with OpenAI, checked by the image worker and boss, and approved by the team lead. Those are team-reported roles, not facts proved by code. The checks were not by medical staff and were not a medical review. The pictures teach a pose; they do not prove that the camera can check hand placement.

## Languages

**Which languages were tried?**  
The team reports that Hindi, Kannada, English and Gujarati were tried on `a99cd63` and worked well. This was a team try, not formal translation review or medical review. Do not call the wording, voices or medical content formally validated.

**What about the other six languages?**  
Tamil, Telugu, Malayalam, Marathi, Bengali and Odia remain unreviewed. The picker lists ten languages; being listed does not mean a language has been reviewed. Use English for this demo.

## Source notes

Feature claims were read in `app/src/main` from the fetched `origin/cpr-coach` source, which matches `a99cd63`. In this list, `ui/`, `engine/` and bare Kotlin file names are short paths under `java/com/zeroflux/cprcoach/`; asset and resource paths start at `app/src/main`:

- **Home and styles:** `assets/strings/en.json:4`; `java/com/zeroflux/cprcoach/ui/HomeScreen.kt:115-162,202-268`; `ui/Theme.kt:37-59,171-256`.
- **Readings and limits:** `engine/LiveAnalyzer.kt:17-41,135-162`; `engine/SessionMetrics.kt:7-15,43-63`; `ui/Contract.kt:29-49`; `ui/ScorecardScreen.kt:118-137`. These define the reported values, not clinical validity.
- **Dashboard:** `assets/strings/en.json:127-138`; `assets/dashboard_samples.json` has testing and synthetic tags; `ui/DashboardLogic.kt:77-98,116-125`; `ui/DashboardScreen.kt:553-563`.
- **Mode flow:** `PractiseHost.kt:119-180,270-286,377-385`; `EmergencyHost.kt:174-209,222-244`.
- **Emergency speech and summary:** `engine/EmergencyCoach.kt:28-44,99-126,169-176`; `engine/CoachPolicy.kt:46-83,86-102,195-229`; `engine/EmergencySequence.kt:18-62`; `EmergencyHost.kt:100,198-209,226-230,366-439`; `ui/EmergencyScreen.kt:264-268,309-317`; [EMERGENCY_TALK.md](EMERGENCY_TALK.md), “Change” and “Why voice was unreliable.” No replay result or old measurement is claimed here as a fresh phone result.
- **Guide and pictures:** `ui/GuideScreen.kt:160-193,228-284,423-470`; `ui/WalkthroughScreen.kt:120-124,260-263`; `ui/EmergencyScreen.kt:140-145`; the eight files in `res/drawable-nodpi`; `assets/strings/en.json:74,96-110,139-140`.
- **Contact and dialer:** `ui/EmergencyContactDialog.kt:35-68`; `AppRoot.kt:226-245`; `EmergencyHost.kt:61-80,237-240`.
- **Language list:** `ui/Contract.kt:6-18`.

Source can show what ships and what the code does, not that a phone test passed, how a language sounded to people, or who made, checked or approved an image. Phone pass, four-language use and image origin/check/approval are attributed team reports. This docs edit did not run the app, repeat tests, or conduct a translation or medical review.
