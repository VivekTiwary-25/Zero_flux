# Final phone test (CPH2467, under 4 minutes)

For the final build on CPH2467. Start the app from its icon, not a link or test tool. Rescuer pushes a firm cushion, never a person. Holder holds the phone steady side-on. Mic OFF. Do not call anyone: cancel the dialer each time it opens.

Allow 235 s in all, with the optional row (under 240 s / 4 minutes). Use a clock. Do not repeat the whole run for each mode.

On **every app screen** below, switch the phone between light and dark mode and check both views. Include boxes that open, the AED pop-up, setup, countdown, pause, scorecard, session detail, Emergency, and handover. Do this within each row's time. Text and buttons must stay clear, with no cut-off text. Camera screens must keep vivid green and amber in both modes, whatever the phone's setting; no dull or washed-out colours. No **"Debug replay"** text may show anywhere in the app.

| Step | Seconds | ALONE or TWO PEOPLE | Do | Expect | OK? |
|---|---|---|---|---|---|
| 1 | 20 s | ALONE | Tap the app icon. Look at Home. Tap Dashboard tab, CPR guide tab, Home tab. Check each in light and dark mode. | App opens from its icon. All three tabs switch cleanly. The new look shows well on Home in both modes. No "Debug replay" text. | [ ] |
| 2 | 30 s | ALONE | Tap CPR guide. Tap tabs 1–5, Previous, Next. On step 2, tap the AED (?) button, read the pop-up, then tap its close button. On step 5 tap Open Emergency, then hold Stop to leave and go Home. | Each guide step shows the scope note, picture, title, text, and tip. Previous and Next work. AED (?) opens a clear, readable box; its close button shuts it and leaves step 2 in view. Open Emergency on step 5 opens Emergency. | [ ] |
| 3 | 20 s | ALONE | On Home, tap Emergency contact. Type a name and number; tap Save. Open Emergency contact again, tap Pick from contacts, then cancel. Keep the saved contact for step 7. | Home shows the saved name. Pick from contacts opens the phone's contact picker with no permission asked. Cancel returns to the app. | [ ] |
| 4 | 15 s | ALONE | From Home tap Practise. Tap Back within 1 s. Tap Practise again. | Screen changes at once, with no freeze. Camera shows promptly; setup goes ready. Back within 1 s returns cleanly with no crash. Second open is prompt and ready. | [ ] |
| 5 | 45 s | TWO PEOPLE | With rescuer in the framing view, tap Start (3 s countdown). Rescuer pushes the cushion on the beat for 25 s. Tap pause, resume, then Stop. Check scorecard; tap Home. Check both modes as you go. | New look is clear on setup, countdown, pause, camera, and scorecard. Camera keeps vivid green and amber in both modes. Scorecard says "Rate measured for X of Y s". Compare with ee682a1: Practise must feel the same. | [ ] |
| 6 | 30 s | ALONE | Tap Dashboard. Check tags on charts and rows. Open the step 5 Practise session, then tap Back. Tap Clear sample data last. | "Testing data" and "Synthetic data" tags show on sample rows and charts. Charts draw. The step 5 session is listed with no such tag. Its detail opens. Clear sample data removes tagged rows and keeps the new session. | [ ] |
| 7 | 65 s | TWO PEOPLE | Tap Emergency. Rescuer pushes the cushion slow. Once told to push faster, push on the beat past 30 s from Emergency start; say nothing. Check both modes while pushing. Tap the saved contact button; cancel the dialer. Hold Stop for 1 s, then tap Home. Clear the test contact. | Hand picture shows in opening lines only. Saved contact button is smaller and beside the bigger Call 112 button. When rate shows slow, FASTER shows within about 1 s; "Push faster" voice follows about 1.5 s after the slow rate, when no other line is playing. Back on beat: "Good" once for the change. Keep pushing: short prompts about every 10 s, never two voices at once. At 30 s, text says "Ask someone to bring an AED (heart-shock box)". Contact button opens the dialer only; it does not call. Hold to stop opens handover. Home returns; test contact clears. | [ ] |
| 8 (optional) | 10 s | ALONE | If the new pictures made it into this build, look at them in the guide and at Emergency start. If not, mark N/A. | Hands are on the centre of the chest. No odd hands, limbs, or poses. Pictures look clear in light and dark mode. | [ ] / N/A |

Total: 225 s for steps 1–7; 235 s with step 8. Both are under 240 s / 4 minutes. The mode and colour checks apply to every row, not just Home.

## Send back

- OK or not for each step; N/A only for the optional new pictures. Name any screen that failed in light or dark mode, lost vivid camera colours, or showed "Debug replay".
- Seconds from the first slow rate to FASTER on screen, and to the start of the "Push faster" voice in step 7. Note if another line was still playing.
- Note if the AED (?) pop-up or its close button failed, or the exact AED line was missing at 30 s.
- Rollback: `powershell -ExecutionPolicy Bypass -File D:\projects\zero_flux\golden\install-golden.ps1`
- Plug the phone in after, for the trace pull.

Which steps have only been run on the emulator or replay so far: all of them. This sheet is a plan, not proof of a phone pass.
