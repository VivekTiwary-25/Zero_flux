# Stage demo script: CPR Coach, 2 minutes

**The safety line. Spoken verbatim at the start and at the end.**

> CPR Coach is a practice tool. It is not a medical device. In a real emergency, call 112. Never delay care to use the app. We practise on a cushion, never on a person. We never call 112 on stage.

---

## Before walking on stage

- Two Android phones (Android 11 or newer): one demo phone, one backup phone.
- Volume up on both phones. Verify no Bluetooth devices or headphones are connected.
- Set screen timeout to stay awake for the demo duration.
- Demo phone starts on Home in English (`AppLanguage.EN`). Backup phone starts on Home in English with volume up.
- One cushion placed on stage for compressions. No manikin, no human subject.
- Keep judges behind the holder; maintain a clear area behind the rescuer.

---

## Stage roles & layout

- **Rescuer:** Kneels at the cushion facing forward. Hands positioned on the cushion. Does not touch or watch the phone; listens only to spoken cues.
- **Holder:** Stands at the rescuer's side (never in front). Holds the phone horizontally in landscape. Points camera at rescuer's upper body. Operates UI `ClayButton` controls (Start, Pause, Stop).
- **Presenter:** Stands on the other side facing judges. Delivers timed commentary and safety lines. Does not handle the phone.

---

## Screen visual system & contract components

- `CoachColors.Coral` (Coral red): Emergency mode only (`home_emergency`, `em_call`).
- `CoachColors.Teal` (Teal): In range (100–120 bpm), good status, ready guide (`frame_ready`, `live_elbows_straight`).
- `CoachColors.Amber` (Amber): Adjustment required (`live_elbows_bent`, rate out of range, `live_paused`).
- `ClayButton`: Dimensional tactile buttons (`home_emergency`, `home_practise`, `frame_start`, `em_call`).
- `GlassPanel`: Frosted glass containers (walkthrough cards, scorecard dialog, language picker).
- `CameraScrim`: Translucent flat overlay on camera feed with zero blur to ensure high frame rate.
- `MicState`: OFF, LOADING (`mic_loading`), LISTENING (`mic_listening`). Kept OFF during demo so speech recognition does not mute spoken coaching.
- `LocalStrings`: Every visible string maps to `assets/strings/en.json`.

---

## Timed Two-Minute Timeline (0:00–2:00)

