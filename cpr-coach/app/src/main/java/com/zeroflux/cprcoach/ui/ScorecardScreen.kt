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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CoachColors.Navy)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                ) {
                    Text(
                        text = strings.get("score_title"),
                        style = MaterialTheme.typography.headlineSmall,
                        color = CoachColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = strings.get("score_in_range"),
                        style = MaterialTheme.typography.titleMedium,
                        color = CoachColors.TextSecondary,
                    )
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = card.secondsInRange.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            color = CoachColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = strings.get("score_seconds_unit"),
                            modifier = Modifier.padding(bottom = 10.dp),
                            style = MaterialTheme.typography.titleLarge,
                            color = CoachColors.TextSecondary,
                        )
                    }
                    Spacer(Modifier.height(22.dp))
                    ScorecardResult(
                        label = strings.get("score_average"),
                        value = card.averageRate?.toString() ?: strings.get("score_not_measured"),
                        valueColor = card.averageRate
                            ?.takeIf { it in 100..120 }
                            ?.let { CoachColors.Teal }
                            ?: CoachColors.TextPrimary,
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
                }
            }
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = onSwapRoles,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, CoachColors.Teal),
            ) {
                Text(
                    text = strings.get("score_swap"),
                    color = CoachColors.Teal,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
            TextButton(
                onClick = onHome,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(48.dp),
            ) {
                Text(strings.get("score_home"), color = CoachColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun ScorecardResult(
    label: String,
    value: String,
    valueColor: Color = CoachColors.TextPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = CoachColors.TextSecondary,
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}
