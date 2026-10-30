# Second opinion: Marathi, Gujarati, Bengali, Odia text (machine review)

> **This is a machine-generated review, not a native-speaker check. A native speaker
> of Marathi, Gujarati, Bengali and Odia MUST still confirm every row below before
> any wording ships, and a first-aid trainer should confirm the CPR-relevant ones.**

Sources compared, key by key, against the English sources: `app/src/main/assets/strings/en.json`,
`tools/clipgen/phrases_en.json`, and the `mr`/`gu`/`bn`/`or` sections of
`app/src/main/assets/commands.json` (reference copy on branch `cpr-coach`).
Sibling languages were used only as consistency cross-checks.
Items already flagged in `tools/clipgen/TRANSLATION_NOTES.md` (mr `em_swap`/`em_push`/`score_pauses`,
gu loanwords/`em_swap`/`em_aed`, bn `slow_down` wording choice, or `push_faster`/`slow_down`/`frame_arm_out`/`em_aed`)
are not repeated here; nothing below contradicts that file.

**Missing keys (all four languages, also `hi` which is out of scope):**
`camera_numeric_value` (`"{value}"`) and `live_no_rate` (`"—"`) exist only in `en.json`.
Both degrade gracefully to English, so this is a gap, not a wording bug — add them only if the holder UI must be fully localised.

| Language | Key | Current text | Problem | Suggested wording |
|---|---|---|---|---|
| mr | strings `home_practise` | सराव करा | Imperative verb phrase on a button; `score_title` and all three sibling languages use the bare noun, so this is inconsistent and longer than needed | सराव |
| mr | strings `walk_rescuer_body` | …गुढग्यावर बसून… | Nonstandard spelling of "knee" (गुडघा) | …गुडघ्यावर बसून… (rest unchanged) |
| mr | strings `frame_arm_out` | मनगट फ्रेमबाहेर आहे | "फ्रेमबाहेर" written joined reads as one coined word; bn gets this right with the genitive (ফ্রেমের বাইরে) | मनगट फ्रेमच्या बाहेर आहे |
| mr | strings `frame_hip_out` | कंबर फ्रेमबाहेर आहे | Same joined-word problem | कंबर फ्रेमच्या बाहेर आहे |
| mr | strings `live_resume` | पुढे सुरू करा | पुढे means "forward/ahead", the wrong adverb for "resume"; gu/bn/or all use their word for "again" | पुन्हा सुरू करा |
| mr | strings `em_helper_camera` | मदत कॅमेरा | मदत is the noun "help"; gu/bn/or all use a real "helper" adjective (મદદગાર/সহায়ক/ସହାୟକ) | मदतनीस कॅमेरा |
| mr | strings `score_seconds_unit` | से | Collides with the postposition से ("from/by"); next to a number it misreads | सेकंद |
| mr | phrases `countdown` | सुरू करत आहोत तीन, दोन, एक | Formal announcement word order ("we are starting, 3-2-1"); a spoken countdown should lead with the numbers, matching the metronome lead-in | तीन, दोन, एक — सुरू |
| gu | strings `score_pauses` | રોકાણ | **Wrong word:** રોકાણ means a financial investment, not a pause in compressions; scorecard label is misleading | વિરામ |
| gu | phrases `countdown` | શરૂ કરીએ છીએ ત્રણ, બે, એક | Same announcement-style order as mr | ત્રણ, બે, એક — શરૂ |
| bn | strings `live_per_minute` | প্রতি মিনিটে | Locative ending (-ে) suits a sentence, not a unit caption under a big number; mr/gu/or all use the bare form | প্রতি মিনিট |
| bn | strings `live_resume` | আবার শুরু | Verbless fragment while every sibling resume button has a verb (કરો/करा/କରନ୍ତୁ) | আবার শুরু করুন |
| bn | strings `score_seconds_unit` | সে | Collides with the pronoun সে ("he/she"); ambiguous as a unit | সেকেন্ড |
| bn | phrases `countdown` | শুরু হচ্ছে তিন, দুই, এক | Same announcement-style order as mr/gu | তিন, দুই, এক — শুরু |
| or | strings `walk_title_1` | …ଲାଇଭ୍ ସହାୟତା ସହ… | ସହାୟତା means "help/assistance", mistranslating "live feedback"; gu/bn transliterate feedback, mr uses मार्गदर्शन (guidance) | …ଲାଇଭ୍ ଫିଡବ୍ୟାକ୍ ସହ… |
| or | strings `walk_rescuer_body` | …କେବଳ ଆପ୍ ର କଥା… | Spaced genitive (ଆପ୍ ର) is nonstandard; mr/gu/bn all join it (ॲपचा/એપનો/অ্যাপের) | …କେବଳ ଆପର କଥା… |
| or | strings `walk_holder_body` | …ଫୋନ୍ କୁ ଆଡ଼େଇ… | Spaced accusative (ଫୋନ୍ କୁ), same artifact | …ଫୋନକୁ ଆଡ଼େଇ… (rest unchanged) |
| or | strings `em_call` | 112 କୁ କଲ୍ କରନ୍ତୁ | Spaced କୁ, same artifact | 112କୁ କଲ୍ କରନ୍ତୁ |
| or | strings `em_step_call` | 112 କୁ କଲ୍ କରନ୍ତୁ। … | Spaced କୁ, same artifact | 112କୁ କଲ୍ କରନ୍ତୁ। … |
| or | phrases `em_call` | ଏକ ଏକ ଦୁଇ କୁ କଲ୍ କରନ୍ତୁ। … | Spaced କୁ (ଦୁଇ କୁ), same artifact | ଏକ ଏକ ଦୁଇକୁ କଲ୍ କରନ୍ତୁ। … |
| or | strings `live_resume` | ପୁଣି ଆରମ୍ଭ | Verbless fragment, same as bn | ପୁଣି ଆରମ୍ଭ କରନ୍ତୁ |
| or | strings `score_seconds_unit` | ସେ | Collides with the pronoun ସେ ("he/that"); ambiguous as a unit | ସେକେଣ୍ଡ |
| or | phrases `countdown` | ଆରମ୍ଭ ହେଉଛି ତିନି, ଦୁଇ, ଏକ | Same announcement-style order as the other three | ତିନି, ଦୁଇ, ଏକ — ଆରମ୍ଭ |

**Minor cross-language watch item (not tabled, low priority):** `em_stop_hold` in all four
languages uses the same verb as the compression cues (mr दाबून धरा / gu દબાવી રાખો /
bn চেপে ধরে রাখুন / or ଦବାଇ ରଖନ୍ତୁ — "keep pressing") for the holder's hold-to-stop button.
Button context disambiguates it today, but if holders ever misread it mid-session, name the
button explicitly (बटण / બટન / বোতাম / ବୁଟନ୍).

**Checked, no change recommended:** `commands.json` synonyms for all four languages look natural
with good formal/informal coverage (bn থামো/থামান, or ওড়িয়া/ଓଡିଆ spellings, gu's doubled
ઇમરજન્સી/ઈમરજન્સી); the `em_aed` letter triples map A-E-D correctly in all four scripts
(ए ई डी / એ ઈ ડી / এ ই ডি / ଏ ଇ ଡି); pause vs stop buttons are clearly distinct in all four;
bn's choice of ধীরে over আস্তে for `slow_down` is the medically safer one. No invented
numbers found in any reviewed file. No new `en.json` keys needed.
