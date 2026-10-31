# Guard re-anchor ghost study

## Result

**Leave the guard alone.** The recordings contain repeated wrist-only, under-elbow transitions, especially recording 15, but the diagnostic cannot establish that these are ghosts rather than real motion. Its candidate rejection changes shown rates in four real recordings. Do not ship that rule.

This is a Python-port replay, not Kotlin execution or a visual ground-truth review. I ran `origin/main:cpr-coach/tools/rate_bent_lean.py` from fetched commit `adf9409`, the base named by this task, against recordings 1–15 and `negative-cph2467-nobody.csv`. The port converts coordinates/visibility to Kotlin Float, applies the recorded-side guard, and replays analyzer ticks. No Gradle, SDK, phone, or adb was used.

## Occurrences

The port's `phantom_sensitivity` diagnostic marks under-elbow wrist poses only when a nearby transition has a wrist jump over 0.035 image units, an elbow jump under 0.020, and switches between under-elbow and horizontally separated wrist geometry. It is an offline detector: it uses the next frame, and these recordings do not label true/ghost joints. Treat hits as pattern-like evidence, not confirmed ghosts. Times below are seconds from each CSV's first frame; frame numbers are 1-based data rows, excluding the header.

| Recording | Candidate frames and time ranges | Candidate frames |
| --- | --- | ---: |
| 1 | — | 0 |
| 2 | 251–252 (12.67–12.71 s) | 2 |
| 3 | 52 (2.30); 62–66 (2.78–2.96); 71–78 (3.20–3.56); 2220–2221 (102.81–102.85); 2223 (102.93) | 17 |
| 4 | 206 (9.61); 744 (34.18) | 2 |
| 5 | — | 0 |
| 6 | — | 0 |
| 7 | — | 0 |
| 8 | 281 (12.74) | 1 |
| 9 | — | 0 |
| 10 | — | 0 |
| 11 | 348–351 (15.98–16.11) | 4 |
| 12 | 854–857 (39.37–39.52); 906–908 (41.77–41.87) | 7 |
| 13 | 614 (30.94); 617 (31.07); 873–875 (43.30–43.38) | 5 |
| 14 | — | 0 |
| 15 | 1228 (54.62); 1235–1238 (54.92–55.10); 1248 (55.67); 1250 (55.75); 1253–1254 (55.91–55.95); 1261–1268 (56.29–56.58); 1272–1274 (56.76–56.87); 1276–1277 (56.96–57.03); 1281 (57.21); 1289 (57.62); 1292 (57.76); 1295 (57.88); 1298 (58.02); 1301–1305 (58.17–58.37); 1307 (58.46); 1309 (58.54); 1313 (58.71); 1326–1331 (59.34–59.55); 1338–1341 (59.84–59.96); 1347 (60.26); 1361 (60.88); 1373–1376 (61.43–61.54); 1379 (61.68); 1387–1391 (62.01–62.18); 1394 (62.32); 1408–1409 (62.97–63.02); 1433–1434 (64.07–64.11); 1464 (65.53); 1466 (65.60); 1501 (67.12); 1521 (68.03) | 66 |
| Nobody | 94 (4.49); 343 (15.74); 345 (15.82) | 3 |

Recording 15 has recurring hits over 54.62–68.03 s. The guard replay promoted a re-anchor at frame 1233 (54.826 s), between hits at frames 1228 and 1235–1238. This is a coincident event, not proof that the promoted pose is a ghost. Other re-anchor promotions occur in the recordings too; promotion alone does not identify a bad arm.

## What the user may see or hear

A rejected frame is withheld from the analyzer. The Practice overlay holds its last accepted arm, so a run of rejections can make the skeleton appear frozen while feedback becomes stale; a later re-anchor accepts the new arm and can move the overlay abruptly. If the analyzer's 500 ms accepted-frame timeout expires, the rate is suppressed and tracking can show as lost/warming. These are code-path consequences, not measured audio or screen observations; the Python port does not replay Compose rendering or speech.

## Other joints and checks

Across 15,273 finite poses with torso length at least 0.05 in the CSVs, the proxy marked 107. In these marked frames, median upper-arm and forearm lengths were 0.769 and 0.542 torso lengths, with forearm/upper-arm ratio 0.709; unmarked medians were 0.611, 0.509, and 0.829. Median shoulder/elbow/wrist/hip visibility on marked frames was 1.000/0.989/0.983/1.000, versus 1.000/0.979/0.958/1.000 unmarked. Visibility therefore does not separate these candidate events. At the 62 transitions that triggered the proxy, median unscaled shoulder, elbow, and wrist displacement was 0.0060, 0.0068, and 0.0564; in recording 15's 43 transitions it was 0.0056, 0.0055, and 0.0622. A stable shoulder/elbow paired with a wrist-only switch and forearm shortening is a useful diagnostic clue, but real wrist movement can produce the same geometry. It is not a safe frame-by-frame classifier.

## Candidate-rule impact and decision

I tested only the diagnostic mask as an offline candidate: skip marked frames before the analyzer. These are hypothetical changed-frame counts, not a production rule. The guard and rate estimator were otherwise the port's base behavior.

| Recording | Candidate frames skipped | Shown-rate ticks changed (baseline → candidate) | Shown median (baseline → candidate) |
| --- | ---: | ---: | ---: |
| 1 | 0 | 0 | — → — |
| 2 | 2 | 0 | 106 → 106 |
| 3 | 17 | 5 | 104 → 104 |
| 4 | 2 | 0 | 101 → 101 |
| 5 | 0 | 0 | 107 → 107 |
| 6 | 0 | 0 | 110 → 110 |
| 7 | 0 | 0 | 114 → 114 |
| 8 | 1 | 0 | 114 → 114 |
| 9 | 0 | 0 | 112 → 112 |
| 10 | 0 | 0 | 116 → 116 |
| 11 | 4 | 0 | 117.5 → 117.5 |
| 12 | 7 | 3 | 121 → 121 |
| 13 | 5 | 12 | 98.5 → 97.5 |
| 14 | 0 | 0 | 112 → 112 |
| 15 | 66 | 20 | 107 → 107 |
| Nobody | 3 | 0 | — → — |

The changed ticks include exact displayed rate-value changes; recording 3 changes 5, recording 12 changes 3, recording 13 changes 12, and recording 15 changes 20. The overall median hides changes in individual displayed values. The real recordings include pushing, and the diagnostic is not ground-truth-labelled, so changing these shown rates is unacceptable. A stricter rule using visibility would miss these high-visibility cases; bone lengths and elbow stability help explain the proxy but cannot distinguish a hallucination from real arm movement reliably in these samples. **Recommendation: leave the guard alone; do not deploy this wrist-only rule.**