# Takeover log

Practise replay is compared with ee682a1 by `tools/check_practise.ps1`. See `PLAN_0810.md`.

| Time | Task | Branch | Merged hash | Tests | Practise replay identical | APK sha1 |
|---|---|---|---|---|---|---|
| 08:15 | Plan | cpr-coach | See git log | Docs only | Not run, no app code changed | - |
| 08:16 | 0 Replay check and gate; a deliberate change made it fail | takeover/t0-replay-check | 3d43225 | 363 run, 0 failed, 5 skipped | Yes | Same as ee682a1 `3b3623f7c811` |
| 08:26 | 1 Emergency talks more | takeover/t1-emergency-talk | 69817ed | 372 run, 0 failed, 5 skipped | Yes | - |
| 08:26 | 2 Snappier feel; Practise model load moved off the main thread | takeover/t2-snappy | 325457e | 372 run, 0 failed, 5 skipped | Yes | `71007c3036283b355b9325f970d3d006eb74a63e` |
| 08:42 | 3 Small additions | takeover/t3-small-additions | eae6c9d | 374 run, 0 failed, 5 skipped | Yes | - |
| 08:42 | 4 CPR guide | takeover/t4-final | 270070e | 374 run, 0 failed, 5 skipped | Yes | `2db874361b2f24c03c104e87ee567f246092a545` |
| 08:59 | Translation corrections, seven languages | takeover/t4b-strings | 0c30a81 | 374 run, 0 failed, 5 skipped | Yes | - |
| 09:05 | 6 Re-skin, style only | takeover/t6-reskin | 7b396db | 374 run, 0 failed, 5 skipped | Yes | - |
