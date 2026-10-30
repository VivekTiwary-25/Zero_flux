# Translation notes: drafts needing native-speaker and first-aid review

All translations in `app/src/main/assets/strings/<code>.json` and `tools/clipgen/phrases_<code>.json` remain drafts until a native speaker listens and a first-aid reviewer checks the instructions. The English, Hindi and Kannada tables below record an earlier spoken-line revision. The two-review revision appended below records this task's exact old and new lines. Every row needs a native speaker's ear; no listening approval is claimed.

The earlier revision covered English, Hindi and Kannada audio; those clips and phrase files are unchanged by this task. This revision updates on-screen compression cues in all ten languages, disambiguates posture instructions that used a word for both arms and hands, and updates Tamil, Telugu, Marathi, Gujarati and Bengali spoken compression cues. Malayalam and Odia coaching audio falls back to matching English clips, except their original `lang_name.wav`. All keys and the short faster/good/slower screen labels are retained. `slow_down` targets compression speed, not pressure or depth. The earlier tables mention `shoulders_over_hands`; this task does not add that key or any other key. `turn_phone` remains a silent holder instruction.

## English (`en`)

| Key | Old line | New line | Review status |
|---|---|---|---|
| `countdown` | Starting in three, two, one | Get ready. Three, two, one. | Draft: needs a native speaker's ear |
| `push_faster` | Push faster | Push the chest faster. | Draft: needs a native speaker's ear |
| `slow_down` | Slow down | Reduce the speed of your chest compressions. | Draft: needs a native speaker's ear |
| `elbows_straight` | Keep your elbows straight | Keep both elbows straight. | Draft: needs a native speaker's ear |
| `keep_going` | Keep going | Keep pushing the chest. | Draft: needs a native speaker's ear |
| `good` | Well done, keep going | Good. Keep pushing the chest. | Draft: needs a native speaker's ear |
| `session_done` | Great work, session complete | Well done. Your practice session is complete. | Draft: needs a native speaker's ear |
| `em_call` | Call one one two. Put it on speaker. | Call one one two. Turn on the phone's speaker. | Draft: needs a native speaker's ear |
| `em_hands` | Place your hands in the centre of the chest. | Put both hands in the centre of the chest. | Draft: needs a native speaker's ear |
| `em_push` | Push hard and fast, with the beat. | Push the chest hard and fast, in time with the beat. | Draft: needs a native speaker's ear |
| `em_swap` | If someone can take over, switch now. | If someone else can push the chest, swap now. | Draft: needs a native speaker's ear |
| `em_aed` | If there's an AED nearby, ask someone to bring it. | If an A E D is nearby, ask someone to bring it. | Draft: needs a native speaker's ear |
| `em_continue` | Keep going until help arrives. | Keep pushing the chest until help arrives. | Draft: needs a native speaker's ear |
| `shoulders_over_hands` | (new key) | Bring your shoulders over your hands. | Draft: needs a native speaker's ear |

Review speed-versus-force clarity, the spoken emergency digits, separate A E D letters, countdown timing and whether the shoulder cue clearly puts shoulders vertically above hands.

## 1. Hindi (`hi`, हिन्दी)

| Key | Old line | New line | Review status |
|---|---|---|---|
| `lang_name` | हिन्दी | हिन्दी | Draft: needs a native speaker's ear; identity unchanged |
| `countdown` | शुरू कर रहे हैं तीन, दो, एक | तैयार रहें। तीन, दो, एक। | Draft: needs a native speaker's ear |
| `push_faster` | और तेज़ दबाएँ | छाती और तेज़ी से दबाएँ। | Draft: needs a native speaker's ear |
| `slow_down` | रफ़्तार थोड़ी कम करें | छाती दबाने की रफ़्तार कम करें। | Draft: needs a native speaker's ear |
| `elbows_straight` | कोहनी सीधी रखें | दोनों कोहनियाँ सीधी रखें। | Draft: needs a native speaker's ear |
| `keep_going` | दबाते रहें | छाती दबाते रहें। | Draft: needs a native speaker's ear |
| `good` | बहुत अच्छे, दबाते रहें | ठीक है। छाती दबाते रहें। | Draft: needs a native speaker's ear |
| `session_done` | शाबाश, अभ्यास पूरा हुआ | अच्छा किया। आपका अभ्यास पूरा हो गया है। | Draft: needs a native speaker's ear |
| `em_call` | एक एक दो पर कॉल करें। फ़ोन स्पीकर पर रखें। | एक एक दो पर फ़ोन करें। फ़ोन का स्पीकर चालू करें। | Draft: needs a native speaker's ear |
| `em_hands` | छाती के बीच में दोनों हाथ रखें। | अपने दोनों हाथ छाती के बीचोंबीच रखें। | Draft: needs a native speaker's ear |
| `em_push` | ताल के साथ ज़ोर से और तेज़ दबाएँ। | ताल के साथ छाती ज़ोर से और तेज़ी से दबाएँ। | Draft: needs a native speaker's ear |
| `em_swap` | अगर कोई और कर सकता है, तो अभी बारी बदल लें। | अगर कोई और छाती दबा सकता है, तो अब बारी बदलें। | Draft: needs a native speaker's ear |
| `em_aed` | अगर पास में शॉक मशीन या ए ई डी है, तो किसी से मँगवाएँ। | अगर पास में ए ई डी मशीन है, तो किसी से लाने को कहें। | Draft: needs a native speaker's ear |
| `em_continue` | मदद आने तक दबाते रहें। | मदद आने तक छाती दबाते रहें। | Draft: needs a native speaker's ear |
| `shoulders_over_hands` | (new key) | अपने कंधे हाथों के ठीक ऊपर लाएँ। | Draft: needs a native speaker's ear |

Review polite imperatives and natural cadence. `रफ़्तार` refers specifically to the speed of chest compressions; it must not be heard as less force. Confirm that `ताल` is understood as the metronome beat, `बीचोंबीच` clearly identifies chest centre, and the separated emergency digits and AED letters remain intelligible in Piper. The AED cue now names the device rather than the loose label "shock machine"; confirm lay recognition of `ए ई डी मशीन`. First-aid review must check hand-placement clarity without adding medical claims.

