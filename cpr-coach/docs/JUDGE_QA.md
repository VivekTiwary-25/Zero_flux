# Judge Q&A — CPR Coach

CPR Coach is a CPR **practice tool, not a medical device**. It does not diagnose, certify skills, or replace dispatchers and instructors. In a real emergency, call 112 and follow the dispatcher. Never delay care to use the app. Practice is on a cushion, never on a person.

## Accuracy and limits

**What does the app measure?**
It estimates compression rate, elbow angle and pauses from visible arm motion. It does not measure compression depth or hand placement, and it does not give an overall CPR score. A scorecard shows only readings the app could measure; a dash or “Not measured” is not a zero. One camera cannot reliably measure depth, force, chest recoil or hand placement.

**How accurate is the compression rate?**
We do not claim a proven accuracy figure. Testing includes real phone recordings replayed through the app's own scorecard code, but that is not clinical validation or a completed counted accuracy study. The rate estimator can miss real pushing, as recording 16 shows below.

**How fast is feedback?**
We quote no latency figure. Anything not measured, we say is not measured.

**Why do synthetic sessions appear on the dashboard?**
Testing data comes from real phone recordings, processed by the app's own scorecard code. Synthetic sessions are made-up data used to show how the charts look; they are tagged so they are not mistaken for real sessions. “Clear sample data” removes both synthetic and testing sessions. Real sessions have no tag.

**What is the known lean-pause problem?**
During a weak, uneven lean, pushing can continue while the rate estimator rejects the movement. The rate then drops out and the app shows “Pause” after its rate hold runs out. This is a known limit, not a crash. In recording 16, from 104–123 seconds the current setting showed a rate on 13 of 77 reads; a tried rate change raised that to 45 of 77. But the change also raised reads with a rate during the unclear 93–104 second block from 12 of 45 to 34 of 45, increased readings more than 10/min away from the independent peak pace from 15.1% to 25.3% across recordings, and caused worse results elsewhere. It was not shipped. A tried pause-rule change still gave the false cue at 103.25 seconds and lost a real rest cue in recording 14; it also removed an Emergency cue in recording 12. That was not shipped either. The known limit remains; keep the phone still and push steadily.

**How do you know the numbers aren't guesses?**
Rate appears only when the app detects a steady beat over several seconds of frames with visible arms. The display greys out to “Can't see arms” when landmarks are unclear. The scorecard uses a dash instead of inventing a number. Per-frame CSV traces can be saved on the device so the analysis can be replayed.

## How to use it

**Why two modes?**
Practise teaches CPR motion with camera feedback and a scorecard. Emergency gives spoken steps and a metronome immediately; CPR does not wait for camera tracking. Camera-based corrections can join once arms are visible.

**Why one word and no number in Emergency?**
The emergency screen uses one large instruction so someone under pressure can focus on the next action. Practise is where measured feedback and a scorecard belong; Emergency does not show a made-up rate.

**What does the “For the ambulance crew” summary show? Why might it show dashes?**
It shows total CPR time, average measured rate and pauses. A dash means a value was not measured, not that it was zero.

**Why not use a sensor manikin?**
Sensor manikins give useful feedback but need equipment and instructors. CPR Coach is designed for low-cost practice with a phone and a cushion, and is limited to the motion it can observe.

**Why practise on a cushion instead of a manikin?**
A cushion lets someone safely rehearse rhythm, straight elbows and continuity. The app coaches motion, not clinical effectiveness.

**How is this different from a metronome app?**
A metronome sets a beat. CPR Coach also observes arm motion and can estimate rate, identify bent elbows and pauses, and show this practice run's measured readings.

**What happens when tracking fails?**
Emergency's metronome and spoken steps continue. The instruction area says “Keep pushing with the beat”; no invented number appears. Tracking failure never blocks CPR.

**What is an AED?**
An AED is a portable box that can restart the heart with an electric shock. AEDs can be found in malls, airports, metro stations and offices. Switch it on and it tells you what to do.

**Does the emergency contact or Call 112 button place a call?**
No. The emergency contact and Call 112 buttons only open the phone dialer. Nothing auto-calls. The user chooses whether to place a call.

**Is this medical advice?**
No. CPR Coach guides practice motion; it does not teach when to do CPR or judge clinical quality. Its readings are not certification. In an emergency, call 112, follow the dispatcher and start CPR right away.

**Who is it for?**
Colleges, NSS units and community groups that want supervised practice between instructor sessions.

## Pictures (only if pictures ship)

**Are the pictures AI-made, and how were they checked?**
The teaching drawings are AI-made. People and tools checked them against these points: hands only on the lower half of the breastbone, straight arms, shoulders above hands, and correct fingers. This was a teaching-picture check, not a clinical review.

## Languages, offline and privacy

**Which languages really work yet?**
English and Hindi are the first-build languages. Hindi and Kannada CPR guide text came from teammates. Machine translations have not been reviewed by a person; `docs/STRINGS_UNREVIEWED.md` lists them. Do not describe those translations as human-checked.

**Why ten languages?**
The picker lists ten languages so instructions can be available in more languages. That does not mean every translation or voice has been reviewed or verified.

**Does it work offline?**
Yes. The pose model, signal math, feedback logic, coaching clips and speech recognition run on the phone. The app has no networking, so no data leaves the device.

**What data does it collect?**
Camera frames are processed in memory. An optional per-frame CSV trace may be stored on the device for testing. The app has no networking and sends no data off the phone.
