# Takeover log

Practise replay is compared with ee682a1 by `tools/check_practise.ps1`. See `PLAN_0810.md`.

| Time | Task | Branch | Merged hash | Tests | Practise replay identical | APK sha1 |
|---|---|---|---|---|---|---|
| 08:15 | Plan | cpr-coach | See git log | Docs only | Not run, no app code changed | - |
| 08:16 | 0 Replay check and gate; a deliberate change made it fail | takeover/t0-replay-check | 3d43225 | 363 run, 0 failed, 5 skipped | Yes | Same as ee682a1 `3b3623f7c811` |
