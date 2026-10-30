# Review: rejected frames and seen hold

Reviewed `e50a11a985738d4c6aa90849b13b6b0f9dac63a2` and its parent diff with `git show e50a11a`.
The reference copy is `../ref-t261031-0410-3273/cpr-coach`.
The fetched branch was extracted outside this checkout. Kotlin production and test sources were then pinned to the reviewed commit.
No app code in this checkout was changed.
Paths below are relative to `cpr-coach/app/src/main/java/com/zeroflux/cprcoach/`.

## Findings

### 1. High: visible-person hold republishes an old rate as a current measurement

**Code:** `engine/LiveAnalyzer.kt:69-74,112-144,150-152,159-161`; `engine/RateEstimator.kt:68-89`.
`lastSeenMs` bypasses the accepted-sample timeout. It does not make estimator data fresh.
`RateEstimator.read()` has no current-time argument. Its window still ends at the last accepted sample.
`updateMoving()` also returns its old answer when fewer than three recent samples remain.
An unchanged `Beat` therefore still produces `BEATING`, a number, and `moving=true`.
Every read even rewrites `heldRateAtMs` to the UI clock, although no new measurement arrived.

**Concrete sequence:** aspect `1`, side `false`, all joint visibility `.95`.
Shoulder is `(.40,.30)`, elbow `(.40,.52)`, hip `(.60,.50)`.
Feed frames at `0,33,...,9900` ms.
Use wrist x `.30` and y `.75 + .03*sin(2*pi*1.8*t/1000)`.
Compute sample signal and angle with the real `PoseMath` functions.
Read after each accepted frame. At `T=9900`, the measured rate is **108/min**, with `moving=true`.
Feed phantom wrist `(.40,.78)` at `T+100`, `T+200`, and `T+300`.
All three are `JUMP` rejections. Then deliver no more results before the `T+750` UI tick.

Observed at **10650 ms**: `BEATING`, rate **108**, `moving=true`, `posture=null`.
There has been no usable measurement for **750 ms**.
The last rejected frame is only 450 ms old, so the new seen predicate passes.
A direct engine smoke also returned the number at `T+800` and returned `LOST` at `T+801`.
It observed the rate timestamp advance from `10400` to `10650` to `10700` on reads alone.

The rate is shown by `engine/PractiseSession.kt:118-120` and `ui/CameraOverlay.kt:234-242,298-303`.
Rate coaching and metrics also consume it. This is an old measurement presented as current pushing, not a newly inferred rate from rejected coordinates.
[INFERENCE] The parent's old timeout would return `LOST` after 500 ms on this sequence. The changed predicate opens the extra exposure.

**Held-rate interaction:** the same stale movement enables the `NoBeat` fallback at `LiveAnalyzer.kt:123-128`.
A second JUnit case continues the warmup with accepted irregular wrist y values, every 33 ms:
`.75 + .06*(java.util.Random(7).nextDouble()-.5)`.
The estimator first reaches `NoBeat` with held rate **108** at **11121 ms**.
After the same three rejected frames and a result gap, the read at **11871 ms** still returns **108**, `moving=true`, and `posture=null`.
This exercises the real held-rate branch without mutating private state or substituting an estimator.

`HOLD_RATE_MS=2000` is a rate-age limit, not proof of current movement.
It does not fix either counterexample.
No unbounded extension was established: `LOST` clears the hold, and an accepted-sample gap over 500 ms also clears signals.

**Suggested fix:** separate person presence from usable measurement freshness.
During seen-only hold, return a nonnumeric uncertain/warming reading once accepted measurements expire.
Require fresh movement samples for `NoBeat` rate holding. An empty movement window must not mean “still moving.”
Timestamp a new measured rate from its accepted sample, not from repeated UI reads.
Preserve the two-second rate hold only while usable new movement evidence continues.

### 2. Medium: expired posture survives through the legacy elbow verdict

**Code:** `engine/LiveAnalyzer.kt:81-88,119-144,185-187`.
`currentPosture()` correctly expires the structured posture after 500 ms.
It does not expire `elbowVerdict` or the old elbow median returned beside it.

