package com.zeroflux.cprcoach.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zeroflux.cprcoach.engine.HandoverSummary

data class EmergencyUiState(
    /** String key of the one instruction: em_word_*, or em_step_* during the opening. */
    val instructionKey: String,
    val elapsedSeconds: Int,
    val showSetupHint: Boolean,
)

private val FrameWidth = 10.dp
private val ControlsWidth = 200.dp
private val TabularStyle = TextStyle(fontFeatureSettings = "tnum")

/** mm:ss, with minutes growing past two digits. Negative input shows as 00:00. */
internal fun formatElapsed(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val rest = safe % 60
    return "${minutes.toString().padStart(2, '0')}:${rest.toString().padStart(2, '0')}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmergencyScreen(
    state: EmergencyUiState,
    micState: MicState,
    onCall112: () -> Unit,
    onStopConfirmed: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    camera: @Composable () -> Unit = {},
) {
    val strings = LocalStrings.current
    Box(modifier.fillMaxSize().background(CoachColors.Navy)) {
        Box(Modifier.fillMaxSize()) { camera() }
        // The frame never moves and never takes touches.
        Box(Modifier.fillMaxSize().border(FrameWidth, CoachColors.Coral))

        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(FrameWidth + 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.height(40.dp)) {
                    Text(
                        formatElapsed(state.elapsedSeconds),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoachColors.CameraScrim)
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        color = CoachColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = TabularStyle,
                        maxLines = 1,
                    )
                }

                // Leave the top of the picture clear: the holder has to see the rescuer's arms.
                Spacer(Modifier.weight(0.8f))
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(CoachColors.CameraScrim)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        strings.get(state.instructionKey),
                        modifier = Modifier.fillMaxSize().wrapContentHeight(Alignment.CenterVertically),
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 1.1f.em,
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 120.sp, stepSize = 1.sp),
                    )
                }

                // Fixed slot, so the instruction never jumps when the hint comes and goes.
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.showSetupHint) {
                        Text(
                            strings.get("em_setup_hint"),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CoachColors.CameraScrim)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            color = CoachColors.TextPrimary,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                            maxLines = 2,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.width(ControlsWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ClayButton(
                    onClick = onCall112,
                    modifier = Modifier.weight(1f).fillMaxWidth().heightIn(min = 96.dp),
                    color = CoachColors.Coral,
                    colorDeep = CoachColors.CoralDeep,
                    shape = RoundedCornerShape(28.dp),
                    elevation = 16.dp,
                    pressedScale = 0.96f,
                ) {
                    BasicText(
                        strings.get("em_call"),
                        modifier = Modifier.fillMaxSize().padding(8.dp).wrapContentHeight(Alignment.CenterVertically),
                        style = TextStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        ),
                        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 34.sp, stepSize = 1.sp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MicButton(state = micState, onTap = onMicTap)
                    HoldToStopButton(
                        text = strings.get("em_stop_hold"),
                        onStopConfirmed = onStopConfirmed,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
fun EmergencySummaryScreen(
    summary: HandoverSummary,
    onCall112: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val dash = strings.get("live_no_rate")
    val rate = summary.averageRate?.let { "$it ${strings.get("live_per_minute")}" } ?: dash
    val pauses = summary.pauses?.toString() ?: dash
    val measured = if (summary.measuredSeconds > 0) formatElapsed(summary.measuredSeconds) else dash

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoachColors.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .width(72.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CoachColors.Coral),
        )
        Text(
            strings.get("em_summary_title"),
            modifier = Modifier.fillMaxWidth(),
            color = CoachColors.TextPrimary,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        GlassPanel(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SummaryRow(strings.get("em_summary_total"), formatElapsed(summary.totalSeconds))
                SummaryRow(strings.get("em_summary_rate"), rate)
                SummaryRow(strings.get("em_summary_pauses"), pauses)
                Text(
                    "${strings.get("em_summary_measured")}: $measured",
                    color = CoachColors.TextSecondary,
                    fontSize = 18.sp,
                    style = TabularStyle,
                )
            }
        }
        Text(
            strings.get("em_summary_note"),
            modifier = Modifier.fillMaxWidth(),
            color = CoachColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        ClayButton(
            onClick = onCall112,
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            color = CoachColors.Coral,
            colorDeep = CoachColors.CoralDeep,
            shape = RoundedCornerShape(28.dp),
            elevation = 16.dp,
            pressedScale = 0.96f,
        ) {
            Text(
                strings.get("em_call"),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        ClayButton(
            onClick = onHome,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) {
            Text(
                strings.get("score_home"),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                color = CoachColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = CoachColors.TextSecondary, fontSize = 16.sp, lineHeight = 20.sp)
        Text(
            value,
            color = CoachColors.TextPrimary,
            fontSize = 40.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Bold,
            style = TabularStyle,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HoldToStopButton(
    text: String,
    onStopConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { mutableFloatStateOf(0f) }
    // The screen recomposes as time passes, and that must never restart a hold in progress.
    val currentStop by rememberUpdatedState(onStopConfirmed)
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CoachColors.Raised)
            .semantics {
                role = Role.Button
                onLongClick(label = text) {
                    currentStop()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId: PointerId = down.id
                    val startedAt = down.uptimeMillis
                    var stillDown = true
                    while (stillDown) {
                        val elapsed = (android.os.SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
                        progress.floatValue = (elapsed / HOLD_TO_STOP_MILLIS.toFloat()).coerceIn(0f, 1f)
                        if (elapsed >= HOLD_TO_STOP_MILLIS) {
                            currentStop()
                            break
                        }
                        withTimeoutOrNull(16L) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change: PointerInputChange? = event.changes.firstOrNull { it.id == pointerId }
                            stillDown = change?.pressed == true
                        }
                    }
                    progress.floatValue = 0f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.floatValue)
                .fillMaxHeight()
                .background(CoachColors.Coral)
                .align(Alignment.CenterStart),
        )
        BasicText(
            text,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp).wrapContentHeight(Alignment.CenterVertically),
            style = TextStyle(
                color = CoachColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 20.sp, stepSize = 1.sp),
        )
    }
}

private const val HOLD_TO_STOP_MILLIS = 1_000L
