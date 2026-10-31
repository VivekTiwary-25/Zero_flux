package com.zeroflux.cprcoach.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Where a saved Practise session came from. Real sessions have no tag. */
internal enum class SessionTag { NONE, TESTING, SYNTHETIC }

/**
 * One finished Practise session. A null field was not measured: callers show a dash, never 0.
 * [rates] null means the per-second series was not saved. A null entry is a second with no rate.
 */
internal data class PractiseRecord(
    val id: String,
    val tag: SessionTag = SessionTag.NONE,
    val endedAtEpochMs: Long? = null,
    val daysAgo: Int? = null,
    val minuteOfDay: Int? = null,
    val durationSeconds: Int,
    val averageRate: Int? = null,
    val secondsInRange: Int? = null,
    val elbowsStraightPercent: Int? = null,
    val pauses: Int? = null,
    val measuredSeconds: Int? = null,
    val rates: List<Int?>? = null,
)

internal data class HistoryFile(
    val samplesCleared: Boolean = false,
    val sessions: List<PractiseRecord> = emptyList(),
)

internal data class DatedSession(
    val record: PractiseRecord,
    val atEpochMs: Long,
)

internal data class ChartPoint(
    val id: String,
    val atEpochMs: Long,
    val value: Int?,
    val tag: SessionTag,
)

internal data class RateSplit(
    val tooSlowSeconds: Int,
    val inRangeSeconds: Int,
    val tooFastSeconds: Int,
)

internal data class DashboardStats(
    val sessionCount: Int,
    val totalSeconds: Int,
    val averageRate: Int?,
    val inRangePercent: Int?,
    val split: RateSplit?,
    val leftOutOfDonut: Int,
    val donutTags: Set<SessionTag>,
    val ratePoints: List<ChartPoint>,
    val inRangeBars: List<ChartPoint>,
    val elbowPoints: List<ChartPoint>,
    val pausePoints: List<ChartPoint>,
    val best: DatedSession?,
    val streakDays: Int,
    val aboutCompressions: Int?,
)

internal object DashboardLogic {
    const val TARGET_MIN = 100
    const val TARGET_MAX = 120
    const val TWO_WEEKS_DAYS = 14

    /** Null stays a dash. A real 0 stays 0. */
    fun displayNumber(value: Int?, missing: String = "—"): String = value?.toString() ?: missing

    fun fromScorecard(
        id: String,
        endedAtEpochMs: Long,
        durationSeconds: Int,
        averageRate: Int?,
        secondsInRange: Int,
        elbowsStraightPercent: Int?,
        pauses: Int,
        measuredSeconds: Int,
        rates: List<Int?>,
    ): PractiseRecord = PractiseRecord(
        id = id,
        tag = SessionTag.NONE,
        endedAtEpochMs = endedAtEpochMs,
        durationSeconds = durationSeconds,
        averageRate = averageRate,
        secondsInRange = secondsInRange.takeIf { measuredSeconds > 0 },
        elbowsStraightPercent = elbowsStraightPercent,
        pauses = pauses,
        measuredSeconds = measuredSeconds,
        rates = rates,
    )

    fun parseSamples(text: String): List<PractiseRecord> = parseStore(text).sessions

    fun parseStore(text: String): HistoryFile {
        val root = DashboardJson.parse(text) as? Map<*, *> ?: return HistoryFile()
        val cleared = root["samplesCleared"] == true
        val sessions = (root["sessions"] as? List<*>).orEmpty().mapNotNull { item ->
            (item as? Map<*, *>)?.let { recordFrom(it) }
        }
        return HistoryFile(cleared, sessions)
    }

    fun encodeStore(file: HistoryFile): String = DashboardJson.encode(storeMap(file))

    fun encodeSamples(sessions: List<PractiseRecord>): String =
        DashboardJson.encode(mapOf("sessions" to sessions.map { recordMap(it) }))

    /** Hides both sample kinds. Real sessions stay. */
    fun clearSamples(file: HistoryFile): HistoryFile = file.copy(
        samplesCleared = true,
        sessions = file.sessions.filter { it.tag == SessionTag.NONE },
    )

