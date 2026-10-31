# Final phone test (CPH2467, under 4 minutes)

Demo build: **a99cd63** on CPH2467. The team reports that it passed the phone tests on 31 Oct 2026. This sheet keeps the checks for a repeat run; blank boxes below are not fresh results. Start the app from its icon, not a link or test tool. Rescuer pushes a firm cushion, never a person. Holder holds the phone steady side-on. Mic OFF. Do not call anyone: cancel the dialer each time it opens.

Allow 235 s in all (under 240 s / 4 minutes). Use a clock. Do not repeat the whole run for each mode.

On **every app screen** below, switch the phone between light and dark mode and check both views. Include boxes that open, the AED pop-up, setup, countdown, pause, scorecard, session detail, Emergency, and handover. Do this within each row's time. Text and buttons must stay clear, with no cut-off text. Camera screens must keep vivid green and amber in both modes, whatever the phone's setting; no dull or washed-out colours. No **"Debug replay"** text may show anywhere in the app.

| Step | Seconds | ALONE or TWO PEOPLE | Do | Expect | OK? |
|---|---|---|---|---|---|
| 1 | 20 s | ALONE | Tap the app icon. Look at Home. Tap Dashboard tab, CPR guide tab, Home tab. Check each in light and dark mode. | App opens from its icon. The CPR coach logo is at the top left of Home. All three tabs switch cleanly. The raised buttons and panels are clear in both modes. No "Debug replay" text. | [ ] |
| 2 | 30 s | ALONE | Tap CPR guide. Tap tabs 1–5, Previous, Next. On step 2, tap the AED (?) button, read the pop-up, then tap its close button. On step 5 tap Open Emergency, then hold Stop to leave and go Home. | Each guide step shows the scope note, picture, title, text, and tip. Previous and Next work. AED (?) opens a clear, readable box; its close button shuts it and leaves step 2 in view. Open Emergency on step 5 opens Emergency. | [ ] |
| 3 | 20 s | ALONE | On Home, tap Emergency contact. Type a name and number; tap Save. Open Emergency contact again, tap Pick from contacts, then cancel. Keep the saved contact for step 7. | Home shows the saved name. Pick from contacts opens the phone's contact picker with no permission asked. Cancel returns to the app. | [ ] |
| 4 | 15 s | ALONE | From Home tap Practice. Tap Back within 1 s. Tap Practice again. | Screen changes at once, with no freeze. Camera shows promptly; setup goes ready. Back within 1 s returns cleanly with no crash. Second open is prompt and ready. | [ ] |
| 5 | 45 s | TWO PEOPLE | With rescuer in the framing view, tap Start (3 s countdown). Rescuer pushes the cushion on the beat for 25 s. Tap pause, resume, then Stop. Check scorecard; tap Home. Check both modes as you go. | New look is clear on setup, countdown, pause, camera, and scorecard. Camera keeps vivid green and amber in both modes. Scorecard says "Rate measured for X of Y s". Compare with ee682a1: Practice must feel the same. | [ ] |
| 6 | 30 s | ALONE | Tap Dashboard. Check tags on charts and rows. Open the step 5 Practice session, then tap Back. Tap Clear sample data last. | "Testing data" and "Synthetic data" tags show on sample rows and charts. Charts draw. The step 5 session is listed with no such tag. Its detail opens. Clear sample data removes tagged rows and keeps the new session. | [ ] |
| 7 | 65 s | TWO PEOPLE | Tap Emergency. Rescuer pushes the cushion slow. Once told to push faster, push on the beat past 30 s from Emergency start; say nothing. Check both modes while pushing. Tap the saved contact button; cancel the dialer. Hold Stop for 1 s, then tap Home. Clear the test contact. | Hand picture shows in opening lines only. Saved contact button is smaller and beside the bigger Call 112 button. With steady slow readings, FASTER has a 0.5 s hold; "Push faster" has a 1.5 s steady-reading hold, subject to the 4 s speech gap and idle playback. Once a spoken correction is fixed, "Good" follows when eligible. On pace, Good is due every 10 s; while pushing, fillers alternate after at least 8 s of silence from the last clip's end. No voices overlap. The AED reminder is due at 30 s; while its clip plays, text is exactly "Ask someone to bring an AED (the box that can restart the heart)". A busy clip can delay it. Contact button opens the dialer only; it does not call. Hold to stop opens handover. Home returns; test contact clears. | [ ] |
| 8 | 10 s | ALONE | Look at the shipped OpenAI-made pictures in the guide and at Emergency start. | Hands are on the centre of the chest. No odd hands, limbs, or poses. Pictures look clear in light and dark mode. | [ ] |

Total: 235 s for steps 1–8, under 240 s / 4 minutes. The mode and colour checks apply to every row, not just Home.

## Send back

- OK or not for each step. Name any screen that failed in light or dark mode, lost vivid camera colours, or showed "Debug replay".
- Seconds from the first slow rate to FASTER on screen, and to the start of the "Push faster" voice in step 7. Note if another line was still playing.
- Note if the AED (?) pop-up or its close button failed. For the 30 s reminder, note any delay from a busy clip and whether the exact AED text showed while it played.
- Rollback: `powershell -ExecutionPolicy Bypass -File D:\projects\zero_flux\golden\install-golden.ps1`
- Plug the phone in after, for the trace pull.

The team also tried Hindi, Kannada, English and Gujarati on the phone on 31 Oct 2026 and said they worked well. This was a team try, not a formal translation or medical review. Tamil, Telugu, Malayalam, Marathi, Bengali and Odia remain unreviewed. These are team reports, not checks run by this docs worker.