Existing screen-only review item, unchanged:

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `walk_title_3` (`hi.json`) | हरा-नीला = सही। पीला = कुछ सुधारें। | "Teal" and "Amber" have no single everyday Hindi words; "हरा-नीला" and "पीला" are approximations. | Low medical risk (practice UI only), but holder must map the words to the on-screen glow colours. |

## 2. Kannada (`kn`, ಕನ್ನಡ)

| Key | Old line | New line | Review status |
|---|---|---|---|
| `lang_name` | ಕನ್ನಡ | ಕನ್ನಡ | Draft: needs a native speaker's ear; identity unchanged |
| `countdown` | ಶುರುವಾಗುತ್ತಿದೆ: ಮೂರು, ಎರಡು, ಒಂದು | ಸಿದ್ಧರಾಗಿ. ಮೂರು, ಎರಡು, ಒಂದು. | Draft: needs a native speaker's ear |
| `push_faster` | ಇನ್ನೂ ವೇಗವಾಗಿ ಒತ್ತಿ | ಎದೆಯನ್ನು ಇನ್ನಷ್ಟು ವೇಗವಾಗಿ ಒತ್ತಿ. | Draft: needs a native speaker's ear |
| `slow_down` | ವೇಗ ಸ್ವಲ್ಪ ಕಡಿಮೆ ಮಾಡಿ | ಎದೆ ಒತ್ತುವ ವೇಗವನ್ನು ಕಡಿಮೆ ಮಾಡಿ. | Draft: needs a native speaker's ear |
| `elbows_straight` | ಮೊಣಕೈಗಳನ್ನು ನೇರವಾಗಿಡಿ | ಎರಡೂ ಮೊಣಕೈಗಳನ್ನು ನೇರವಾಗಿ ಇಡಿ. | Draft: needs a native speaker's ear |
| `keep_going` | ಒತ್ತುತ್ತಾ ಇರಿ | ಎದೆಯನ್ನು ಒತ್ತುತ್ತಿರಿ. | Draft: needs a native speaker's ear |
| `good` | ಚೆನ್ನಾಗಿದೆ, ಒತ್ತುತ್ತಾ ಇರಿ | ಚೆನ್ನಾಗಿದೆ. ಎದೆಯನ್ನು ಒತ್ತುತ್ತಿರಿ. | Draft: needs a native speaker's ear |
| `session_done` | ಶಾಬಾಶ್, ಅಭ್ಯಾಸ ಮುಗಿಯಿತು | ಚೆನ್ನಾಗಿ ಮಾಡಿದಿರಿ. ನಿಮ್ಮ ಅಭ್ಯಾಸ ಮುಗಿದಿದೆ. | Draft: needs a native speaker's ear |
| `em_call` | ಒಂದು ಒಂದು ಎರಡು ಕ್ಕೆ ಕಾಲ್ ಮಾಡಿ. ಸ್ಪೀಕರ್ ಆನ್ ಮಾಡಿ. | ಒಂದು ಒಂದು ಎರಡು ಸಂಖ್ಯೆಗೆ ಕರೆ ಮಾಡಿ. ಫೋನಿನ ಸ್ಪೀಕರ್ ಆನ್ ಮಾಡಿ. | Draft: needs a native speaker's ear |
| `em_hands` | ಎದೆಯ ಮಧ್ಯದಲ್ಲಿ ಕೈಗಳನ್ನು ಇಡಿ. | ನಿಮ್ಮ ಎರಡೂ ಕೈಗಳನ್ನು ಎದೆಯ ಮಧ್ಯದಲ್ಲಿ ಇಡಿ. | Draft: needs a native speaker's ear |
| `em_push` | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಬಲವಾಗಿ ಮತ್ತು ವೇಗವಾಗಿ ಒತ್ತಿ. | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಎದೆಯನ್ನು ಬಲವಾಗಿ ಮತ್ತು ವೇಗವಾಗಿ ಒತ್ತಿ. | Draft: needs a native speaker's ear |
| `em_swap` | ಬೇರೆಯವರು ಇದ್ದರೆ, ಈಗ ಸರದಿ ಬದಲಿಸಿ. | ಬೇರೆಯವರು ಎದೆ ಒತ್ತಲು ಸಿದ್ಧರಿದ್ದರೆ, ಈಗ ಸರದಿ ಬದಲಿಸಿ. | Draft: needs a native speaker's ear |
| `em_aed` | ಹತ್ತಿರದಲ್ಲಿ ಎ ಇ ಡಿ ಇದ್ದರೆ, ಯಾರಾದರೂ ತರಲು ಹೇಳಿ. | ಹತ್ತಿರ ಏ ಈ ಡಿ ಯಂತ್ರ ಇದ್ದರೆ, ಯಾರನ್ನಾದರೂ ತರಲು ಹೇಳಿ. | Draft: needs a native speaker's ear |
| `em_continue` | ಸಹಾಯ ಬರುವವರೆಗೂ ಒತ್ತುತ್ತಾ ಇರಿ. | ಸಹಾಯ ಬರುವವರೆಗೂ ಎದೆಯನ್ನು ಒತ್ತುತ್ತಿರಿ. | Draft: needs a native speaker's ear |
| `shoulders_over_hands` | (new key) | ನಿಮ್ಮ ಭುಜಗಳನ್ನು ಕೈಗಳ ನೇರ ಮೇಲಕ್ಕೆ ತನ್ನಿ. | Draft: needs a native speaker's ear |

Review `ಎದೆಯನ್ನು` as an explicit chest object and `ವೇಗವನ್ನು ಕಡಿಮೆ ಮಾಡಿ` as reducing speed, never force. Check the polite forms, naturalness of the shoulder cue, chest-centre placement and whether `ಏ ಈ ಡಿ` sounds like separate AED letters. MMS removes punctuation before synthesis, so a person must specifically check countdown separation, sentence boundaries and the emergency digits; spelling alone is not proof of intelligibility.