    fun visible(file: HistoryFile, samples: List<PractiseRecord>): List<PractiseRecord> {
        val real = file.sessions.filter { it.tag == SessionTag.NONE }
        if (file.samplesCleared) return real
        return real + samples.filter { it.tag != SessionTag.NONE }
    }

    fun dateSessions(records: List<PractiseRecord>, nowEpochMs: Long, zone: ZoneId): List<DatedSession> =
        records.map { DatedSession(it, displayEpochMs(it, nowEpochMs, zone)) }
            .sortedByDescending { it.atEpochMs }

    fun displayEpochMs(record: PractiseRecord, nowEpochMs: Long, zone: ZoneId): Long {
        record.endedAtEpochMs?.let { return it }
        val days = record.daysAgo ?: return nowEpochMs
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val date = today.minusDays(days.toLong().coerceAtLeast(0))
        val minute = record.minuteOfDay?.takeIf { it in 0..1439 } ?: 12 * 60
        return date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
    }

    fun stats(dated: List<DatedSession>, nowEpochMs: Long, zone: ZoneId): DashboardStats {
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val chronological = dated.sortedBy { it.atEpochMs }
        return DashboardStats(
            sessionCount = dated.size,
            totalSeconds = dated.sumOf { it.record.durationSeconds.coerceAtLeast(0) },
            averageRate = weightedRate(dated),
            inRangePercent = inRangePercent(dated),
            split = rateSplit(dated),
            leftOutOfDonut = dated.count { !hasMeasuredRates(it.record) },
            donutTags = tagsOf(dated.filter { hasMeasuredRates(it.record) }),
            ratePoints = chronological.map { point(it, it.record.averageRate) },
            inRangeBars = chronological.takeLast(6).map { point(it, it.record.secondsInRange) },
            elbowPoints = chronological.map { point(it, it.record.elbowsStraightPercent) },
            pausePoints = chronological.map { point(it, it.record.pauses) },
            best = bestSession(dated),
            streakDays = streak(dated, today, zone),
            aboutCompressions = aboutCompressions(dated),
        )
    }

    fun hasMeasuredRates(record: PractiseRecord): Boolean = record.rates?.any { it != null } == true

    fun splitOf(rates: List<Int?>?): RateSplit? {
        if (rates == null || rates.none { it != null }) return null
        var slow = 0
        var mid = 0
        var fast = 0
        for (rate in rates) {
            if (rate == null) continue
            when {
                rate < TARGET_MIN -> slow++
                rate > TARGET_MAX -> fast++
                else -> mid++
            }
        }
        return RateSplit(slow, mid, fast)
    }

    fun tagsOf(dated: List<DatedSession>): Set<SessionTag> =
        dated.map { it.record.tag }.filter { it != SessionTag.NONE }.toSet()

    private fun weightedRate(dated: List<DatedSession>): Int? {
        var sum = 0.0
        var weight = 0
        for (item in dated) {
            val rate = item.record.averageRate ?: continue
            val measured = item.record.measuredSeconds ?: continue
            if (measured <= 0) continue
            sum += rate.toDouble() * measured
            weight += measured
        }
        return if (weight == 0) null else Math.round(sum / weight).toInt()
    }

    private fun inRangePercent(dated: List<DatedSession>): Int? {
        var inRange = 0
        var measured = 0
        for (item in dated) {
            val seconds = item.record.measuredSeconds ?: continue
            val inside = item.record.secondsInRange ?: continue
            if (seconds <= 0) continue
            inRange += inside
            measured += seconds
        }
        return if (measured == 0) null else Math.round(100.0 * inRange / measured).toInt()
    }

    private fun rateSplit(dated: List<DatedSession>): RateSplit? {
        var slow = 0
        var mid = 0
        var fast = 0
        var any = false
        for (item in dated) {
            val split = splitOf(item.record.rates) ?: continue
            any = true
            slow += split.tooSlowSeconds
            mid += split.inRangeSeconds
            fast += split.tooFastSeconds
        }
        return if (any) RateSplit(slow, mid, fast) else null
    }