**Concrete sequence:** use finding 1's exact frames and tick at **10650 ms**.
Observed output is `posture=null`, but **`elbowsBent=false`** and elbow median **157.1059 degrees** remain.
`ui/CameraOverlay.kt:345-366` displays the teal “straight elbows” badge while `BEATING`, independently of posture freshness.
`engine/CoachPolicy.kt:123-125` and `engine/EmergencyCoach.kt:108-110` fall back to `elbowsBent` when posture is null.
[INFERENCE] A prior bent verdict can also remain eligible for a correction after structured posture expires. That speech transition was not exercised here.
The reproduced output is the stale straight-elbows verdict. The badge's direct consumption of that field was verified in source, not on a rendered UI.

**Suggested fix:** freshness-gate `elbowsBent` and `elbowDegrees` with the evidence that produced them.
When judge-owned posture expires, return null for its legacy elbow verdict too.
Do not let a stale judge result masquerade as a fresh sample-only legacy result through the fallback.
This fix is separate from hiding the rate: PAUSED/moving coaching can also use the elbow fallback.

### 3. Medium: the new majority rule promotes repeated short phantom bursts

**Code:** `engine/LandmarkGuard.kt:130-163,191-199,262-267`.
The new pool survives accepted real-hand frames. Rejection count is treated as evidence that the rejected track is real.
This breaks the stated protection for phantom bursts of at most about 300 ms with real frames between.

**Concrete sequence:** use the same landmarks, with constant real wrist `(.30,.75)`.
Settle at `0,33,...,1980` ms.
Starting at **2013**, repeat **nine phantom frames `(.40,.78)`, then one real frame**, every 33 ms, for 30 cycles.
Each phantom burst ends only 297 ms after the preceding accepted real frame. Neither old recovery timer can fire.
At **3663 ms**, the new majority path returns **`REANCHOR` for the phantom**.
The pool has 46 identical rejected positions spanning 1650 ms. Fifteen accepted timestamps remain inside the two-second window.
Thus `46 >= 3*15` and the 1500 ms minimum pool span both pass.
The most recent real frame was at 3630, so `blind` is only **33 ms**.

Executed result: the first accepted phantom is **3663 ms**; **25 of 30 real frames are rejected** in the full run.
After promotion, recurring accepted phantoms keep resetting the force timer.
The sparse real observations cannot win the inverse 3:1 vote.
The overlay and analyzer now prefer the phantom even though a real wrist keeps returning between individually bounded bursts.
[INFERENCE] The parent has no majority path; its 350/400 ms timers cannot promote any of these bursts. The parent was not executed.

**Suggested fix:** do not equate repeated rejection frequency with a validated relocation.
Keep both geometric hypotheses while they conflict. Require corroborating elbow/body/bone motion or an explicitly chosen continuous-evidence policy before publishing the alternate track as certain.
Keep rate/posture uncertain during ambiguous recovery.
A wrist-only stream cannot identify truth from vote count alone. Preserving the old track can delay a genuine relocation; that tradeoff must be explicit rather than promising that short ghost bursts never win.

## What was not found

The task's “stricter re-anchor now needs 3:1 over two seconds” premise is incomplete.
The majority rule is an additional recovery path. `REANCHOR_MS=350` and unconditional `FORCE_MS=400` remain before it.
A continuously delivered valid real move is not forced to wait two seconds.
The executed control first accepted the moved wrist at **2343 ms**, **363 ms** after the last old reference at 1980.
Analyzer reads with **180 ms** simulated result latency stayed out of `LOST` throughout that control.
No majority-induced LOST bug was established.

Likewise, **1500 ms is the seen-hold ceiling, not a demonstrated ordinary all-rejected duration**.
With valid matching samples, monotonic timestamps, stable arm side, and ordinary delivery, force recovery admits a frame around 400 ms.
Sparse accepted frames also refresh `lastGoodMs`.
The demonstrated stale-output case uses rejected frames followed by a result gap. It does not pretend the guard rejects uninterrupted valid input for 1500 ms.
Large delivery delays or invalid/missing samples can still cause LOST; that is not evidence that the new majority threshold blocks the old force path.

