# LandmarkGuard and overlay hold: independent review

Reviewed `c578e2f` from `origin/cpr-coach`, including `git show c578e2f` and `LandmarkGuardTest.kt`. Source was extracted into `../ref-t261031-0353-d318/cpr-coach`; no application code in this checkout was changed. File/line references below refer to that commit, with paths relative to `cpr-coach/`.

## Verdict

**Two significant behavioral bugs:** a phantom re-anchor can indefinitely exclude a predominantly real arm, and inference/delivery latency can turn a short rejection burst into a tracking `LOST` transition. **Two overlay gaps:** Practice can return a frame it just rejected when no held arm exists, and Emergency still renders raw phantom coordinates. No shared-guard race was established in the ordinary live/replay/session lifecycle paths.

These are deterministic synthetic frame counterexamples, not claims that the corresponding frequency was measured on a phone. No phone or emulator UI was exercised.

In the timing tables, “capture time” means `PoseFrame.timeMs`, the engine uptime taken before `detectAsync`, not CameraX's sensor timestamp.

## 1. High: a phantom re-anchor can become a persistent wrong hypothesis

**Path:** `engine/LandmarkGuard.kt:123-138` promotes the current rejected frame after 350 ms of agreement, or 400 ms unconditionally. `:171-177` then installs it as `ref`, clears the rejected candidates, and rebuilds both reference windows. Every subsequent accepted phantom does the same; `:145-149` compares returning real frames against that phantom.

**Concrete sequence:** aspect `1`, side `false`, visibility `0.95`, shoulder `(0.40, 0.30)`, elbow `(0.40, 0.52)`, hip `(0.60, 0.50)`, wrist y `0.75`:

1. Real wrist x `0.30`, every 33 ms from `0` through `1980`.
2. Phantom wrist x `0.40` for 12 frames, `2013` through `2376`.
3. Repeat four real frames followed by one phantom, still every 33 ms, for 60 cycles.

The phantom becomes `REANCHOR` at **2343 ms**. The next **240 real frames are all rejected over 9.9 seconds**, although they constitute 80% of the subsequent input. A phantom arrives every 165 ms, so the last-accepted timestamp and rejected-candidate list reset before recovery can promote the real arm. The sideways difference is approximately `0.354` torso lengths, above the capped jump limit of `0.265` even when the real frames are the majority. This repeating input can continue without a time bound. The held Practice arm also follows the wrong hypothesis; the analyzer receives only the phantom samples after capture.

**Boundary:** this is not an unlimited lock against a continuously visible real arm with no intervening phantom. With ordinary 30–70 ms gaps, continuous rejected input reaches the 350/400 ms recovery checks on a subsequent frame. The bug is that a sparse recurring wrong hypothesis continually prevents that recovery. Startup on a phantom can produce the same bias without the initial promotion.

**Suggested fix:** do not discard the established arm hypothesis and all rejected evidence on one timeout promotion or matching phantom. Retain a bounded alternate candidate/history across accepted frames and select a sustained dominant, geometrically consistent candidate; use both elbow and wrist/bone evidence when promoting. Distinguishing a real relocation from a persistent hallucination is genuinely ambiguous, so uncertain recovery must not immediately publish confident posture. Simply shortening the force timer trades this bug for admitting more ghosts.

## 2. High: the 500 ms timeout includes latency that recovery does not budget

**Path:** `LandmarkGuard.kt:125-136` measures recovery using frame timestamps. `LiveAnalyzer.kt:65-69` drops rejected frames before updating `lastGoodMs`; `:92-94` records the accepted sample timestamp, and `:103-108` compares that capture timestamp with the current UI clock. `PoseEngine.kt:179-186,198-199` preserves submission time and measures result latency separately. `PractiseHost.kt:252-255,283-293` posts results to main and ticks with current uptime, not the last result's capture time. Emergency also reads on a 250 ms loop (`EmergencyHost.kt:162-193`).

