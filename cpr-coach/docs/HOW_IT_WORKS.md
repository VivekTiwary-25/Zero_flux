# How CPR Coach works — one page for judges

**CPR Coach is a CPR practice app, not a medical device.** It watches a rescuer practise compressions on a cushion and coaches out loud. Two people: the **rescuer** kneels and pushes, and only listens. The **holder** stands at the rescuer's side, holds the phone sideways, and watches the screen. Voice is for the rescuer; the screen is for the holder. In a real emergency, call 112 — the app never replaces that.

## The pipeline: camera to one spoken correction

1. **Camera sees the arms.** The holder points the phone at the rescuer from the side. A body-outline guide plus on-screen hints ("Move back", "Wrist out of frame") help frame the shot. Hints are never spoken. The session cannot start until the shoulder, elbow and wrist are clearly visible ("Ready").
2. **On-device pose model finds the joints.** MediaPipe Pose Landmarker, bundled inside the app, marks the shoulder, elbow, wrist and hip in every frame. No video ever leaves the phone.
3. **Signal math turns motion into meaning.** The wrist's height above the hip cancels small wobble from the holder's hand; the repeating beat over the last few seconds gives compressions per minute. The elbow angle comes from the shoulder–elbow–wrist geometry. Arms visible but no steady beat means a pause. Landmarks unclear means tracking is lost — and then the app shows no number at all, never a guess.
4. **One spoken correction at a time.** Priority order: "Keep going" after a pause, then "Push faster" / "Slow down" against the 100–120 per minute target, then "Keep your elbows straight", with occasional praise for steady work. A correction plays only after its reading holds steady, and a posture correction uses a shorter wait than a rate correction. A separate gap between phrases keeps them apart, and the same phrase never plays twice in a row. While tracking is lost the app stays silent and the metronome keeps playing.
5. **Metronome and voice clips, no internet.** The beat and all spoken phrases are short pre-recorded clips stored in the app (live synthesis is too slow for coaching). The full session is two minutes, then a scorecard shows this run's readings — or "Not measured" for anything the app could not really see.

## Everything on-device — what each part does

| Part | Where it runs | What it does |
|---|---|---|
| Camera + pose model | On the phone (GPU, CPU fallback) | Finds shoulder, elbow, wrist, hip each frame |
| Rate / elbow / pause math | On the phone | Beat from wrist motion, angle from joints, pauses from gaps |
| Feedback logic | On the phone | Picks at most one correction, demands steady readings first |
| Metronome + voice clips | On the phone | Beat plus short clips in the selected language |
| Speech recognition + mic commands | On the phone | Keyword commands ("start", "stop"); listening mutes coaching voice, metronome continues |
| Network | Nowhere | The app has no networking; nothing is uploaded |

## What it cannot do

One camera cannot measure compression depth, force, or hand placement — the app estimates rate, elbow posture and pauses only. Every number on screen is measured or it is absent. Accuracy and latency figures live in `docs/MEASUREMENTS.md`; anything not yet measured is stated as not yet measured.