    /** Most seconds at 100–120. Not an overall quality score. Missing time cannot win. */
    private fun bestSession(dated: List<DatedSession>): DatedSession? =
        dated.filter { (it.record.measuredSeconds ?: 0) > 0 && it.record.secondsInRange != null }
            .maxWithOrNull(
                compareBy<DatedSession> { it.record.secondsInRange!! }
                    .thenBy { it.record.secondsInRange!!.toDouble() / it.record.measuredSeconds!! }
                    .thenBy { it.atEpochMs },
            )

    /**
     * Consecutive days ending today, or yesterday if today has no session yet.
     * A last session older than yesterday is not a live streak.
     */
    private fun streak(dated: List<DatedSession>, today: LocalDate, zone: ZoneId): Int {
        val days = dated.map { Instant.ofEpochMilli(it.atEpochMs).atZone(zone).toLocalDate() }.toSet()
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0
        var count = 0
        while (cursor in days) {
            count++
            cursor = cursor.minusDays(1)
        }
        return count
    }

    private fun aboutCompressions(dated: List<DatedSession>): Int? {
        var sum = 0
        var any = false
        for (item in dated) {
            val rates = item.record.rates ?: continue
            for (rate in rates) {
                if (rate == null) continue
                sum += rate
                any = true
            }
        }
        return if (any) Math.round(sum / 60.0).toInt() else null
    }

    private fun point(item: DatedSession, value: Int?) = ChartPoint(
        id = item.record.id,
        atEpochMs = item.atEpochMs,
        value = value,
        tag = item.record.tag,
    )

    private fun recordFrom(node: Map<*, *>): PractiseRecord? {
        val mode = node["mode"] as? String
        if (mode != null && mode != "practise") return null
        val id = (node["id"] as? String)?.trim().orEmpty()
        if (id.isEmpty()) return null
        val tag = when (node["tag"] as? String) {
            null, "", "none" -> SessionTag.NONE
            "testing" -> SessionTag.TESTING
            "synthetic" -> SessionTag.SYNTHETIC
            else -> return null
        }
        val duration = intOrNull(node["durationSeconds"]) ?: return null
        if (duration < 0) return null
        return PractiseRecord(
            id = id,
            tag = tag,
            endedAtEpochMs = longOrNull(node["endedAt"]),
            daysAgo = intOrNull(node["daysAgo"])?.takeIf { it >= 0 },
            minuteOfDay = intOrNull(node["minuteOfDay"])?.takeIf { it in 0..1439 },
            durationSeconds = duration,
            averageRate = intOrNull(node["averageRate"]),
            secondsInRange = intOrNull(node["secondsInRange"]),
            elbowsStraightPercent = intOrNull(node["elbowsStraightPercent"]),
            pauses = intOrNull(node["pauses"]),
            measuredSeconds = intOrNull(node["measuredSeconds"]),
            rates = ratesOrNull(node["rates"]),
        )
    }

    private fun ratesOrNull(value: Any?): List<Int?>? {
        val list = value as? List<*> ?: return null
        return list.map { item -> intOrNull(item) }
    }

    private fun intOrNull(value: Any?): Int? = when (value) {
        is Int -> value
        is Long -> value.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        is Double -> value.takeIf { it % 1.0 == 0.0 }?.toInt()
        else -> null
    }

    private fun longOrNull(value: Any?): Long? = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.takeIf { it % 1.0 == 0.0 }?.toLong()
        else -> null
    }

    private fun storeMap(file: HistoryFile): Map<String, Any?> = mapOf(
        "samplesCleared" to file.samplesCleared,
        "sessions" to file.sessions.map { recordMap(it) },
    )

    private fun recordMap(record: PractiseRecord): Map<String, Any?> = linkedMapOf(
        "id" to record.id,
        "tag" to when (record.tag) {
            SessionTag.NONE -> null
            SessionTag.TESTING -> "testing"
            SessionTag.SYNTHETIC -> "synthetic"
        },
        "endedAt" to record.endedAtEpochMs,
        "daysAgo" to record.daysAgo,
        "minuteOfDay" to record.minuteOfDay,
        "durationSeconds" to record.durationSeconds,
        "averageRate" to record.averageRate,
        "secondsInRange" to record.secondsInRange,
        "elbowsStraightPercent" to record.elbowsStraightPercent,
        "pauses" to record.pauses,
        "measuredSeconds" to record.measuredSeconds,
        "rates" to record.rates,
    )
}

