# Strings Coverage

English (`en.json`) has 69 keys. Each of the nine other files (bn, gu, hi, kn, ml, mr, or, ta, te) has 64 keys.

## Keys missing per language

All nine non-English files miss the same 5 keys.

| Key | en value |
| --- | --- |
| `camera_numeric_value` | `{value}` |
| `live_no_rate` | `—` |
| `debug_replay` | `Debug replay — synthetic input, not measurements` |
| `debug_replay_error` | `Debug replay failed — check trace file; camera disabled` |
| `practise_help` | `?` |

| Language | Missing count | Missing keys |
| --- | --- | --- |
| bn | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| gu | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| hi | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| kn | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| ml | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| mr | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| or | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| ta | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |
| te | 5 | `camera_numeric_value`, `live_no_rate`, `debug_replay`, `debug_replay_error`, `practise_help` |

Three of the missing keys look intentionally untranslated (`{value}`, `—`, `?` need no translation). The two `debug_replay*` keys are debug-only strings.

## Keys only in a non-English file

None. Every key in each non-English file also exists in `en.json`.

| Language | Extra keys |
| --- | --- |
| bn | none |
| gu | none |
| hi | none |
| kn | none |
| ml | none |
| mr | none |
| or | none |
| ta | none |
| te | none |

## Values identical to English

None. No non-English value is byte-identical to its English value for a shared key.

| Language | Identical-to-English values |
| --- | --- |
| bn | none |
| gu | none |
| hi | none |
| kn | none |
| ml | none |
| mr | none |
| or | none |
| ta | none |
| te | none |
