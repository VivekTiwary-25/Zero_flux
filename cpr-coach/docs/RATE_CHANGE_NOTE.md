# Rate changes can look paused

Confirmed against `cpr-coach` revision `53761f4` using an external Python port; synthetic evidence, not recording validation. App unchanged; logic remains frozen pending a real recording.

Fixture: frames `t=0,33,…,13992` ms; change `c=2500`; signal `1.2+0.08*sin(2π*cycles)`. Phase-reset cycles are `old*t/60000` before c, `new*(t-c)/60000` afterwards. Continuous cycles instead integrate both rates without resetting phase.

The “6000 ms” checkpoint actually ends at **5973 ms**. Reset-phase 85→110 gives lag 18, global correlation **0.556251**, recent correlation **0.137375 < 0.5**, hence `NoBeat`; `LiveAnalyzer` maps this to `PAUSED`. Adjacent-cycle correlation is **0.844403**; older offsets dilute it. Adding an actual frame at 6000 yields **0.150762**, still `NoBeat`—not exactly 0.137.

Recovery below is milliseconds after c: first frame after which **every** reading through 13992 remains `Beat` / remains `Beat` within **±3/min** of the new rate. Initial `Warming` lasts until 3003 ms.

| Change/min | Reset: Beat / accurate | Continuous: Beat / accurate |
|---|---:|---:|
| 85→110 | 3704 / 4463 | 4034 / 5024 |
| 110→130 | 4067 / 4661 | 4331 / 4991 |
| 130→100 | 3440 / 4067 | 3605 / 4793 |

Smallest candidate: cap `recentCorrelation` at `maxOffset=lag`, retaining other gates. It removes the quoted false pause, but still reports 100.92/min there; continuous 130→100 still has dropouts. **Not a complete fix.** Proposed regression: assert `Beat` at the quoted checkpoint, sweep all three continuous changes after warm-up for false pauses and recovery, and retain still/noisy-stop rejection within two seconds. Do not apply before real-trace review.
