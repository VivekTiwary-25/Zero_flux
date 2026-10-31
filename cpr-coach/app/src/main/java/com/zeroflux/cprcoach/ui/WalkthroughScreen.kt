package com.zeroflux.cprcoach.ui

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale

@Composable
fun WalkthroughScreen(
    onSkip: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableIntStateOf(0) }
    val strings = LocalStrings.current
    val colors = LocalCoachColors.current
    val motionEnabled = rememberCoachMotionEnabled()
    Column(
        modifier
            .fillMaxSize()
            .background(colors.Navy)
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
                                color = colors.TextPrimary,
                                fontSize = 30.sp,
                                lineHeight = 39.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                strings.get("walk_body_1"),
                                color = colors.TextSecondary,
                                fontSize = 18.sp,
                                lineHeight = 26.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                strings.get("adult_cpr_only"),
                                color = colors.TextSecondary,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(12.dp))
                            Box(
                                modifier = Modifier.size(width = 240.dp, height = 140.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    painter = painterResource(com.zeroflux.cprcoach.R.drawable.hand_placement),
                                    contentDescription = null,
                                    modifier = Modifier.size(width = 210.dp, height = 140.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                        1 -> {
                            Text(
                                strings.get("walk_title_2"),
                                color = colors.TextPrimary,
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
                                color = colors.TextPrimary,
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
                                ColorSwatch(colors.Teal)
                                ColorSwatch(colors.Amber)
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
                    Text(strings.get("walk_back"), color = colors.TextSecondary, fontSize = 16.sp)
                }
            } else {
                Spacer(Modifier.width(48.dp))
            }

            TextButton(onClick = onSkip, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(strings.get("walk_skip"), color = colors.TextSecondary, fontSize = 16.sp)
            }

            if (page < 2) {
                TextButton(onClick = { page++ }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(strings.get("walk_next"), color = colors.Teal, fontSize = 16.sp)
                }
            } else {
                ClayButton(
                    onClick = onStart,
                    modifier = Modifier.heightIn(min = 48.dp).width(112.dp),
                    color = colors.Teal,
                    colorDeep = colors.Teal,
                ) {
                    Text(
                        strings.get("walk_start"),
                        color = colors.OnAction,
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
    val colors = LocalCoachColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RoleIcon(rescuer)
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = colors.Teal,
            fontSize = 17.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = colors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RoleIcon(rescuer: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(
                if (rescuer) com.zeroflux.cprcoach.R.drawable.role_rescuer
                else com.zeroflux.cprcoach.R.drawable.role_holder,
            ),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit,
        )
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
