# Judge Q&A — CPR Coach

CPR Coach is a CPR **practice tool, not a medical device**. It does not diagnose, certify skills, or replace dispatchers and instructors. In a real emergency, call 112 and follow the dispatcher. Never delay care to use the app. Practice is on a cushion, never on a person.

Only **English and Hindi are the first-build languages**. Translations and voices for the other eight languages are unchecked drafts until a native speaker has listened (see "Which languages really work yet?").

## Accuracy and honesty

**How accurate is the compression rate?**
Measured against a hand count with a stopwatch — the result is [from MEASUREMENTS.md]. That is a prototype test on a cushion, not clinical validation. Counted, timed runs are still pending; the measurement table's run rows are currently empty.

**How fast is the feedback?**
The measured time from camera frame to on-screen result is [from MEASUREMENTS.md]. Anything we have not measured, we say we have not measured — we never quote a figure we did not record.

**What exactly does it measure — and what can't it?**
It estimates compression rate (per minute), elbow angle and pauses from the visible motion of the arms. One camera cannot reliably measure compression depth, force, chest recoil or hand placement, so the app does not try. The scorecard shows "Not measured" for any row it could not really observe.

**How do you know the numbers aren't guesses?**
The pipeline refuses to guess by design: rate appears only while a steady beat is detected over several seconds of arm-visible frames; the number greys out to "Can't see arms" the moment landmarks go unclear; the scorecard shows a dash instead of a number when there was nothing real to report. Every frame is also saved as a CSV trace so the maths can be re-tested on a PC.

## Why this design

**Why not just use a manikin with sensors?**
Sensor manikins give good feedback but need instructors and expensive equipment most colleges and communities don't have. Our bet: an ordinary phone the team already owns, a cushion, and a second person holding the camera reaches far more practice sessions — with honest "can't see it" states instead of false precision.

**Why practise on a cushion instead of a manikin?**
A cushion lets the rescuer rehearse the motion — steady rhythm, straight elbows, no long pauses — safely and cheaply. The app coaches motion, not clinical effectiveness, so a cushion is enough for what it measures.

**How is this different from a metronome app?**
A metronome only sets the beat. CPR Coach watches the rescuer: it reports their actual rate, notices bent elbows and pauses, says the one correction that matters most right now, and shows only this run's measured scorecard at the end.

**What happens when tracking fails?**
The rate number and elbow indicator disappear, "Can't see arms" appears, coaching goes silent, and the metronome keeps playing. The app never shows an old or invented rate. The holder reframes (hints stay on screen, never spoken) and the number returns only after a fresh steady reading. In Emergency mode the metronome and spoken steps continue regardless of the camera.

**What happens if the camera, microphone or speaker fails?**
The app degrades to what still works: Emergency mode needs no camera at all; the scorecard marks unmeasured rows instead of failing; if audio fails the holder reads the screen. Nothing critical depends on any single sensor, and the Call 112 button is a plain dialer shortcut that works without the app's audio or camera.

**Is the 112 button safe? Does it auto-dial?**
It opens the phone dialer with 112 already filled in and waits — it never places the call itself. On stage we stop at the dialer and press Back; we never call 112 during demos or tests.

## Languages, offline, privacy

**Why ten languages?**
CPR instructions only help if you understand them while panicking. The picker lists English, Hindi, Kannada, Tamil, Telugu, Malayalam, Marathi, Gujarati, Bengali and Odia, each in its own script, with every visible word and every spoken clip in the selected language.

**Which languages really work yet?**
This app has not yet been run end to end on a phone. The speech models were checked on real phones for English and Hindi in an earlier project (iTantra). One Lab test on an RMX3392 showed hand-held pose tracking working outdoors, and voice-mode audio kept playing while dialling. The other eight voice and text translations are generated drafts: per `docs/CLIP_STATUS.md`, "checked by a person" is NO for every clip — nobody has listened to them yet. The demo runs in English.

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
Counted rate-accuracy runs (not done yet), an end-to-end run of this app on a phone, connected-call audio behaviour, per-language voice checks by native speakers, and testing across more phones, lighting and camera angles. The honest status of each is tracked in `docs/MEASUREMENTS.md`, `docs/CLIP_STATUS.md` and `docs/PHONE_TEST.md`.
