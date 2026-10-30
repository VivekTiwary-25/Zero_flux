# Hindi Text Review & Second Opinion (CPR Coach)

> **Notice:** This document is an automated machine review conducted for the build. A native Hindi speaker and certified first-aid / medical reviewer must still confirm and physically test all proposed wordings before final production release.

---

## 1. Executive Summary & Critical Findings

### 1.1 Medically Misleading & Safety Risks
1. **`slow_down` ("थोड़ा धीरे करें") — High Clinical Risk:**
   - In Hindi, *"धीरे"* colloquially denotes both **"slowly"** (reduced tempo/frequency) and **"softly / gently"** (reduced force/pressure).
   - In CPR, when compression frequency exceeds 120 bpm, the rescuer must reduce *rate only*. Chest compression depth (5–6 cm in adults) must **never** decrease. Telling a stressed lay rescuer *"थोड़ा धीरे करें"* or *"धीरे"* frequently leads to shallow compressions (<5 cm), causing an immediate drop in coronary perfusion pressure and cardiac output.
   - Furthermore, the verb *"करें"* (do) is ambiguous compared to `push_faster` (*"और तेज़ दबाएँ"* — press faster).
   - **Recommendation:** Change to **"रफ़्तार थोड़ी कम करें"** (reduce speed slightly) or **"धीमी गति से दबाएँ"** (press at a slower pace). This explicitly isolates compression rate without compromising compression depth.

2. **`em_step_hands` vs `em_hands` — Inconsistency & Hand Placement Risk:**
   - Screen text (`em_step_hands`) states: *"छाती के बीच में हाथ रखें।"* (singular/unspecified "हाथ").
   - Spoken audio clip (`em_hands`) states: *"छाती के बीच में दोनों हाथ रखें।"* (explicitly "दोनों हाथ" — both hands).
   - Adult CPR requires two hands interlocked over the lower half of the sternum. Single-hand compressions in an adult fail to deliver the 50–60 kg force required for effective chest compression.
   - **Recommendation:** Reconcile screen text to **"छाती के बीच में दोनों हाथ रखें।"** so visual guidance and spoken prompts are identical.

3. **`em_aed` ("अगर पास में ए ई डी है, तो किसी से मँगवाएँ।") — Lay Comprehension Risk:**
   - Public awareness of the acronym "AED" (Automated External Defibrillator) is extremely low among lay bystanders in India. In an emergency, shouting for an "ए ई डी" often produces hesitation or blank responses.
   - **Recommendation:** Pair the acronym with its descriptive everyday term: **"अगर पास में शॉक मशीन या ए ई डी है, तो किसी से मँगवाएँ।"**

### 1.2 Cognitive Confusion: "Pause" vs "Stop"
- In `assets/strings/hi.json`:
  - `live_pause` (Pause): *"रोकें"*
  - `live_stop` (Stop): *"बंद करें"*
  - `em_stop_hold` (Hold to stop): *"रोकने के लिए दबाए रखें"*
- The root verb *"रोकना"* is used for **Pause** on the live practice screen, but for **Stop** on the emergency screen.
- Crucially, in `assets/commands.json`, *"रोको"* and *"रुको"* map to `VoiceCommand.STOP`. If a user reads the on-screen pause button (*"रोकें"*) aloud, the voice recognition engine triggers a full **Stop** rather than Pause.
- **Recommendation:** Standardize Pause to **"पॉज़"** or **"थोड़ी देर रोकें"**, and standardize Stop to **"बंद करें"** across all modes (`em_stop_hold`: *"बंद करने के लिए दबाए रखें"*).

### 1.3 Button Length & UI Truncation Risks
- **`em_call` ("112 पर कॉल करें"):** Displayed in `EmergencyScreen` inside a Coral `ClayButton` at `fontSize = 34.sp` bold with a fixed height of `96.dp`. English *"Call 112"* is 8 characters. Hindi *"112 पर कॉल करें"* is 15 characters. At 34sp on standard viewports (360dp width), 15 characters will wrap onto two lines or clip vertically. Shorter suggestion: **"112 कॉल करें"** or **"112 मिलाएँ"**.
- **`home_practise` ("अभ्यास करें"):** Asymmetric with `home_emergency` (*"इमरजेंसी"*). The noun **"अभ्यास"** (or colloquial loanword **"प्रैक्टिस"**) is punchier, shorter, and matches the noun form of Emergency.

