# CPR Coach docs — index

Every file in this folder, what it is, and who it is for.

## For the demo

- [`DEMO_2MIN.md`](DEMO_2MIN.md) — two-minute stage run: the safety line, stage roles, the cue card and the "do not" list; for the presenter, rescuer and holder on stage.
- [`DEMO_SCRIPT.md`](DEMO_SCRIPT.md) — three-minute stage run with the same roles, what each screen means, plus fallbacks when tracking, audio or the app fails; for the same three people, when the slot is longer.
- [`PITCH.md`](PITCH.md) — spoken two-minute pitch with timings and the honest-limits close; for the team member talking to judges before the demo.
- [`HOW_IT_WORKS.md`](HOW_IT_WORKS.md) — one page on the camera-to-spoken-correction pipeline and what the app cannot measure; for judges reading after the demo.
- [`JUDGE_QA.md`](JUDGE_QA.md) — prepared answers on accuracy, design choices, languages, privacy and scope; for whoever fields judge questions.

## For testing

- [`PHONE_TEST.md`](PHONE_TEST.md) — tick-box checklist for a real phone, including the colour meanings and its own install steps; for the two people testing a build on a device.
- [`ACCURACY_TEST.md`](ACCURACY_TEST.md) — procedure for three hand-counted runs on a cushion and how to pull and analyse the traces; for the rescuer, holder and counter running that test.
- [`MEASUREMENTS.md`](MEASUREMENTS.md) — the table where counted runs are entered from pulled traces (its run rows are still empty) plus the M0 single-reading observations; for whoever runs `ACCURACY_TEST.md`.
- [`CLIP_CHECK.md`](CLIP_CHECK.md) — machine transcription check of the generated voice clips per language, and explicitly not a substitute for listening; for whoever checks the audio before a demo.
- [`DESIGN_AUDIT.md`](DESIGN_AUDIT.md) — source-only review against the `SPEC.md` design rules, failures ranked by likely judge-visible impact and separated from the checks that pass; for whoever fixes the UI.
- [`SPEC_CHECK.md`](SPEC_CHECK.md) — source-only review of the `SPEC.md` §3 behaviour requirements, marking each as met, partly or missing; for whoever fixes app behaviour.

## For building

- [`SPEC.md`](SPEC.md) — the build brief: what the app is, agreed behaviour per screen, how the feedback works, the design philosophy and the build order; for the developer.
- [`INSTALL.md`](INSTALL.md) — building the debug APK and pushing the speech models to a team phone from a Windows PC; for the person installing on devices.
- [`CLIP_STATUS.md`](CLIP_STATUS.md) — record of which voice clips were generated for each language and that none have been checked by a person; for whoever prepares the audio assets.
- [`HINDI_REVIEW.md`](HINDI_REVIEW.md) — machine review of the Hindi strings and clips with suggested wordings; for whoever edits Hindi text and audio.
- [`KANNADA_REVIEW.md`](KANNADA_REVIEW.md) — the same machine review for Kannada; for whoever edits Kannada text and audio.
- [`OTHER_REVIEW.md`](OTHER_REVIEW.md) — the same machine review for Marathi, Gujarati, Bengali and Odia; for whoever edits those languages.
