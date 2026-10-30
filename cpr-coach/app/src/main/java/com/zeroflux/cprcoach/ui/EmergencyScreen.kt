package com.zeroflux.cprcoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class EmergencyUiState(
    val compressing: Boolean,
    val helperCameraOn: Boolean,
    val stepKey: String?,
)

@Composable
fun EmergencyScreen(
    state: EmergencyUiState,
    micState: MicState,
    onCall112: () -> Unit,
    onStartCompressions: () -> Unit,
    onToggleHelperCamera: () -> Unit,
    onStopConfirmed: () -> Unit,
    onMicTap: () -> Unit,
    modifier: Modifier = Modifier,
    showHelperCameraButton: Boolean = false,
    helperCamera: @Composable () -> Unit = {},
) {
    val strings = LocalStrings.current
    Box(modifier.fillMaxSize().background(CoachColors.Navy)) {
        if (state.helperCameraOn) {
            Box(Modifier.fillMaxSize()) { helperCamera() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(if (state.helperCameraOn) CoachColors.CameraScrim else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.helperCameraOn) {
                ClayButton(
                    onClick = onCall112,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    color = CoachColors.Coral,
                    colorDeep = CoachColors.CoralDeep,
                    shape = RoundedCornerShape(28.dp),
                    elevation = 16.dp,
                    pressedScale = 0.96f,
                ) {
                    Text(
                        strings.get("em_call"),
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (!state.compressing) {
                ClayButton(
                    onClick = onStartCompressions,
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                ) {
                    Text(
                        strings.get("em_start"),
                        color = CoachColors.TextPrimary,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.compressing && state.stepKey != null) {
                    Text(
                        strings.get(state.stepKey),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (state.helperCameraOn) CoachColors.CameraScrim else Color.Transparent)
                            .padding(12.dp),
                        color = CoachColors.TextPrimary,
                        fontSize = 36.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            if (showHelperCameraButton) {
                SecondaryEmergencyButton(
                    text = strings.get(if (state.helperCameraOn) "em_helper_camera_off" else "em_helper_camera"),
                    onClick = onToggleHelperCamera,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
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

@Composable
private fun SecondaryEmergencyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CoachColors.Raised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = CoachColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HoldToStopButton(
    text: String,
    onStopConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CoachColors.Raised)
            .semantics {
                role = Role.Button
                onLongClick(label = text) {
                    onStopConfirmed()
                    true
                }
            }
            .pointerInput(onStopConfirmed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pointerId: PointerId = down.id
                    val startedAt = down.uptimeMillis
                    var stillDown = true
                    while (stillDown) {
                        val elapsed = (android.os.SystemClock.uptimeMillis() - startedAt).coerceAtLeast(0L)
                        progress.floatValue = (elapsed / HOLD_TO_STOP_MILLIS.toFloat()).coerceIn(0f, 1f)
                        if (elapsed >= HOLD_TO_STOP_MILLIS) {
                            onStopConfirmed()
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
                .height(64.dp)
                .background(CoachColors.Coral),
        )
        Text(
            text,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            color = CoachColors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

private const val HOLD_TO_STOP_MILLIS = 1_000L
