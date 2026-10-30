package com.zeroflux.cprcoach.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random

/** Messy, real-like input for the pure engine parts: nothing may throw and the rules must hold. */
class EngineFuzzTest {

    private val specials = doubleArrayOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -500.0, 0.0, 400.0, 1e300)

    private fun messyDouble(r: Random, normal: () -> Double): Double =
        if (r.nextInt(10) == 0) specials[r.nextInt(specials.size)] else normal()

    private fun messyTime(r: Random, t: Long): Long = when (r.nextInt(12)) {
        0 -> t - r.nextInt(5000)          // backwards
        1 -> t                            // duplicate
        2 -> t + r.nextInt(60_000)        // big gap
        3 -> t + Int.MAX_VALUE.toLong()   // huge gap
        else -> t + 80 + r.nextInt(80)
    }

    private fun messySample(r: Random, t: Long): PoseSample? {
        if (r.nextInt(15) == 0) return null
        val vis = when (r.nextInt(6)) {
            0 -> Float.NaN
            1 -> 0.5f
            2 -> 0.499f + r.nextFloat() * 0.002f
            else -> r.nextFloat()
        }
        return PoseSample(t, messyDouble(r) { r.nextGaussian() * 0.3 }, messyDouble(r) { 60 + r.nextDouble() * 95 }, vis)
    }

    private fun checkReading(seed: Long, reading: LiveReading) {
        val msg = "seed $seed gave $reading"
        if (reading.tracking != TrackingState.BEATING) assertNull(msg, reading.ratePerMinute)
        reading.elbowDegrees?.let { assertTrue(msg, it.isFinite()) }
        if (reading.tracking == TrackingState.LOST) {
            assertNull(msg, reading.elbowDegrees)
            assertNull(msg, reading.elbowsBent)
        }
    }

    private fun checkCard(seed: Long, c: Scorecard) {
        val msg = "seed $seed gave $c"
        assertTrue(msg, c.sessionSeconds >= 0 && c.measuredSeconds >= 0 && c.secondsInRange >= 0 && c.pauses >= 0)
        c.elbowsStraightPercent?.let { assertTrue(msg, it in 0..100) }
        c.averageRate?.let { assertTrue(msg, it >= 0) }
        assertTrue(msg, c.secondsInRange <= c.measuredSeconds + 1)
    }

    @Test
    fun `analyzer policy and metrics survive messy frames`() {
        for (seed in 1L..300L) {
            val r = Random(seed)
            val analyzer = LiveAnalyzer()
            val policy = CoachPolicy()
            val metrics = SessionMetrics()
            var t = 1_000L
            var lastPhrase: Phrase? = null
            repeat(300) {
                t = messyTime(r, t)
                analyzer.addFrame(messySample(r, t))
                val reading = analyzer.read(if (r.nextInt(20) == 0) t - r.nextInt(3000) else t)
                checkReading(seed, reading)
                if (r.nextInt(25) == 0) metrics.pause()
                metrics.add(reading)
                val phrase = policy.update(reading)
                if (phrase != null) {
                    assertNotEquals("seed $seed repeated $phrase", lastPhrase, phrase)
                    assertNotEquals("seed $seed spoke while LOST", TrackingState.LOST, reading.tracking)
                    lastPhrase = phrase
                }
                checkCard(seed, metrics.scorecard())
            }
        }
    }

    @Test
    fun `policy and metrics take any tracking state, rate and time`() {
        val rates = intArrayOf(0, 1, 60, 100, 110, 120, 121, 400, Int.MAX_VALUE)
        for (seed in 1L..300L) {
            val r = Random(seed)
            val policy = CoachPolicy()
            val metrics = SessionMetrics()
            var t = 0L
            var lastPhrase: Phrase? = null
            repeat(400) {
                t = if (r.nextInt(30) == 0) t - r.nextInt(10_000) else t + r.nextInt(4000)
                val state = TrackingState.values()[r.nextInt(4)]
                val rate = if (r.nextBoolean()) rates[r.nextInt(rates.size)] else null
                val bent = when (r.nextInt(3)) { 0 -> null; 1 -> true; else -> false }
                val reading = LiveReading(t, state, rate, if (bent == null) null else 100.0, bent)
                if (r.nextInt(10) == 0) metrics.pause()
                metrics.add(reading)
                val phrase = policy.update(reading)
                if (phrase != null) {
                    assertNotEquals("seed $seed repeated $phrase", lastPhrase, phrase)
                    assertNotEquals("seed $seed spoke while LOST", TrackingState.LOST, state)
                    lastPhrase = phrase
                }
                val c = metrics.scorecard()
                assertTrue("seed $seed $c", c.sessionSeconds >= 0 && c.measuredSeconds >= 0 && c.secondsInRange >= 0)
                c.elbowsStraightPercent?.let { assertTrue("seed $seed $c", it in 0..100) }
            }
        }
    }

    @Test
    fun `policy is silent while lost`() {
        val policy = CoachPolicy()
        for (i in 0 until 100) {
            assertNull(policy.update(LiveReading(i * 1000L, TrackingState.LOST, null, null, null)))
        }
    }

    @Test
    fun `scorecard with nothing measured keeps nulls and zero length is zero`() {
        val m = SessionMetrics()
        var c = m.scorecard()
        assertEquals(0, c.sessionSeconds)
        assertNull(c.averageRate)
        assertNull(c.elbowsStraightPercent)
        m.add(LiveReading(5, TrackingState.LOST, null, null, null))
        m.add(LiveReading(5, TrackingState.LOST, null, null, null))
        m.add(LiveReading(3, TrackingState.LOST, null, null, null))
        c = m.scorecard()
        assertEquals(0, c.sessionSeconds)
        assertNull(c.averageRate)
        assertNull(c.elbowsStraightPercent)
    }

    @Test
    fun `very long session does not overflow`() {
        val m = SessionMetrics()
        var t = 0L
        repeat(200) {
            m.add(LiveReading(t, TrackingState.BEATING, 110, 170.0, false))
            t += 3_600_000L * 24 * 30
        }
        val c = m.scorecard()
        assertTrue(c.sessionSeconds > 0 && c.measuredSeconds > 0)
        assertEquals(110, c.averageRate)
        assertEquals(100, c.elbowsStraightPercent)
    }

    @Test
    fun `nan samples count as not visible and do not poison the analyzer`() {
        val analyzer = LiveAnalyzer()
        for (i in 0 until 100) {
            val t = i * 110L
            val nan = i % 3 == 0
            analyzer.addFrame(PoseSample(t, if (nan) Double.NaN else Math.sin(i * 0.55), if (nan) Double.NaN else 120.0, 0.9f))
            val reading = analyzer.read(t)
            reading.elbowDegrees?.let { assertTrue(it.isFinite()) }
        }
        analyzer.addFrame(PoseSample(20_000, Double.NaN, Double.NaN, 0.9f))
        assertEquals(TrackingState.LOST, analyzer.read(20_000).tracking)
    }

    @Test
    fun `real recording replays with pause and resume at every moment`() {
        val file = listOf("tools/testdata/real-rmx3392-1.csv", "../tools/testdata/real-rmx3392-1.csv")
            .map(::File).firstOrNull { it.exists() } ?: return
        val rows = file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
        val samples = rows.map { PoseSample(it[0].toLong(), it[1].toDouble(), it[2].toDouble(), it[3].toFloat()) }
        val step = maxOf(1, samples.size / 120)
        for (pauseAt in samples.indices step step) {
            val analyzer = LiveAnalyzer()
            val metrics = SessionMetrics()
            val policy = CoachPolicy()
            var lastPhrase: Phrase? = null
            for ((i, s) in samples.withIndex()) {
                analyzer.addFrame(s)
                val reading = analyzer.read(s.timeMs)
                checkReading(pauseAt.toLong(), reading)
                if (i == pauseAt) metrics.pause()
                metrics.add(reading)
                val p = policy.update(reading)
                if (p != null) {
                    assertNotEquals(lastPhrase, p)
                    lastPhrase = p
                }
            }
            checkCard(pauseAt.toLong(), metrics.scorecard())
        }
    }

    @Test
    fun `framing advisor survives odd landmarks and flicker`() {
        val vals = floatArrayOf(Float.NaN, Float.POSITIVE_INFINITY, -3f, 0f, 0.03f, 0.5f, 0.97f, 1f, 7f)
        for (seed in 1L..300L) {
            val r = Random(seed)
            val advisor = FramingAdvisor()
            var t = 0L
            repeat(200) {
                t = if (r.nextInt(15) == 0) t - r.nextInt(3000) else t + r.nextInt(700)
                fun f() = if (r.nextInt(3) == 0) vals[r.nextInt(vals.size)] else r.nextFloat()
                val arm = if (r.nextInt(10) == 0) null else ArmLandmarks(f(), f(), f(), f(), f(), f(), f(), f(), f(), f(), f(), f())
                val advice = advisor.update(arm, t)
                if (advice.ready) assertNull("seed $seed", advice.hintKey)
            }
        }
        val nan = ArmLandmarks(0.5f, 0.5f, Float.NaN, 0.5f, 0.5f, 0.9f, 0.5f, 0.5f, 0.9f, 0.5f, 0.6f, 0.9f)
        val a = FramingAdvisor()
        a.update(nan, 0)
        assertTrue(!a.update(nan, 5000).ready)
    }

    @Test
    fun `emergency sequence survives odd times`() {
        val seq = EmergencySequence()
        assertTrue(seq.tick(100).isEmpty())
        for (start in longArrayOf(0, -1000, Long.MAX_VALUE / 2)) {
            seq.start(start)
            for (d in longArrayOf(-5, 0, 29_999, 30_000, 30_000, 61_000, 1_000_000, Long.MAX_VALUE / 4, 10)) {
                seq.tick(start + d)
            }
        }
        seq.start(Long.MIN_VALUE / 2)
        seq.tick(Long.MAX_VALUE / 2)
    }
}
