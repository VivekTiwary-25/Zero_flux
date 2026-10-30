# Translation notes: drafts needing native-speaker and first-aid review

All translations in `app/src/main/assets/strings/<code>.json` and `tools/clipgen/phrases_<code>.json` remain drafts until a native speaker listens and a first-aid reviewer checks the instructions. The English, Hindi and Kannada tables below record the exact old and new spoken lines from this revision. Every row, including each unchanged language name, needs a native speaker's ear; no listening approval is claimed.

Only the three phrase lists and their generated clips are revised here. Screen strings are unchanged. Every compression cue names the chest; `slow_down` targets compression speed, not pressure or depth. Numbers are written as words. `shoulders_over_hands` is the sole new spoken key; `turn_phone` is deliberately absent because it is a silent holder instruction. Existing `lang_name` keys are retained without renaming the languages; no English `lang_name` key existed in the reference.

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
