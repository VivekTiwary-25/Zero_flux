# Clip coverage

`tools/clip_coverage.py` lists every clip key the app can play and checks all ten languages.

Phrase keys come from `engine/CoachPolicy.kt`. `em_*` string literals come from `engine/EmergencyCoach.kt` and `EmergencyHost.kt`. Literal `play("...")` calls come from `app/src/main/java`. `engine/EmergencySequence.kt` is read as well, because `em_aed`, `em_continue`, and `em_swap` are played through `audio.play(key)` and are not written as `play("...")`.

A cell is `OK` when `manifest.json` lists the key and `<key>.wav` exists and is not empty. `NOT IN MANIFEST` means the key is absent from that language manifest. `MISSING WAV` means the key is listed and the wav is missing or empty. Comments are ignored.

`em_word_*` values are `EmergencyWord` screen strings. `em_step_*` values are on-screen step labels. They are not voice clips. Those rows do not change the exit code.

Run `python tools/clip_coverage.py` from the cpr-coach directory. Exit code 0 means every played key is `OK` in every language. Exit code 1 means a played key is missing. `--self-check` checks the three statuses on a temp tree.

Checked cpr-coach commit `f9481181cb7ec1732552fd4987c3b591c80822ac`. Output:

```
languages (10): en, hi, kn, ta, te, ml, mr, gu, bn, or
keys: 22

| key | role | en | hi | kn | ta | te | ml | mr | gu | bn | or |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `keep_going` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `push_faster` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `slow_down` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `elbows_straight` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `shoulders_over_hands` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `good` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `countdown` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `session_done` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_aed` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_continue` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_swap` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_call` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_hands` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_push` | played | OK | OK | OK | OK | OK | OK | OK | OK | OK | OK |
| `em_step_call` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_step_hands` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_step_push` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_word_keep_pushing` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_word_faster` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_word_slower` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_word_straighten` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |
| `em_word_good` | screen | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST | NOT IN MANIFEST |

played: 14 keys, 140 cells, OK 140, MISSING WAV 0, NOT IN MANIFEST 0
screen: 8 keys, 80 cells, OK 0, MISSING WAV 0, NOT IN MANIFEST 80
played gaps: none
result: played clips complete

clips not required by the app sources:
- lang_name: manifest in hi, kn, ta, te, ml, mr, gu, bn, or; wav in en, hi, kn, ta, te, ml, mr, gu, bn, or; manifest missing in en
```

All 14 played keys have a manifest entry and a non-empty wav in en, hi, kn, ta, te, ml, mr, gu, bn, and or. No played cell is `MISSING WAV` or `NOT IN MANIFEST`.

`lang_name.wav` exists in all ten languages. The app sources above never play it. English `manifest.json` does not list it. The other nine manifests do.
