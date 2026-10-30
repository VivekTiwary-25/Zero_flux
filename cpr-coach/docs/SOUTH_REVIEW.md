# Tamil, Telugu and Malayalam — second opinion

This is a machine review. A native speaker of each language must still confirm every row before any wording is changed. The WAV clips were not listened to.

Checked `app/src/main/assets/strings/{ta,te,ml}.json` and `tools/clipgen/phrases_{ta,te,ml}.json` against the English files, and the `ta` / `te` / `ml` blocks in `app/src/main/assets/commands.json` against `en`. Button length was judged from the screens that draw the keys: walkthrough Start is a fixed 112dp clay button; camera Pause and Stop are 20sp in one row with the elbow label; Call 112 and Start compressions are full-width. Voice matching is whole-word, not a prefix. Unlisted lines were read and not flagged.

Left off the table on purpose:

- Colour words track the real teal (`#2DD4BF`) and amber (`#FBBF24`). Yellow for amber is close enough.
- "Hard" was not rewritten as "deep". The English says hard.
- "Centre of the chest" matches the English lay line.
- Tamil singular "elbow" is normal spoken grammar. Telugu `నిటారుగా` for locked elbows is acceptable. Malayalam `-ഊ` in clips and `-ഉക` on buttons is the right split.
- Countdown clips that count and then say "start" are acceptable.
- Telugu `తాళం` in `em_push` is the usual word for a musical beat. It also means "lock". Left unlisted because the metronome is playing when that line is shown and spoken. If that ambiguity is not acceptable, use `లయతో పాటు`.
- `commands.json` name variants such as Tamil `கன்னடா` and `மராட்டி` are extra aliases, not screen text. They do not block the correct names already in the list.

| Language | Key | Current text | Problem | Suggested wording |
|---|---|---|---|---|
| Tamil | `strings/ta.json` `walk_back` | பின்னால் | Navigation Back. `பின்னால்` means "behind / backwards", the same word as the framing hint "move back". The button word is `பின்`. | பின் |
| Tamil | `strings/ta.json` `live_paused` | நிறுத்தப்பட்டது | Status while paused. This means "stopped", and it fights the Pause button `இடைநிறுத்து`. The holder can read a pause as the session having ended. | இடைநிறுத்தம் |
| Tamil | `strings/ta.json` `score_in_range` | 100–120 வேக நேரம் | Label for time spent in the 100–120 range. `வேக நேரம்` is not a phrase; it reads as "speed-time". Telugu and Malayalam already say "time in 100–120". | 100–120 வரம்பில் நேரம் |
| Tamil | `strings/ta.json` `score_pauses` | இடைவெளிகள் | Count of compression pauses. `இடைவெளி` is a gap or interval, including the gap that should be there between pushes. It does not mean a stop. | இடைநிறுத்தங்கள் |
| Tamil | `strings/ta.json` `score_swap` | இடம் மாறி மீண்டும் முயல்க | Button. `முயல்க` is a literary imperative ("may you try"), not a control label. | இடம் மாறி மீண்டும் முயற்சி செய் |
| Tamil | `phrases_ta.json` `slow_down` | கொஞ்சம் மெதுவாக அழுத்துங்கள் | Spoken when the rate is too fast. `மெதுவாக அழுத்து` is the ordinary way to say "press gently", so the rescuer may ease off depth instead of only slowing the rate. | வேகத்தைக் கொஞ்சம் குறையுங்கள் |
| Tamil | `phrases_ta.json` `good` | நன்று, தொடர்ந்து அழுத்துங்கள் | Mid-compression clip. `நன்று` is textbook Tamil, not what a coach says out loud. | நல்லது, தொடர்ந்து அழுத்துங்கள் |
| Tamil | `phrases_ta.json` `session_done` | சிறப்பு, பயிற்சி முடிந்தது | `சிறப்பு` means "special" or "a specialty". It is not "great work", and it is not used as praise on its own. | அருமை, பயிற்சி முடிந்தது |
| Tamil | `phrases_ta.json` `em_swap` | வேறு யாராவது இருந்தால், இப்போது மாறிக்கொள்ளுங்கள். | English is "if someone can take over". This says "if anyone else is present", so an untrained bystander is told to switch in. | யாராவது தொடர முடிந்தால், இப்போது மாறிக்கொள்ளுங்கள். |
| Tamil | `phrases_ta.json` `em_aed` | அருகில் ஏ இ டி இருந்தால், யாரையாவது எடுத்து வரச் சொல்லுங்கள். | Spoken acronym. Short `இ` is the vowel "i", not the English letter E. Still needs an ear check on the Tamil voice. | அருகில் ஏ ஈ டி இருந்தால், யாரையாவது எடுத்து வரச் சொல்லுங்கள். |
| Tamil | `commands.json` `ta.stop` | ஸ்டாப், நிறுத்து, நிறுத்துங்க, போதும் | Whole-word match. The Pause button is `இடைநிறுத்து`, which is not in this list, so saying that label does nothing. English puts "pause" under stop. The Stop label `நிறுத்து` does match. | Add இடைநிறுத்து |
| Telugu | `strings/te.json` `frame_hip_out` | నడుము ఫ్రేమ్‌లో లేదు | Framing hint for the hip landmark. `నడుము` is the waist / lower back. Hip is `తుంటి`. The holder can frame the wrong part and still cut the hip off. | తుంటి ఫ్రేమ్‌లో లేదు |
| Telugu | `phrases_te.json` `slow_down` | కొంచెం నెమ్మదిగా నొక్కండి | Spoken when the rate is too fast. `నెమ్మదిగా నొక్కండి` is "press a little slowly / gently", so depth can drop when only the rate should. | వేగం కొంచెం తగ్గించండి |
| Telugu | `commands.json` `te.stop` | స్టాప్, ఆపు, ఆపండి, నిలుపు | Whole-word match. The Stop button is `నిలిపివేయి`, which is not in this list, so saying the label does nothing. `నిలుపు` does not contain it. The Pause label `ఆపు` does match stop. | Add నిలిపివేయి |
| Malayalam | `strings/ml.json` `walk_title_1` | ലൈവ് സഹായത്തോടെ സിപിആർ പരിശീലിക്കൂ | English is "with live feedback". `സഹായം` is help, so the card can be read as a person helping live, which is not the two-role drill. | ലൈവ് ഫീഡ്‌ബാക്കോടെ സിപിആർ പരിശീലിക്കൂ |
| Malayalam | `strings/ml.json` `live_pause` | താൽക്കാലികമായി നിർത്തുക | Too long for the camera control. It is 20sp in the same row as Stop, the elbow label, and a 72dp end inset. English "Pause" is one short word. It will wrap or crowd the overlay. | പോസ് |
| Malayalam | `phrases_ml.json` `slow_down` | കുറച്ച് പതുക്കെ ആക്കൂ | Spoken when the rate is too fast. `പതുക്കെ` is the ordinary word for gently / softly, and the line does not even say to keep pressing. The rescuer can ease off depth. | വേഗം കുറച്ച് കുറയ്ക്കൂ |
| Malayalam | `phrases_ml.json` `em_aed` | അടുത്ത് എ ഇ ഡി ഉണ്ടെങ്കിൽ, കൊണ്ടുവരാൻ ആരോടെങ്കിലും പറയൂ. | Spoken acronym. Short `എ ഇ` is "e i", not English A-E. Malayalam has the long letters. Still needs an ear check on the Piper voice. | അടുത്ത് ഏ ഈ ഡി ഉണ്ടെങ്കിൽ, കൊണ്ടുവരാൻ ആരോടെങ്കിലും പറയൂ. |
