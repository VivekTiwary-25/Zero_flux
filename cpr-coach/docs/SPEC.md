# CPR Coach — Starting Prompt (Anvathon 2026)

You are building **CPR Coach**, a native Android app, for a 24-hour hackathon (Anvathon, NIE Mysore, 30–31 Oct 2026). You're working with Vivek on his Windows PC. This is a **solo task: no multi-PC swarm**. You are the lead, and you should use subagents (see "How to work") to move fast.

**Start in plan mode.** Read this whole file, look at the repos, then write a plan and **check it with Vivek before writing any code**. Even if a message sounds like a go-ahead, confirm before you build.

---

## 1. What the app is

A CPR **practice** app with live camera feedback and spoken coaching in 10 languages, plus a no-setup **emergency** mode. It's a hackathon demo, not a medical product: don't spend effort on clinical accuracy or real-world safety validation. But never invent numbers: any accuracy or latency we show must be really measured.

**Two people in Practise:**
- **Rescuer:** kneels and does compressions on a cushion (no manikin), and only *listens* to the app.
- **Holder:** stands at the rescuer's side, holds the phone sideways (landscape) and *watches* the screen.

**Core rule: voice is for the rescuer, the screen is for the holder.** Framing hints ("move back", "wrist out of frame") are shown on screen and **never spoken**.

Demo runs on 2–3 team Android phones (Android 11+), installed by adb. No release build or Play Store setup needed.

---

## 2. Where things are

- **Build here:** the team repo **Zero Flux** (GitHub `VivekTiwary-25/Zero_flux`). Make a new top-level folder `cpr-coach/` for the Android project. **Do not touch the `swarm/` folder.** Work on a branch and commit often.
- **Reuse from here:** Vivek's SIH app **iTantra**. Local path is `D:\projects\sih\iTantra` (GitHub `VivekTiwary-25/itantra`, public). Use the `final-app` branch, or a newer branch if `git log` shows newer speech fixes. Ask Vivek if unsure.

### What to copy from iTantra (speech layer only)
- `app/src/main/java/speech/SpeechEngine.kt`
- `app/src/main/java/speech/SpeechRecognizerManager.kt`: STT. English = Whisper tiny.en (in APK assets); hi, gu, mr, ta, te, or, bn, kn, ml = IndicConformer, read from the app's private files folder (`indicconformer/`, shared encoder + per-language files).
- `app/src/main/java/com/chmod777/itantra/TtsHelper.kt`: offline TTS through sherpa-onnx. Voices: en/hi/ml = Piper; bn/or/gu/mr/te/kn/ta = Meta MMS (decoder in fp16).
- All `*TextNormalizer.kt` files (Kannada, Tamil, Telugu, Bengali, Odia, Marathi, Gujarati).
- The `vits-*` TTS voice folders and Whisper files in `app/src/main/assets/`.
- `setup-models.ps1` / `setup-models.bat`: the script that pushes the IndicConformer model to a phone. **Update it for the new app's package name.**
- The sherpa-onnx Android library and dependency setup (check `build.gradle.kts` and the libs folder).
- `docs/STT_INDICCONFORMER.md` and `STATUS.md` for context on what's proven.

### What NOT to copy
Anything from transport, BLE, RFCOMM, DTN, network, relay, SOS, identity, contacts or crypto. CPR Coach has no networking at all.

### What iTantra's own STATUS.md says
- English and Hindi STT/TTS are physically verified on real phones.
- Kannada TTS exists but **no one has listened to it for quality yet.**
- MMS voices are slow to start (Bengali ~2.4 s from text to first sound). That's why coaching uses **pre-generated clips** (section 6).

---

## 3. App behaviour (agreed with Vivek)

### App-wide
- **No first-launch flow.** The app opens straight to Home.
- **Ask for all permissions at app open** (camera, mic, anything else).
- **Language:** default = the phone's system language if it's one of the 10, else English. A language chip on Home opens a picker. Language changes the screen text and the spoken voice.
- **Mic button on every screen**, easy to reach:
  - Tap it to turn on hands-free listening.
  - While listening, the app's **spoken voice stays silent**.
  - It **auto-mutes after a few seconds of no speech.** Tap again to mute manually.
  - The **metronome keeps playing** while listening (may change after testing).
  - Commands: Home = "emergency", "practise", a language name · Practise = "start", "stop", "repeat" · Emergency = "call" (opens the dialer), "stop".
  - Auto-mute needs voice activity detection. Check whether iTantra's hands-free mode already has one. If not, sherpa-onnx ships a Silero VAD.
  - Command matching can be simple keyword matching on the transcript, per language.

### Home
- A big **Emergency** button (most of the top half).
- A smaller **Practise** button below it.
- Language chip in a corner, plus the mic button.

### Practise
1. **Walkthrough:** first Practise only, with a "?" to reopen it later. Three cards with Back/Next, and **Skip** on each:
   1. "Practise CPR with live feedback. You need two people."
   2. Rescuer and holder roles, side by side on one card.
   3. "Teal = good, amber = fix something." Start button goes to the camera.
