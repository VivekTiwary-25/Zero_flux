# Clip check 0400

Machine check of what the clips say. It does not replace a person listening. Numbers are from this run.

Source is cpr-coach `f99a1bca04143dd431b5db2ea4f333c5025082cf`, read with `git archive` into `../ref-t261031-0356-b784`. Ran `2026-10-31T04:04:52`. sherpa-onnx `1.13.8`.

New since `docs/CLIP_CHECK.md` (2026-10-30, 140 clips): `shoulders_over_hands` in every language. This run measured 15 keys × 10 languages = 150 clips. 42 flagged. None were flagged for format, clipping, silence, or duration. Every clip was mono 16-bit PCM, had no sample at -32768 or 32767, was not silent, and lasted between 0.2 and 8 seconds.

`shoulders_over_hands` was not flagged in en, hi, kn, bn, gu, mr, te, or ta (character error rate 0 to 4/30). The ml and or copies are the English file, so they flag below.

## Command

From `../ref-t261031-0356-b784/cpr-coach/tools/clipgen`:

```
python -u check_clips.py --lang en,hi,kn,bn,or,gu,mr,te,ta,ml
```

Defaults resolved to the extracted clips and phrase files, Whisper tiny.en int8 at `D:\projects\sih\iTantra\app\src\main\assets` (`tiny.en-encoder.int8.onnx`, `tiny.en-decoder.int8.onnx`, `tiny.en-tokens.txt`), and IndicConformer at `D:\projects\sih\iTantra\model-staging\indicconformer` (`<code>.onnx`, `languages/<code>/tokens.txt`, `shared/encoder.weights.bin`). All of those files were present.

English uses Whisper tiny.en. The other nine languages use IndicConformer. A row is flagged when character error rate is above 0.3, or the phrase or clip is missing. A rate equal to 0.3 is not a flag. Score is edits / characters in the normalized expected text.

| Language | Rows | Flagged | Expected English-copy flags |
|---|---:|---:|---:|
| en | 15 | 2 | 0 |
| hi | 15 | 1 | 0 |
| kn | 15 | 1 | 0 |
| bn | 15 | 1 | 0 |
| or | 15 | 15 | 14 |
| gu | 15 | 2 | 0 |
| mr | 15 | 3 | 0 |
| te | 15 | 1 | 0 |
| ta | 15 | 2 | 0 |
| ml | 15 | 14 | 14 |
| total | 150 | 42 | 28 |

## Flagged rows, own-voice clips

ml has none in this table. or has only `lang_name` here; the other 14 or flags are English copies and are listed separately. App sources in this commit do not reference `lang_name`.

| Language | Key | Score | Heard | Expected |
|---|---|---|---|---|
| en | `lang_name` | n/a | inlet | (missing from `phrases_en.json`) |
| en | `countdown` | 15/23 (0.6522) | Ready, 3, 2, 1. | Get ready. Three, two, one. |
| hi | `lang_name` | 3/6 (0.5000) | हिंद | हिन्दी |
| kn | `lang_name` | 5/5 (1.0000) | ಜೆ | ಕನ್ನಡ |
| bn | `lang_name` | 5/5 (1.0000) | তুলনলে | বাংলা |
| or | `lang_name` | 5/5 (1.0000) | ପରେ | ଓଡ଼ିଆ |
| gu | `lang_name` | 3/7 (0.4286) | તે ગુજરાતી | ગુજરાતી |
| gu | `keep_going` | 5/15 (0.3333) | ધ્યાતિ દબાવતા રહ | છાતી દબાવતા રહો |
| mr | `lang_name` | 4/5 (0.8000) | वाट | मराठी |
| mr | `keep_going` | 5/14 (0.3571) | क्षतीि दापत राहा | छाती दाबत राहा |
| mr | `good` | 10/18 (0.5556) | झ झछाति द बत्र | छान. छाती दाबत राहा. |
| te | `lang_name` | 3/6 (0.5000) | తెలివుణ | తెలుగు |
| ta | `lang_name` | 5/5 (1.0000) | கல | தமிழ் |
| ta | `keep_going` | 11/31 (0.3548) | இவ்மார்பவை தொடர்ந்து அழுத்து | மார்பைத் தொடர்ந்து அழுத்துங்கள் |

`or/lang_name.wav` is a native 16 kHz clip, not a copy of English. `ml/lang_name.wav` is also native and was not flagged: 1/6 (0.1667), heard മലയാള, expected മലയാളം.

## ml and or English copies

Every `ml` and `or` wav except `lang_name.wav` is byte-identical to the matching `en` wav. IndicConformer compared that English speech with the Odia or Malayalam phrase, so these flags are expected. They are not evidence that the file is corrupt. The same bytes, transcribed with Whisper as English, matched the English phrase except `countdown` (flagged above) and `lang_name` (no English phrase).