---

## 2. Comprehensive Review Table

| Key / Source | Current Hindi | Problem | Suggested Wording |
|---|---|---|---|
| `slow_down`<br>*(phrases_hi.json)* | थोड़ा धीरे करें | **High medical risk:** "धीरे" in Hindi commonly means both "slowly" (speed) and "softly/gently" (force). Rescuers may reduce compression depth below 5 cm instead of only reducing cadence. Also uses vague verb "करें" instead of "दबाएँ". | **रफ़्तार थोड़ी कम करें**<br>*(or "धीमी गति से दबाएँ")* |
| `em_step_hands`<br>*(strings/hi.json)* | छाती के बीच में हाथ रखें। | **Medical risk & audio mismatch:** Omits "दोनों" (both). Adult CPR requires two interlocked hands; single-hand compressions are ineffective. Also mismatches spoken clip `em_hands` which correctly says "दोनों हाथ". | **छाती के बीच में दोनों हाथ रखें।** |
| `score_in_range`<br>*(strings/hi.json)* | 100–120 में समय | **Unnatural / confusing:** Literal calque of "Time at 100–120". Sounds like "time in [year/room] 100–120". Fails to clarify that 100–120 refers to compression rate/speed. | **100–120 की गति में समय**<br>*(or "लक्ष्य गति (100–120) का समय")* |
| `score_pauses`<br>*(strings/hi.json)* | रुकावटें | **Wrong connotation:** "रुकावटें" means obstacles, hurdles, or roadblocks (e.g. "कार्य में रुकावटें"). In CPR, a pause is a temporary cessation of chest compressions. | **ठहराव**<br>*(or "पॉज़" / "रुके (बार)")* |
| `em_call`<br>*(strings/hi.json)* | 112 पर कॉल करें | **Button overflow risk:** Rendered at `34.sp` bold inside fixed `96.dp` button. 15 characters wrap awkwardly onto two lines on ≤360dp screens. | **112 कॉल करें**<br>*(or "112 मिलाएँ")* |
| `home_practise`<br>*(strings/hi.json)* | अभ्यास करें | **Part-of-speech asymmetry & length:** `home_emergency` is a noun ("इमरजेंसी"), while `home_practise` is an imperative verb phrase ("अभ्यास करें"). A concise noun fits the button hierarchy better. | **अभ्यास**<br>*(or "प्रैक्टिस")* |
| `live_pause`<br>*(strings/hi.json)* | रोकें | **Ambiguity & command collision:** "रोकना" means both Pause and Stop. `em_stop_hold` uses "रोकने" for Stop, and `commands.json` maps "रोको" to STOP. If user reads button aloud, it triggers Stop. | **पॉज़**<br>*(or "थोड़ी देर रोकें")* |
| `em_stop_hold`<br>*(strings/hi.json)* | रोकने के लिए दबाए रखें | **Terminology clash:** Uses "रोकने" for Stop, whereas `live_stop` uses "बंद करें" and `live_pause` uses "रोकें". Also "दबाए रखें" (hold press) could momentarily blur with chest compressions ("दबाना"). | **बंद करने के लिए दबाए रखें**<br>*(or "समाप्त करने के लिए दबाए रखें")* |
| `em_helper_camera`<br>*(strings/hi.json)* | मददगार कैमरा | **Unnatural machine translation:** "मददगार" applies to helpful persons, not software features. Calling a camera "मददगार कैमरा" sounds artificial and bizarre. | **कैमरा सहायता**<br>*(or "कैमरा गाइड")* |
| `walk_title_3`<br>*(strings/hi.json)* | हरा-नीला = सही। पीला = कुछ सुधारें। | **Unnatural phrasing:** "हरा-नीला" is an awkward literal translation of teal (better: "फ़िरोज़ी" or "हरा"). "कुछ सुधारें" literally means "repair something" rather than corrective action. | **फ़िरोज़ी = सही। पीला = सुधार करें।**<br>*(or "हरा = सही। पीला = ठीक करें।")* |
| `walk_rescuer_body`<br>*(strings/hi.json)* | घुटनों के बल बैठकर तकिए को दबाता है। बस ऐप की आवाज़ सुनता है। | **Unnatural gendered habitual tense:** Uses third-person masculine singular verbs ("दबाता है", "सुनता है"), sounding like a detached description of an absent male rather than direct instructional copy. | **घुटनों के बल बैठें और तकिया दबाएँ। सिर्फ़ ऐप की आवाज़ सुनें।** |
| `walk_holder_body`<br>*(strings/hi.json)* | बगल में खड़ा होता है, फ़ोन आड़ा पकड़ता है और स्क्रीन देखता है। | **Unnatural gendered habitual tense & phrasing:** Uses masculine singular ("खड़ा होता है", "पकड़ता है", "देखता है"). "फ़ोन आड़ा पकड़ता है" is colloquial; instructional imperative is much clearer. | **बगल में खड़े हों, फ़ोन आड़ा पकड़ें और स्क्रीन देखें।** |
| `live_elbows_straight`<br>*(strings/hi.json)* | कोहनी सीधी | **Grammatical number:** Singular "कोहनी" vs plural "कोहनियाँ". Both elbows must be locked straight during CPR. | **कोहनियाँ सीधी** |
| `live_elbows_bent`<br>*(strings/hi.json)* | कोहनी मुड़ी हुई | **Grammatical number:** Singular "कोहनी" vs plural "कोहनियाँ". | **कोहनियाँ मुड़ी हुई** |
| `score_elbows`<br>*(strings/hi.json)* | कोहनी सीधी | **Grammatical number on scorecard:** Singular "कोहनी" vs plural "कोहनियाँ". | **कोहनियाँ सीधी** |
| `live_resume`<br>*(strings/hi.json)* | फिर शुरू करें | **Length / Android UI convention:** 13 characters. Standard Hindi Android terminology for Resume / Continue is cleaner and shorter. | **जारी रखें** |
| `score_swap`<br>*(strings/hi.json)* | बारी बदलें और फिर कोशिश करें | **Button text length:** 28 characters inside a 52dp button. Quite long for smaller screens. | **बारी बदलकर फिर करें**<br>*(or "बारी बदलें और फिर करें")* |
| `countdown`<br>*(phrases_hi.json)* | शुरू कर रहे हैं तीन, दो, एक | **Pacing / timing conflict:** Countdown window is strictly 3 seconds. The lead-in clause "शुरू कर रहे हैं" takes ~1.2s, forcing the numbers "तीन, दो, एक" into <1.8s or trailing into the live metronome beats. | **तीन, दो, एक, शुरू!** |
| `em_aed`<br>*(phrases_hi.json)* | अगर पास में ए ई डी है, तो किसी से मँगवाएँ। | **Comprehension barrier:** "ए ई डी" is not recognized by lay bystanders in India. Rescuers need bystanders to identify the device. | **अगर पास में शॉक मशीन या ए ई डी है, तो किसी से मँगवाएँ।** |
| `em_swap`<br>*(phrases_hi.json)* | अगर कोई और कर सकता है, तो अभी बारी बदल लें। | **Clarity in high stress:** "अगर कोई और कर सकता है" ("if someone else can do it") is wordy. Emphasize presence of another person to relieve fatigue quickly. | **अगर कोई और मौजूद है, तो अभी बारी बदल लें।** |
| `stop`<br>*(commands.json [hi])* | `["स्टॉप", "रुको", "रोको", "बंद करो"]` | **Missing spoken button match:** The UI stop button says "बंद करें" (`live_stop`). Because `CommandMatcher` uses exact word boundaries on phrases, a user reading "बंद करें" fails to match "बंद करो". Also missing "रोकें". | Add: **`"बंद करें"`**, **`"रोकें"`**, **`"रुकें"`** |
| `practise`<br>*(commands.json [hi])* | `["प्रैक्टिस", "अभ्यास", "ट्रेनिंग", "सीखना"]` | **Missing exact button text:** UI button displays "अभ्यास करें". Adding the compound phrase avoids partial-match dependency. | Add: **`"अभ्यास करें"`** |
| `emergency`<br>*(commands.json [hi])* | `["इमरजेंसी", "आपातकाल", "बचाओ", "मदद"]` | **Missing loanword:** "हेल्प" is one of the most frequently blurted words in urgent distress in urban India. | Add: **`"हेल्प"`** |
| `call`<br>*(commands.json [hi])* | `["कॉल", "फोन लगाओ", "कॉल करो", "एम्बुलेंस बुलाओ"]` | **Missing common variant:** "फोन करो" is very common in spoken Hindi, but only "फोन लगाओ" and "कॉल करो" are listed. | Add: **`"फोन करो"`** |

