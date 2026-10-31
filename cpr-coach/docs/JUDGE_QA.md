# Judge Q&A — CPR Coach

CPR Coach is a CPR practice tool, not a medical device. It does not diagnose, certify skills, or replace dispatchers or instructors. In an emergency call 112, follow the dispatcher and do not delay care for the app. Practice on a cushion, never a person.

## Accuracy and limits

**What does the app measure?**  
It estimates compression rate, elbow angle and pauses from visible arm motion. It does not measure compression depth, force, chest recoil or hand placement, and it gives no overall CPR score. A dash or “Not measured” means the app did not measure that value; it is not zero.

**How accurate is the compression rate?**  
We make no proven accuracy claim. Testing includes real phone recordings replayed through the app's scorecard code, but that is not clinical validation or a completed accuracy study. The estimator can miss ongoing pushing; Recording 16 documents that limit.

**How fast is feedback?**  
We quote no latency figure. Feedback depends on visible arms, readings and the app's timing rules.

**Why are synthetic sessions on the dashboard, and why are they tagged?**  
The Dashboard includes testing examples derived from recorded sessions and synthetic, made-up examples to demonstrate the charts. The “Testing data” and “Synthetic data” tags distinguish both sample kinds from real saved practice sessions. Code treats real sessions as untagged and can clear the samples without deleting real sessions. These examples are not real-user evidence and do not demonstrate clinical performance.

**What is the known lean-pause problem?**  
With weak or uneven pushing during a lean, motion can continue while the rate estimator rejects the movement. When it has no beat and its two-second rate hold expires, the screen can show Pause even though pushes continue. Recording 16 found rate on 13 of 77 reads during trace 104–123 seconds; a tried rate change raised that to 45/77, but also raised rates in an unclear block and made off-pace readings worse across recordings, so it was not applied. A tried pause-rule change still produced the false cue and lost a real rest cue in recording 14, so it too was held back. This is a known limitation, not proof that the rescuer stopped.

**How do you know the numbers are not guesses?**  
The app derives readings from camera-observed arm motion and can show that a value was not measured. This does not make the estimates clinically validated. Per-frame traces can be saved on-device for testing; no accuracy guarantee follows from that.

## How to use it

**Why two modes?**  
Practise provides camera-based feedback and a session summary. Emergency starts spoken steps and a metronome without waiting for camera tracking; camera-based corrections still depend on usable readings.

**Why one word and no number in Emergency?**  
Emergency prioritizes the next action instead of presenting a potentially unreliable numeric rate. Practise is where measured readings and the session summary appear.

**What does “For the ambulance crew” show? Why can values be dashes?**  
The summary presents total CPR time, average measured rate and pauses. A missing reading is a dash, not zero.

**Why not use a sensor manikin?**  
Sensor manikins can provide useful feedback but require equipment. CPR Coach is designed for phone-based practice with a cushion; it only estimates the motion it can observe.

**Why practise on a cushion instead of a manikin?**  
A cushion supports safe rehearsal of rhythm and arm motion. It does not establish clinical effectiveness.

**How is this different from a metronome app?**  
The beat sets a pace; CPR Coach also observes arm motion and estimates rate and selected posture/pause cues. Those estimates are limited and are not an overall quality score.

**What happens when tracking fails?**  
Emergency's spoken steps and metronome continue; tracking failure does not block them. Do not infer a rate when the camera cannot provide one.

**What is an AED?**  
An AED is an automated external defibrillator: a portable device that can deliver an electric shock to help restart the heart. It gives spoken instructions. The English guide text names shopping malls, airports, metro stations and offices as places they may be found. Follow the device prompts and emergency dispatcher.

**Does the emergency contact or Call 112 button place a call?**  
No automatic call is placed. Saving a contact only stores its name and number. The Emergency contact control opens the dialer for that contact, and Call 112 opens the dialer for 112; the user must choose whether to call. The app does not auto-call.

**Is this medical advice?**  
No. It is a practice aid, not diagnosis or certification. In an emergency call 112, follow the dispatcher, and do not delay care to use the app.

## Pictures (only if pictures ship)

**Are the pictures AI-made, and how were they checked?**  
Only if pictures ship: the teaching drawings are AI-made. People and tools checked them against teaching points: hands on the lower half of the breastbone, straight arms, shoulders above hands, and correct fingers. That was a check against those points, not a clinical review or proof that hand placement can be detected by the camera.

## Languages, offline and privacy

**Which translations have been reviewed?**  
Do not say all languages were human-reviewed. `cpr-coach/docs/STRINGS_UNREVIEWED.md` records machine-translated strings across ten languages and identifies Hindi/Kannada guide text copied from the prototype (some manually edited); no person has checked these as of that record. The note explicitly says a native speaker should review them. Use English for this demo unless the chosen language has had appropriate review.

**Why ten languages?**  
The picker lists ten languages. Availability in the picker does not mean every translation or voice has been human-reviewed or verified.

**Does it work offline? What data does it collect?**  
The cited sources describe the pose model and processing as on-device, with optional per-frame CSV traces stored on the device for testing. Do not claim broader privacy properties without checking the exact release build and permissions. Camera frames are used for live processing; optional traces are a separate testing artifact.

## Source notes

Checked against app implementation and documents on `origin/takeover/t5-final`; AED definition checked against `origin/takeover/t8-aed:cpr-coach/app/src/main/assets/strings/en.json`. AI-art verification and translation status are reported from `origin/cpr-coach:cpr-coach/docs/JUDGE_QA.md` and `STRINGS_UNREVIEWED.md`, respectively; AI illustration methodology was not independently validated in source code. The `GuideScreen.kt` on t5-final has no AED popup, so do not promise one unless it is present in the demo build.
