# Stage demo script: CPR Coach, 3 minutes

**The safety line. Say it at the start and at the end.**

> CPR Coach is a practice tool. It is not a medical device. In a real emergency, call 112. Never delay care to use the app. We practise on a cushion, never on a person. We never call 112 on stage.

## Before you walk on stage

- Use two Android phones, Android 11 or newer. One demo phone. One backup phone.
- Install the debug build on both. Open the app. Allow camera and microphone.
- Make the walkthrough appear on the demo phone. Install fresh, or clear the app's storage, then allow camera and microphone again. Do this before the stage, not on stage.
- Set the app to English on both phones. Tap the language chip and choose English. The app starts in the phone language if it is one of the ten.
- Turn media volume up. Check that no headphones and no Bluetooth speaker are connected.
- Set the screen to stay awake for the whole slot.
- Put one cushion on stage. No manikin. No person.
- Ask judges to stay behind the holder. Keep the space behind the rescuer clear.
- Leave the backup phone on Home, in English, with the volume up.
- Do a short run backstage. Check framing, Ready, the countdown, a rate on screen, audio and the scorecard.

If the walkthrough does not appear, Practise goes straight to framing. Do not clear storage on stage. Say the role lines yourself and tap Start.

## Where everyone stands

- **Rescuer:** kneels on the cushion at the front. Hands on the cushion. Does not touch the phone. Only listens. Never practises on a person.
- **Holder:** stands at the rescuer's side, not in front. Holds the phone sideways. Points the camera at the rescuer's upper body. Watches the screen. Taps Start, Pause, Stop and the other controls.
- **Presenter:** stands on the other side, facing the judges. Talks. Does not take the phone.
- **Controls:** Pause shows "Paused" in amber and stops the metronome. Resume continues. Stop ends the session and opens the scorecard. The scorecard also has "Swap roles and try again". We use Home for time.
- The app is locked to landscape. The holder keeps the phone sideways and still.
- Voice is for the rescuer. The screen is for the holder. Framing hints are never spoken.

## What the screen means

| Signal | Meaning |
|---|---|
| Teal | Good. In range. Ready. |
| Amber | Something needs fixing. |
| Coral red | Emergency-only actions and the Emergency instruction frame. |
| Clay button | Soft raised block. Sinks when pressed. Emergency, Practise, Start and Call 112. |
| Glass panel | Frosted panel. Walkthrough, scorecard and language picker. |
| Mic | Off in this demo. If on, it shows "Getting ready", then "Listening". Listening mutes the app's voice. |

The UI uses the team's shared pieces: `CoachColors` for the colours above, `ClayButton` for the four clay actions, `GlassPanel` for the frosted surfaces, `AppLanguage` for the ten names, `MicState` for the mic states, `CameraUiState` for framing, countdown and live, and `LocalStrings` for every visible word. Every translated word comes from `LocalStrings.current.get("key")` with keys in `assets/strings/en.json`. The ten language names come from `AppLanguage`, in their own script. Do not patch a missing word on stage. Report it after the demo.

## The three minutes

### 0:00–0:15 · Open

**Phone:** On Home. Do not touch it.

**People:** Presenter faces the judges. Rescuer kneels on the cushion. Holder stands at the rescuer's side with the phone sideways.

**Say:** "CPR Coach is a practice tool. It is not a medical device. In a real emergency, call 112. Never delay care to use the app. We practise on a cushion, never on a person. We never call 112 on stage."

### 0:15–0:30 · Home and language

**Phone:** Holder taps the language chip. The glass picker opens and lists the ten languages in their own script. Tap English.

**Say:** "The picker lists ten languages, each in its own script. We demo in English. Emergency is the big coral-red button. Practise is the smaller button below it."

**If you are behind:** skip the picker. Point at the language chip and say the line.

### 0:30–0:45 · Walkthrough

**Phone:** Tap Practise. Card 1 opens. Tap Next. Card 2 shows Rescuer and Holder. Tap Next. Card 3 shows teal and amber. Tap Start.

**Say:** "Two people. The rescuer kneels and pushes on a cushion. The holder stands at the side and watches the screen. The rescuer only listens. Teal means good. Amber means fix something."

The walkthrough is one glass card at a time. Back, Next and Skip are on every card. We use Next.

**If the walkthrough is already skipped:** Practise goes straight to framing. Do not clear storage on stage. Say the role lines yourself and continue.

### 0:45–1:00 · Framing

**Phone:** The holder stands at the rescuer's side. Hold the phone still. Wait for the body outline to turn teal and "Ready" to appear. Tap Start.

**Say:** "The holder sees hints like 'Move back' or 'Wrist out of frame'. They are never spoken. The app will not start until it can see the shoulder, elbow and wrist. Now it is ready."