---

## 3. String vs Spoken Audio Consistency Check

| Concept | Screen String (`assets/strings/hi.json`) | Spoken Audio (`tools/clipgen/phrases_hi.json`) | Status & Recommendation |
|---|---|---|---|
| **Hand placement** | `em_step_hands`: *"छाती के बीच में हाथ रखें।"* | `em_hands`: *"छाती के बीच में दोनों हाथ रखें।"* | **Discrepant.** Audio specifies "दोनों हाथ" (both hands), screen omits "दोनों". Reconcile screen to match audio: *"छाती के बीच में दोनों हाथ रखें।"* |
| **Emergency call instruction** | `em_step_call`: *"112 पर कॉल करें। स्पीकर पर रखें।"* | `em_call`: *"एक एक दो पर कॉल करें। फ़ोन स्पीकर पर रखें।"* | **Consistent in meaning; intentionally adapted for TTS.** Audio spells "एक एक दो" so Piper TTS does not read "112" as "एक सौ बारह". Audio includes "फ़ोन", which improves clarity. |
| **Push instruction** | `em_step_push`: *"ताल के साथ ज़ोर से और तेज़ दबाएँ।"* | `em_push`: *"ताल के साथ ज़ोर से और तेज़ दबाएँ।"* | **Perfect match.** Identical wording and cadence across screen and audio. |
| **Keep going / continue** | `score_title`: *"अभ्यास पूरा हुआ"* | `keep_going`: *"दबाते रहें"*<br>`good`: *"बहुत अच्छे, दबाते रहें"*<br>`em_continue`: *"मदद आने तक दबाते रहें।"* | **Consistent.** Uses *"दबाते रहें"* (keep pressing) throughout audio coaching, which is medically precise for CPR. |
| **Roles / Turn switching** | `walk_title_2`: *"दो भूमिकाएँ"*<br>`score_swap`: *"बारी बदलें और फिर कोशिश करें"* | `em_swap`: *"अगर कोई और कर सकता है, तो अभी बारी बदल लें।"* | **Consistent.** Both scorecard and emergency audio use the natural conversational idiom *"बारी बदलें"* (take turns / switch roles). |
| **Stop action** | `live_stop`: *"बंद करें"*<br>`em_stop_hold`: *"रोकने के लिए दबाए रखें"* | Voice commands: `["स्टॉप", "रुको", "रोको", "बंद करो"]` | **Discrepant.** Screen stop button is *"बंद करें"*, while emergency hold button uses *"रोकने"* and voice commands omit *"बंद करें"*. Align on *"बंद करें"*. |