**Concrete sequence:** use the same real/phantom geometry, 33 ms capture gaps, and a constant **180 ms** result delay:

| Event | Capture time | Arrival / UI time | Result |
|---|---:|---:|---|
| Last accepted real frame | 1980 | 2160 | `lastGoodMs = 1980` |
| Phantom results delivered so far | 2013–2277 | 2193–2457 | All rejected |
| Main-loop tick | — | 2481 | Accepted capture is 501 ms old: `LOST` |
| Nominal re-anchor, if no LOST reset intervened | 2343 | 2523 | Too late for that tick |

Executed result: **unguarded sample-only baseline `WARMING`; guarded analyzer `LOST`** at 2481. This is compatible with the app's 250 ms tick cadence. `read()` clears the guard on LOST (`LiveAnalyzer.kt:174-178`), so the next delivered plausible frame can be admitted earlier than the nominal re-anchor; this does not undo the already published LOST transition and discarded signal windows.

**Cadence distinction:** at zero delivery latency and uninterrupted 30–70 ms frame gaps, the force deadline is reached before 470 ms, below the default 500 ms timeout. There is no inherent ordinary-FPS timeout bug in that narrower scenario. But that leaves only 30–100 ms for result/main-thread delay. `LoadGovernor.kt:79-85` permits 90 ms submission gaps and reacts to median latency above 150 ms; real result gaps can stretch further. Its configuration does not guarantee the remaining margin. The 180 ms counterexample is a simulated timing condition, not a measured latency on this workstation.

**Suggested fix:** make freshness and recovery use an explicit, coherent clock contract. Pass result-arrival time into the analyzer; keep capture times for rate estimation, but account for bounded inference/handler delay when deciding whether an accepted result is stale and when recovery must occur. Keep a separate hard capture-age limit so delayed results cannot look indefinitely fresh. Do not solve this by feeding rejected wrist positions into the rate estimator or merely raising `lostAfterMs` without budgeting latency and cadence.

## 3. Medium: the overlay returns rejected coordinates when its held arm is absent

**Path:** `LandmarkGuard.kt:67-74`. A no-person frame returns `ACCEPT` and sets `lastArm = null`, without clearing the geometric reference (`:77`). A subsequent rejected arm falls through `lastArm?.let { ... } ?: frame`, returning the original rejected arm.

**Concrete sequence:** call `overlayFrame` with the real arm at `0`, no-person `(sample = null, arm = null)` at `33`, and the phantom at `66`. The existing reference rejects the phantom as `JUMP`, but `overlayFrame` returns that phantom unchanged. Its finite, high-visibility joints pass `OverlayPose.fill()` (`engine/OverlayPose.kt:82-94`), so it is drawable. A brief no-person dropout is enough; this does not require a long phantom run. Initial absolute `CROSSED` rejection and resets that clear `lastArm` have the same unsafe fallback.

**Suggested fix:** if rejected and there is no accepted arm to hold, return a frame with `arm = null` so the overlay fades/hides instead of drawing known-rejected coordinates. Preserve timestamp/aspect and keep the original measurement frame separate. A later genuinely accepted arm can repopulate the hold.

## 4. Medium: Emergency's skeleton still follows rejected phantom points

**Path:** `EmergencyHost.kt:257-262` pushes the raw frame to `overlayPose`, then posts that original frame to main. `:220-225` feeds it to `LiveAnalyzer`, whose private guard rejects it. Emergency replay likewise pushes raw frames (`:145-147`). `OverlayPose` checks coordinate finiteness/visibility, not geometric plausibility. Practice alone calls `overlayGuard.overlayFrame` (`PractiseHost.kt:252-255,270-272`).

**Concrete sequence:** settle the same real arm at 33 ms intervals for two seconds, then deliver wrist x `0.40` instead of `0.30` for three frames and return to real. Each phantom is high-visibility and finite. The analyzer drops it, but the raw overlay consumes it and moves toward the ghost. Smoothing reduces the movement; it does not hold the accepted arm.