**If it is not ready:** read the hint on screen and fix it. Do not tap the grey Start. It does nothing. Move back slowly, or move so the wrist and hip are in view. Wait for teal Ready.

### 1:00–1:05 · Countdown

**Phone:** The spoken countdown plays. Then the metronome starts.

**Say:** "The countdown is spoken. Then the metronome sets the beat. The rescuer does not look at the screen."

No rate number appears during the countdown.

### 1:05–1:50 · Live session

**Phone and people:** The rescuer pushes on the cushion at a steady pace with straight elbows. The holder keeps the phone still. The rescuer does not look at the screen.

**Say:** "The holder sees the rate with 'per minute', the elbow indicator and the time left. The elbow indicator says 'Elbows straight' in teal, or 'Elbows bent' in amber. The big number is this rescuer's rate. It appears only after a steady reading. If the app cannot see the arms, it says 'Can't see arms' and shows no number. It never guesses. One correction at a time, in the rescuer's language."

At **1:25**, the rescuer slows down clearly for a few seconds.

**Say:** "The rescuer is slowing down on purpose."

Wait for "Push faster". Do not wait more than a few seconds. At **1:35**, the rescuer returns to a steady pace.

**If the correction does not play:** say, "The app waits for steady readings before it corrects. That is why one odd push does not trigger a prompt." Then move on.

**If the rate turns amber:** point at it. Say, "Amber means out of range."
**If the rate turns teal:** point at it. Say, "Teal means in range."

Do not call out a rate as a result. Do not say the app is accurate.

### 1:50–2:05 · Stop and scorecard

**Phone:** Holder taps Stop. The scorecard opens. If Home is below the card, scroll down. Tap Home.

**Say:** "Session complete. We stopped early. The full session is two minutes. These are this run's readings, not sample values. Time at 100–120. Average rate. Elbows straight. Pauses. If the app did not measure a row, it says 'Not measured'. It never fills in a number. We do not use this score to claim accuracy."

**If the scorecard does not open:** tap Stop once more. If it still does not open, use the system Back or switch to the backup phone. Do not debug on stage.

### 2:05–2:45 · Emergency: one tap, coaching, handover

**Phone:** Tap Emergency once. The landscape camera opens, and the metronome and spoken steps start at once; CPR does not wait for camera tracking. Keep the camera on. Show the single large instruction word in the coral-red frame—no numbers—with elapsed time in a corner. The Call 112 button stays visible. Do not open the dialer during this timed beat.
The selected-language instruction is FASTER, GOOD, SLOWER or STRAIGHTEN ARMS.

**Say:** "Practise teaches you. Emergency talks you through it. One tap starts the beat and spoken steps while the camera opens. The camera adds spoken corrections when arms are visible; it never holds up CPR. One word, no number: keep your attention on the next instruction. The setup hint never blocks anything: hold the phone from the side, or prop it on the floor facing the rescuer's side."

**Phone:** Hold the stop control deliberately until the session ends. Show the "For the ambulance crew" summary.

**Say:** "The summary reports total CPR time, average rate, and pauses using measured values only. A dash means that item was not measured; it is never filled in."

**112 safety:** If demonstrating Call 112 instead, tap it only to show the dialer, then press Back without pressing Call. Never place a call on stage.

**If tracking fails:** The metronome and spoken steps continue; the word area says "Keep pushing with the beat". Do not invent or read a number.

### 2:45–3:00 · Close

**Phone:** Keep the "For the ambulance crew" summary visible.

**Say:** "CPR Coach is a practice tool. It is not a medical device. In a real emergency, call 112. Never delay care to use the app. We practise on a cushion, never on a person. We never call 112 on stage. Follow the dispatcher. Thank you."

**If you are behind, cut in this order:** the language picker, the deliberate slow beat, the scorecard explanation. Never cut the safety line, the emergency beat, the stop-and-summary moment, or the closing line.

## If tracking is lost

**On screen:**

- The Emergency screen keeps the camera on; the single instruction-word area says "Keep pushing with the beat".
- The metronome and spoken emergency steps continue without interruption.
- No guessed number appears.

**What the holder does:**

1. Keep the phone level. If arms leave view, do not wait to continue: the metronome and spoken steps keep going and the word area says "Keep pushing with the beat".
2. When arms are visible, the same camera analysis and coaching as Practise can add spoken corrections alongside emergency steps: call 112, hands on chest, swap every 2 minutes, and AED once.
3. The display remains one localized instruction word, never a number; do not read out an unmeasured rate.

**What the presenter says:**

- "The app lost the arms. It shows no number rather than an old or made-up one. This is the honest state."
- Do not read an old rate. Do not say "it was about" a number.
- Do not pretend the practice session is still measuring.

**If it happens during framing:**