| Time | Step | Who speaks | Spoken line | What they do | What the audience sees |
|---|---|---|---|---|---|
| **0:00–0:12** | **1. Open & Safety** | Presenter | "This is CPR Coach. It is a practice tool, not a medical device. In a real emergency, call 112. Never delay care to use the app. Today we practise on a cushion, never on a person, and we never call 112 on stage." | Presenter faces judges. Rescuer kneels at cushion with hands ready. Holder holds phone horizontally at rescuer's side. | Phone on Home screen: Dark Navy background, large Coral `ClayButton` ("Emergency"), secondary `ClayButton` ("Practise"), language chip ("English"). |
| **0:12–0:24** | **2. Home & Practise** | Presenter | "Two roles: the rescuer pushes and only listens; the holder watches the screen. The picker supports ten offline languages. We tap Practise." | Holder points to language chip, taps Practise (`home_practise`), skips walkthrough cards straight to camera framing. | Practise button depresses with tactile clay animation; screen transitions cleanly from glass card to camera framing view. |
| **0:24–0:40** | **3. Camera Framing** | Presenter | "The camera requires shoulder, elbow, and wrist in view. The holder sees visual framing hints like 'Move back'—never spoken aloud. Once aligned, the outline turns teal and shows Ready." | Holder stands steady at rescuer's side framing upper body. Waits for teal body guide. Taps active teal Start button (`frame_start`). | Translucent camera overlay with body outline. On-screen guidance updates to teal "Ready" (`frame_ready`). Start `ClayButton` illuminates teal. |
| **0:40–0:45** | **4. Countdown** | App Voice / Presenter | *App Voice:* "Starting in 3, 2, 1."<br>*Presenter:* "Spoken countdown, then the metronome sets the 110-beat pace." | Spoken countdown clip plays. Metronome begins steady tick at ~110 bpm. Rescuer starts rhythmic compressions on the cushion. | Screen shows 3-2-1 countdown overlay, then reveals live tracking HUD without an invented rate until readings stabilize. |
| **0:45–1:15** | **5. Live Coaching** | App Voice / Presenter | *Presenter (0:45):* "Holder sees live rate per minute, teal in-range glow, and elbow angle. Rescuer only hears spoken coaching."<br>*App Voice (0:55):* "Good, keep going."<br>*Presenter (1:02):* "Rescuer slows down deliberately to test coaching."<br>*App Voice (1:07):* "Push faster."<br>*Presenter (1:11):* "One prompt at a time, only on steady data. Never an invented number." | Rescuer compresses at 110 bpm with straight elbows. At 1:02, rescuer deliberately slows pace for 4 seconds, hears "Push faster", then resumes steady 110 bpm. Holder holds phone still. | Live HUD shows rate number in teal ("110 per minute") and teal "Elbows straight". At 1:02, rate shifts to amber, app speaks "Push faster", then returns to teal once pace recovers. |
| **1:15–1:30** | **6. Stop & Scorecard** | Presenter | "We stop early. The scorecard shows this run's actual readings: time at 100 to 120, average rate, straight elbows, and pauses. No guessed numbers. Any unmeasured row shows 'Not measured'." | Holder taps Stop (`live_stop`). Scorecard glass sheet appears. Holder reviews metrics, scrolls down, and taps Home (`score_home`). | Frosted `GlassPanel` scorecard displaying: Time at 100–120, Average rate, Elbows straight %, Pauses. Tapping Home returns smoothly to Home screen. |
| **1:30–1:45** | **7. Emergency Mode & 112 Dialer** | Presenter | "In emergency mode, zero setup. Tap Emergency, then Call 112. The app opens Android's dialer with 112 pre-filled—it never auto-dials. We back out now: never call 112 on stage." | Holder taps coral-red Emergency button (`home_emergency`), then taps Call 112 (`em_call`). Android dialer opens with 112 pre-filled. Holder immediately presses system Back button. | App transitions instantly to Emergency screen. Tapping Call 112 brings up system dialer with 112 populated. System Back returns directly to Emergency screen. |
| **1:45–1:55** | **8. Hands-Free Compressions** | Presenter / App Voice | *Presenter:* "Start compressions works with the phone on the floor—no camera needed. Spoken guidance and metronome run offline."<br>*App Voice:* "Call 112. Put it on speaker. Hands in the centre of the chest. Push hard and fast, with the beat." | Holder taps Start compressions (`em_start`). Metronome starts. Rescuer pushes to the beat. Holder presses and holds 'Hold to stop' (`em_stop_hold`) until the indicator fills, returning to Home. | Emergency compression screen active. Call 112 button remains visible at top. Long-press progress bar fills on "Hold to stop", smoothly returning to Home. |
| **1:55–2:00** | **9. Close & Safety** | Presenter | "Two roles, ten offline languages, zero internet. CPR Coach is a practice tool, not a medical device. In a real emergency, call 112 and follow the dispatcher. Thank you." | Rescuer stops compressions and stands up. Holder lowers phone. Presenter concludes presentation and addresses judges. | Phone rests stably on Home screen. Team acknowledges judges. |

---

## Fallback procedures during demo

### Fallback if camera loses tracking
- **What happens on screen:**
  - Rate number and elbow indicator disappear immediately.
  - "Can't see arms" (`live_cant_see`) appears on screen.
  - Teal/amber edge glow turns off.
  - Metronome keeps playing uninterrupted; app does not utter a coaching error phrase.
