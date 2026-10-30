# Translation Notes (Drafts — Native Speaker & First-Aid Review Required)

All translations in `app/src/main/assets/strings/<code>.json` and `tools/clipgen/phrases_<code>.json` across the 9 Indian languages (`hi`, `kn`, `ta`, `te`, `ml`, `mr`, `gu`, `bn`, `or`) are **drafts** until verified by a native speaker of each language and reviewed for first-aid clarity.

---

## 1. Hindi (`hi` — हिन्दी)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `em_push` (`phrases_hi.json`, `hi.json`) | ताल के साथ ज़ोर से और तेज़ दबाएँ। | "ताल" (beat) vs. transliterated "बीट"; everyday speakers understand both, but TTS prosody should be checked. | Must convey both compression depth ("ज़ोर से") and rate ("तेज़") without sounding like jerky or violent thrusts. |
| `em_hands` (`phrases_hi.json`, `hi.json`) | छाती के बीच में दोनों हाथ रखें। | Everyday phrasing omits "sternum" (उरोस्थि) to stay plain and spoken. | Lay rescuers could place hands too low (over xiphoid/stomach) if "छाती के बीच में" is interpreted loosely. |
| `em_aed` (`phrases_hi.json`) | अगर पास में ए ई डी है, तो किसी से मँगवाएँ। | Phonetic letters "ए ई डी" depend on Piper TTS spacing so they are not merged into one word. | Bystanders in India may not know the acronym "AED" without "हार्ट शॉक मशीन" context, though brevity is required here. |
| `walk_title_3` (`hi.json`) | हरा-नीला = सही। पीला = कुछ सुधारें। | "Teal" and "Amber" have no single everyday Hindi words; "हरा-नीला" and "पीला" are approximations. | Low medical risk (practice UI only), but holder must map the words to the on-screen glow colours. |

---

## 2. Kannada (`kn` — ಕನ್ನಡ)

| Key / File | Draft Text | Uncertainty / Reason | Medical or Safety Risk |
|---|---|---|---|
| `countdown` (`phrases_kn.json`) | ಮೂರು, ಎರಡು, ಒಂದು, ಶುರು | Word order places numbers before "ಶುರು" (start) for natural Kannada rhythm; check if MMS TTS pauses properly at commas. | If TTS rushes the numbers together, the rescuer may start compressions out of sync with the metronome. |
| `em_push` (`phrases_kn.json`, `kn.json`) | ತಾಳಕ್ಕೆ ತಕ್ಕಂತೆ ಜೋರಾಗಿ ಮತ್ತು ವೇಗವಾಗಿ ಒತ್ತಿ. | "ಜೋರಾಗಿ" commonly means both "hard/firmly" and "loudly" depending on context; paired with "ಒತ್ತಿ" (press) it means firmly. | Needs native verification that "ಜೋರಾಗಿ ಒತ್ತಿ" unambiguously instructs deep chest compressions (~5–6 cm). |
| `em_hands` (`phrases_kn.json`, `kn.json`) | ಎದೆಯ ಮಧ್ಯದಲ್ಲಿ ಕೈಗಳನ್ನು ಇಡಿ. | Uses plain "ಎದೆಯ ಮಧ್ಯದಲ್ಲಿ" (in the middle of the chest). | Risk of hand placement slightly off the lower half of the sternum if not paired with visual guidance. |
| `em_aed` (`phrases_kn.json`) | ಹತ್ತಿರದಲ್ಲಿ ಎ ಇ ಡಿ ಇದ್ದರೆ, ಯಾರಾದರೂ ತರಲು ಹೇಳಿ. | Short vowel letters "ಎ ಇ ಡಿ" vs. long "ಏ ಈ ಡೀ" in Meta MMS Kannada TTS. | If MMS pronounces short vowels unnaturaly fast, the acronym "AED" may be unrecognisable to listeners. |

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
