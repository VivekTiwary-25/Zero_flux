# Phone test checklist — CPR Coach

Use this on a real Android phone, with two people: a **rescuer** who pushes on a cushion (never on a person) and a **holder** who holds the phone sideways and watches the screen. Tick a box only when the app does what the line says. If a check fails, write down the phone model, what you did, and what you saw.

**Never call 112 during a test.** The Call 112 check stops at the dialer: it only confirms that the dialer opens with 112 filled in. Do not press the call button. If any test ever needs a real call, dial a teammate's number, never 112.

**Colour meanings**

| Colour | Meaning | Where it should appear |
|---|---|---|
| Teal `#2DD4BF` | Good, in range, ready | "Ready", in-range rate, straight elbows, listening mic |
| Amber `#FBBF24` | Something needs fixing | Off-range rate, bent elbows, "Paused" |
| Coral-red `#FF5A5F` | Emergency only | Emergency button, Call 112 |
| Dark navy `#0B1220` | Background | Every screen |

If a colour is used for a different meaning, or an ordinary button is coral-red, note it as a fail.

Clay buttons (Emergency, Practise, Start, Call 112) are soft raised blocks that sink slightly when pressed. Glass panels (walkthrough cards, the scorecard, the language picker and the language chip) look frosted and see-through. If a clay button looks flat, or a glass panel is solid, note it.

## Install

- [ ] Use an Android 11 or newer phone. Check **Settings → About phone → Android version**.
- [ ] Get the debug build (`app-debug.apk`) from the person who built it. If you install it yourself, connect the phone by USB, turn on **Developer options** and **USB debugging**, allow the computer, then run `adb install -r app-debug.apk`. The terminal should finish with **Success**.
- [ ] Open **CPR Coach** from the app drawer. The app opens straight to Home, with no welcome screen, sign-in or setup wizard.
- [ ] On the first open, allow camera and microphone, plus notifications if your phone asks. All permission requests appear at app open, not later in the middle of a session.
- [ ] Turn the media volume up. The countdown, coaching voice and metronome should come from the phone's speaker.

## Home

- [ ] Look at Home. **Emergency** is the large coral-red button filling most of the top half.
- [ ] **Practise** is the smaller dark button below Emergency. The two buttons are deliberately unequal in size.
- [ ] The language chip is in a top corner and shows the current language's own name.
- [ ] The mic button is on Home and easy to reach with one thumb.
- [ ] Tap **Emergency**. It presses in like a soft clay block and opens the Emergency screen.
- [ ] Watch Home for a few seconds without touching it. Nothing moves: no pulsing, drifting or looping animation.
- [ ] Nothing overlaps or is cut off on this phone, and the language chip and mic do not cover the Emergency button.
- [ ] Coral-red appears only on Emergency (and, on other screens, on Call 112).

## Language

- [ ] On a phone whose system language is one of the ten, the first launch starts in that language. On any other phone it starts in English.
- [ ] Tap the language chip. A frosted-glass picker opens.
- [ ] The picker lists all ten languages in their own script: English, हिन्दी, ಕನ್ನಡ, தமிழ், తెలుగు, മലയാളം, मराठी, ગુજરાતી, বাংলা, ଓଡ଼ିଆ.
- [ ] Every name is complete, with no missing letters, cut-off text or empty boxes.
- [ ] Pick **ಕನ್ನಡ**. The picker closes and the chip reads **ಕನ್ನಡ**.
- [ ] All visible words on Home change to Kannada, including **Emergency** and **Practise**. No English words remain.
- [ ] Go through the walkthrough, framing, a live session, the scorecard and Emergency in Kannada. No screen shows leftover English, except the language names inside the picker.
- [ ] Start a session in Kannada. The spoken coaching voice is Kannada, not English. Quality may vary; the check is that the language changes.
- [ ] Close the app completely and open it again. It is still in Kannada.
- [ ] Change back to English. Every screen and the spoken voice return to English.

## Walkthrough

- [ ] On a fresh install, tap **Practise**. The walkthrough opens by itself and does not go straight to the camera.
- [ ] Card 1 says "Practise CPR with live feedback" and "You need two people."
- [ ] Tap **Next**. Card 2 shows **Rescuer** and **Holder** side by side, with a short line for each.
- [ ] Tap **Back**. Card 1 returns.
- [ ] Tap **Next** twice. Card 3 says "Teal = good. Amber = fix something." and shows a teal swatch and an amber swatch.
- [ ] On every card, **Skip** is visible and does not overlap Back, Next or Start.
- [ ] Tap **Skip** on a middle card. The walkthrough closes and the camera framing screen opens.
- [ ] Reopen the walkthrough and tap **Start** on card 3. The camera framing screen opens.
- [ ] The cards are frosted-glass panels, one card at a time, not a long scrolling page.
- [ ] Go Home and tap **Practise** again. The walkthrough does not appear; the app goes to framing.
- [ ] On this later Practise visit, tap the **?** control. The walkthrough opens again at card 1.
- [ ] Close and reopen the app, then tap **Practise**. The walkthrough stays skipped.

