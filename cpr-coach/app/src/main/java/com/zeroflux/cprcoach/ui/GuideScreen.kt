package com.zeroflux.cprcoach.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeroflux.cprcoach.R

/**
 * CPR guide: the 5 adult CPR steps from the prototype.
 *
 * Plain composable taking [onBack] and [onEmergency]. Task 6 adds the bottom bar later.
 */
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onEmergency: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val strings = LocalStrings.current
    val motionEnabled = rememberCoachMotionEnabled()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoachColors.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Back navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 44.dp),
            ) {
                BackArrowIcon(tint = CoachColors.TextSecondary)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = strings.get("cprGuideBack"),
                    color = CoachColors.TextSecondary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }

        // Header
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = strings.get("cprGuideNav").uppercase(),
                color = CoachColors.Teal,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text = strings.get("cprGuideTitle"),
                color = CoachColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = strings.get("cprGuideIntro"),
                color = CoachColors.TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
            )
        }

        // Scope note: "For adults. If this is happening now, call 112."
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CoachColors.Teal.copy(alpha = 0.12f))
                .border(1.dp, CoachColors.Teal.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                InfoIcon(tint = CoachColors.Teal, modifier = Modifier.padding(top = 2.dp))
                Text(
                    text = strings.get("cprGuideScope"),
                    color = CoachColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp,
                )
            }
        }

        // Step tabs 1-5
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (i in 0..4) {
                val selected = i == step
                val shape = RoundedCornerShape(8.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(shape)
                        .then(
                            if (selected) {
                                Modifier.background(CoachColors.Teal)
                            } else {
                                Modifier
                                    .background(CoachColors.Charcoal)
                                    .border(1.dp, CoachColors.GlassEdge, shape)
                            }
                        )
                        .clickable { step = i }
                        .semantics { role = Role.Tab },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (i + 1).toString(),
                        color = if (selected) CoachColors.Navy else CoachColors.TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        // Step Card
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (!motionEnabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(220)) { w -> if (forward) w else -w } + fadeIn(tween(220))) togetherWith
                        (slideOutHorizontally(tween(220)) { w -> if (forward) -w else w } + fadeOut(tween(220)))
                }
            },
            label = "guideStepCard",
        ) { currentStep ->
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = "${strings.get("cprGuideStep").uppercase()} ${(currentStep + 1).toString().padStart(2, '0')} / 05",
                        color = CoachColors.Teal,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )

                    val drawableRes = when (currentStep) {
                        0 -> R.drawable.guide_step_1
                        1 -> R.drawable.guide_step_2
                        2 -> R.drawable.guide_step_3
                        3 -> R.drawable.guide_step_4
                        else -> R.drawable.guide_step_5
                    }
                    val visualDesc = strings.get("cprStep${currentStep + 1}Visual")

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CoachColors.TextPrimary)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(id = drawableRes),
                            contentDescription = visualDesc,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                        )
                        Text(
                            text = visualDesc,
                            color = CoachColors.Charcoal,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Text(
                        text = strings.get("cprStep${currentStep + 1}Title"),
                        color = CoachColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = strings.get("cprStep${currentStep + 1}Body"),
                        color = CoachColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 22.sp,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CoachColors.Teal.copy(alpha = 0.10f))
                            .drawBehind {
                                drawLine(
                                    color = CoachColors.Teal,
                                    start = Offset(1.5.dp.toPx(), 0f),
                                    end = Offset(1.5.dp.toPx(), size.height),
                                    strokeWidth = 3.dp.toPx(),
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CheckIcon(tint = CoachColors.Teal, modifier = Modifier.padding(top = 2.dp))
                            Text(
                                text = strings.get("cprStep${currentStep + 1}Tip"),
                                color = CoachColors.TextPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp,
                            )
                        }
                    }
                }
            }
        }

        // Pager buttons: Previous / Next / Open Emergency
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (step > 0) {
                ClayButton(
                    onClick = { step-- },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                    elevation = 6.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        BackArrowIcon(tint = CoachColors.TextPrimary)
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = strings.get("cprGuidePrevious"),
                            color = CoachColors.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (step < 4) {
                ClayButton(
                    onClick = { step++ },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    color = CoachColors.Teal,
                    colorDeep = CoachColors.Charcoal,
                    shape = RoundedCornerShape(14.dp),
                    elevation = 6.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = strings.get("cprGuideNext"),
                            color = CoachColors.Navy,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        ForwardArrowIcon(tint = CoachColors.Navy)
                    }
                }
            } else {
                ClayButton(
                    onClick = onEmergency,
                    modifier = Modifier
                        .weight(1.3f)
                        .heightIn(min = 48.dp),
                    color = CoachColors.Coral,
                    colorDeep = CoachColors.CoralDeep,
                    shape = RoundedCornerShape(14.dp),
                    elevation = 6.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        PhoneIcon(tint = CoachColors.Navy)
                        Text(
                            modifier = Modifier.weight(1f, fill = false),
                            text = strings.get("cprGuideEmergency"),
                            color = CoachColors.Navy,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2 - 1.dp.toPx()
        drawCircle(color = tint, radius = radius, center = center, style = stroke)
        drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(size.width / 2, size.height * 0.32f))
        drawLine(
            color = tint,
            start = Offset(size.width / 2, size.height * 0.48f),
            end = Offset(size.width / 2, size.height * 0.72f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CheckIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.76f)
            lineTo(size.width * 0.82f, size.height * 0.26f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun BackArrowIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.65f, size.height * 0.2f)
            lineTo(size.width * 0.3f, size.height * 0.5f)
            lineTo(size.width * 0.65f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun ForwardArrowIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(14.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.35f, size.height * 0.2f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.35f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun PhoneIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(16.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.22f, size.height * 0.30f)
            cubicTo(
                size.width * 0.22f, size.height * 0.65f,
                size.width * 0.35f, size.height * 0.78f,
                size.width * 0.70f, size.height * 0.78f,
            )
            lineTo(size.width * 0.78f, size.height * 0.65f)
            lineTo(size.width * 0.62f, size.height * 0.55f)
            lineTo(size.width * 0.54f, size.height * 0.60f)
            cubicTo(
                size.width * 0.45f, size.height * 0.55f,
                size.width * 0.40f, size.height * 0.45f,
                size.width * 0.45f, size.height * 0.38f,
            )
            lineTo(size.width * 0.38f, size.height * 0.24f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}