| Language | Key | Score | Heard |
|---|---|---|---|
| or | `countdown` | 18/23 (0.7826) | ଏଭ ଫଥ୍ରୀ ୍ୟୁ ୱ |
| or | `push_faster` | 14/19 (0.7368) | ଖୁଶ ଚଷ୍ଟ ଫ୍ୟାଷ୍ଟ |
| or | `slow_down` | 30/16 (1.8750) | ରଡ଼ିଡିୁସ୍ ଦ ସ୍ପ  ଅଫ୍ୟ ଚେଷ୍ଟ୍ମ୍ପ୍ରେନ୍ |
| or | `elbows_straight` | 21/17 (1.2353) | ହିପ ପର୍ଫ୍ଲବର୍ ସ୍ଷ୍ଟ୍ରେଟ |
| or | `keep_going` | 12/12 (1.0000) | କି ଫସିଂ ଦ ଚା |
| or | `good` | 15/20 (0.7500) | ି ଫସିଂ ଦେ ଚ ଚା |
| or | `session_done` | 32/26 (1.2308) | ୱଲ ଡନ୍ ୟୋର ପ୍ରାକ୍ଟଟିସ ସେସନ୍ ଇସ୍ କମ୍ପ୍ଟ |
| or | `em_call` | 33/39 (0.8462) | ଖଅ ୱନ୍ନ୍ ସେଓ ଟର୍ନ୍ ଅନ୍ ଦ ଫୋନ୍ ସ୍ପିକକ |
| or | `em_hands` | 37/21 (1.7619) | ଫର୍ ବଥ ହ ହେଣ୍ଡ୍ ଇନ୍ ଦ ସଣ୍ଟର୍ ଅଫ ଦ ଚ ଚାଷ୍ଟ |
| or | `em_push` | 33/32 (1.0313) | ଫୁଷ୍ଟସ୍ତ ଚଷ୍ଟ ହଡ଼ ଆଣ୍ଡ ଫଷ୍ଟନ୍ ଠଥଓମତେ ବି୍ |
| or | `em_swap` | 31/36 (0.8611) | ଏ ସ ସମର୍ିଓ ସ୍କି ଫଷ୍ଟଟ ଚଷ୍ଟ ସ୍ ସୱପ୍ନ |
| or | `em_aed` | 38/43 (0.8837) | ଫ ଏ ଡି ଜ ନ ନିୟର ବାଏ ଆସ୍କ୍ ସମୁଞ୍ଚ ବ୍ରିଙ୍ଗଂ ଟ୍ |
| or | `em_continue` | 28/36 (0.7778) | ହି ଫର୍ସିଂ ଦ ଛସ୍ ନ୍ ଲ ହେଲପୁ ରାାଇମ୍ |
| or | `shoulders_over_hands` | 26/26 (1.0000) | ଶୋରୁଜ ସ୍ଷ୍ଟ୍ରଟ ଓବର୍ରୟୋ ହ ହେଣ୍ଡ |
| ml | `countdown` | 22/27 (0.8148) | എവറി ഫ്രീ ്യൂ വ |
| ml | `push_faster` | 19/23 (0.8261) | ഹുഷ് ചെസ്റ്റ് ഫസ് |
| ml | `slow_down` | 23/22 (1.0455) | ഡ്യൂസ് സ്പീഡ്ോർ ചസ് ഇംപ്രഷൻ |
| ml | `elbows_straight` | 24/26 (0.9231) | ഹി പർഫ്ൽബർസ് സ്്ട്രേറ്റ് |
| ml | `keep_going` | 14/16 (0.8750) | ഹീ ഫഷങ് ദ ച |
| ml | `good` | 16/23 (0.6957) | ഹി ഫഷങ്ന്ന് ച്യാ |
| ml | `session_done` | 30/32 (0.9375) | വെൽ ഡൺ യോർ പ്രാക്ടിസ് സെഷൻ ഇസ് കംപ്ീ |
| ml | `em_call` | 43/54 (0.7963) | ഹോ വൺ വൺ സവ ടർൺ ഓൻ ദ ഫോൺ സ്്പീക്ക |
| ml | `em_hands` | 32/29 (1.1034) | ഫർ ബൗത്ത് ഹൻസ് ഇൻ ദ സെൻറ്റർഓഫ ദ ചെസ് |
| ml | `em_push` | 38/42 (0.9048) | ഫ് ചെസ് ഹോഡൻഡ ഫാസ്റ്റ് ഇ ഠാായമോ പ ബീറ്റ് |
| ml | `em_swap` | 37/50 (0.7400) | സന്യാ സ്കൻ ഫസ്്റ്റ് ചെസ്റ്റ് സ്വാപ്പന |
| ml | `em_aed` | 44/54 (0.8148) | ഇഫ്ഐ  ഇ ഡി ഇ നിിയർ ബായ ആസ്ക് സമുഞ്ച് ബ്രങ്റ്റ് |
| ml | `em_continue` | 33/38 (0.8684) | ഹീ ഫഷങ്  ചഷസ് ഇൻസ ഹൽ ഹൽപ്പ ുറായം |
| ml | `shoulders_over_hands` | 29/34 (0.8529) | ശൂസ് സ്റ്റ്രേറ്റ് ഓവർോ ഹൻഡ |

## Listen first

Ten own-voice flags, worst score first. Ties at 5/5 keep the tool's language order. The 28 English-copy flags are not in this list.

1. kn `lang_name` — 5/5 (1.0000) — ಜೆ
2. bn `lang_name` — 5/5 (1.0000) — তুলনলে
3. or `lang_name` — 5/5 (1.0000) — ପରେ
4. ta `lang_name` — 5/5 (1.0000) — கல
5. mr `lang_name` — 4/5 (0.8000) — वाट
6. en `countdown` — 15/23 (0.6522) — Ready, 3, 2, 1.
7. mr `good` — 10/18 (0.5556) — झ झछाति द बत्र
8. hi `lang_name` — 3/6 (0.5000) — हिंद
9. te `lang_name` — 3/6 (0.5000) — తెలివుణ
10. gu `lang_name` — 3/7 (0.4286) — તે ગુજરાતી

Eight of those ten are `lang_name`. The played clips in the ten are en `countdown` and mr `good`. Just outside the ten, still flagged: mr `keep_going` 5/14 (0.3571), ta `keep_going` 11/31 (0.3548), gu `keep_going` 5/15 (0.3333). en `lang_name` has no score; Whisper heard "inlet" and the phrase file has no key.
