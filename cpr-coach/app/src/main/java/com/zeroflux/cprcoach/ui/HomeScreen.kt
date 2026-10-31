package com.zeroflux.cprcoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    language: AppLanguage,
    micState: MicState,
    onEmergency: () -> Unit,
    onPractise: () -> Unit,
    onLanguageChosen: (AppLanguage) -> Unit,
    onMicTap: () -> Unit,
    emergencyContactName: String?,
    onEmergencyContact: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = LocalCoachColors.current
    val motionEnabled = rememberCoachMotionEnabled()
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var emergencyPressed by remember { mutableStateOf(false) }
    val pulse = remember { Animatable(1f) }
    val emergencyShape = RoundedCornerShape(12.dp)
    val languageTitle = strings.get("language_title")

    LaunchedEffect(emergencyPressed, motionEnabled) {
        if (emergencyPressed && motionEnabled) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, tween(durationMillis = 450))
        } else {
            pulse.snapTo(1f)
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.Navy)
            .safeDrawingPadding(),
    ) {
        val emergencyHeight = maxOf(200.dp, maxHeight * 0.42f)
        val practiseHeight = maxOf(112.dp, maxHeight * 0.22f)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .padding(bottom = 80.dp), // Scroll clear of tabs without shrinking the original cards.
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                GlassPanel(
                    modifier = Modifier
                        .semantics(mergeDescendants = true) {
                            contentDescription = languageTitle
                        }
                        .clickable(role = Role.Button) { pickerOpen = true },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    // Native names come from the shared language contract, not a translation.
                    Text(
                        text = language.nativeName,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        color = colors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 2.dp,
                        shape = emergencyShape,
                        clip = false,
                        ambientColor = colors.CoralDeep,
                        spotColor = colors.CoralDeep,
                    )
                    .drawWithContent {
                        drawContent()
                        val progress = pulse.value
                        if (motionEnabled && progress > 0f && progress < 1f) {
                            val spread = 8.dp.toPx() * progress
                            drawRoundRect(
                                color = colors.Coral.copy(alpha = (1f - progress) * 0.28f),
                                topLeft = Offset(-spread, -spread),
                                size = Size(size.width + spread * 2, size.height + spread * 2),
                                cornerRadius = CornerRadius(12.dp.toPx() + spread),
                                style = Stroke(1.5.dp.toPx()),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        // Observe without consuming: ClayButton still owns the click and sink.
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            emergencyPressed = true
                            try {
                                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                            } finally {
                                emergencyPressed = false
                            }
                        }
                    },
            ) {
                ClayButton(
                    onClick = onEmergency,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = emergencyHeight)
                        .semantics { role = Role.Button },
                    color = colors.CoralDeep,
                    colorDeep = colors.CoralDeep,
                    shape = emergencyShape,
                    elevation = 2.dp,
                    pressedScale = if (motionEnabled) 0.94f else 1f,
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = strings.get("home_emergency"),
                            color = colors.OnDanger,
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = strings.get("adult_cpr_only"),
                            color = colors.OnDanger,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            ClayButton(
                onClick = onPractise,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = practiseHeight)
                    .semantics { role = Role.Button },
                pressedScale = if (motionEnabled) 0.97f else 1f,
            ) {
                Text(
                    text = strings.get("home_practise"),
                    modifier = Modifier.padding(24.dp),
                    color = colors.TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onEmergencyContact),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    text = buildString {
                        append(strings.get("home_emergency_contact"))
                        if (!emergencyContactName.isNullOrBlank()) append(": $emergencyContactName")
                    },
                    color = colors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicButton(state = micState, onTap = onMicTap)
            }
        }
    }

    if (pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { pickerOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.Charcoal,
            contentColor = colors.TextPrimary,
            dragHandle = null,
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = languageTitle,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        color = colors.TextPrimary,
                    )
                    LazyColumn(Modifier.fillMaxWidth().selectableGroup()) {
                        items(AppLanguage.entries, key = { it.code }) { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .selectable(
                                        selected = option == language,
                                        role = Role.RadioButton,
                                        onClick = {
                                            pickerOpen = false
                                            onLanguageChosen(option)
                                        },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                RadioButton(
                                    selected = option == language,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = colors.Teal,
                                        unselectedColor = colors.TextSecondary,
                                    ),
                                )
                                Text(
                                    text = option.nativeName,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = colors.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