2. **Framing** (a state of the camera screen, not a separate screen): a body-outline guide, plus on-screen hints for the holder. Shows "Ready" once shoulder, elbow and wrist are clearly visible.
3. **Start:** Start button (or "start"), spoken countdown 3-2-1, then the metronome at ~110/min.
4. **Live session (2 minutes):**
   - **Holder sees:** a big rate number (per minute), a teal (in range) / amber (off) glow, an elbow indicator, a time-left ring, and pause/stop.
   - **Rescuer hears:** one correction at a time. Priority order: "Keep going" (after a pause) → "Push faster" / "Slow down" → "Keep your elbows straight" → occasional "Good, keep going".
   - A correction plays only after a few seconds of steady readings, never the same phrase twice in a row.
   - **Arms out of frame:** the number greys out and shows "Can't see arms". The rescuer only hears the metronome. **Never show a made-up number.**
5. **Scorecard:** time in the 100–120 range, average rate, elbows straight %, number of pauses. Buttons: "Swap roles and try again" and "Home".

### Emergency (zero setup, panic-proof)
- **Entry and audio:** one tap on **Emergency** opens the camera in landscape and immediately starts the metronome and spoken steps. CPR never waits for camera readiness. Emergency provides no setup flow.
- **Guidance:** spoken steps cover calling 112, hands on chest, swapping every 2 minutes, and one AED reminder. Once the arms are visible, the same camera analysis and coaching as Practise add spoken corrections blended with the emergency steps.
- **Screen:** show one huge instruction word in the chosen language—**FASTER**, **GOOD**, **SLOWER** or **STRAIGHTEN ARMS**—inside a coral-red frame, with elapsed time in a corner. Show no numbers. Keep a non-blocking hint on screen: hold the phone from the side, or prop it on the floor facing the rescuer's side.
- **Tracking unavailable:** keep the metronome and voice going; show **Keep pushing with the beat** in the word area. Never invent a number.
- **Call 112:** always visible, including while the camera is on. It opens the dialer with 112 filled in (`ACTION_DIAL`) and never places a call.
- **Stop and summary:** a deliberate hold on Stop opens the portrait **For the ambulance crew** summary with total CPR time, average rate and pauses. Show measured values only; use a dash when a value is unmeasured. Home returns in portrait.
- **Changed from the previous design:** Emergency now starts camera, metronome and voice together on entry; there is no Start compressions or Helper camera control, and Call 112 no longer hides while the camera is active. Emergency replaces the old optional-camera view/no-score flow with the instruction-word screen and ambulance summary.
- **Not yet proven:** rate detection on a real person, the redesigned Emergency screen with a real person, and audio during a connected call. Do not claim accuracy or latency measurements.

### Parked (only if time is left)
Solo mode (phone strapped to the forearm, motion sensor gives rate and pauses), past sessions list, leaderboard, session length choice.

---

## 4. How the feedback works (your design, check with Vivek)
1. **Pose:** MediaPipe Pose Landmarker for Android, fully on-device, with the model bundled in the app. Check the current version and API before using it.
2. **Rate:** take the wrist's vertical position **relative to the hip or knee** (cancels small hand wobble from the holder), find the steady beat over the last few seconds (peak counting or autocorrelation), and convert to compressions/min.
3. **Elbow:** the angle at the elbow from shoulder, elbow and wrist. The angle doesn't change if the whole frame shifts.
4. **Pause:** no steady beat while the arms are visible.
5. **Tracking lost:** low landmark confidence → stop reporting numbers.
6. **Feedback logic:** pick one correction by priority, require steady readings first, add a cooldown, never repeat back to back.
7. Thresholds: target 100–120/min. Elbow "bent" threshold to be tuned by testing.

---

## 5. Design philosophy (Vivek's, apply it to every screen)

> **Vivek's UI/UX Design Philosophy**
>
> **1. Material hierarchy.** Combine neumorphism, claymorphism, and glassmorphism, but give each a distinct purpose.
> - Claymorphism / neumorphism: primary, high-touch interactive objects. Soft dimensional surfaces, rounded geometry, gentle bevels, layered shadows, and tactile press responses.
> - Glassmorphism: background layers, overlays, floating panels, dialogs, and secondary surfaces. Frosted transparency, backdrop blur, subtle gradients, thin illuminated edges, and carefully controlled depth.
> - Bento grids: organize different functions using clean, asymmetrical but balanced layouts. Important features receive more space and visual weight.
>
> Not everything should be a card. Not every card needs to be glass.
>
> **2. Visual personality.** The interface should feel premium, atmospheric, minimalistic, and approachable. Prefer deliberate whitespace, strong typography, soft lighting, subtle depth, and cohesive colors. Dark navy/charcoal with restrained luminous accents is a favored direction. Colors should match the product's identity, not be copied blindly from previous projects.
>
> **3. Motion philosophy.** Animate the region, not the sentence. Motion should communicate interactions, transitions, progress, or state changes. Primary interactive elements deserve the richest animations; secondary elements stay comparatively quiet. Use tactile depression, gentle elevation, restrained pulses, and soft transitions. Avoid animating everything simultaneously. Prefer a few memorable, expressive interactions over perpetual decorative movement. Respect reduced-motion preferences.
>
> **4. Information architecture.** Use progressive disclosure and clear visual hierarchy. The screen should communicate its primary purpose immediately. Avoid dashboard clutter, redundant controls, gratuitous decorations, unnecessary nested cards, and excessive borders. A temporary state should not automatically become another screen.
>
> **5. Function before ornament.** Beautiful design must remain genuinely usable. Maintain readable contrast, accessible touch targets, meaningful feedback, clear navigation, and honest status information. Animations and glass effects must never interfere with performance, legibility, safety, or essential actions.
>
> **6. Creative freedom.** This philosophy is a design vocabulary, not a mandatory component library. Adapt its materials, proportions, colors, and animation intensity to the product. The goal is an interface that feels crafted, tactile, alive, elegant, and unmistakably intentional — without sacrificing simplicity.