**Suggested fix:** apply an independently owned overlay guard to Emergency's live and replay paths, with the no-held-arm behavior from finding 3. Do not share the measurement guard with the camera callback: its mutable state is not thread-safe.

## Thread ownership and lifecycle

| Path / event | Observed ownership and behavior |
|---|---|
| Practice live overlay | The remembered `overlayGuard` is called by MediaPipe's result callback; detector-unavailable null frames instead originate synchronously on the camera executor. One engine does not use both result sources. `PoseEngine.kt:175-190,235-248`; `PractiseHost.kt:188-190,249-257`. |
| Practice measurement guard | A different instance, privately default-created by `LiveAnalyzer` (`:40-46`). Camera results are posted through the main-looper handler; `PractiseSession.onFrame`, tick, buttons and analyzer replacements are main-owned. No guard is passed between those owners. |
| Debug replay | CSV reading alone uses `Dispatchers.IO`; the callback loop resumes in the caller's Compose coroutine context. Overlay and analyzer are then main-owned. Live camera and replay branches are exclusive. `debug/.../PractiseReplay.kt:31-54`; `PractiseHost.kt:249-280`. |
| Rendering | Main reads `OverlayPose`, not `LandmarkGuard`; its filters/coordinates are synchronized and its aspect is volatile (`OverlayPose.kt:30-79,97-118`). |
| Side switch | `PoseEngine.kt:288-319` includes the selected side after visibility hysteresis. Each guard resets its reference when a known side changes (`LandmarkGuard.kt:87-95`). Unknown sides in old recordings cannot trigger this reset. This is not a cross-thread reset. The analyzer's rate/posture windows are not reset merely by `SIDE_SWITCH`; no additional reproducible side-switch regression was established. |
| User pause/resume | Paused frames do not feed the analyzer (`PractiseSession.kt:49-56`). Resume constructs a fresh `LiveAnalyzer` (`:143-155`); its guard cannot remain anchored to a pre-pause arm. Overlay processing continues while paused and its separate remembered guard survives. Their decisions may differ at resume because their histories differ, not because they share state. |
| Session restart | Countdown-to-live constructs a new analyzer (`PractiseSession.kt:78-90`). Stop → scorecard removes `PractiseCamera`; swap roles recomposes it with fresh remembered session/overlay objects (`PractiseHost.kt:121-141,186-190`). |
| Activity background/resume | Camera binding follows lifecycle; there is no ordinary lifecycle-resume reset of the remembered overlay guard. A frame gap over 1000 ms resets its reference (`LandmarkGuard.kt:89-94`). Analyzer reads timeout/reset independently. |
| Emergency | Camera results are posted to main before `analyzerBox[0].addFrame`; periodic reads/replay also run on main. There is currently no Emergency overlay guard to share. `EmergencyHost.kt:97-98,143-147,162-193,220-225,257-262`. |

**Conditional ownership caveat, not a demonstrated race:** the Practice overlay guard outlives its camera effect, and the callback mutates it before checking `active`. `PoseEngine.close()` explicitly permits an already-entered listener to finish after close returns (`PoseEngine.kt:376-380`). If the same camera composition undergoes rapid engine replacement while an old listener is stalled, two engine generations could touch the same guard. No ordinary restart or pause/resume path establishes that overlap; a real permission/replacement transition would need to be demonstrated before calling it a confirmed production race. Per-engine overlay-guard ownership would avoid that conditional hazard; an `active` check alone is not atomic with disposal.

## Units and invalid inputs