## Generation and machine validation

The unmodified reference `generate_clips.py` is loaded outside this checkout, with its phrase-directory lookup redirected to these edited lists. Models are read only from `D:\projects\sih\iTantra\app\src\main\assets`. It generated English, Hindi and Kannada WAVs and per-language manifests without failures. Model-provided sample rates, mono sixteen-bit PCM, silence trimming and peak normalization follow the existing generator. No model files or other language packs are changed.

Generation produced fourteen English, fifteen Hindi and fifteen Kannada clips, with zero generation failures. Phrase/manifest checks confirmed all original keys survive, only `shoulders_over_hands` was added, no numeric digits occur in the spoken text, and every manifest text/hash matches its phrase.

Validation command (run from this task checkout; checker stays in the external reference):

```powershell
python ../ref-t261031-0212-a105/cpr-coach/tools/clipgen/check_clips.py --clips cpr-coach/app/src/main/assets/clips --phrases cpr-coach/tools/clipgen --whisper D:/projects/sih/iTantra/app/src/main/assets --indic D:/projects/sih/iTantra/app/src/main/assets --lang en,hi,kn --markdown ../ref-t261031-0212-a105/CLIP_CHECK.md --checkpoint ../ref-t261031-0212-a105/clip-check.json
```

The checker exited zero but reported **forty-four rows, thirty-two flagged**, so this is not a clean transcription pass. All forty-four clips passed its audio checks: mono sixteen-bit PCM, non-silent, unclipped and within its duration window. The generated sample rates were measured as 22050 Hz for English/Hindi and 16000 Hz for Kannada. The checker itself returns zero even when rows are flagged.

| Language | Checked rows | Flagged rows | Reason |
|---|---|---|---|
| English | 14 | 2 | `countdown` and `shoulders_over_hands` exceed the character-error threshold |
| Hindi | 15 | 15 | IndicConformer recognition files missing from the permitted model root; no transcripts |
| Kannada | 15 | 15 | IndicConformer recognition files missing from the permitted model root; no transcripts |

English `countdown` was transcribed as "Ready, 3, 2, 1." (15/23 character errors, 0.6522), whereas the phrase is "Get ready. Three, two, one." The recognizer's digits differ from the required spelled-out TTS input; it also omitted "Get". English `shoulders_over_hands` was transcribed as "Bring your shoulders over your hands. Bring your shoulders" (21/36 character errors, 0.5833). A person must listen to determine whether that repetition is in the audio or a recognition error; the requested instruction was not rewritten to optimize a machine score. The unflagged `good` transcript omitted "Good", so its opening also needs listening review. Emergency digits and AED letter pronunciation need listening review even where the machine score is below threshold.

Hindi/Kannada transcription requires `hi.onnx`, `kn.onnx`, `languages/hi/tokens.txt`, `languages/kn/tokens.txt` and `shared/encoder.weights.bin` under the permitted assets root. They are absent there. The checker was explicitly pointed at that root instead of its default model-staging directory to respect the read-only model-location restriction. No models were fetched, copied or read outside the permitted root. Native-speaker listening and first-aid review remain required for every line; no phone or adb was used, and no Android compilation was performed for this asset-only change. No new screen-string keys are required.

---

## 3. Tamil (`ta` — தமிழ்)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `em_call` (`phrases_ta.json`) | ஒன்று ஒன்று இரண்டுக்கு அழைக்கவும். ஸ்பீக்கரில் போடுங்கள். | Spoken Tamil often uses "ஒண்ணு ஒண்ணு ரெண்டு" colloquially, whereas TTS engines typically pronounce standard "ஒன்று ஒன்று இரண்டு" more cleanly. | Mispronunciation or slurring of the emergency number `112` could delay calling emergency services. |
| `em_push` (`phrases_ta.json`, `ta.json`) | தாளத்தோடு வேகமாகவும் பலமாகவும் அழுத்தவும். | "பலமாகவும்" (firmly/strongly) vs. "ஆழமாகவும்" (deeply). | Rescuer must understand they need full compression depth, not just surface speed. |
| `elbows_straight` (`phrases_ta.json`) | முழங்கையை நேராக வையுங்கள் | Singular collective "முழங்கையை" vs. plural "முழங்கைகளை" for brevity. | Both elbows must remain locked straight during compressions to avoid rescuer fatigue and shallow depth. |
| `em_aed` (`phrases_ta.json`) | அருகில் ஏ இ டி இருந்தால், யாரையாவது எடுத்து வரச் சொல்லுங்கள். | Letter-by-letter rendering "ஏ இ டி" in Tamil script. | Must be checked on Tamil MMS TTS so "இ" is pronounced like English letter 'E' rather than short 'i'. |

---

## 4. Telugu (`te` — తెలుగు)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `slow_down` (`phrases_te.json`) | కొంచెం నెమ్మదిగా నొక్కండి | Added "నొక్కండి" (press) because bare "నెమ్మదిగా" can sound like "stop/relax". | Rescuer might reduce compression depth instead of only reducing compression rate from >120/min to 100–120/min. |
| `elbows_straight` (`phrases_te.json`, `te.json`) | మోచేతులు నిటారుగా ఉంచండి | "నిటారుగా" (straight/upright) vs. "తిన్నగా" (straight); regional preference varies across Telangana and Andhra. | Must clearly instruct locking the elbow joints vertically over the chest. |
| `em_hands` (`phrases_te.json`, `te.json`) | ఛాతీ మధ్యలో చేతులు ఉంచండి. | Everyday wording for chest centre. | Same medical risk as other languages: general "chest centre" without heel-of-hand detail. |
| `em_aed` (`phrases_te.json`) | దగ్గరలో ఏ ఈ డీ ఉంటే, ఎవరినైనా తీసుకురమ్మని చెప్పండి. | Uses long vowels "ఏ ఈ డీ" to match English letter names A-E-D. | Requires TTS check to verify distinct letter pauses. |

