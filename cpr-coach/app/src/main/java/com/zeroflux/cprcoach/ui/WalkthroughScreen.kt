package com.zeroflux.cprcoach.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WalkthroughScreen(
    onSkip: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    val strings = LocalStrings.current
    val motionEnabled = rememberCoachMotionEnabled()
    Column(
        modifier
            .fillMaxSize()
            .background(CoachColors.Navy)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                if (!motionEnabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val movingForward = targetState > initialState
                    slideInHorizontally(tween(240)) { width -> if (movingForward) width else -width } togetherWith
                        slideOutHorizontally(tween(240)) { width -> if (movingForward) -width else width }
                }
            },
        ) { targetPage ->
            GlassPanel(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (targetPage) {
                        0 -> {
                            Text(
                                strings.get("walk_title_1"),
                                color = CoachColors.TextPrimary,
                                fontSize = 30.sp,
                                lineHeight = 39.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                strings.get("walk_body_1"),
                                color = CoachColors.TextSecondary,
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        1 -> {
                            Text(
                                strings.get("walk_title_2"),
                                color = CoachColors.TextPrimary,
                                fontSize = 26.sp,
                                lineHeight = 34.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RoleCard(
                                    modifier = Modifier.weight(1f),
                                    title = strings.get("walk_rescuer_title"),
                                    body = strings.get("walk_rescuer_body"),
                                    rescuer = true,
                                )
                                RoleCard(
                                    modifier = Modifier.weight(1f),
                                    title = strings.get("walk_holder_title"),
                                    body = strings.get("walk_holder_body"),
                                    rescuer = false,
                                )
                            }
                        }
                        else -> {
                            Text(
                                strings.get("walk_title_3"),
                                color = CoachColors.TextPrimary,
                                fontSize = 28.sp,
                                lineHeight = 36.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(28.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ColorSwatch(CoachColors.Teal)
                                ColorSwatch(CoachColors.Amber)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page > 0) {
                TextButton(onClick = { page-- }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(strings.get("walk_back"), color = CoachColors.TextSecondary, fontSize = 16.sp)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }

            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(strings.get("walk_skip"), color = CoachColors.TextSecondary, fontSize = 16.sp)
            }

            if (page < 2) {
                TextButton(onClick = { page++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(strings.get("walk_next"), color = CoachColors.Teal, fontSize = 16.sp)
                }
            } else {
                ClayButton(
                    onClick = onStart,
                    modifier = Modifier.heightIn(min = 48.dp).width(112.dp),
                    color = CoachColors.Teal,
                    colorDeep = Color(0xFF159E91),
                ) {
                    Text(
                        strings.get("walk_start"),
                        color = CoachColors.Navy,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(
    modifier: Modifier,
    title: String,
    body: String,
    rescuer: Boolean,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoleIcon(rescuer)
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = CoachColors.Teal,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = CoachColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoleIcon(rescuer: Boolean) {
    Canvas(Modifier.size(width = 54.dp, height = 58.dp)) {
        val ink = CoachColors.Teal
        val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        val head = Offset(size.width * 0.5f, size.height * 0.19f)
        drawCircle(ink, radius = size.width * 0.12f, center = head)
        if (rescuer) {
            drawLine(ink, Offset(size.width * 0.48f, size.height * 0.34f), Offset(size.width * 0.39f, size.height * 0.66f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.39f, size.height * 0.66f), Offset(size.width * 0.69f, size.height * 0.68f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.69f, size.height * 0.68f), Offset(size.width * 0.79f, size.height * 0.9f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.39f, size.height * 0.66f), Offset(size.width * 0.21f, size.height * 0.9f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.46f, size.height * 0.42f), Offset(size.width * 0.74f, size.height * 0.39f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.74f, size.height * 0.39f), Offset(size.width * 0.81f, size.height * 0.57f), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.25f, size.height * 0.93f), Offset(size.width * 0.82f, size.height * 0.93f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        } else {
            drawRoundRect(
                color = ink,
                topLeft = Offset(size.width * 0.34f, size.height * 0.38f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.52f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                style = stroke,
            )
            drawLine(ink, Offset(size.width * 0.42f, size.height * 0.78f), Offset(size.width * 0.59f, size.height * 0.78f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(ink, Offset(size.width * 0.5f, size.height * 0.48f), Offset(size.width * 0.5f, size.height * 0.68f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .background(color, CircleShape),
    )
}