## Framing

- [ ] Open the camera screen and turn the phone sideways, as the holder would.
- [ ] The screen shows a body-outline guide and on-screen hints for the holder. No hint is spoken.
- [ ] With nobody in view, the hint says "Point the camera at the rescuer". **Start** is greyed and does nothing.
- [ ] Hold the phone too close to the rescuer. The hint says "Move back".
- [ ] Move so the wrist is hidden. The hint says "Wrist out of frame".
- [ ] Move so the hip is hidden. The hint says "Hip out of frame".
- [ ] Stand in front of the rescuer instead of at the side. The hint says "Stand at the rescuer's side".
- [ ] Get the shoulder, elbow and wrist clearly in view. The outline turns teal, **Ready** appears in teal, and **Start** becomes an active teal clay button.
- [ ] Listen while the hints change: no voice reads them aloud.
- [ ] No rate number, score or timer appears on the framing screen.
- [ ] The holder can read the hints from the side while holding the phone; they are large enough.

## Countdown

- [ ] Tap **Start** when Ready. Big digits **3, 2, 1** appear, one per second.
- [ ] You hear the spoken countdown from the phone.
- [ ] After 1, the metronome starts at about 110 beats per minute and the live screen appears.
- [ ] The time-left ring starts at 2 minutes.
- [ ] No rate number or score appears during 3, 2, 1.
- [ ] The digits are large enough for the holder to read at arm's length.
- [ ] The mic button is still reachable during the countdown.

## Live session

- [ ] The holder sees a big number with "per minute", an elbow indicator, a time-left ring, and **Pause** and **Stop**.
- [ ] The number appears only after the app has a steady reading. Until then it shows a dash or no number, never a guess.
- [ ] Compress in the 100–120 range. The number and the screen edge turn teal.
- [ ] Compress clearly below 100. The number and edge turn amber.
- [ ] Compress clearly above 120. The number and edge turn amber.
- [ ] Keep your elbows straight. The elbow indicator says "Elbows straight" in teal.
- [ ] Bend your elbows. The elbow indicator says "Elbows bent" in amber.
- [ ] If the app cannot judge the elbows, no elbow indicator is shown instead of a guess.
- [ ] The time-left ring and the seconds number count down. At zero the session ends and the Scorecard opens.
- [ ] The metronome keeps a steady beat at about 110 per minute, and the rescuer can push to it without looking at the screen.
- [ ] Tap **Pause**. The number area empties or greys out, "Paused" appears, and the session waits. Tap **Resume**; the session continues.
- [ ] Tap **Stop**. The live session ends.
- [ ] The rescuer hears at most one spoken correction at a time. Two prompts never overlap.
- [ ] Stop pushing but keep your arms in view for a few seconds, then start again. The first correction is "Keep going".
- [ ] Push clearly too slowly for several seconds. You hear "Push faster".
- [ ] Push clearly too fast for several seconds. You hear "Slow down".
- [ ] Push in range with bent elbows. You hear "Keep your elbows straight".
- [ ] After a steady stretch of good compressions, you hear "Good, keep going" now and then, not nonstop.
- [ ] A correction waits for a few seconds of the same reading; one odd push does not trigger a prompt.
- [ ] The same phrase never plays twice in a row.
- [ ] The holder can read the number from the side, and the rescuer never needs to look at the screen.
- [ ] Coral-red does not appear anywhere in the live overlay.

### Rate check results

Do this after the live checks, with the rescuer on the cushion and the holder counting. For each row, the holder starts a stopwatch and counts every compression by hand for the same stretch, then writes the stopwatch time and the rate the app showed at the end of that stretch. Use a fresh steady stretch for each row. **Leave every result box empty until you have measured it.** The three pacing labels are targets for the test, not results.

| Pacing target (about) | Compressions counted by hand | Stopwatch seconds | App's rate (per minute) | Notes |
|---|---|---|---|---|
| about 90 per minute |  |  |  |  |
| about 110 per minute |  |  |  |  |
| about 130 per minute |  |  |  |  |

## Tracking lost

- [ ] During a live session, ask the rescuer to move fully out of the camera's view. The number disappears, "Can't see arms" appears, and the teal or amber edge glow goes away.
- [ ] No old rate number stays on screen while the arms are lost.
- [ ] The elbow indicator disappears while tracking is lost.
- [ ] The metronome keeps playing.
- [ ] The rescuer hears only the metronome, with no spoken correction.
- [ ] Move back into view and compress steadily. The number returns only when the app has a steady reading; it does not jump back to the old number.
- [ ] If the app never finds the arms again, it keeps saying "Can't see arms" and never invents a rate.
- [ ] The app does not announce the tracking loss out loud; the holder sees it on screen.

