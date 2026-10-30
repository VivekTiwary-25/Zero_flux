# Judge Q&A — CPR Coach

CPR Coach is a CPR **practice tool, not a medical device**. It does not diagnose, certify skills, or replace dispatchers and instructors. In a real emergency, call 112 and follow the dispatcher. Never delay care to use the app. Practice is on a cushion, never on a person.

Only **English and Hindi are the first-build languages**. Translations and voices for the other eight languages are unchecked drafts until a native speaker has listened (see "Which languages really work yet?").

## Accuracy and honesty

**How accurate is the compression rate?**
Not demonstrated yet. Counted accuracy runs are not done, so we quote no accuracy figure. The one real recording so far gave no usable beat, and work is in progress. Tests so far are on a cushion, not clinical validation.

**How fast is the feedback?**
We quote no latency figure. Anything we have not measured, we say we have not measured — we never quote a figure we did not record.

**What exactly does it measure — and what can't it?**
It estimates compression rate (per minute), elbow angle and pauses from the visible motion of the arms. One camera cannot reliably measure compression depth, force, chest recoil or hand placement, so the app does not try. The scorecard shows "Not measured" for any row it could not really observe.

**How do you know the numbers aren't guesses?**
The pipeline refuses to guess by design: rate appears only while a steady beat is detected over several seconds of arm-visible frames; the number greys out to "Can't see arms" the moment landmarks go unclear; the scorecard shows a dash instead of a number when there was nothing real to report. Every frame is also saved as a CSV trace so the maths can be re-tested on a PC.

## Why this design

**Why two modes?**
Practise teaches CPR motion calmly: measured camera feedback, teal and amber cues, and a scorecard. Emergency talks you through it: one tap, no setup, immediate metronome and spoken steps, with camera-based corrections joining once the arms are visible. CPR never waits for tracking.

**Why one word and no number in Emergency?**
The emergency screen is designed to be panic-proof: one large instruction in the chosen language keeps attention on the next action instead of asking someone to interpret numbers. Practise is where numerical feedback and a scorecard belong; Emergency never shows a made-up rate.

**What does the "For the ambulance crew" summary show? Why might it show dashes?**
Total CPR time, average rate and pauses, based only on measured values. A dash means a value was not measured; it is not zero and is never guessed.

**Why not just use a manikin with sensors?**
Sensor manikins give good feedback but need instructors and expensive equipment most colleges and communities don't have. Our bet: an ordinary phone the team already owns, a cushion, and a second person holding the camera reaches far more practice sessions — with honest "can't see it" states instead of false precision.

**Why practise on a cushion instead of a manikin?**
A cushion lets the rescuer rehearse the motion — steady rhythm, straight elbows, no long pauses — safely and cheaply. The app coaches motion, not clinical effectiveness, so a cushion is enough for what it measures.

**How is this different from a metronome app?**
A metronome only sets the beat. CPR Coach watches the rescuer: it reports their actual rate, notices bent elbows and pauses, says the one correction that matters most right now, and shows only this run's measured scorecard at the end.

**What happens when tracking fails?**
In Emergency the camera stays on, but CPR does not wait for it: metronome and spoken steps start immediately and keep going. If tracking fails, the one-word area says "Keep pushing with the beat" and no invented number appears. Once arms are visible, the same camera analysis and coaching as Practise can add spoken corrections alongside emergency steps.

**What happens if the camera fails?**
Emergency's metronome and spoken steps continue; the instruction-word area says "Keep pushing with the beat". Call 112 stays visible and only opens the dialer. Camera tracking failure never blocks CPR or creates a rate.

**Is the 112 button safe? Does it auto-dial?**
It opens the phone dialer with 112 already filled in and waits — it never places the call itself. On stage we stop at the dialer and press Back; we never call 112 during demos or tests.

## Languages, offline, privacy

**Why ten languages?**
CPR instructions should be understandable under pressure. The picker lists ten languages in their own script; spoken coaching is verified only for a subset.


**Which languages really work yet?**
The app now runs end to end on two real phones with no known crash in the latest build. An early real-person session found a crash, which was fixed. The speech models were checked on real phones for English and Hindi in an earlier project (iTantra). The other eight voice and text translations are generated drafts: per `docs/CLIP_STATUS.md`, "checked by a person" is NO for every clip — nobody has listened to them yet. The demo runs in English.

**Does it work offline?**
Yes. The pose model, signal math, feedback logic, pre-recorded coaching clips and speech recognition all run on the phone. The app has no networking at all, so there are no dead zones and no data leaves the device.

**What data does it collect?**
Nothing leaves the phone. Camera frames are processed in memory; the only stored output is an optional per-frame CSV trace on the device itself, used to measure accuracy.

## Safety and scope

**Is this medical advice?**
No. It is guided practice for the motion of compressions: rate, elbows, continuity. It does not teach when to do CPR, does not judge clinical quality, and its score is this run's readings — not a certification.

**Who is it for?**
Colleges, NSS units and community groups that want supervised practice between instructor sessions — always with the safety line: call 112 in a real emergency, follow the dispatcher, start CPR right away.

**What is still unproven?**

Counted rate-accuracy runs are not done and we quote no accuracy or latency figures. Rate detection on a real person is not yet demonstrated: the one real recording so far gave no usable beat, and work is in progress. The redesigned Emergency screen has not yet been run with a real person. Still unproven: connected-call audio behaviour, per-language voice checks by native speakers, and testing across more phones, lighting and camera angles. The honest status of each is tracked in `docs/MEASUREMENTS.md`, `docs/CLIP_STATUS.md` and `docs/PHONE_TEST.md`.