/** The small JSON this screen writes. Missing numbers stay null, never 0. */
internal object DashboardJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.readValue()
        reader.skipWhitespace()
        if (!reader.atEnd()) error("Unexpected trailing JSON")
        return value
    }

    fun encode(value: Any?): String = buildString { write(value, 0) }

    private fun StringBuilder.write(value: Any?, indent: Int) {
        when (value) {
            null -> append("null")
            is String -> append(quote(value))
            is Boolean -> append(if (value) "true" else "false")
            is Int -> append(value)
            is Long -> append(value)
            is Double -> append(value)
            is List<*> -> {
                append('[')
                if (value.isEmpty()) {
                    append(']')
                    return
                }
                value.forEachIndexed { index, item ->
                    append('\n')
                    append("  ".repeat(indent + 1))
                    write(item, indent + 1)
                    if (index != value.lastIndex) append(',')
                }
                append('\n')
                append("  ".repeat(indent))
                append(']')
            }
            is Map<*, *> -> {
                append('{')
                val entries = value.entries.toList()
                if (entries.isEmpty()) {
                    append('}')
                    return
                }
                entries.forEachIndexed { index, entry ->
                    append('\n')
                    append("  ".repeat(indent + 1))
                    append(quote(entry.key.toString()))
                    append(": ")
                    write(entry.value, indent + 1)
                    if (index != entries.lastIndex) append(',')
                }
                append('\n')
                append("  ".repeat(indent))
                append('}')
            }
            else -> error("Cannot encode ${value.javaClass.simpleName}")
        }
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (ch in text) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

    private class Reader(private val text: String) {
        private var offset = 0

        fun atEnd(): Boolean = offset >= text.length

        fun skipWhitespace() {
            while (!atEnd() && text[offset].isWhitespace()) offset++
        }

        fun readValue(): Any? {
            skipWhitespace()
            if (atEnd()) error("Unexpected end of JSON")
            return when (text[offset]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                else -> readNumber()
            }
        }

        private fun readObject(): Map<String, Any?> {
            expect('{')
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (peek('}')) return map
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                map[key] = readValue()
                skipWhitespace()
                if (peek('}')) return map
                expect(',')
            }
        }

        private fun readArray(): List<Any?> {
            expect('[')
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (peek(']')) return list
            while (true) {
                list.add(readValue())
                skipWhitespace()
                if (peek(']')) return list
                expect(',')
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (!atEnd()) {
                val ch = text[offset++]
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        if (atEnd()) error("Bad escape")
                        when (val esc = text[offset++]) {
                            '"', '\\', '/' -> out.append(esc)
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (offset + 4 > text.length) error("Bad unicode escape")
                                val hex = text.substring(offset, offset + 4)
                                out.append(hex.toInt(16).toChar())
                                offset += 4
                            }
                            else -> error("Bad escape")
                        }
                    }
                    else -> out.append(ch)
                }
            }
            error("Unclosed string")
        }

        private fun readNumber(): Any {
            val start = offset
            if (peek('-')) offset++
            if (atEnd() || !text[offset].isDigit()) error("Bad number")
            while (!atEnd() && text[offset].isDigit()) offset++
            var fractional = false
            if (!atEnd() && text[offset] == '.') {
                fractional = true
                offset++
                while (!atEnd() && text[offset].isDigit()) offset++
            }
            if (!atEnd() && (text[offset] == 'e' || text[offset] == 'E')) {
                fractional = true
                offset++
                if (!atEnd() && (text[offset] == '+' || text[offset] == '-')) offset++
                while (!atEnd() && text[offset].isDigit()) offset++
            }
            val token = text.substring(start, offset)
            return if (fractional) token.toDouble() else token.toLong()
        }

        private fun readLiteral(word: String, value: Any?): Any? {
            if (!text.startsWith(word, offset)) error("Expected $word")
            offset += word.length
            return value
        }

        private fun expect(ch: Char) {
            skipWhitespace()
            if (atEnd() || text[offset] != ch) error("Expected $ch")
            offset++
        }

        private fun peek(ch: Char): Boolean {
            if (atEnd() || text[offset] != ch) return false
            offset++
            return true
        }
    }
}
