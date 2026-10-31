package com.zeroflux.cprcoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

internal class MainNavigationDestination<T>(
    val destination: T,
    val labelKey: String,
    val icon: @Composable () -> Unit,
    val content: @Composable () -> Unit,
)

@Composable
internal fun <T> MainNavigationBar(
    destinations: List<MainNavigationDestination<T>>,
    selectedDestination: T,
    onDestinationSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalCoachColors.current
    val strings = LocalStrings.current
    NavigationBar(modifier = modifier, containerColor = colors.Charcoal, contentColor = colors.TextPrimary) {
        destinations.forEach { entry ->
            NavigationBarItem(
                selected = entry.destination == selectedDestination,
                onClick = { onDestinationSelected(entry.destination) },
                icon = entry.icon,
                label = { Text(strings.get(entry.labelKey)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.Teal,
                    selectedTextColor = colors.TextPrimary,
                    indicatorColor = colors.Raised,
                    unselectedIconColor = colors.TextSecondary,
                    unselectedTextColor = colors.TextSecondary,
                ),
            )
        }
    }
}

@Composable
internal fun HomeNavigationIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        fun point(x: Float, y: Float) = Offset(size.width * x / 24f, size.height * y / 24f)
        drawLine(color, point(3f, 11f), point(12f, 3f), stroke, StrokeCap.Round)
        drawLine(color, point(12f, 3f), point(21f, 11f), stroke, StrokeCap.Round)
        drawLine(color, point(6f, 9f), point(6f, 21f), stroke, StrokeCap.Round)
        drawLine(color, point(6f, 21f), point(18f, 21f), stroke, StrokeCap.Round)
        drawLine(color, point(18f, 21f), point(18f, 9f), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun GuideNavigationIcon() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        fun point(x: Float, y: Float) = Offset(size.width * x / 24f, size.height * y / 24f)
        drawLine(color, point(3f, 4f), point(3f, 19f), stroke, StrokeCap.Round)
        drawLine(color, point(3f, 4f), point(8f, 4f), stroke, StrokeCap.Round)
        drawLine(color, point(8f, 4f), point(12f, 6f), stroke, StrokeCap.Round)
        drawLine(color, point(12f, 6f), point(16f, 4f), stroke, StrokeCap.Round)
        drawLine(color, point(16f, 4f), point(21f, 4f), stroke, StrokeCap.Round)
        drawLine(color, point(21f, 4f), point(21f, 19f), stroke, StrokeCap.Round)
        drawLine(color, point(21f, 19f), point(16f, 19f), stroke, StrokeCap.Round)
        drawLine(color, point(16f, 19f), point(12f, 21f), stroke, StrokeCap.Round)
        drawLine(color, point(12f, 21f), point(8f, 19f), stroke, StrokeCap.Round)
        drawLine(color, point(8f, 19f), point(3f, 19f), stroke, StrokeCap.Round)
        drawLine(color, point(12f, 6f), point(12f, 21f), stroke, StrokeCap.Round)
    }
}