---

## 5. Malayalam (`ml` — മലയാളം)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `push_faster` / `slow_down` (`phrases_ml.json`) | കൂടുതൽ വേഗത്തിൽ അമർത്തൂ / കുറച്ച് പതുക്കെ ആക്കൂ | Uses friendly imperative `-ഊ` ("അമർത്തൂ", "ആക്കൂ") suited for live coaching; screen buttons use `-ഉക` infinitive-imperative. | "കുറച്ച് പതുക്കെ ആക്കൂ" must not cause the rescuer to press softly (shallow compressions) when only rate needs slowing. |
| `live_pause` (`ml.json`) | താൽക്കാലികമായി നിർത്തുക | Malayalam has no single short everyday word distinguishing "Pause" from "Stop" ("നിർത്തുക"). | Button label is longer than English "Pause" and should be checked for truncation on smaller screens. |
| `em_aed` (`phrases_ml.json`) | അടുത്ത് എ ഇ ഡി ഉണ്ടെങ്കിൽ, കൊണ്ടുവരാൻ ആരോടെങ്കിലും പറയൂ. | Malayalam lacks a distinct long 'ē'/'ī' vowel letter differentiation in traditional acronym spelling ("എ ഇ ഡി"). | Piper Malayalam TTS pronunciation of "എ ഇ ഡി" should be verified by ear. |

---

## 6. Marathi (`mr` — मराठी)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `em_swap` (`phrases_mr.json`) | कोणी बदली करू शकत असेल, तर आता जागा बदला. | "बदली करू शकत असेल" (if someone can take over/relieve) vs. "मदतीला असेल". | Medical risk if rescuers pause compressions for too long (>10 seconds) while discussing or switching places. |
| `em_push` (`phrases_mr.json`, `mr.json`) | तालासोबत जोरात आणि वेगाने दाबा. | "जोरात" in Marathi means hard/forcefully (and sometimes loudly). | In CPR context with "दाबा" (press), it means press hard, but native review should confirm if "खोलवर" (deeply) is safer. |
| `score_pauses` (`mr.json`) | खंड | "खंड" vs. "थांबे" for pauses on the scorecard. | UI clarity only; no direct clinical risk. |

---

## 7. Gujarati (`gu` — ગુજરાતી)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `home_practise` / `score_title` (`gu.json`, `phrases_gu.json`) | પ્રેક્ટિસ / પ્રેક્ટિસ પૂરી થઈ | Everyday Gujarati speakers strongly prefer loanword "પ્રેક્ટિસ" over formal "મહાવરો" or "અભ્યાસ". | Low medical risk, but should be checked that emergency mode ("ઇમરજન્સી") and practice mode ("પ્રેક્ટિસ") are unmistakably distinct. |
| `em_swap` (`phrases_gu.json`) | જો કોઈ બીજું કરી શકે તેમ હોય, તો અત્યારે બદલાઈ જાઓ. | "બદલાઈ જાઓ" (switch places/roles) is conversational; verify naturalness in high-stress emergency audio. | Switch must be immediate to minimise hands-off time during cardiac arrest. |
| `em_aed` (`phrases_gu.json`) | જો નજીકમાં એ ઈ ડી હોય, તો કોઈને લાવવા કહો. | Spelled "એ ઈ ડી" for TTS letter readout. | Verify MMS Gujarati TTS does not blend the three vowels into a single diphthong. |

---

## 8. Bengali (`bn` — বাংলা)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `push_faster` (`phrases_bn.json`) | আরও দ্রুত চাপুন | "দ্রুত" (fast) vs. colloquial "তাড়াতাড়ি" (quickly); "দ্রুত" is shorter and cleaner for rhythmic coaching. | Rescuer needs to increase cadence to 100–120/min while still allowing full chest recoil between compressions. |
| `slow_down` (`phrases_bn.json`) | একটু ধীরে করুন | "একটু ধীরে করুন" vs. "একটু আস্তে চাপুন" ("আস্তে" in Bengali often means "softly/gently", which is dangerous in CPR). | **High medical risk avoided:** avoided "আস্তে" because pressing softly compromises perfusion; native speaker should confirm "ধীরে" clearly targets speed only. |
| `em_hands` (`phrases_bn.json`, `bn.json`) | বুকের ঠিক মাঝখানে হাত রাখুন। | "বুকের ঠিক মাঝখানে" (right in the middle of the chest). | Standard lay-CPR phrasing, though clinical training specifies interlocking fingers with the heel of the hand on the lower sternum. |

---

## 9. Odia (`or` — ଓଡ଼ିଆ)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `push_faster` / `em_push` (`phrases_or.json`, `or.json`) | ଆହୁରି ଶୀଘ୍ର ଦବାନ୍ତୁ / ତାଳ ସହିତ ଜୋରରେ ଏବଂ ଶୀଘ୍ର ଦବାନ୍ତୁ। | "ଶୀଘ୍ର" (quickly/fast) vs. "ଦ୍ରୁତ ଗତିରେ" (at fast speed); "ଶୀଘ୍ର" is more everyday spoken Odia. | Must ensure "ଶୀଘ୍ର ଦବାନ୍ତୁ" conveys faster compression rhythm rather than rushing without recoil. |
| `slow_down` (`phrases_or.json`) | ଟିକେ ଧୀରେ କରନ୍ତୁ | In Odia, "ଧୀରେ" can mean either slowly or softly/gently. | **Medical risk:** if a rescuer interprets "ଟିକେ ଧୀରେ କରନ୍ତୁ" as "press gently", compression depth will drop below effective levels. Native reviewer should check if "ଗତି ଟିକେ କমান୍ତୁ" (reduce speed slightly) is safer. |
| `frame_arm_out` (`or.json`) | କଚଟି ଫ୍ରେମ୍ ବାହାରେ ଅଛି | "କଚଟି" (wrist) is standard Odia, though some speakers say "ହାତ ଗଣ୍ଠି". | Framing hint only (for phone holder). |
| `em_aed` (`phrases_or.json`) | ଯଦି ପାଖରେ ଏ ଇ ଡି ଅଛି, କାହାକୁ ଆଣିବାକୁ କୁହନ୍ତୁ। | Spelled "ଏ ଇ ଡି" in Odia script. | Verify Meta MMS Odia voice pronounces the three letters distinctly. |

