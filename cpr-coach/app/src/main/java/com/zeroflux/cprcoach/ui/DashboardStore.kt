package com.zeroflux.cprcoach.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.ZoneId

/** Practise history on the phone. Samples stay in assets until cleared. */
internal object DashboardStore {
    private const val FILE_NAME = "dashboard_sessions.json"
    private const val ASSET_NAME = "dashboard_samples.json"

    fun load(
        context: Context,
        nowEpochMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DatedSession> {
        val store = readStore(context)
        val samples = if (store.samplesCleared) emptyList() else readSamples(context)
        return DashboardLogic.dateSessions(DashboardLogic.visible(store, samples), nowEpochMs, zone)
    }

    fun samplesCleared(context: Context): Boolean = readStore(context).samplesCleared

    /**
     * Called only after the scorecard is already showing. A failure is ignored by the caller.
     * Writes off the caller's thread; this method itself does the file work.
     */
    fun savePractise(
        context: Context,
        id: String,
        endedAtEpochMs: Long,
        durationSeconds: Int,
        averageRate: Int?,
        secondsInRange: Int,
        elbowsStraightPercent: Int?,
        pauses: Int,
        measuredSeconds: Int,
        rates: List<Int?>,
    ) {
        val record = DashboardLogic.fromScorecard(
            id = id,
            endedAtEpochMs = endedAtEpochMs,
            durationSeconds = durationSeconds,
            averageRate = averageRate,
            secondsInRange = secondsInRange,
            elbowsStraightPercent = elbowsStraightPercent,
            pauses = pauses,
            measuredSeconds = measuredSeconds,
            rates = rates,
        )
        val existing = readStore(context)
        writeStore(context, existing.copy(sessions = existing.sessions + record))
    }

    fun clearSamples(context: Context) {
        writeStore(context, DashboardLogic.clearSamples(readStore(context)))
    }

    private fun readStore(context: Context): HistoryFile {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) return HistoryFile()
        return try {
            DashboardLogic.parseStore(file.readText())
        } catch (_: Exception) {
            HistoryFile()
        }
    }

    private fun readSamples(context: Context): List<PractiseRecord> {
        return try {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            DashboardLogic.parseSamples(text)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeStore(context: Context, file: HistoryFile) {
        val root = JSONObject()
        root.put("samplesCleared", file.samplesCleared)
        val sessions = JSONArray()
        for (record in file.sessions) sessions.put(toJson(record))
        root.put("sessions", sessions)
        File(context.filesDir, FILE_NAME).writeText(root.toString())
    }

    private fun toJson(record: PractiseRecord): JSONObject {
        val obj = JSONObject()
        obj.put("id", record.id)
        when (record.tag) {
            SessionTag.NONE -> obj.put("tag", JSONObject.NULL)
            SessionTag.TESTING -> obj.put("tag", "testing")
            SessionTag.SYNTHETIC -> obj.put("tag", "synthetic")
        }
        obj.put("endedAt", record.endedAtEpochMs ?: JSONObject.NULL)
        obj.put("daysAgo", record.daysAgo ?: JSONObject.NULL)
        obj.put("minuteOfDay", record.minuteOfDay ?: JSONObject.NULL)
        obj.put("durationSeconds", record.durationSeconds)
        obj.put("averageRate", record.averageRate ?: JSONObject.NULL)
        obj.put("secondsInRange", record.secondsInRange ?: JSONObject.NULL)
        obj.put("elbowsStraightPercent", record.elbowsStraightPercent ?: JSONObject.NULL)
        obj.put("pauses", record.pauses ?: JSONObject.NULL)
        obj.put("measuredSeconds", record.measuredSeconds ?: JSONObject.NULL)
        val rates = record.rates
        if (rates == null) {
            obj.put("rates", JSONObject.NULL)
        } else {
            val array = JSONArray()
            for (rate in rates) array.put(rate ?: JSONObject.NULL)
            obj.put("rates", array)
        }
        return obj
    }
}