- **Aspect:** the guard scales normalized x by rotated-image width/height, leaves y in normalized-height units, then divides both by torso length (`LandmarkGuard.kt:97-113`). `PoseMath` uses the same convention (`PoseEngine.kt:478-492`). Overlay coordinates remain normalized and are mapped with aspect once, not twice (`OverlayPose.kt:144-163`). No aspect-unit bug found.
- **Small torso:** guard lengths below `0.05` pass through without moving the reference (`:101-102`). This is not equivalent to dropping the frame. `PoseMath.wristSignal` returns `0.0` for a torso at or below `0.001`, and divides by the torso above that (`PoseEngine.kt:478-482`). A visible finite arm with coincident shoulder/hip can therefore keep analyzer tracking alive with a fabricated zero signal; a torso of `0.01` can amplify tiny motion while the guard does nothing. This sample-generation behavior predates this commit. Actual occurrence with high visibility on phone footage was not established.
- **NaN/infinity:** invalid aspect/coordinates pass through the guard without changing its reference (`:78-83`). Overlay rejects nonfinite coordinates and NaN visibility (`OverlayPose.kt:89-93`); analyzer rejects nonfinite *sample signals/angles* and NaN visibility (`LiveAnalyzer.kt:86-89`). The guard comment that invalid joints are always dropped downstream is nevertheless too broad: nonfinite `hipX` can become a finite zero wrist signal in `PoseMath.wristSignal`, while elbow angle remains finite, so analysis can still accept the sample. This is a conditional malformed-model-output case, not evidence that MediaPipe actually emits such values.
- **Visibility thresholds:** `0.3 <= visibility < 0.5` is intentionally drawable by `OverlayPose` but bypasses geometric checking in `LandmarkGuard`. A reference-only check at `0.40` confirmed that a phantom replaces the held arm and is drawable. This limits the claimed hold behavior; it is not counted as a separate serious defect because the differing visibility thresholds are explicit. If the intended contract is “never replace a plausible held arm with an unchecked arm,” overlay fallback needs a distinct unchecked verdict instead of conflating it with acceptance.

For the numeric boundary cases, the safe correction would be to validate usable torso geometry before creating/accepting a `PoseSample`, using a shared threshold and an explicit invalid result rather than zero. Do not retune ghost thresholds to compensate for invalid normalization.

## Reproduction code

The following JUnit 4 class runs in the reference copy's `app/src/test/java/com/zeroflux/cprcoach/engine/`. Assertions specify the desired safe behavior and therefore **fail on the reviewed commit**. They cover findings 1–3 without changing production code.

```kotlin
package com.zeroflux.cprcoach.engine

import org.junit.Assert.*
import org.junit.Test

class ReviewLandmarkGuardTest {
    private fun frame(t: Long, x: Float = .30f): PoseFrame {
        val arm = ArmLandmarks(.40f, .30f, .95f, .40f, .52f, .95f,
            x, .75f, .95f, .60f, .50f, .95f)
        return PoseFrame(t, PoseSample(t, .5,
            PoseMath.elbowDegrees(arm, 1f), .95f, .95f, true),
            arm, 0L, 1f, false)
    }

    @Test fun realMajorityRecoversAfterPhantomReanchor() {
        val guard = LandmarkGuard()
        for (t in 0L..1980L step 33) check(guard.check(frame(t)).accepted)
        var t = 2013L
        repeat(12) { guard.check(frame(t, .40f)); t += 33 }
        var acceptedReal = 0
        repeat(60) {
            repeat(4) {
                if (guard.check(frame(t)).accepted) acceptedReal++
                t += 33
            }
            check(guard.check(frame(t, .40f)).accepted)
            t += 33
        }
        assertTrue("Real majority must eventually recover; got $acceptedReal/240",
            acceptedReal > 0)
    }

    @Test fun latencyMustNotTurnGuardHoldIntoLost() {
        val baseline = LiveAnalyzer()
        val guarded = LiveAnalyzer()
        for (t in 0L..1980L step 33) {
            val f = frame(t)
            baseline.addFrame(f.sample)
            guarded.addFrame(f)
            baseline.read(t + 180)
            guarded.read(t + 180)
        }
        for (t in 2013L..2277L step 33) {
            val f = frame(t, .40f)
            baseline.addFrame(f.sample)
            guarded.addFrame(f)
        }
        assertNotEquals(TrackingState.LOST, baseline.read(2481).tracking)
        assertNotEquals(TrackingState.LOST, guarded.read(2481).tracking)
    }

    @Test fun rejectedFrameWithoutHeldArmMustNotBeDisplayed() {
        val guard = LandmarkGuard()
        guard.overlayFrame(frame(0))
        guard.overlayFrame(PoseFrame(33, null, null, 0L, 1f))
        assertNull(guard.overlayFrame(frame(66, .40f)).arm)
    }
}
```