## Two-review revision (task t261031-0244-45fd)

All rows below are drafts needing a native speaker's ear; no native-speaker, first-aid, or listening approval is claimed. Missing chest nouns are made explicit, Bengali `slow_down` names compression speed (not soft pressure), and ambiguous arms/hands posture words now name elbows. The short `em_word_faster`, `em_word_good`, and `em_word_slower` labels are unchanged. Unrelated MINOR suggestions (Hindi setup hint, Marathi speaker wording, Tamil rate-label register) are intentionally not applied.

### On-screen lines

| Language / key | Old line | New line | Review status |
|---|---|---|---|
| `en/em_step_push` | Push hard and fast, with the beat. | Push the chest hard and fast, with the beat. | Draft: needs a native speaker's ear |
| `en/em_word_keep_pushing` | Keep pushing with the beat | Keep pushing the chest with the beat | Draft: needs a native speaker's ear |
| `hi/em_step_push` | ताल के साथ ज़ोर से और तेज़ दबाएँ। | ताल के साथ छाती ज़ोर से और तेज़ दबाएँ। | Draft: needs a native speaker's ear |
| `hi/em_word_keep_pushing` | ताल के साथ दबाते रहें | ताल के साथ छाती दबाते रहें | Draft: needs a native speaker's ear |
| `kn/em_step_push` | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಬಲವಾಗಿ, ವೇಗವಾಗಿ ಒತ್ತಿ. | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಎದೆಯನ್ನು ಬಲವಾಗಿ, ವೇಗವಾಗಿ ಒತ್ತಿ. | Draft: needs a native speaker's ear |
| `kn/em_word_keep_pushing` | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಒತ್ತುತ್ತಿರಿ | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಎದೆಯನ್ನು ಒತ್ತುತ್ತಿರಿ | Draft: needs a native speaker's ear |
| `ta/posture_straighten_arms` | கைகளை நேராக்குங்கள் | இரு முழங்கைகளையும் நேராக வையுங்கள் | Draft: needs a native speaker's ear |
| `ta/em_step_push` | தாளத்தோடு வேகமாகவும் பலமாகவும் அழுத்தவும். | தாளத்தோடு மார்பை வேகமாகவும் பலமாகவும் அழுத்தவும். | Draft: needs a native speaker's ear |
| `ta/em_word_keep_pushing` | தாளத்தோடு தொடர்ந்து அழுத்துங்கள் | தாளத்தோடு மார்பைத் தொடர்ந்து அழுத்துங்கள் | Draft: needs a native speaker's ear |
| `te/posture_straighten_arms` | చేతులను నిటారుగా ఉంచండి | మోచేతులను నిటారుగా ఉంచండి | Draft: needs a native speaker's ear |
| `te/em_step_push` | తాళంతో పాటు గట్టిగా, వేగంగా నొక్కండి. | తాళంతో పాటు ఛాతీని గట్టిగా, వేగంగా నొక్కండి. | Draft: needs a native speaker's ear |
| `te/em_word_keep_pushing` | తాళంతో నొక్కుతూ ఉండండి | తాళంతో ఛాతీని నొక్కుతూ ఉండండి | Draft: needs a native speaker's ear |
| `ml/posture_straighten_arms` | കൈകൾ നേരെയാക്കുക | കൈമുട്ടുകൾ നിവർത്തി വെക്കുക | Draft: needs a native speaker's ear |
| `ml/em_step_push` | താളത്തിനൊത്ത് ശക്തമായും വേഗത്തിലും അമർത്തുക. | താളത്തിനൊത്ത് നെഞ്ച് ശക്തമായും വേഗത്തിലും അമർത്തുക. | Draft: needs a native speaker's ear |
| `ml/em_word_keep_pushing` | താളത്തിനൊത്ത് അമർത്തിക്കൊണ്ടിരിക്കൂ | താളത്തിനൊത്ത് നെഞ്ച് അമർത്തിക്കൊണ്ടിരിക്കൂ | Draft: needs a native speaker's ear |
| `mr/posture_straighten_arms` | हात सरळ करा | दोन्ही कोपर सरळ ठेवा | Draft: needs a native speaker's ear |
| `mr/em_step_push` | तालासोबत जोरात आणि वेगाने दाबा. | तालासोबत छाती जोरात आणि वेगाने दाबा. | Draft: needs a native speaker's ear |
| `mr/em_word_keep_pushing` | तालासोबत दाबत राहा | तालासोबत छाती दाबत राहा | Draft: needs a native speaker's ear |
| `gu/posture_straighten_arms` | હાથ સીધા કરો | બંને કોણી સીધી રાખો | Draft: needs a native speaker's ear |
| `gu/em_step_push` | તાલ સાથે જોરથી અને ઝડપથી દબાવો. | તાલ સાથે છાતી જોરથી અને ઝડપથી દબાવો. | Draft: needs a native speaker's ear |
| `gu/em_word_keep_pushing` | તાલ સાથે દબાવતા રહો | તાલ સાથે છાતી દબાવતા રહો | Draft: needs a native speaker's ear |
| `bn/posture_straighten_arms` | হাত সোজা করুন | দুই কনুই সোজা রাখুন | Draft: needs a native speaker's ear |
| `bn/em_step_push` | তালের সাথে জোরে ও দ্রুত চাপুন। | তালের সাথে বুক জোরে ও দ্রুত চাপুন। | Draft: needs a native speaker's ear |
| `bn/em_word_keep_pushing` | তালে তালে চাপতে থাকুন | তালে তালে বুক চাপতে থাকুন | Draft: needs a native speaker's ear |
| `or/posture_straighten_arms` | ହାତ ସିଧା କରନ୍ତୁ | ଦୁଇ କହୁଣୀ ସିଧା ରଖନ୍ତୁ | Draft: needs a native speaker's ear |
| `or/em_step_push` | ତାଳ ସହିତ ଜୋରରେ ଓ ଶୀଘ୍ର ଦବାନ୍ତୁ। | ତାଳ ସହିତ ଛାତି ଜୋରରେ ଓ ଶୀଘ୍ର ଦବାନ୍ତୁ। | Draft: needs a native speaker's ear |
| `or/em_word_keep_pushing` | ତାଳ ସହିତ ଦବାଇ ଚାଲନ୍ତୁ | ତାଳ ସହିତ ଛାତି ଦବାଇ ଚାଲନ୍ତୁ | Draft: needs a native speaker's ear |