The overlay change holds an accepted arm; it does not generally hide every rejected frame.
It hides when there is no held arm. No separate regression was established in that fallback or Emergency's added independent overlay guard.

## Executed verification

- `./gradlew testDebugUnitTest` could not execute because the archived wrapper lacked execute permission.
- `sh gradlew testDebugUnitTest` began downloading Gradle 9.6.0, then hit the 120-second command deadline. The Android Gradle suite did **not** run. No SDK failure was observed.
- Ran standalone **JUnit 4.13.2** in the reference copy with **Kotlin 2.2.0**, JVM target 11, on OpenJDK 25.
- Compiled the actual `FramingAdvisor`, `RateEstimator`, `ElbowGate`, `Posture`, `PostureJudge`, `LandmarkGuard`, and `LiveAnalyzer` sources.
- Copied the actual pure `PoseFrame`, `WorldMath`, and `PoseMath` declarations from `PoseEngine.kt` into the throwaway JVM boundary. No fake estimator, guard, judge, or geometry implementation was used.
- Executed `ReviewSeenHoldTest` plus the existing `LandmarkGuardTest`: **23 tests, four intentional failures, 19 passes**. All **17 existing guard tests passed**. The review's two controls passed.
- The four failing assertions are stale Beat rate, stale held rate, expired legacy elbow verdict, and phantom majority promotion. They assert safe behavior and therefore fail on the reviewed commit.
- Ran a separate JVM `SeenHoldSmokeKt` main against the same production classes. Exit **0**. It printed each rejected verdict and the UI readings at ages 500, 750, 800, and 801 ms. It confirmed the stale outputs and bounded LOST transition independently of JUnit.
- These are deterministic synthetic engine inputs. No phone, emulator, rendered Compose surface, actual MediaPipe recording, or audible coaching was exercised.

JUnit invocation, from the reference `cpr-coach` directory after compilation:

```sh
java -cp 'review-jvm/classes:../jvm-libs/*' org.junit.runner.JUnitCore \
  com.zeroflux.cprcoach.engine.ReviewSeenHoldTest \
  com.zeroflux.cprcoach.engine.LandmarkGuardTest
```

## Reproduction class

Place this in `app/src/test/java/com/zeroflux/cprcoach/engine/ReviewSeenHoldTest.kt` in the reference copy.
It is the executed class, including the two passing controls.
With a working Android build environment, it can also be selected with `sh gradlew testDebugUnitTest --tests com.zeroflux.cprcoach.engine.ReviewSeenHoldTest`.
That Gradle invocation was not completed here.

