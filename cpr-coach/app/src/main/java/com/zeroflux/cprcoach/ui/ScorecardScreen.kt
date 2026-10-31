package com.zeroflux.cprcoach.ui

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zeroflux.cprcoach.engine.Scorecard

@Composable
fun ScorecardScreen(
    card: Scorecard,
    onSwapRoles: () -> Unit,
    onHome: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val colors = LocalCoachColors.current
    val animationsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }
    val entrance = if (animationsEnabled) {
        fadeIn(tween(durationMillis = 380)) + slideInVertically(
            animationSpec = tween(durationMillis = 380),
            initialOffsetY = { it / 24 },
        )
    } else {
        EnterTransition.None
    }

    AnimatedVisibility(
        visible = true,
        modifier = modifier.fillMaxSize(),
        enter = entrance,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.Navy)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        Text(
                            text = strings.get("score_title"),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = strings.get("score_in_range"),
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.TextSecondary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = card.secondsInRange.toString(),
                                modifier = Modifier.alignByBaseline(),
                                style = MaterialTheme.typography.displayLarge,
                                color = colors.Teal,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = strings.get("score_seconds_unit"),
                                modifier = Modifier.alignByBaseline(),
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.TextSecondary,
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        ScorecardResult(
                            label = strings.get("score_average"),
                            value = card.averageRate?.toString() ?: strings.get("score_not_measured"),
                            valueColor = card.averageRate
                                ?.takeIf { it in 100..120 }
                                ?.let { colors.Teal }
                                ?: colors.TextPrimary,
                        )
                        ScorecardResult(
                            label = strings.get("score_elbows"),
                            value = card.elbowsStraightPercent?.let { "$it%" }
                                ?: strings.get("score_not_measured"),
                        )
                        ScorecardResult(
                            label = strings.get("score_pauses"),
                            value = card.pauses.toString(),
                        )
                        Text(
                            text = strings.get("score_rate_measured")
                                .format(card.measuredSeconds, card.sessionSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSwapRoles,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.Teal),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = strings.get("score_swap"),
                        color = colors.Teal,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                TextButton(
                    onClick = onHome,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .heightIn(min = 48.dp),
                ) {
                    Text(strings.get("score_home"), color = colors.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun ScorecardResult(
    label: String,
    value: String,
    valueColor: Color? = null,
) {
    val colors = LocalCoachColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.TextSecondary,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: colors.TextPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}