### Regenerated spoken lines (Tamil, Telugu, Marathi, Gujarati, Bengali)

Every clip in these five language folders is regenerated with the existing tool and read-only local MMS models. The table lists text changes only; other phrase texts and all keys are retained.

| Language / key | Old line | New line | Review status |
|---|---|---|---|
| `ta/push_faster` | இன்னும் வேகமாக அழுத்துங்கள் | மார்பை இன்னும் வேகமாக அழுத்துங்கள் | Draft: needs a native speaker's ear |
| `ta/slow_down` | வேகத்தைக் கொஞ்சம் குறையுங்கள் | மார்பை அழுத்தும் வேகத்தைக் குறையுங்கள் | Draft: needs a native speaker's ear |
| `ta/keep_going` | தொடர்ந்து அழுத்துங்கள் | மார்பைத் தொடர்ந்து அழுத்துங்கள் | Draft: needs a native speaker's ear |
| `ta/good` | நன்று, தொடர்ந்து அழுத்துங்கள் | நன்று. மார்பைத் தொடர்ந்து அழுத்துங்கள். | Draft: needs a native speaker's ear |
| `ta/em_push` | தாளத்தோடு வேகமாகவும் பலமாகவும் அழுத்துங்கள். | தாளத்தோடு மார்பை வேகமாகவும் பலமாகவும் அழுத்துங்கள். | Draft: needs a native speaker's ear |
| `ta/em_swap` | யாராவது தொடர முடிந்தால், இப்போது மாறிக்கொள்ளுங்கள். | வேறு யாராவது மார்பை அழுத்த முடிந்தால், இப்போது மாறிக்கொள்ளுங்கள். | Draft: needs a native speaker's ear |
| `ta/em_continue` | உதவி வரும் வரை தொடர்ந்து அழுத்துங்கள். | உதவி வரும் வரை மார்பைத் தொடர்ந்து அழுத்துங்கள். | Draft: needs a native speaker's ear |
| `te/push_faster` | ఇంకా వేగంగా నొక్కండి | ఛాతీని ఇంకా వేగంగా నొక్కండి | Draft: needs a native speaker's ear |
| `te/slow_down` | వేగం కొంచెం తగ్గించండి | ఛాతీ నొక్కే వేగాన్ని తగ్గించండి | Draft: needs a native speaker's ear |
| `te/keep_going` | నొక్కుతూ ఉండండి | ఛాతీని నొక్కుతూ ఉండండి | Draft: needs a native speaker's ear |
| `te/good` | బాగుంది, నొక్కుతూ ఉండండి | బాగుంది. ఛాతీని నొక్కుతూ ఉండండి. | Draft: needs a native speaker's ear |
| `te/em_push` | తాళంతో పాటు గట్టిగా, వేగంగా నొక్కండి. | తాళంతో పాటు ఛాతీని గట్టిగా, వేగంగా నొక్కండి. | Draft: needs a native speaker's ear |
| `te/em_swap` | ఎవరైనా మారగలిగితే, ఇప్పుడే మారండి. | మరొకరు ఛాతీని నొక్కగలిగితే, ఇప్పుడే వంతు మారండి. | Draft: needs a native speaker's ear |
| `te/em_continue` | సహాయం వచ్చే వరకు నొక్కుతూ ఉండండి. | సహాయం వచ్చే వరకు ఛాతీని నొక్కుతూ ఉండండి. | Draft: needs a native speaker's ear |
| `mr/push_faster` | अजून वेगाने दाबा | छाती अजून वेगाने दाबा | Draft: needs a native speaker's ear |
| `mr/slow_down` | वेग थोडा कमी करा | छाती दाबण्याचा वेग कमी करा | Draft: needs a native speaker's ear |
| `mr/keep_going` | दाबत राहा | छाती दाबत राहा | Draft: needs a native speaker's ear |
| `mr/good` | छान, दाबत राहा | छान. छाती दाबत राहा. | Draft: needs a native speaker's ear |
| `mr/em_push` | तालासोबत जोरात आणि वेगाने दाबा. | तालासोबत छाती जोरात आणि वेगाने दाबा. | Draft: needs a native speaker's ear |
| `mr/em_swap` | यापुढेही दाबत राहू शकेल अशी व्यक्ती असल्यास, आता जागा बदला. | दुसरे कोणी छाती दाबू शकत असेल, तर आता पाळी बदला. | Draft: needs a native speaker's ear |
| `mr/em_continue` | मदत येईपर्यंत दाबत राहा. | मदत येईपर्यंत छाती दाबत राहा. | Draft: needs a native speaker's ear |
| `gu/push_faster` | વધુ ઝડપથી દબાવો | છાતી વધુ ઝડપથી દબાવો | Draft: needs a native speaker's ear |
| `gu/slow_down` | દબાવવાની ઝડપ થોડી ઓછી કરો | છાતી દબાવવાની ઝડપ ઓછી કરો | Draft: needs a native speaker's ear |
| `gu/keep_going` | દબાવતા રહો | છાતી દબાવતા રહો | Draft: needs a native speaker's ear |
| `gu/good` | સરસ, દબાવતા રહો | સરસ. છાતી દબાવતા રહો. | Draft: needs a native speaker's ear |
| `gu/em_push` | તાલ સાથે જોરથી અને ઝડપથી દબાવો. | તાલ સાથે છાતી જોરથી અને ઝડપથી દબાવો. | Draft: needs a native speaker's ear |
| `gu/em_swap` | જો કોઈ બીજું કરી શકે તેમ હોય, તો અત્યારે બદલાઈ જાઓ. | જો બીજું કોઈ છાતી દબાવી શકે, તો અત્યારે વારો બદલો. | Draft: needs a native speaker's ear |
| `gu/em_continue` | મદદ આવે ત્યાં સુધી દબાવતા રહો. | મદદ આવે ત્યાં સુધી છાતી દબાવતા રહો. | Draft: needs a native speaker's ear |
| `bn/push_faster` | আরও দ্রুত চাপুন | বুক আরও দ্রুত চাপুন | Draft: needs a native speaker's ear |
| `bn/slow_down` | একটু ধীরে করুন | বুক চাপার গতি কমান। | Draft: needs a native speaker's ear |
| `bn/keep_going` | চাপতে থাকুন | বুক চাপতে থাকুন | Draft: needs a native speaker's ear |
| `bn/good` | বেশ ভালো, চাপতে থাকুন | বেশ ভালো। বুক চাপতে থাকুন। | Draft: needs a native speaker's ear |
| `bn/em_push` | তালের সাথে জোরে এবং দ্রুত চাপুন। | তালের সাথে বুক জোরে এবং দ্রুত চাপুন। | Draft: needs a native speaker's ear |
| `bn/em_swap` | অন্য কেউ থাকলে, এখনই পালা বদল করুন। | অন্য কেউ বুক চাপতে পারলে, এখনই পালা বদল করুন। | Draft: needs a native speaker's ear |
| `bn/em_continue` | সাহায্য না আসা পর্যন্ত চাপতে থাকুন। | সাহায্য না আসা পর্যন্ত বুক চাপতে থাকুন। | Draft: needs a native speaker's ear |

