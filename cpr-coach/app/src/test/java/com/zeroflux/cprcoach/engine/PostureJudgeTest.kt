package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Synthetic trajectories: these are the fire and clear time tests. Aspect is 1 so x is already scaled. */
class PostureJudgeTest {
    private val fps = 30
    private val stepMs = 1000L / fps

    private class Pose(
        val elbow: Double,
        /** Shoulder offset behind the hands, over arm length (the judge's own measure). */
        val offset: Double = 0.05,
        val sideOn: Boolean = true,
        val vis: Float = 0.95f,
    )

    private fun frame(t: Long, p: Pose): Pair<PoseSample, ArmLandmarks> {
        val u = 0.2
        val theta = Math.toRadians(p.elbow)
        // Upper arm points down; forearm is rotated so the interior angle at the elbow is theta.
        val turn = PI - theta
        val ex = u * sin(0.0)
        val ey = u
        val wx = ex + u * (sin(0.0) * cos(turn) + cos(0.0) * sin(turn))
        val wy = ey + u * (cos(0.0) * cos(turn) - sin(0.0) * sin(turn))
        val armLen = 2 * u
        // Shoulder behind the wrist: the wrist is right of the hip, so behind means left of the wrist.
        val shoulderX = 0.5 + wx - p.offset * armLen
        val arm = ArmLandmarks(
            shoulderX.toFloat(), 0.3f, p.vis,
            (0.5 + ex).toFloat(), (0.3 + ey).toFloat(), p.vis,
            (0.5 + wx).toFloat(), (0.3 + wy).toFloat(), p.vis,
            (0.5 + wx - 0.2).toFloat(), 0.7f, p.vis,
        )
        val sample = PoseSample(t, 0.5, p.elbow, p.vis, p.vis, p.sideOn)
        return sample to arm
    }

    /** Runs [scenario] for [seconds]; returns (time, reading) for every frame. */
    private fun run(seconds: Double, judge: PostureJudge = PostureJudge(), scenario: (Double) -> Pose): List<Pair<Long, PostureReading>> {
        val out = ArrayList<Pair<Long, PostureReading>>()
        var t = 0L
        while (t <= seconds * 1000) {
            val (s, a) = frame(t, scenario(t / 1000.0))
            out.add(t to judge.update(s, a, 1f))
            t += stepMs
        }
        return out
    }

    @Test
    fun `a personal baseline makes a mild bend count that the fixed threshold ignores`() {
        // 8 s of straight arms at 178, then a mild bend at 147 that is above the fixed 145.
        val scenario = { t: Double -> Pose(elbow = if (t < 8.0) 178.0 else 147.0) }
        val withBaseline = PostureJudge()
        val after = run(14.0, withBaseline, scenario).filter { it.first > 11_000 }
        assertTrue(withBaseline.baseline != null)
        assertTrue("flagged bent with a baseline", after.all { it.second.issue == PostureIssue.ELBOWS_BENT })
        // The same bend with no straight start has no baseline and stays unflagged.
        val fresh = PostureJudge()
        val noBase = run(8.0, fresh) { Pose(elbow = 147.0) }
        assertTrue(fresh.baseline == null)
        assertTrue("not flagged without a baseline", noBase.none { it.second.issue == PostureIssue.ELBOWS_BENT })
    }

    private val rng = Random(7)

    /** Compression: about 1.83 Hz (110/min); the elbow angle swings by [amp] degrees around [base]. */
    private fun pushing(base: Double, t: Double, amp: Double = 8.0, noise: Double = 2.0) =
        base + amp * sin(2 * PI * 1.83 * t) + noise * rng.nextGaussian()

    private fun firstAt(r: List<Pair<Long, PostureReading>>, fromMs: Long, test: (PostureReading) -> Boolean) =
        r.firstOrNull { it.first >= fromMs && test(it.second) }?.first

    @Test fun straightPushingStaysGoodAndNeverFires() {
        val r = run(15.0) { t -> Pose(pushing(168.0, t)) }
        assertTrue(r.none { it.second.issue != null })
        assertEquals(true, r.last().second.good)
    }

    @Test fun noisyStraightPushingNeverFires() {
        val r = run(20.0) { t -> Pose(pushing(166.0, t, amp = 10.0, noise = 7.0), offset = 0.1 + 0.05 * rng.nextGaussian()) }
        assertTrue(r.none { it.second.issue != null })
    }

    @Test fun bentTo120FiresAndClearsWithinTwoSeconds() {
        val onset = 6.0
        val back = 14.0
        val r = run(22.0) { t -> Pose(pushing(if (t in onset..back) 120.0 else 168.0, t)) }
        assertTrue("nothing before onset", r.filter { it.first < onset * 1000 }.none { it.second.issue != null })
        val fire = firstAt(r, (onset * 1000).toLong()) { it.issue == PostureIssue.ELBOWS_BENT }
        assertTrue("fired", fire != null)
        val fireDelay = fire!! - (onset * 1000).toLong()
        val clear = firstAt(r, (back * 1000).toLong()) { it.issue == null }
        assertTrue("cleared", clear != null)
        val clearDelay = clear!! - (back * 1000).toLong()
        println("synthetic bent 120: fire after ${fireDelay}ms, clear after ${clearDelay}ms")
        assertTrue("fire $fireDelay", fireDelay in 800..2000)
        assertTrue("clear $clearDelay", clearDelay in 800..2000)
    }

