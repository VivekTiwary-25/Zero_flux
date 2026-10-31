# Short phone test (CPH2467, under 1 minute)

For build **270070e** (APK sha1 `2db874361b2f`). It includes everything in 325457e, so test that sheet too if you haven't. No pushing needed except step 5. Don't call anyone: cancel the dialer every time it opens.

| Step | Do | Expect | OK? |
|---|---|---|---|
| 1. 5 s | Look at Home | "Adult CPR only" under Emergency. New "Emergency contact" and "CPR guide" entries. Nothing else moved | [ ] |
| 2. 15 s | Tap CPR guide. Tap tabs 1-5, Next, Previous. On step 5 tap Open Emergency, then hold Stop to leave | Top note, picture, title, text, tip on each step. Emergency opens | [ ] |
| 3. 10 s | Tap Emergency contact. Type a name and number, Save | Home shows the name. Pick from contacts works, no permission asked | [ ] |
| 4. 10 s | Tap Emergency | Hand picture during the opening lines only. Smaller button with the name beside Call 112, which stays bigger. Tap the name: dialer opens with the number, nothing dials | [ ] |
| 5. 10 s | Push a few beats, hold Stop, then Practise until a scorecard | Scorecard says "Rate measured for X of Y s" | [ ] |

The AED, Continue and Swap text only shows at about 2 and 4 minutes into Emergency, so check that if you have time.

Clear the test contact afterwards: Emergency contact, Clear.

## Send back
- OK or not OK per row. Does the hand picture look right, and does it cover anything?
- Rollback is `D:\projects\apk-builds\325457e.apk`, or `ee682a1.apk`.

None of these screens have been seen on a device or emulator yet. Translations in the other languages are machine made and unchecked: see `STRINGS_UNREVIEWED.md`.
