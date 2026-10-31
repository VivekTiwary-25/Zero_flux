package com.zeroflux.cprcoach.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zeroflux.cprcoach.engine.HandoverSummary

data class EmergencyUiState(
    /** String key of the one instruction: em_word_*, or em_step_* during the opening. */
    val instructionKey: String,
    val elapsedSeconds: Int,
    val showSetupHint: Boolean,
    /** True only while one of the opening clips (call, hands, push) is playing. */
    val showHandPlacement: Boolean = false,
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
    onContact: (() -> Unit)?,
    contactName: String?,
    onStopConfirmed: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    beat: BeatSource? = null,
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
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    // Only a pulse on the beat: the screen stays one big word. Still and dim when the beat is off.
                    BeatIndicator(
                        beat = beat,
                        pushes = null,
                        showTrack = false,
                        modifier = Modifier.padding(start = 12.dp).size(36.dp),
                    )
                }

                // Leave the top of the picture clear: the holder has to see the rescuer's arms.
                // The hand picture lives in this same empty slot, so it takes no extra space and moves nothing.
                Box(Modifier.weight(0.8f).fillMaxWidth()) {
                    if (state.showHandPlacement) {
                        Image(
                            painter = painterResource(com.zeroflux.cprcoach.R.drawable.hand_placement),
                            contentDescription = strings.get("em_hand_placement_desc"),
                            modifier = Modifier.align(Alignment.CenterStart).sizeIn(maxHeight = 76.dp).fillMaxHeight()
                                .aspectRatio(1.5f, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
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
                            color = CoachColors.wordColor(state.instructionKey),
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
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClayButton(
                        onClick = onCall112,
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 96.dp),
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
                            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 26.sp, stepSize = 1.sp),
                        )
                    }
                    if (contactName != null && onContact != null) {
                        ClayButton(
                            onClick = onContact,
                            modifier = Modifier.width(68.dp).fillMaxHeight(),
                            color = CoachColors.Charcoal,
                            colorDeep = CoachColors.Charcoal,
                            shape = RoundedCornerShape(20.dp),
                            elevation = 4.dp,
                            pressedScale = 0.96f,
                        ) {
                            BasicText(
                                contactName,
                                modifier = Modifier.fillMaxSize().padding(4.dp).wrapContentHeight(Alignment.CenterVertically),
                                style = TextStyle(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                ),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 16.sp, stepSize = 1.sp),
                            )
                        }
                    }
                }
                // The mic sits above the button so the label gets the whole column width.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    MicButton(state = micState, onTap = onMicTap)
                }
                HoldToStopButton(
                    text = strings.get("em_stop_hold"),
                    onStopConfirmed = onStopConfirmed,
                    modifier = Modifier.fillMaxWidth(),
                )
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
    val colors = LocalCoachColors.current
    val dash = strings.get("live_no_rate")
    val pauses = summary.pauses?.toString() ?: dash
    val measured = if (summary.measuredSeconds > 0) formatElapsed(summary.measuredSeconds) else dash

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // The facts may scroll as a last resort; the two buttons below never leave the screen.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .width(72.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(colors.Coral),
            )
            BasicText(
                strings.get("em_summary_title"),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = colors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 1.2f.em,
                ),
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 30.sp, stepSize = 1.sp),
            )
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryRow(strings.get("em_summary_total"), formatElapsed(summary.totalSeconds))
                    SummaryRow(
                        strings.get("em_summary_rate"),
                        summary.averageRate?.toString() ?: dash,
                        unit = if (summary.averageRate != null) strings.get("live_per_minute") else null,
                    )
                    SummaryRow(strings.get("em_summary_pauses"), pauses)
                    Text(
                        "${strings.get("em_summary_measured")}: $measured",
                        color = colors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        style = TabularStyle,
                    )
                }
            }
            Text(
                strings.get("em_summary_note"),
                modifier = Modifier.fillMaxWidth(),
                color = colors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Center,
            )
        }
        ClayButton(
            onClick = onCall112,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            color = colors.CoralDeep,
            colorDeep = colors.CoralDeep,
            shape = RoundedCornerShape(12.dp),
            elevation = 2.dp,
            pressedScale = 0.96f,
        ) {
            Text(
                strings.get("em_call"),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = colors.OnDanger,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        ClayButton(
            onClick = onHome,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(
                strings.get("score_home"),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = colors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Label and value share a row; the label wraps before the value ever does. */
@Composable
private fun SummaryRow(label: String, value: String, unit: String? = null) {
    val colors = LocalCoachColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = colors.TextSecondary,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        )
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                value,
                color = colors.TextPrimary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                style = TabularStyle,
                maxLines = 1,
                softWrap = false,
            )
            if (unit != null) {
                Text(
                    unit,
                    modifier = Modifier.padding(bottom = 5.dp),
                    color = colors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                )
            }
        }
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
            .height(56.dp)
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp).wrapContentHeight(Alignment.CenterVertically),
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 20.sp, stepSize = 0.5.sp),
        )
    }
}

private const val HOLD_TO_STOP_MILLIS = 1_000L
