package com.zeroflux.cprcoach.engine

import com.zeroflux.cprcoach.ui.TestAssets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.cos

class PushDetectorTest {
    private fun run(times: List<Long>, values: List<Double?>): List<Long> {
        val d = PushDetector()
        val out = ArrayList<Long>()
        for (i in times.indices) {
            val v = values[i]
            if (v == null) d.gap() else d.update(times[i], v)?.let { out.add(it.timeMs) }
        }
        return out
    }

    private fun synthetic(bpm: Double, amp: Double, noise: Double, seconds: Int, fps: Int = 30, seed: Long = 1): Pair<List<Long>, List<Double?>> {
        val r = Random(seed)
        val n = seconds * fps
        val t = List(n) { (it * 1000L / fps) + 1000 }
        val v = t.map { ms -> -1.0 + amp / 2 * cos(2 * PI * bpm / 60.0 * (ms - 1000) / 1000.0) + r.nextGaussian() * noise }
        return t to v
    }

    @Test
    fun `clean synthetic finds each push at 110`() {
        val (t, v) = synthetic(110.0, 0.3, 0.0, 30)
        val e = run(t, v)
        assertTrue("events=${e.size}", e.size in 50..56)
        assertTrue(e.zipWithNext { a, b -> b - a }.all { it in 450..650 })
    }

    @Test
    fun `noisy synthetic stays close to true rate`() {
        for (seed in 1L..5L) {
            val (t, v) = synthetic(110.0, 0.3, 0.03, 30, seed = seed)
            val e = run(t, v)
            assertTrue("seed $seed events=${e.size}", e.size in 47..58)
        }
    }

    @Test
    fun `tiny motion and noise give no events`() {
        val (t, v) = synthetic(110.0, 0.04, 0.02, 30)
        assertEquals(0, run(t, v).size)
        val (t2, v2) = synthetic(110.0, 0.0, 0.03, 30)
        assertEquals(0, run(t2, v2).size)
    }

    @Test
    fun `fast jitter is not counted twice`() {
        val r = Random(3)
        val t = List(900) { it * 33L }
        val v = t.map { ms ->
            -1.0 + 0.15 * cos(2 * PI * 100 / 60.0 * ms / 1000.0) + 0.05 * cos(2 * PI * 5.0 * ms / 1000.0) + r.nextGaussian() * 0.01
        }
        val e = run(t, v)
        assertTrue("events=${e.size}", e.size in 42..52)
        assertTrue(e.zipWithNext { a, b -> b - a }.all { it >= PushDetector.REFRACTORY_MS })
    }

    @Test
    fun `gaps and bad input never throw`() {
        val d = PushDetector()
        assertEquals(null, d.update(0, Double.NaN))
        d.update(10, 1.0)
        assertEquals(null, d.update(5, 1.0))
        assertEquals(null, d.update(20, Double.POSITIVE_INFINITY))
        d.update(100000, 0.0)
        d.gap()
    }

    private fun file(name: String): Pair<List<Long>, List<Double?>> {
        val rows = File(TestAssets.projectDir, "tools/testdata/$name").readLines().drop(1).filter { it.isNotBlank() }.map { it.split(',') }
        return rows.map { it[0].toLong() } to rows.map { it[1].trim().takeIf { s -> s.isNotEmpty() }?.toDouble() }
    }

    @Test
    fun `real recordings give rates near their own`() {
        for (name in listOf("real-cph2467-5.csv", "real-cph2467-7.csv")) {
            val (t, v) = file(name)
            val e = run(t, v)
            // The files include stretches with no pushing, so judge the busiest 20 seconds.
            val busiest = e.maxOf { start -> e.count { it >= start && it < start + 20_000 } } * 3
            println("PUSHDET $name events=${e.size} duration=${(t.last() - t.first()) / 1000.0}s busiest20sPerMin=$busiest")
            assertTrue("$name $busiest", busiest in 90..130)
        }
    }

    @Test
    fun `nobody recording gives very few events`() {
        val (t, v) = file("negative-cph2467-nobody.csv")
        val e = run(t, v)
        println("PUSHDET negative events=${e.size} over ${(t.last() - t.first()) / 1000.0}s")
        assertTrue("events=${e.size}", e.size <= 6)
    }
}