### Malayalam and Odia: English voice fallback

Every `ml` and `or` WAV except `lang_name.wav` is copied byte-for-byte from the matching `en` WAV. The native language-name recordings and on-screen language remain unchanged except for the screen fixes listed above. Manifests retain the destination language code but record the actual English spoken text and its UTF-8 SHA-256; the native language-name entry is retained. The Malayalam and Odia phrase files are left as historical draft translations and are not the voice source for these fallback clips. English, Hindi and Kannada clips and phrase files are untouched.

| Language / clip key | Old spoken line | New spoken line (English fallback) | Review status |
|---|---|---|---|
| `ml/countdown` | മൂന്ന്, രണ്ട്, ഒന്ന്, തുടങ്ങാം | Get ready. Three, two, one. | Draft: needs a native speaker's ear; English fallback |
| `ml/push_faster` | കൂടുതൽ വേഗത്തിൽ അമർത്തൂ | Push the chest faster. | Draft: needs a native speaker's ear; English fallback |
| `ml/slow_down` | വേഗം കുറച്ച് കുറയ്ക്കൂ | Reduce the speed of your chest compressions. | Draft: needs a native speaker's ear; English fallback |
| `ml/elbows_straight` | കൈമുട്ടുകൾ നിവർത്തി വെക്കൂ | Keep both elbows straight. | Draft: needs a native speaker's ear; English fallback |
| `ml/keep_going` | തുടർന്ന് അമർത്തൂ | Keep pushing the chest. | Draft: needs a native speaker's ear; English fallback |
| `ml/good` | നല്ലത്, തുടർന്ന് അമർത്തൂ | Good. Keep pushing the chest. | Draft: needs a native speaker's ear; English fallback |
| `ml/session_done` | വളരെ നല്ലത്, പരിശീലനം പൂർത്തിയായി | Well done. Your practice session is complete. | Draft: needs a native speaker's ear; English fallback |
| `ml/em_call` | ഒന്ന് ഒന്ന് രണ്ട് നമ്പറിലേക്ക് വിളിക്കൂ. സ്പീക്കറിൽ ഇടൂ. | Call one one two. Turn on the phone's speaker. | Draft: needs a native speaker's ear; English fallback |
| `ml/em_hands` | നെഞ്ചിന്റെ നടുവിൽ കൈകൾ വെക്കൂ. | Put both hands in the centre of the chest. | Draft: needs a native speaker's ear; English fallback |
| `ml/em_push` | താളത്തിനൊത്ത് ശക്തമായും വേഗത്തിലും അമർത്തൂ. | Push the chest hard and fast, in time with the beat. | Draft: needs a native speaker's ear; English fallback |
| `ml/em_swap` | മറ്റൊരാൾക്ക് മാറി ചെയ്യാൻ പറ്റുമെങ്കിൽ, ഇപ്പോൾ മാറൂ. | If someone else can push the chest, swap now. | Draft: needs a native speaker's ear; English fallback |
| `ml/em_aed` | അടുത്ത് എ ഇ ഡി ഉണ്ടെങ്കിൽ, കൊണ്ടുവരാൻ ആരോടെങ്കിലും പറയൂ. | If an A E D is nearby, ask someone to bring it. | Draft: needs a native speaker's ear; English fallback |
| `ml/em_continue` | സഹായം എത്തുന്നതുവരെ അമർത്തുന്നത് തുടരൂ. | Keep pushing the chest until help arrives. | Draft: needs a native speaker's ear; English fallback |
| `or/countdown` | ଆରମ୍ଭ ହେଉଛି ତିନି, ଦୁଇ, ଏକ | Get ready. Three, two, one. | Draft: needs a native speaker's ear; English fallback |
| `or/push_faster` | ଆହୁରି ଶୀଘ୍ର ଦବାନ୍ତୁ | Push the chest faster. | Draft: needs a native speaker's ear; English fallback |
| `or/slow_down` | ଗତି ଟିକେ କମାନ୍ତୁ | Reduce the speed of your chest compressions. | Draft: needs a native speaker's ear; English fallback |
| `or/elbows_straight` | କହୁଣୀ ସିଧା ରଖନ୍ତୁ | Keep both elbows straight. | Draft: needs a native speaker's ear; English fallback |
| `or/keep_going` | ଦବାଇ ଚାଲନ୍ତୁ | Keep pushing the chest. | Draft: needs a native speaker's ear; English fallback |
| `or/good` | ବହୁତ ଭଲ, ଦବାଇ ଚାଲନ୍ତୁ | Good. Keep pushing the chest. | Draft: needs a native speaker's ear; English fallback |
| `or/session_done` | ବହୁତ ବଢ଼ିଆ, ଅଭ୍ୟାସ ଶେଷ ହେଲା | Well done. Your practice session is complete. | Draft: needs a native speaker's ear; English fallback |
| `or/em_call` | ଏକ ଏକ ଦୁଇ କୁ କଲ୍ କରନ୍ତୁ। ସ୍ପିକରରେ ରଖନ୍ତୁ। | Call one one two. Turn on the phone's speaker. | Draft: needs a native speaker's ear; English fallback |
| `or/em_hands` | ଛାତି ମଝିରେ ହାତ ରଖନ୍ତୁ। | Put both hands in the centre of the chest. | Draft: needs a native speaker's ear; English fallback |
| `or/em_push` | ତାଳ ସହିତ ଜୋରରେ ଏବଂ ଶୀଘ୍ର ଦବାନ୍ତୁ। | Push the chest hard and fast, in time with the beat. | Draft: needs a native speaker's ear; English fallback |
| `or/em_swap` | ଯଦି ଆଉ କେହି ଅଛନ୍ତି, ଏବେ ପାଳି ବଦଳାନ୍ତୁ। | If someone else can push the chest, swap now. | Draft: needs a native speaker's ear; English fallback |
| `or/em_aed` | ଯଦି ପାଖରେ ଏ ଇ ଡି ଅଛି, କାହାକୁ ଆଣିବାକୁ କୁହନ୍ତୁ। | If an A E D is nearby, ask someone to bring it. | Draft: needs a native speaker's ear; English fallback |
| `or/em_continue` | ସାହାଯ୍ୟ ଆସିବା ପର୍ଯ୍ୟନ୍ତ ଦବାଇ ଚାଲନ୍ତୁ। | Keep pushing the chest until help arrives. | Draft: needs a native speaker's ear; English fallback |