### Applied to CPR Coach (already agreed)
- **One custom theme app-wide.** Don't use Android dynamic colour / Material You.
- **Palette:** dark navy/charcoal base · **coral-red = emergency only** · **teal = good / in range / ready** · **amber = needs correcting**. One meaning per colour across the app.
- **Clay:** Emergency, Practise, Start and Call 112 buttons. Emergency gets the richest press animation.
- **Glass:** walkthrough cards, scorecard sheet, language picker, language chip.
- **Camera screen:** **flat translucent overlays, no backdrop blur**, so pose tracking stays smooth. Make them beautiful anyway: big rate number, soft teal/amber edge glow.
- Home is an unequal two-block bento: Emergency dominant, Practise secondary.
- Nothing animates while a screen is at rest.

---

## 6. Coaching audio: pre-generated clips
Live TTS is too slow for coaching. Write a small desktop or on-phone tool that runs iTantra's TTS once per phrase per language and saves WAV/OGG files into the app's assets. At runtime, just play clips.

Phrase keys (wording can be tuned):
- `countdown`: "Starting in 3, 2, 1"
- `push_faster`: "Push faster"
- `slow_down`: "Slow down"
- `elbows_straight`: "Keep your elbows straight"
- `keep_going`: "Keep going"
- `good`: "Good, keep going"
- `session_done`: "Great work, session complete"
- `em_call`: "Call 112. Put it on speaker."
- `em_hands`: "Place your hands in the centre of the chest."
- `em_push`: "Push hard and fast, with the beat."
- `em_swap`: "If someone can take over, switch now."
- `em_aed`: "If there's an AED nearby, ask someone to bring it."
- `em_continue`: "Keep going until help arrives."
- One clip per language speaking that language's own name (for the picker).

Translations into the 9 Indian languages: draft them, then teammates (plenty speak Kannada) listen and fix bad ones. Keep a clear list of which clips have been checked by a person.

---

## 7. Build order (risk first)
Do the scariest unknowns first, so a dead end shows up early.

- **M0, check early (before building screens):**
  - Can app audio (metronome + clips) play while a 112 call is on speaker? Test on a real phone with a dummy number (never actually call 112).
  - A rough MediaPipe test on a phone: does a hand-held side view give a usable wrist beat and elbow angle?
  - Report results to Vivek before going further.
- **M1:** new Android project in `cpr-coach/` builds and installs. iTantra speech layer ported. The app speaks English + Kannada and transcribes on a real phone.
- **M2:** camera + pose → live rate number, elbow indicator, tracking lost. Measure real accuracy (count compressions against a phone stopwatch) and latency.
- **M3:** feedback logic + clip pack + metronome.
- **M4:** screens: Home, walkthrough, Practise session, scorecard, Emergency, helper camera.
- **M5:** mic button: hands-free listening, auto-mute, per-screen commands.
- **M6:** polish + demo hardening: test on all 2–3 phones, fix crashes, rehearse the demo.

---

## 8. How to work

### Subagents: use them
- **You (lead):** architecture, the pose → rate/elbow math, feedback logic, and reviewing everything subagents produce. Don't let unreviewed agent code into the main branch.
- **Sonnet subagents:** well-specified implementation pieces: screens, porting the speech layer, the clip generator, the metronome, the model setup script.
- **Haiku subagents:** cheap fast work: searching the iTantra repo, reading logs, checking file lists, simple refactors, writing test checklists.
- Run independent pieces in parallel (for example, screens while the pose engine is being built), but keep tightly coupled parts (pose → rate → feedback) with one agent.
- Give each subagent a self-contained brief: exact files, what done means, and what not to touch.

### Rules
- **Plan mode first. Confirm with Vivek before building.**
- **Honest status:** something is only "working" once it's been run on a real phone. Vivek does the physical check; tell him the exact steps to test.
- **No invented numbers** anywhere in the app or the pitch.
- Commit often to a branch in Zero Flux, with clear messages.
- Phones are Android 11+, connected to Vivek's Windows PC by adb.

### Talking to Vivek
- Plain, simple language. Short messages.
- End each message with a short **TLDR**.
- Put anything he has to act on or answer at the **very end**, after the TLDR.
- Spell things out instead of "the three items above".
- When he's excited or stressed, respond to that first. Don't open by correcting him.
