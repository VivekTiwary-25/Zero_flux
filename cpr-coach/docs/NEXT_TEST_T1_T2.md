# Short phone test (CPH2467, under 1 minute)

For build **325457e** (APK sha1 `71007c303628`). Rescuer pushes a firm cushion, holder holds the phone steady side-on. Never push on a person. Mic OFF. Don't call anyone; cancel the dialer if it opens.

Pushing faster or slower than the metronome makes the rate go off target. Rate needs a few seconds to lock first.

| Step | Do | Expect | OK? |
|---|---|---|---|
| 1. 5 s | From Home, tap Practise | Screen changes at once, no freeze. Camera up in about 1 s | [ ] |
| 2. 5 s | Push a few beats, tap Stop | Scorecard as before | [ ] |
| 3. 10 s | Home, tap Emergency. Start pushing on the beat when told | Opening voice as before. Then "Good" | [ ] |
| 4. 12 s | Push clearly slower | FASTER on screen in about 1 s after the rate shows slow. "Push faster" about 1.5 s after that | [ ] |
| 5. 8 s | Back on the beat | FASTER clears. "Good" once | [ ] |
| 6. 15 s | Keep pushing on the beat, say nothing | A line at least about every 10 s, never two at once | [ ] |

Practise replay is byte-identical to ee682a1, so a quick Practise check is enough.

## Send back
- OK or not OK for each row, and how many seconds the screen and voice took in steps 4 and 5.
- Is it too chatty? Say so. Rollback is `D:\projects\apk-builds\ee682a1.apk`.
- Then plug the phone in. I'll pull the trace.

These times are from replay. Nothing in this build has run on a phone yet.