`ml/lang_name.wav` remains മലയാളം; `or/lang_name.wav` remains ଓଡ଼ିଆ. Both remain drafts needing a native speaker's ear.

The unchanged checker compares per-language phrase JSON, not manifests, and selects IndicConformer for every non-English language. Its Malayalam/Odia transcription and error-rate flags therefore compare English fallback audio against historical native draft text and cannot establish fallback quality. English fallback byte identity and native-name preservation must be checked separately. No new English string keys.

### Verification of this revision

- `generate_clips.py --models D:/projects/sih/iTantra/app/src/main/assets --out cpr-coach/app/src/main/assets/clips --lang ta,te,mr,gu,bn`: 70 clips generated, zero failures. Existing mono 16-bit PCM layout and model-provided sample rates retained; models only read. The MMS frontend reported punctuation filtering and an unsupported `ॉ` in the unchanged Marathi `em_call` text; its pronunciation still needs a native ear.
- The reference app and unit-test sources compiled using the installed Android SDK at `%LOCALAPPDATA%/Android/Sdk` and Android Studio JBR. `gradlew.bat testDebugUnitTest --tests com.zeroflux.cprcoach.ui.StringAssetsTest --console=plain`: BUILD SUCCESSFUL, two tests, zero failures/errors/skips. No APK installation, adb, or phone interaction.
- A throwaway asset smoke check verified all retained keys, explicit chest objects in the ten screen cue pairs and five regenerated spoken compression sets, and unchanged short faster/good/slower screen labels. It also verified 26 fallback WAVs byte-for-byte against English and both native language-name WAVs against their originals.
- The real `check_clips.py` WAV inspector passed all 98 delivered WAVs: mono 16-bit PCM, within its duration bounds, non-silent and unclipped. All seven manifests have exactly the WAV keys and valid UTF-8 text hashes; regenerated manifests match their phrase lists.
- `check_clips.py` ran across all ten languages, in two checkpointed passes: 140 rows, 40 flagged. The checker returns zero even when rows are flagged; this is not a clean speech-quality pass. Detailed machine transcripts and checkpoint are outside this checkout in `../ref-t261031-0244-45fd/clip-check-t261031-0244-45fd.md` and the matching `.json`.
- Of those flags, 26 are the intentional Malayalam/Odia English-versus-native comparison mismatch; nine concern language names (English missing from its unchanged phrase list, eight native-name CER flags); one is the unchanged English countdown (`Ready, 3, 2, 1.` versus spelled-out digits); four concern the regenerated cues below. No WAV-format flags.

| Draft clip needing a native speaker's ear | Expected line | Machine transcript | Character error rate |
|---|---|---|---|
| `gu/keep_going` | છાતી દબાવતા રહો | ધ્યાતિ દબાવતા રહ | 5/15 (0.3333) |
| `mr/keep_going` | छाती दाबत राहा | क्षतीि दापत राहा | 5/14 (0.3571) |
| `mr/good` | छान. छाती दाबत राहा. | झ झछाति द बत्र | 10/18 (0.5556) |
| `ta/keep_going` | மார்பைத் தொடர்ந்து அழுத்துங்கள் | இவ்மார்பவை தொடர்ந்து அழுத்து | 11/31 (0.3548) |

These ASR flags are unresolved listening-review items, not approval or proof of a translation defect. No native speaker listened, no first-aid review was performed, and on-device UI/playback was not checked. All revised lines remain drafts needing a native speaker's ear.
