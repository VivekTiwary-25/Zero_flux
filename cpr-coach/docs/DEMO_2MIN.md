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
| **0:00–0:12** | **1. Open & safety** | Presenter | "CPR Coach is a practice tool. It is not a medical device. In a real emergency, call 112. Never delay care to use the app. We practise on a cushion, never on a person. We never call 112 on stage." | Presenter faces judges; rescuer kneels at cushion; holder stands at rescuer's side. | Home in English, with Emergency and Practise visible. |
| **0:12–0:24** | **2. Practise** | Presenter | "Practise teaches you. Emergency talks you through it. Practise is calm, measured coaching for learning; the holder sees the camera feedback while the rescuer listens." | Holder taps Practise, frames the rescuer from the side, and starts when Ready appears. | Practise camera framing, then live feedback when available. |
| **0:24–0:52** | **3. Practise & measured handover** | Presenter | "The app coaches from visible arm movement. It never guesses a number. A scorecard shows only this run's measured values; an unmeasured item stays blank or says 'Not measured'." | Rescuer practises on the cushion. Holder demonstrates one live correction if it occurs, then stops Practise and briefly shows its scorecard. | Actual live state and scorecard; do not invent readings or claim accuracy. |
| **0:52–1:00** | **4. Back to Home** | Presenter | "Now the emergency path: no setup, no waiting for camera tracking." | Holder returns Home and places the phone for the side view or floor prop position. | Home, ready to demonstrate Emergency. |
| **1:00–1:45** | **5. Emergency: one tap, beat, voice, one word, handover** | Presenter / App Voice | *Presenter:* "One tap. Practise teaches you. Emergency talks you through it. The camera opens in landscape; the beat and spoken steps start at once. CPR never waits for the camera."<br>*App Voice:* Let actual steps play; do not cue or invent text.<br>*Presenter:* "The camera adds spoken corrections when arms are visible. One large localized word, no numbers, keeps attention on the next instruction. Call 112 stays visible and opens only the dialer. Rate detection on a real person and this redesigned Emergency screen with a real person are not proven. A dash means not measured, not zero." | Holder taps Emergency once and leaves camera on. Rescuer pushes on the cushion to the beat. At a suitable point, holder deliberately holds Stop until it ends, then displays "For the ambulance crew". | Camera remains on; coral-red frame contains one word (FASTER, GOOD, SLOWER or STRAIGHTEN ARMS, localized), elapsed time in a corner, no numbers. Call 112 remains visible. Summary shows total CPR time, average rate and pauses from measured data only; unmeasured values are dashes. |
| | **Setup hint** | | "Hold the phone from the side, or prop it on the floor facing the rescuer's side." | | Nonblocking on-screen hint only; it never blocks anything. |
| **1:45–2:00** | **6. Close & safety** | Presenter | "CPR Coach is a practice tool. It is not a medical device. In a real emergency, call 112. Never delay care to use the app. We practise on a cushion, never on a person. We never call 112 on stage. Follow the dispatcher. Thank you." | Keep "For the ambulance crew" summary visible; do not open the dialer. | Summary, then end. Never press Call. |

Emergency camera fallback: if tracking fails, the metronome and spoken steps continue; the word area says "Keep pushing with the beat". Do not expect a number. Never place a test call: if separately showing Call 112, tap only to open the dialer, then Back without pressing Call.

---

## Fallback procedures during demo

### Fallback if camera loses tracking in Practise
- **On screen:** the rate number and elbow indicator disappear, "Can't see arms" appears, and the metronome keeps playing.
- **Holder:** keep the phone level and step back slowly. If tracking does not return within a few seconds, tap Stop.
- **Presenter:** "The app lost the arms and shows no number rather than guessing."

### Fallback if camera tracking fails in Emergency
- **What happens on screen:** The camera remains on; the instruction word says "Keep pushing with the beat". The metronome and spoken emergency steps continue without a guessed number. When arms are visible, camera analysis and coaching can add spoken corrections alongside emergency steps.
- **What the holder does:** Keep the phone steady and continue CPR; do not pause waiting for tracking.
- **What the presenter says:** "Emergency does not wait for the camera. The beat and spoken steps continue, and the screen shows no invented number."


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
  "Counted accuracy runs are not done, and rate detection on a real person is not proven. We quote no accuracy figure."
- **"How fast is the feedback?"**
  "We quote no latency figure; anything not measured is reported as not measured."
- **"Does it measure depth or hand position?"**
  "No. A single 2D camera cannot measure depth or force reliably. It estimates rate, elbow angle, and pauses. It never guesses depth or hand placement."
- **"Does it run offline?"**
  "Yes. Pose landmarker, coaching clips, and speech run 100% on-device with zero network requests."
- **"What about calling 112?"**
  "The button opens Android's dialer with 112 filled in and never places a call. If demonstrating it, back out at the dialer without pressing Call. In a real emergency, call 112 and follow the dispatcher."
- **"Which languages work?"**
  "The picker lists ten languages. Spoken coaching clips are verified for a subset. English is what we demonstrated today."
- **"What does the score mean?"**
  "It reflects only this specific run's measured readings. It is neither a certification nor a clinical score."
- **"Why practise on a cushion?"**
  "We do not practise on a person. A cushion lets the rescuer rehearse compressions safely."
- **"Why two modes?"** "Practise teaches calmly with measured feedback and a scorecard. Emergency talks you through it: one tap, no setup, immediate beat and spoken steps; camera coaching joins when arms are visible."
- **"Why one word and no number in Emergency?"** "One large localized instruction keeps attention on the next action. Emergency never asks someone to interpret an unmeasured rate."
- **"What does the handover summary show, and why can it show dashes?"** "Total CPR time, average rate and pauses, from measured values only. A dash means not measured, not zero."
- **"What if the camera fails?"** "The beat and spoken steps continue, the word area says 'Keep pushing with the beat', and no number is invented. Rate detection on a real person and this redesigned screen with a real person are not proven."

---

## Strict demo rules ("Do Not")

- **NEVER call 112:** Only if demonstrating Call 112, stop at the dialer and press Back. Never place an actual emergency call.
- **NEVER practise on a person:** Always practise on the cushion.
- **NEVER turn mic on during demo:** Listening mutes the spoken coaching audio.
- **NEVER invent numbers:** Quote no accuracy or latency figures; no invented rate, score or demo value.
- **NEVER claim clinical validation:** Do not say "accurate", "clinically proven", "detects cardiac arrest", or "replaces an ambulance".
- If Emergency camera tracking fails, its metronome and spoken steps continue; the word area says "Keep pushing with the beat".

---

## Cue card (Stage pocket summary)

`Cushion → Home → Practise → Frame → Start → Live coaching → Stop → Scorecard → Home → Emergency (one tap: beat + voice + camera) → One word → Hold to stop → "For the ambulance crew" summary → Close.`

*Safety line verbatim at 0:00 and in the closing segment.*