```kotlin
package com.zeroflux.cprcoach.engine

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ReviewSeenHoldTest {
    private fun frame(t: Long, x: Float = .30f, y: Float = (.75 + .03 * sin(2 * PI * 1.8 * t / 1000.0)).toFloat()): PoseFrame {
        val arm = ArmLandmarks(.40f, .30f, .95f, .40f, .52f, .95f,
            x, y, .95f, .60f, .50f, .95f)
        return PoseFrame(t, PoseSample(t, PoseMath.wristSignal(arm, 1f),
            PoseMath.elbowDegrees(arm, 1f), .95f, .95f, true), arm, 0L, 1f, false)
    }

    private fun warmed(rate: RateEstimator = RateEstimator()): Pair<LiveAnalyzer, Long> {
        val analyzer = LiveAnalyzer(rate = rate)
        for (t in 0L..9900L step 33L) {
            analyzer.addFrame(frame(t))
            analyzer.read(t)
        }
        val last = analyzer.read(9900)
        println("warm: $last")
        assertEquals(TrackingState.BEATING, last.tracking)
        assertNotNull(last.elbowsBent)
        assertNotNull(last.posture)
        return analyzer to 9900L
    }

    private fun burst(analyzer: LiveAnalyzer, last: Long) {
        for (delta in listOf(100L, 200L, 300L)) {
            analyzer.addFrame(frame(last + delta, .40f, .78f))
        }
    }

    @Test fun staleBeatMustNotBePresentedAsCurrentRate() {
        val (analyzer, t) = warmed()
        burst(analyzer, t)
        val stale = analyzer.read(t + 750)
        println("stale beat: $stale")
        assertNotEquals(TrackingState.LOST, stale.tracking)
        assertNull("No usable measurement for 750ms", stale.ratePerMinute)
    }

    @Test fun expiredPostureMustNotLeakThroughLegacyElbowVerdict() {
        val (analyzer, t) = warmed()
        burst(analyzer, t)
        val stale = analyzer.read(t + 750)
        println("stale elbows: $stale")
        assertNull(stale.posture)
        assertNull("Legacy badge/audio must expire with posture", stale.elbowsBent)
    }

    @Test fun staleMovementMustNotEnableHeldRate() {
        val rate = RateEstimator()
        val (analyzer, start) = warmed(rate)
        var t = start
        var held: LiveReading? = null
        val random = java.util.Random(7L)
        for (i in 1..120) {
            t = start + i * 33L
            analyzer.addFrame(frame(t, y = (.75 + .06 * (random.nextDouble() - .5)).toFloat()))
            val reading = analyzer.read(t)
            if (rate.read() == BeatReading.NoBeat && reading.ratePerMinute != null && reading.moving) {
                held = reading
                println("held branch starts: $t $reading")
                break
            }
        }
        assertNotNull("Irregular but visible movement reaches held-rate branch", held)
        burst(analyzer, t)
        val stale = analyzer.read(t + 750)
        println("stale held rate: $stale")
        assertNull("Held rate needs fresh movement evidence", stale.ratePerMinute)
    }

    @Test fun repeatedShortPhantomBurstsMustNotReplaceRealReference() {
        val guard = LandmarkGuard()
        for (t in 0L..1980L step 33L) assertTrue(guard.check(frame(t, y = .75f)).accepted)
        var t = 2013L
        var firstPhantom: Long? = null
        var realRejected = 0
        repeat(30) {
            repeat(9) {
                val verdict = guard.check(frame(t, .40f, .78f))
                if (verdict.accepted && firstPhantom == null) {
                    firstPhantom = t
                    println("first accepted phantom: $t $verdict")
                }
                t += 33
            }
            if (!guard.check(frame(t, y = .75f)).accepted) realRejected++
            t += 33
        }
        println("real rejected: $realRejected/30; first phantom=$firstPhantom")
        assertNull("Sub-350ms bursts must not displace repeatedly observed real wrist", firstPhantom)
    }

    @Test fun continuousRealMoveStillReanchorsBeforeLost() {
        val analyzer = LiveAnalyzer()
        val guard = LandmarkGuard()
        for (t in 0L..1980L step 33L) {
            val f = frame(t, y = .75f)
            guard.check(f)
            analyzer.addFrame(f)
            analyzer.read(t)
        }
        var firstAccepted: Long? = null
        for (t in 2013L..2970L step 33L) {
            val moved = frame(t, .42f, .75f)
            val verdict = guard.check(moved)
            if (verdict.accepted && firstAccepted == null) firstAccepted = t
            analyzer.addFrame(moved)
            assertNotEquals(TrackingState.LOST, analyzer.read(t + 180).tracking)
        }
        println("continuous move first accepted: $firstAccepted")
        assertTrue(firstAccepted!! - 1980 <= 429)
    }

    @Test fun burstThenSilenceEventuallyLosesTrackingAndRate() {
        val (analyzer, t) = warmed()
        burst(analyzer, t)
        val lost = analyzer.read(t + 1000)
        println("after silence: $lost")
        assertEquals(TrackingState.LOST, lost.tracking)
        assertNull(lost.ratePerMinute)
        assertNull(lost.elbowsBent)
    }
}
```
