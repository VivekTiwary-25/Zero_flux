package com.zeroflux.cprcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val strings = LocalStrings.current
    val zone = remember { ZoneId.systemDefault() }
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<DatedSession>?>(null) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    LaunchedEffect(generation) {
        val app = context.applicationContext
        val loaded = withContext(Dispatchers.IO) {
            try {
                DashboardStore.load(app)
            } catch (_: Exception) {
                emptyList()
            }
        }
        sessions = loaded
        if (loaded.none { it.record.id == selectedId }) selectedId = null
    }

    val selected = sessions?.find { it.record.id == selectedId }
    if (selected != null) {
        SessionDetail(selected, zone, onBack = { selectedId = null }, modifier = modifier)
        return
    }

    val stats = sessions?.let { DashboardLogic.stats(it, System.currentTimeMillis(), zone) }
    val missing = strings.get("dash_missing")
    val allTags = sessions?.let { DashboardLogic.tagsOf(it) }.orEmpty()
    Column(
        modifier
            .fillMaxSize()
            .background(CoachColors.Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onHome, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(strings.get("walk_back"), color = CoachColors.TextSecondary)
        }
        Text(
            text = strings.get("home_dashboard"),
            style = MaterialTheme.typography.headlineSmall,
            color = CoachColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = strings.get("dash_intro"),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = CoachColors.TextSecondary,
        )
        when {
            sessions == null -> Spacer(Modifier.height(24.dp))
            sessions!!.isEmpty() -> EmptyHistory()
            stats != null -> {
                Spacer(Modifier.height(16.dp))
                OverviewGrid(stats, missing, allTags)
                Text(
                    text = strings.get("dash_basis"),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoachColors.TextSecondary,
                )
                Spacer(Modifier.height(16.dp))
                DonutCard(stats, missing)
                Spacer(Modifier.height(12.dp))
                LineCard(
                    title = strings.get("dash_line"),
                    points = stats.ratePoints,
                    band = true,
                    missing = missing,
                    zone = zone,
                )
                Spacer(Modifier.height(12.dp))
                BarCard(stats.inRangeBars, missing, zone)
                Spacer(Modifier.height(12.dp))
                LineCard(
                    title = strings.get("score_elbows"),
                    points = stats.elbowPoints,
                    band = false,
                    missing = missing,
                    zone = zone,
                )
                Spacer(Modifier.height(12.dp))
                LineCard(
                    title = strings.get("score_pauses"),
                    points = stats.pausePoints,
                    band = false,
                    missing = missing,
                    zone = zone,
                )
                Spacer(Modifier.height(12.dp))
                SummaryRow(stats, missing, allTags, onOpen = { selectedId = it })
                Spacer(Modifier.height(18.dp))
                Text(
                    text = strings.get("dash_sessions"),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoachColors.TextPrimary,
                )
                sessions!!.forEach { item ->
                    SessionRow(item, zone, missing, onClick = { selectedId = item.record.id })
                }
                if (sessions!!.any { it.record.tag != SessionTag.NONE }) {
                    TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    DashboardStore.clearSamples(context.applicationContext)
                                } catch (_: Exception) {
                                    return@launch
                                }
                                withContext(Dispatchers.Main) { generation++ }
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(strings.get("dash_clear"), color = CoachColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHistory() {
    val strings = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = strings.get("dash_empty"),
            style = MaterialTheme.typography.titleLarge,
            color = CoachColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = strings.get("dash_empty_body"),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = CoachColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OverviewGrid(stats: DashboardStats, missing: String, tags: Set<SessionTag>) {
    val strings = LocalStrings.current
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            Box(Modifier.padding(horizontal = 12.dp)) { TagRow(tags) }
            Row(Modifier.fillMaxWidth()) {
                StatTile(strings.get("dash_sessions"), stats.sessionCount.toString(), Modifier.weight(1f))
                StatTile(strings.get("dash_time"), formatElapsed(stats.totalSeconds), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                StatTile(
                    strings.get("score_average"),
                    DashboardLogic.displayNumber(stats.averageRate, missing),
                    Modifier.weight(1f),
                    unit = if (stats.averageRate == null) "" else strings.get("live_per_minute"),
                )
                StatTile(
                    strings.get("score_in_range"),
                    stats.inRangePercent?.let { "$it%" } ?: missing,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, unit: String = "") {
    Column(modifier.padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoachColors.TextSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = CoachColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            if (unit.isNotEmpty()) {
                Text(
                    text = unit,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoachColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun DonutCard(stats: DashboardStats, missing: String) {
    val strings = LocalStrings.current
    val split = stats.split
    val total = split?.let { it.tooSlowSeconds + it.inRangeSeconds + it.tooFastSeconds } ?: 0
    val center = if (split == null || total == 0) {
        missing
    } else {
        "${Math.round(100.0 * split.inRangeSeconds / total)}%"
    }
    ChartCard(strings.get("dash_band"), stats.donutTags) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = strings.get("dash_band") },
                ) {
                    val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Butt)
                    val arcSize = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx())
                    val topLeft = Offset(10.dp.toPx(), 10.dp.toPx())
                    if (split == null || total == 0) {
                        drawArc(CoachColors.Raised, 0f, 360f, false, topLeft, arcSize, style = stroke)
                    } else {
                        var start = -90f
                        val parts = listOf(
                            split.tooSlowSeconds to CoachColors.Amber,
                            split.inRangeSeconds to CoachColors.Teal,
                            split.tooFastSeconds to CoachColors.Coral,
                        )
                        for ((seconds, color) in parts) {
                            if (seconds <= 0) continue
                            val sweep = 360f * seconds / total
                            drawArc(color, start, sweep, false, topLeft, arcSize, style = stroke)
                            start += sweep
                        }
                    }
                }
                Text(center, color = CoachColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LegendLine(CoachColors.Amber, strings.get("dash_too_slow"), split?.tooSlowSeconds, missing)
                LegendLine(CoachColors.Teal, strings.get("dash_band"), split?.inRangeSeconds, missing)
                LegendLine(CoachColors.Coral, strings.get("dash_too_fast"), split?.tooFastSeconds, missing)
            }
        }
        if (stats.leftOutOfDonut > 0) {
            Text(
                text = strings.get("dash_left_out").replace("{n}", stats.leftOutOfDonut.toString()),
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = CoachColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun LegendLine(color: androidx.compose.ui.graphics.Color, label: String, seconds: Int?, missing: String) {
    val strings = LocalStrings.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(
            text = "$label ${seconds?.let { "$it${strings.get("score_seconds_unit")}" } ?: missing}",
            style = MaterialTheme.typography.bodySmall,
            color = CoachColors.TextSecondary,
        )
    }
}

@Composable
private fun LineCard(
    title: String,
    points: List<ChartPoint>,
    band: Boolean,
    missing: String,
    zone: ZoneId,
) {
    ChartCard(title, DashboardLogic.tagsOf(points.map { DatedSession(PractiseRecord(it.id, tag = it.tag, durationSeconds = 0), it.atEpochMs) })) {
        SeriesChart(points, band, missing, zone)
    }
}

@Composable
private fun BarCard(points: List<ChartPoint>, missing: String, zone: ZoneId) {
    val strings = LocalStrings.current
    ChartCard(strings.get("score_in_range"), tagsOf(points)) {
        Text(
            text = strings.get("dash_last_6"),
            style = MaterialTheme.typography.bodySmall,
            color = CoachColors.Teal,
        )
        BarChart(points, missing, zone)
    }
}

@Composable
private fun SummaryRow(stats: DashboardStats, missing: String, tags: Set<SessionTag>, onOpen: (String) -> Unit) {
    val strings = LocalStrings.current
    val best = stats.best
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = best != null, role = Role.Button) { best?.let { onOpen(it.record.id) } }
                    .padding(16.dp),
            ) {
                Text(strings.get("dash_best"), color = CoachColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = best?.let { formatSessionWhen(it.atEpochMs, ZoneId.systemDefault()) } ?: missing,
                    color = CoachColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = best?.record?.secondsInRange?.let { "$it${strings.get("score_seconds_unit")}" } ?: missing,
                    color = CoachColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(strings.get("dash_best_note"), color = CoachColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                TagRow(setOfNotNull(best?.record?.tag?.takeIf { it != SessionTag.NONE }))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassPanel(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(strings.get("dash_streak"), color = CoachColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = stats.streakDays.toString(),
                        color = CoachColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TagRow(tags)
                }
            }
            GlassPanel(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(strings.get("dash_compressions"), color = CoachColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = stats.aboutCompressions?.let { "${strings.get("dash_about")} $it" } ?: missing,
                        color = CoachColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TagRow(tags)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(item: DatedSession, zone: ZoneId, missing: String, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val rate = item.record.averageRate
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(strings.get("home_practise"), color = CoachColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(
                text = formatSessionWhen(item.atEpochMs, zone),
                color = CoachColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            TagRow(setOfNotNull(item.record.tag.takeIf { it != SessionTag.NONE }))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatElapsed(item.record.durationSeconds), color = CoachColors.TextPrimary)
            Text(
                text = rate?.let { "$it ${strings.get("live_per_minute")}" } ?: missing,
                color = if (rate != null && rate in DashboardLogic.TARGET_MIN..DashboardLogic.TARGET_MAX) {
                    CoachColors.Teal
                } else {
                    CoachColors.TextSecondary
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SessionDetail(
    item: DatedSession,
    zone: ZoneId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val missing = strings.get("dash_missing")
    val record = item.record
    val split = DashboardLogic.splitOf(record.rates)
    Column(
        modifier
            .fillMaxSize()
            .background(CoachColors.Navy)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(strings.get("walk_back"), color = CoachColors.TextSecondary)
        }
        Text(
            text = strings.get("home_practise"),
            style = MaterialTheme.typography.headlineSmall,
            color = CoachColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = formatSessionWhen(item.atEpochMs, zone),
            modifier = Modifier.padding(top = 4.dp),
            color = CoachColors.TextSecondary,
        )
        TagRow(setOfNotNull(record.tag.takeIf { it != SessionTag.NONE }))
        Text(
            text = formatElapsed(record.durationSeconds),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.displaySmall,
            color = CoachColors.TextPrimary,
        )
        Text(strings.get("dash_duration"), color = CoachColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(8.dp)) {
                DetailStat(strings.get("score_average"), record.averageRate?.let { "$it ${strings.get("live_per_minute")}" } ?: missing)
                DetailStat(strings.get("score_in_range"), record.secondsInRange?.let { "$it ${strings.get("score_seconds_unit")}" } ?: missing)
                DetailStat(strings.get("score_elbows"), record.elbowsStraightPercent?.let { "$it%" } ?: missing)
                DetailStat(strings.get("score_pauses"), DashboardLogic.displayNumber(record.pauses, missing))
                DetailStat(strings.get("dash_measured"), record.measuredSeconds?.let { formatElapsed(it) } ?: missing)
                DetailStat(strings.get("dash_too_slow"), split?.tooSlowSeconds?.let { "$it ${strings.get("score_seconds_unit")}" } ?: missing)
                DetailStat(strings.get("dash_too_fast"), split?.tooFastSeconds?.let { "$it ${strings.get("score_seconds_unit")}" } ?: missing)
            }
        }
        Spacer(Modifier.height(12.dp))
        ChartCard(strings.get("dash_rate_chart"), setOfNotNull(record.tag.takeIf { it != SessionTag.NONE })) {
            if (!DashboardLogic.hasMeasuredRates(record)) {
                Text(strings.get("dash_no_rates"), color = CoachColors.TextSecondary)
            } else {
                RateTimeline(record.rates.orEmpty(), strings.get("dash_rate_chart"))
                Text(
                    text = strings.get("dash_gaps"),
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoachColors.TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = CoachColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = CoachColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ChartCard(title: String, tags: Set<SessionTag>, content: @Composable () -> Unit) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, color = CoachColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            TagRow(tags)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun TagRow(tags: Set<SessionTag>) {
    if (tags.isEmpty()) return
    val strings = LocalStrings.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        if (SessionTag.TESTING in tags) TagChip(strings.get("dash_tag_testing"))
        if (SessionTag.SYNTHETIC in tags) TagChip(strings.get("dash_tag_synthetic"))
    }
}

@Composable
private fun TagChip(label: String) {
    Text(
        text = label,
        modifier = Modifier
            .background(CoachColors.Raised, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = CoachColors.Amber,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun SeriesChart(
    points: List<ChartPoint>,
    band: Boolean,
    missing: String,
    zone: ZoneId,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = CoachColors.TextSecondary, fontSize = 10.sp)
    if (points.none { it.value != null }) {
        Text(missing, color = CoachColors.TextSecondary)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val values = points.mapNotNull { it.value }
        val minY = if (band) minOf(60, values.minOrNull() ?: 60) else 0
        val maxY = if (band) maxOf(160, values.maxOrNull() ?: 160) else maxOf(1, values.maxOrNull() ?: 1)
        val left = 28.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val bottom = size.height - 22.dp.toPx()
        fun yOf(value: Int) = top + (1f - (value - minY).toFloat() / (maxY - minY).coerceAtLeast(1)) * (bottom - top)
        fun xOf(index: Int) = if (points.size == 1) (left + right) / 2 else left + index * (right - left) / (points.size - 1)
        if (band) {
            drawRect(
                CoachColors.Teal.copy(alpha = 0.18f),
                Offset(left, yOf(DashboardLogic.TARGET_MAX)),
                Size(right - left, yOf(DashboardLogic.TARGET_MIN) - yOf(DashboardLogic.TARGET_MAX)),
            )
        }
        val path = Path()
        var penDown = false
        points.forEachIndexed { index, point ->
            val value = point.value
            if (value == null) {
                penDown = false
            } else {
                val x = xOf(index)
                val y = yOf(value)
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                drawCircle(CoachColors.Teal, 3.dp.toPx(), Offset(x, y))
            }
        }
        drawPath(path, CoachColors.Teal, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        if (points.size <= 8) {
            points.forEachIndexed { index, point ->
                val day = Instant.ofEpochMilli(point.atEpochMs).atZone(zone).dayOfMonth.toString()
                val layout = measurer.measure(day, labelStyle)
                drawText(layout, topLeft = Offset(xOf(index) - layout.size.width / 2, bottom + 2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun BarChart(points: List<ChartPoint>, missing: String, zone: ZoneId) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = CoachColors.TextSecondary, fontSize = 10.sp)
    if (points.none { it.value != null }) {
        Text(missing, color = CoachColors.TextSecondary)
        return
    }
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val maxValue = points.mapNotNull { it.value }.maxOrNull()?.coerceAtLeast(1) ?: 1
        val labelH = 28.dp.toPx()
        val gap = 8.dp.toPx()
        val barWidth = ((size.width - gap * (points.size + 1)) / points.size).coerceAtLeast(8.dp.toPx())
        points.forEachIndexed { index, point ->
            val left = gap + index * (barWidth + gap)
            val value = point.value
            if (value != null) {
                val height = (size.height - labelH) * value / maxValue
                drawRoundRect(
                    color = CoachColors.Teal,
                    topLeft = Offset(left, size.height - labelH - height),
                    size = Size(barWidth, height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                )
            }
            val day = if (value == null) missing else Instant.ofEpochMilli(point.atEpochMs).atZone(zone).dayOfMonth.toString()
            val layout = measurer.measure(day, labelStyle)
            drawText(layout, topLeft = Offset(left + barWidth / 2 - layout.size.width / 2, size.height - labelH + 4.dp.toPx()))
        }
    }
}

@Composable
private fun RateTimeline(rates: List<Int?>, chartLabel: String) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = CoachColors.TextSecondary, fontSize = 10.sp)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .semantics { contentDescription = chartLabel },
    ) {
        val known = rates.mapNotNull { it }
        val minY = minOf(60, known.minOrNull() ?: 60)
        val maxY = maxOf(160, known.maxOrNull() ?: 160)
        val left = 28.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        fun yOf(value: Int) = top + (1f - (value - minY).toFloat() / (maxY - minY).coerceAtLeast(1)) * (bottom - top)
        fun xOf(index: Int) = if (rates.size <= 1) (left + right) / 2 else left + index * (right - left) / (rates.size - 1)
        drawRect(
            CoachColors.Teal.copy(alpha = 0.18f),
            Offset(left, yOf(DashboardLogic.TARGET_MAX)),
            Size(right - left, yOf(DashboardLogic.TARGET_MIN) - yOf(DashboardLogic.TARGET_MAX)),
        )
        val path = Path()
        var penDown = false
        rates.forEachIndexed { index, rate ->
            if (rate == null) {
                penDown = false
            } else {
                val x = xOf(index)
                val y = yOf(rate)
                if (!penDown) {
                    path.moveTo(x, y)
                    penDown = true
                } else {
                    path.lineTo(x, y)
                }
                // A lone sample between gaps has no line to carry it, so it gets a dot.
                if (rates.getOrNull(index - 1) == null && rates.getOrNull(index + 1) == null) {
                    drawCircle(CoachColors.Teal, 3.dp.toPx(), Offset(x, y))
                }
            }
        }
        drawPath(path, CoachColors.Teal, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val low = measurer.measure("100", labelStyle)
        val high = measurer.measure("120", labelStyle)
        drawText(high, topLeft = Offset(0f, yOf(120) - high.size.height / 2))
        drawText(low, topLeft = Offset(0f, yOf(100) - low.size.height / 2))
    }
}

@Composable
private fun tagsOf(points: List<ChartPoint>): Set<SessionTag> =
    points.map { it.tag }.filter { it != SessionTag.NONE }.toSet()

private fun formatSessionWhen(epochMs: Long, zone: ZoneId): String {
    val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
    val date = zoned.toLocalDate()
    val time = zoned.toLocalTime()
    return "%02d/%02d %02d:%02d".format(date.dayOfMonth, date.monthValue, time.hour, time.minute)
}