    @Test fun bent130IsStillCaught() {
        val r = run(10.0) { t -> Pose(pushing(if (t >= 3.0) 130.0 else 168.0, t)) }
        val fire = firstAt(r, 3000) { it.issue == PostureIssue.ELBOWS_BENT }
        assertTrue("fired", fire != null)
        println("synthetic bent 140: fire after ${fire!! - 3000}ms")
        assertTrue(fire - 3000 <= 2000)
    }

    @Test fun halfSecondGlitchDoesNotFire() {
        val r = run(10.0) { t -> Pose(pushing(if (t in 5.0..5.5) 100.0 else 168.0, t)) }
        assertTrue(r.none { it.second.issue != null })
    }

    @Test fun leaningShouldersFireAndClear() {
        val onset = 5.0
        val back = 12.0
        val r = run(20.0) { t -> Pose(pushing(168.0, t), offset = if (t in onset..back) 0.7 else 0.05) }
        val fire = firstAt(r, (onset * 1000).toLong()) { it.issue == PostureIssue.SHOULDERS_NOT_OVER_HANDS }
        assertTrue("fired", fire != null)
        val clear = firstAt(r, (back * 1000).toLong()) { it.issue == null }
        assertTrue("cleared", clear != null)
        println("synthetic lean: fire after ${fire!! - 5000}ms, clear after ${clear!! - 12000}ms")
        assertTrue(fire - 5000 in 800..2000)
        assertTrue(clear - 12000 in 800..2000)
        assertTrue(r.last().second.shoulderOverWrist!! < 0.1)
    }

    @Test fun leanMeasureIsSignedAndAspectCorrected() {
        val r = run(3.0) { Pose(168.0, offset = 0.2) }
        assertEquals(0.2, r.last().second.shoulderOverWrist!!, 0.02)
        val ahead = run(3.0) { Pose(168.0, offset = -0.2) }
        assertEquals(-0.2, ahead.last().second.shoulderOverWrist!!, 0.02)
    }

    @Test fun notSideOnFiresAfterAboutOneAndAHalfSecondsAndClears() {
        val r = run(14.0) { t -> Pose(pushing(168.0, t), sideOn = t !in 4.0..9.0) }
        val fire = firstAt(r, 4000) { it.issue == PostureIssue.NOT_SIDE_ON }
        assertTrue("fired", fire != null)
        val clear = firstAt(r, 9000) { it.issue == null }
        assertTrue("cleared", clear != null)
        println("synthetic not side-on: fire after ${fire!! - 4000}ms, clear after ${clear!! - 9000}ms")
        assertTrue(fire - 4000 in 1400..2200)
        assertTrue(clear - 9000 <= 2000)
        // Never judged elbows or shoulders while not side-on.
        assertTrue(r.filter { it.first in 6600..8900 }.all { it.second.issue == PostureIssue.NOT_SIDE_ON })
    }

    @Test fun bentArmWhileNotSideOnIsNotJudged() {
        val r = run(10.0) { t -> Pose(pushing(100.0, t), sideOn = false) }
        assertTrue(r.none { it.second.issue == PostureIssue.ELBOWS_BENT || it.second.good == true })
    }

    @Test fun sideOnFlickerIsUncertainSoSilent() {
        var i = 0
        val r = run(10.0) { t -> Pose(pushing(100.0, t), sideOn = (i++ % 2 == 0)) }
        assertTrue(r.all { it.second.good == null && it.second.issue == null })
    }

    @Test fun lowVisibilityIsNull() {
        val r = run(5.0) { t -> Pose(pushing(100.0, t), vis = 0.5f) }
        assertTrue(r.all { it.second.good == null && it.second.issue == null })
        val j = PostureJudge()
        val (s, _) = frame(0, Pose(168.0))
        assertNull(j.update(s, null, 1f).good)
    }

    @Test fun middleAngleIsNotConfident() {
        val r = run(8.0) { Pose(145.0) }
        assertTrue(r.all { it.second.good != true && it.second.issue == null })
    }

    @Test fun elbowsBentReachesLiveReading() {
        val analyzer = LiveAnalyzer()
        var last: LiveReading? = null
        var t = 0L
        while (t < 6000) {
            val (s, a) = frame(t, Pose(pushing(120.0, t / 1000.0)))
            analyzer.addFrame(PoseFrame(t, s, a, 0L, 1f))
            last = analyzer.read(t)
            t += stepMs
        }
        assertEquals(PostureIssue.ELBOWS_BENT, last!!.posture?.issue)
        assertEquals(true, last.elbowsBent)
    }
}
