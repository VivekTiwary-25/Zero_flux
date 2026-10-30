# Decisions taken without Vivek (31 Oct, 04:20 onward)

Rule: take the safer option (no new false warnings, don't break pace counting or start/stop), write it down, keep going.

- **Voices:** stay on the current offline voices (Piper / MMS). No Azure. (Vivek's call.)
- **Posture limits:** bent and lean are judged against the rescuer's own first ~6 s of good pushing (f99a1bc). Chosen over fixed limits because fixed limits that caught recording 15's lean also flagged lean in 45-83% of straight recordings 11 and 12.
- **Phrase repeats:** a correction may repeat only after its condition has been gone for 3 s (1c3aa4d). Stricter than "any clear" so random readings never repeat back to back (EngineFuzzTest).
- **Rate before posture in Emergency:** a locked rate that is off target (Faster/Slower) still outranks Straighten arms, as the existing tests require (a904d2d).
- **Rejected frames keep "person in view" for up to 1.5 s** (e50a11a), so a ghost-wrist burst doesn't blank the number; after 1.5 s without a trusted frame it shows "Can't see arms" as before.
- **English countdown clip:** the old take wasn't clipped; Whisper misses the soft "Get" about half the time. Installed a fresh take of the same text that transcribes exactly (7e09b77). Malayalam and Odia already play the English countdown, so they get the same new take (ccb61e0).
- **Kannada lean line:** now "ಭುಜಗಳು ಕೈಗಳ ನೇರ ಮೇಲೆ ಇರಲಿ." (directly above), 2.9 s. The imperative version ran over 3.3 s. Needs a Kannada speaker's ear.
- **Not done:** Hindi/Bengali/etc. "both palms" rewording from the desk review: no native speaker awake, current lines already say "directly above".
- **Pause rule (4dfb3b6, 63f3fe4):** Keep going needs ~2 s of mostly-still reads, the current read still, and no two moving reads in a row in the last second; a short LOST doesn't drop a confirmed pause; the coach resets when the user resumes from the pause button. Recording 13 no longer gets a Keep going while pushing at 5.3 s.
- **Per-phrase steady times (63f3fe4):** a flickering rate phrase can no longer starve a steady elbow correction. Rate still outranks posture when both are steady.
- **Lean held through side-on flicker (f0d6188):** recording 15's lean was late because a ~1 s "not side-on" flicker wiped it. Now a confirmed issue survives a short flicker (held up to 3 s) instead of lowering the lean threshold, which added false leans on straight recordings 11 and 15.
- **Install:** the CPH dropped off USB at ~04:24 (not seen by adb). Build 63f3fe4 is built and installs automatically as soon as the phone is seen again.
- **Stale numbers (88234d0):** while every frame is rejected for more than 0.5 s, the app shows no rate, elbows or posture (WARMING, no number) instead of the last values; after 1.5 s it shows "Can't see arms". Not fixed: a 9-ghost/1-real repeating pattern can still win the guard's re-anchor; telling it from a real move needs more than the wrist (left for after the freeze).
- **Scorecard pauses (fcb4cc6):** counted with the same "about 2 s mostly still" rule as the voice. Recording 14 now counts its real rest (1, was 0). Recording 15 still counts 1 for its short reposition, because the app's PAUSED state outlasts the real 1.7 s stop; raising the minimum would make the scorecard disagree with the voice.
- **Install target is now fcb4cc6** (replaces 63f3fe4, which never reached the phone).