---

## 4. Missing Keys in `assets/strings/hi.json`

The following keys exist in `assets/strings/en.json` but are absent in `assets/strings/hi.json`:
1. **`camera_numeric_value`**: `"{value}"` — Template for displaying numeric values.
2. **`live_no_rate`**: `"—"` — Dash placeholder when rate is unmeasured.

While `AssetStrings` in code falls back to `en.json` when a key is absent, defining them explicitly in `hi.json` ensures full dictionary parity and eliminates dependency on the fallback map.

---

## 5. Verification Checklist for Native Speaker & First-Aid Reviewer

Before finalizing tonight's build, the native reviewer should verify:
1. **Audio Prosody & Pacing:** Listen to `hi/countdown.wav`, `hi/push_faster.wav`, `hi/slow_down.wav`, and `hi/em_aed.wav` with Piper TTS to confirm that vowel marks and pacing match the metronome beat (~110 bpm).
2. **Compression Depth Safety:** Verify that the replacement for `slow_down` (*"रफ़्तार थोड़ी कम करें"*) unambiguously prevents rescuers from pressing shallower.
3. **Emergency Button Fit:** Test `em_call` (*"112 पर कॉल करें"* vs *"112 कॉल करें"*) on a 360dp-wide device to ensure zero text clipping or awkward wrapping inside the 96dp button.
4. **Voice Command Matching:** Test hands-free speech input with IndicConformer for *"बंद करें"*, *"स्टॉप"*, and *"अभ्यास करें"*.