## Scorecard

- [ ] Finish the 2 minutes. A frosted-glass "Session complete" card opens.
- [ ] It shows "Time at 100–120" with a seconds unit, "Average rate", "Elbows straight" and "Pauses".
- [ ] "Time at 100–120" is shown in seconds.
- [ ] "Elbows straight" is shown as a percentage.
- [ ] If the app could not measure the average rate or elbow posture, that row says "Not measured", never a made-up number or a zero placeholder.
- [ ] Stop pushing for a few seconds while staying in view, then finish. "Pauses" counts at least that pause.
- [ ] The scorecard is a glass panel, not the flat camera overlay, and no coral-red appears on it.
- [ ] Tap **Swap roles and try again**. It returns to the camera framing screen so the other person can take over.
- [ ] Tap **Home**. It returns to Home.
- [ ] The card shows only the agreed results; no extra score is invented.


## Emergency

**Never call 112 during a test.** Stop at the dialer; do not press its call button.

- [ ] From Home, tap **Emergency** once. The screen opens in landscape and the metronome and spoken emergency steps start at once; CPR does not wait for the camera.
- [ ] The camera opens without a setup flow. The non-blocking hint says to hold the phone from the side or prop it on the floor facing the rescuer's side.
- [ ] With arms visible, the screen shows one huge instruction word in the selected language, inside a coral-red frame, with elapsed time in a corner and no numbers. Observe the word change among **FASTER**, **GOOD**, **SLOWER** and **STRAIGHTEN ARMS** as coaching conditions change.
- [ ] Spoken emergency steps cover calling 112, hands on chest, swapping every 2 minutes, and the AED reminder once. Spoken corrections from the Practise analysis blend with those steps when arms are visible.
- [ ] Call 112 remains visible while the camera is on. Tap it: the dialer opens with 112 filled in, no call is placed, and the metronome continues. Do not press the dialer's call button; return without calling.
- [ ] Move the arms out of view. The word area says **Keep pushing with the beat**; the metronome and spoken steps continue and no number is made up.
- [ ] Revoke or deny camera permission, then enter Emergency. The metronome and spoken coaching still start and continue without camera tracking.
- [ ] Press the device Back button during Emergency. The Emergency session, metronome and spoken steps continue.
- [ ] With the camera unavailable, the instruction/measurement display uses dashes for unmeasured values; it never shows invented numbers.
- [ ] Deliberately hold **Stop**. The **For the ambulance crew** summary opens in portrait and shows total CPR time, average rate and pauses; unmeasured values are dashes.
- [ ] From the summary, tap **Home**. Home appears in portrait.

**Not yet proven:** rate detection on a real person and the redesigned Emergency screen with a real person. Connected-call audio is also unproven. Do not report results or quote accuracy or latency numbers.

## Mic commands

- [ ] Find the mic button on Home, the walkthrough, the camera screen (framing, countdown and live), the scorecard and Emergency. It is on every screen.
- [ ] Tap the mic. It shows a spinner or "Getting ready" while it loads, then turns teal with a soft ring and shows "Listening".
- [ ] Tap it again while listening. It turns off and the app's voice can speak again.
- [ ] Turn the mic on during a live session and stay quiet. The metronome keeps playing, but no coaching voice speaks while it listens.
- [ ] Stay quiet for a few seconds. The mic turns itself off (auto-mute).
- [ ] On Home, say "emergency". The Emergency screen opens.
- [ ] On Home, say "practise". The Practise screen opens.
- [ ] On Home, say "Kannada". The app switches to Kannada and the chip and screen text change.
- [ ] In Practise, say "start". The countdown starts and the session begins.
- [ ] In Practise, say "stop". The session ends.
- [ ] In Practise, say "repeat". The last coaching phrase plays again.
- [ ] In Emergency, say "call". The dialer opens with 112 filled in. Do not call; press Back.
- [ ] In Emergency, say "stop". Emergency ends and you return Home.
- [ ] In Kannada, say the Kannada words for "start" and "stop". The same actions happen.
- [ ] Commands work at normal speaking volume from the holder's position near the phone.

## Real person session

- Before starting, make sure the phone battery is above 40%, the phone is not hot to touch, and close other apps.
- Check tracking follows the arms.
- Keep the session for the full 2 minutes.
- If the app or phone dies, note the time and don't uninstall or clear anything, then connect USB.
- After each session, note what the screen said.
- Recordings are in the app's external files folder under `traces/`.
