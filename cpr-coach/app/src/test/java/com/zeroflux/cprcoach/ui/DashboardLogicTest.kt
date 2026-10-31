package com.zeroflux.cprcoach.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DashboardLogicTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-31T15:00:00Z").toEpochMilli()

    @Test
    fun `missing values stay missing and a real zero stays zero`() {
        assertEquals("—", DashboardLogic.displayNumber(null))
        assertEquals("0", DashboardLogic.displayNumber(0))
        val parsed = DashboardLogic.parseSamples(
            """
            {"sessions":[{
              "id":"s",
              "durationSeconds":40,
              "averageRate":null,
              "secondsInRange":0,
              "elbowsStraightPercent":null,
              "pauses":0,
              "measuredSeconds":null,
              "rates":[110,null]
            }]}
            """.trimIndent(),
        )
        val session = parsed.single()
        assertNull(session.averageRate)
        assertEquals(0, session.secondsInRange)
        assertNull(session.elbowsStraightPercent)
        assertEquals(0, session.pauses)
        assertNull(session.measuredSeconds)
        assertEquals(listOf(110, null), session.rates)
    }

    @Test(expected = IllegalStateException::class)
    fun `malformed store json reaches the persistence error handler`() {
        DashboardLogic.parseStore("{")
    }

    @Test
    fun `non object json and emergency records do not become zeroed sessions`() {
        assertTrue(DashboardLogic.parseStore("[]").sessions.isEmpty())
        val parsed = DashboardLogic.parseSamples(
            """
            {"sessions":[
              {"id":"em","mode":"emergency","durationSeconds":30,"averageRate":0},
              {"id":"","durationSeconds":10},
              {"tag":"other","id":"x","durationSeconds":10},
              {"id":"ok","durationSeconds":12,"averageRate":108}
            ]}
            """.trimIndent(),
        )
        assertEquals(listOf("ok"), parsed.map { it.id })
        assertEquals(108, parsed.single().averageRate)
    }

    @Test
    fun `round trip keeps nulls`() {
        val original = HistoryFile(
            samplesCleared = false,
            sessions = listOf(
                PractiseRecord(
                    id = "p-1",
                    endedAtEpochMs = now,
                    durationSeconds = 30,
                    averageRate = null,
                    secondsInRange = 4,
                    pauses = 1,
                    measuredSeconds = 10,
                    rates = listOf(null, 99, 110),
                ),
            ),
        )
        val parsed = DashboardLogic.parseStore(DashboardLogic.encodeStore(original))
        assertEquals(original, parsed)
    }

    @Test
    fun `scorecard without measured seconds keeps in range time missing`() {
        val record = DashboardLogic.fromScorecard(
            id = "unmeasured",
            endedAtEpochMs = now,
            durationSeconds = 30,
            averageRate = null,
            secondsInRange = 0,
            elbowsStraightPercent = null,
            pauses = 0,
            measuredSeconds = 0,
            rates = listOf(null, null),
        )
        assertNull(record.secondsInRange)
        assertEquals("—", DashboardLogic.displayNumber(record.secondsInRange))
        assertNull(statsOf(record).inRangeBars.single().value)
        assertNull(DashboardLogic.parseStore(DashboardLogic.encodeStore(
            HistoryFile(sessions = listOf(record)),
        )).sessions.single().secondsInRange)
    }

    @Test
    fun `measured scorecard keeps a real zero in range time`() {
        val record = DashboardLogic.fromScorecard(
            id = "measured",
            endedAtEpochMs = now,
            durationSeconds = 30,
            averageRate = 90,
            secondsInRange = 0,
            elbowsStraightPercent = null,
            pauses = 0,
            measuredSeconds = 2,
            rates = listOf(90, 90),
        )
        assertEquals(0, record.secondsInRange)
        assertEquals(0, statsOf(record).inRangeBars.single().value)
    }

    @Test
    fun `clearing samples removes both kinds and keeps real sessions`() {
        val file = HistoryFile(
            sessions = listOf(
                PractiseRecord("real", durationSeconds = 20, averageRate = 110),
                PractiseRecord("t", tag = SessionTag.TESTING, durationSeconds = 20),
                PractiseRecord("s", tag = SessionTag.SYNTHETIC, durationSeconds = 20),
            ),
        )
        val cleared = DashboardLogic.clearSamples(file)
        assertTrue(cleared.samplesCleared)
        assertEquals(listOf("real"), cleared.sessions.map { it.id })
        val shown = DashboardLogic.visible(
            cleared,
            listOf(PractiseRecord("asset", tag = SessionTag.SYNTHETIC, durationSeconds = 8)),
        )
        assertEquals(listOf("real"), shown.map { it.id })
    }

    @Test
    fun `relative dates stay inside the past two weeks`() {
        val synthetic = PractiseRecord(id = "syn", tag = SessionTag.SYNTHETIC, daysAgo = 13, minuteOfDay = 8 * 60 + 15, durationSeconds = 60)
        val at = DashboardLogic.displayEpochMs(synthetic, now, zone)
        val day = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        assertEquals(Instant.parse("2026-10-18T08:15:00Z").atZone(zone).toLocalDate(), day)
        assertTrue(synthetic.daysAgo!! in 0 until DashboardLogic.TWO_WEEKS_DAYS)
    }

    @Test
    fun `streak counts a run ending today or yesterday and stops at a gap`() {
        assertEquals(3, streakOf(0, 1, 2))
        assertEquals(2, streakOf(1, 2))
        assertEquals(0, streakOf(2, 3))
        assertEquals(1, streakOf(0, 2))
        assertEquals(0, streakOf())
    }

    @Test
    fun `best session is the most time at 100 to 120 and missing time cannot win`() {
        val shortPerfect = session("short", secondsInRange = 2, measuredSeconds = 2, daysAgo = 1)
        val long = session("long", secondsInRange = 40, measuredSeconds = 80, daysAgo = 2)
        val missing = session("missing", secondsInRange = null, measuredSeconds = null, daysAgo = 0, durationSeconds = 500)
        val stats = statsOf(shortPerfect, long, missing)
        assertEquals("long", stats.best?.record?.id)
    }

    @Test
    fun `donut uses per second rates and leaves out a session with none`() {
        val withRates = session(
            "rated",
            rates = listOf(90, null, 110, 130),
            daysAgo = 1,
        )
        val lacking = session("bare", rates = null, daysAgo = 0, averageRate = 100, measuredSeconds = 10, secondsInRange = 10)
        val stats = statsOf(withRates, lacking)
        assertEquals(RateSplit(1, 1, 1), stats.split)
        assertEquals(1, stats.leftOutOfDonut)
        assertEquals(setOf(SessionTag.SYNTHETIC), stats.donutTags)
        assertNull(DashboardLogic.splitOf(null))
        assertNull(DashboardLogic.splitOf(listOf(null, null)))
    }

    @Test
    fun `averages ignore missing values instead of treating them as zero`() {
        val measured = session("a", averageRate = 110, measuredSeconds = 10, secondsInRange = 10, daysAgo = 1)
        val missing = session("b", averageRate = null, measuredSeconds = null, secondsInRange = null, daysAgo = 0, durationSeconds = 100)
        val stats = statsOf(measured, missing)
        assertEquals(110, stats.averageRate)
        assertEquals(100, stats.inRangePercent)
        assertEquals(130, stats.totalSeconds)
    }

    @Test
    fun `about compressions sums measured rates and is null when none exist`() {
        val stats = statsOf(session("a", rates = listOf(120, null, 60), daysAgo = 0))
        assertEquals(3, stats.aboutCompressions)
        assertNull(statsOf(session("b", rates = null, daysAgo = 0)).aboutCompressions)
    }

    @Test
    fun `bars are the last six sessions and a missing bar is not zero`() {
        val sessions = (0 until 8).map { day ->
            session(
                id = "d$day",
                daysAgo = day,
                secondsInRange = if (day == 1) null else day,
            )
        }
        val stats = statsOf(*sessions.toTypedArray())
        assertEquals(listOf("d5", "d4", "d3", "d2", "d1", "d0"), stats.inRangeBars.map { it.id })
        assertNull(stats.inRangeBars.first { it.id == "d1" }.value)
        assertEquals(0, stats.inRangeBars.first { it.id == "d0" }.value)
    }

    @Test
    fun `chart series keep the tag of the sessions they use`() {
        val stats = statsOf(
            session("syn", daysAgo = 0, averageRate = 108),
            PractiseRecord(id = "real", endedAtEpochMs = now - 86_400_000L, durationSeconds = 20, averageRate = 112),
        )
        assertEquals(setOf(SessionTag.SYNTHETIC), DashboardLogic.tagsOf(stats.ratePoints.map {
            DatedSession(PractiseRecord(it.id, tag = it.tag, durationSeconds = 1), it.atEpochMs)
        }))
        assertTrue(stats.ratePoints.any { it.tag == SessionTag.SYNTHETIC })
        assertTrue(stats.ratePoints.any { it.tag == SessionTag.NONE })
    }

    private fun streakOf(vararg daysAgo: Int): Int =
        statsOf(*daysAgo.map { session("s$it", daysAgo = it) }.toTypedArray()).streakDays

    private fun statsOf(vararg records: PractiseRecord): DashboardStats =
        DashboardLogic.stats(DashboardLogic.dateSessions(records.toList(), now, zone), now, zone)

    private fun session(
        id: String,
        daysAgo: Int = 0,
        durationSeconds: Int = 30,
        averageRate: Int? = 110,
        secondsInRange: Int? = 10,
        measuredSeconds: Int? = 20,
        rates: List<Int?>? = listOf(110),
    ) = PractiseRecord(
        id = id,
        tag = SessionTag.SYNTHETIC,
        daysAgo = daysAgo,
        minuteOfDay = 12 * 60,
        durationSeconds = durationSeconds,
        averageRate = averageRate,
        secondsInRange = secondsInRange,
        measuredSeconds = measuredSeconds,
        rates = rates,
    )
}