For finding 4, the executable smoke used separate guards and two real `OverlayPose` objects: after settling, it pushed the phantom directly to one overlay (Emergency's callback behavior) and `hold.overlayFrame(phantom)` to the other. It compared the drawable wrist x with the measurement guard's rejected verdict. This exercises the engine path; it is not an instrumented Compose/UI test.

## Verification

- Set `sdk.dir=C:/Users/Arena/AppData/Local/Android/Sdk` in the reference copy's `local.properties` after the first Gradle attempt reported a missing SDK.
- Compiled the application and review sources using the reference Gradle wrapper. The first unfiltered `testDebugUnitTest` invocation reported no discovered tests; that run is not evidence of a passing suite. After removing the temporary review sources, the same unfiltered command completed successfully: **34 suites, 288 tests, 0 failures, 0 errors, 3 skipped** (285 executed).
- Ran `gradlew.bat testDebugUnitTest --tests com.zeroflux.cprcoach.engine.ReviewLandmarkGuardTest`: **4 tests executed, 4 failed as expected**. Three are findings 1–3; the fourth deliberately asserts a stronger low-visibility hold contract and demonstrates the documented visibility-threshold limitation, not a fifth serious finding.
- Independently ran JUnit 4.13.2 against the Gradle-compiled main/test classes: **16 tests, 4 failures**. All **12 existing `LandmarkGuardTest` tests passed**; the four failures were the review counterexamples.
- Compiled and launched a throwaway `ReviewLandmarkSmoke` JVM main against the actual compiled engine classes. **Exit 0**; it exercised Emergency-style raw versus held overlay input, invalid torso/sample generation, and equivalent physical coordinates at aspects 1 and 4:3:

```text
phantom reanchors=[2343]; real accepted=0/240 over 9900ms
180ms latency: UI=2481, baseline=WARMING, guarded=LOST, age=501
after no-person frame: rejected phantom returned=true
visibility .40: replaced held arm=true; drawable=true

Emergency callback path: verdict=JUMP; raw wrist=0.38529596; held wrist=0.3
NaN hipX: signal=0.0; guard=ACCEPT; tracking=WARMING; drawable=false
infinite hipX: signal=-0.0; guard=ACCEPT; tracking=WARMING; drawable=false
zero torso: signal=0.0; guard=ACCEPT; tracking=WARMING; drawable=true
0.01 torso: signal=-44.00004172329113; guard=ACCEPT; tracking=WARMING; drawable=true
Aspect 1 vs 4:3 physical-coordinate equivalence: [ACCEPT, ACCEPT, ACCEPT, ACCEPT, ACCEPT, ACCEPT, ACCEPT, ACCEPT, ACCEPT, ACCEPT, JUMP]
```

The first four output lines are from the JUnit counterexamples; the remaining lines are from the standalone smoke. The existing `PostureReplayTest` also reported additional LOST rows with the guard, including `real-cph2467-3.csv` **36 → 85** and `real-cph2467-13.csv` **5 → 17**. Those counters are not measured UI flicker, and that replay calls `read(t)` at the frame timestamp (`PostureReplayTest.kt:62-66`), so it does not cover inference/delivery latency.

Throwaway source files and runner scaffolding were removed from the reference copy after execution. No fix was implemented, and the intentional review failures are not presented as passing regression tests. No branch change, commit or push was performed.