- "Ready" disappears. Start is grey and does nothing.
- The hint tells the holder what to fix: "Point the camera at the rescuer", "Move back", "Wrist out of frame", "Hip out of frame", "Stand at the rescuer's side".
- Fix the phone, wait for teal "Ready", then tap Start. Do not tap the grey Start again and again.
- If Practise framing does not become ready, Emergency starts its beat and spoken steps immediately while its camera opens. Say, "Emergency does not wait for camera tracking."

## If audio fails

**What you notice:**

- No spoken countdown.
- No coaching phrase.
- No metronome.
- The screen still updates.

**Check the likely cause first:**

1. Check the mic button. If it shows "Listening" in teal, the app's voice is muted. Tap it once to turn it off. This is the most likely cause of "no voice".
2. Press the media volume up button.
3. Check that no headphones and no Bluetooth speaker are connected.
4. Check that media is not muted.

**If audio still fails:**

- Do not pretend it works.
- **Practise:** say, "Audio has failed. The screen is still live. The holder will read the rate and the colour." The holder reads "in range" or "fix something". The rescuer keeps a steady pace. Stop after a short stretch and show the scorecard. If the backup phone is ready, switch phones and restart from framing. Do not restart the whole app on the demo phone.
- **Emergency:** say, "Audio has failed. The camera remains on, and the Call 112 button still opens the dialer." Do not claim the beat or spoken steps work if they are inaudible. In a real emergency, call 112 and follow the dispatcher.
- If the backup phone works, switch to it. Say, "We are switching to the backup phone." Do not spend stage time debugging.
- After the demo, write down the phone model and what failed. Report it to the team.

## If the app crashes

Switch to the backup phone. It is already on Home, in English, with the volume up. Do not debug on stage. After the demo, write down the phone model, the screen and what you saw.

## If a judge asks

- **"Why two modes?"** "Practise teaches skills calmly with measured feedback and a scorecard. Emergency is panic-proof: one tap, no setup, and spoken instructions start immediately; camera coaching joins when arms are visible."
- **"Why one word and no number in Emergency?"** "A single large localized correction reduces screen reading under pressure. Numbers are for Practise's scorecard, not an emergency instruction."
- **"What does the handover summary show? Why dashes?"** "It shows total CPR time, average rate and pauses using only measured values. A dash means that value was not measured; it is safer than inventing one."
- **"Is this a medical device?"** "No. It is a practice tool. It does not diagnose, certify or replace dispatchers or instructors. In a real emergency, call 112."
- **"How accurate is the rate?"** "Counted accuracy runs are not done. Rate detection on a real person is not proven. We quote no accuracy or latency figures."
- **"How fast is the feedback?"** "We quote no latency figure. We say plainly when something has not been measured."
- **"Does it measure depth?"** "No. One camera cannot measure depth reliably. It estimates rate, elbow angle and pauses. It cannot measure depth, force or hand placement."
- **"Does it work offline?"** "Yes. Pose, speech and coaching clips run on the phone. No network."
- **"What if the camera fails?"** "The metronome and spoken emergency steps start immediately and continue. The word area says 'Keep pushing with the beat'; the app shows no invented number. Rate detection on a real person, and this redesigned Emergency screen with a real person, are not proven."
- **"What about 112?"** "The button opens the dialer with 112 filled in. It never calls by itself. We stop at the dialer on stage. In a real emergency, call 112 and follow the dispatcher."
- **"Which languages work?"** "The picker lists ten. Spoken coaching is verified for a subset. English is what we demo." Do not claim all ten voices are verified.
- **"What does the score mean?"** "It is this run's readings. It is not a certification or a clinical score."
- **"Why a cushion?"** "We do not practise on a person. A cushion lets the rescuer rehearse the motion safely."

If a line in `MEASUREMENTS.md` has no value, say, "We have not measured that yet." Do not improvise a number.

## Do not

- Do not call 112. Stop at the dialer and press Back.
- Do not practise on a person. Use the cushion.
- Do not turn the mic on during the demo. Listening mutes the coaching voice. Do not use voice commands on stage. The hall is noisy.
- Do not say "accurate", "clinically proven", "detects cardiac arrest" or "replaces an ambulance".
- Do not quote an accuracy or latency figure except from `MEASUREMENTS.md`.
- Do not claim all ten spoken voices are verified.
- Do not read an old rate after tracking is lost. Do not invent a rate, score or demo value.
- Emergency opens the camera in landscape and immediately starts the beat and spoken steps; Call 112 remains visible and opens only the dialer.

## Cue card

Cushion. Home. Language. Practise. Next. Next. Start. Ready. Start. Push. Slow. Stop. Scorecard. Home. Emergency (one tap: camera + beat + voice). One word. Hold to stop. "For the ambulance crew" summary. Close.

Safety line at the start and at the end: practice tool, not a medical device, call 112 in a real emergency. Never call 112 on stage.