- **What the holder does:**
  1. Keeps phone level without shaking or tilting.
  2. Steps back slowly to bring rescuer's shoulder, elbow, and wrist back into view.
  3. If tracking does not return within 3 seconds, taps Stop (`live_stop`).
- **What the presenter speaks (fallback line):**
  > "The app lost the arms and shows 'Can't see arms' with no rate number rather than guessing. The metronome keeps going—this is the honest state."
- **Continuation:** Review unmeasured rows showing "Not measured" (`score_not_measured`), tap Home, and advance directly to Step 7 (Emergency mode).

### Fallback if audio fails
- **What happens:**
  - Metronome or spoken clips are inaudible while the screen continues live visual updates.
- **What the holder does:**
  1. Checks mic button state: if "Listening" (`mic_listening`) in teal, taps it once to mute mic, un-muting coaching audio.
  2. Presses hardware volume-up button to ensure media audio is unmuted.
  3. If still silent, proceeds with visual feedback or transitions to the backup phone.
- **What the presenter speaks (Practise fallback line):**
  > "Audio has failed. The screen is still live. The holder reads the visual cues—teal for in range, amber to adjust—and the rescuer maintains rhythm."
- **What the presenter speaks (Emergency fallback line):**
  > "Audio has failed. The Call 112 button still works. In a real emergency, call 112 and follow the dispatcher."
- **Continuation:** If switching to backup phone: *"We are switching to the backup phone."* Never stall to debug on stage.

---

## Judge Q&A reference

- **"Is this a medical device?"**
  "No. It is a practice tool. It does not diagnose, certify, or replace dispatchers or instructors. In a real emergency, call 112."
- **"How accurate is the rate?"**
  "We measured it against a hand count. The result is [from MEASUREMENTS.md]. That is a prototype test, not clinical validation."
- **"How fast is the feedback?"**
  "The measured time to a first reading is [from MEASUREMENTS.md]. We do not quote a number we did not measure."
- **"Does it measure depth or hand position?"**
  "No. A single 2D camera cannot measure depth or force reliably. It estimates rate, elbow angle, and pauses. It never guesses depth or hand placement."
- **"Does it run offline?"**
  "Yes. Pose landmarker, coaching clips, and speech run 100% on-device with zero network requests."
- **"What about calling 112?"**
  "The button opens Android's dialer with 112 filled in. It never auto-calls. We back out on stage. In an emergency, you press call and speak to the dispatcher."
- **"Which languages work?"**
  "The picker lists ten languages. Spoken coaching clips are verified for a subset. English is what we demonstrated today."
- **"What does the score mean?"**
  "It reflects only this specific run's measured readings. It is neither a certification nor a clinical score."
- **"Why practise on a cushion?"**
  "We do not practise on a person. A cushion lets the rescuer rehearse compressions safely."

---

## Strict demo rules ("Do Not")

- **NEVER call 112:** Stop at the dialer with 112 filled in and press Back. Never place an actual emergency call.
- **NEVER practise on a person:** Always practise on the cushion.
- **NEVER turn mic on during demo:** Ambient auditorium noise can trigger false commands; listening state mutes coaching audio.
- **NEVER invent numbers:** No invented latency, FPS, or accuracy numbers—write only [from MEASUREMENTS.md].
- **NEVER claim clinical validation:** Do not use words like "accurate", "clinically proven", "detects cardiac arrest", or "replaces an ambulance".
- **NEVER show or quote made-up rates:** If tracking is lost, show "Can't see arms" and no number.
- **No Helper camera in this build:** The Emergency screen has no camera button; the Call 112 button stays on screen.

---

## Cue card (Stage pocket summary)

`Cushion → Home → Practise → Framing (Teal Ready) → Start → Countdown (3-2-1) → Push (110 bpm) → Deliberate Slow ("Push faster") → Resume → Stop → Scorecard → Home → Emergency → Call 112 (Dialer opens → Back) → Start compressions → Hold to stop → Close.`

*Safety line verbatim at 0:00 and 1:55.*
