package org.schabi.newpipe.tournesol.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.components.common.LoadingIndicator

private const val WEEKLY_GOAL = 2500
private const val DAILY_GOAL = 2
private const val GOAL_RING_MAX_FONT_GROWTH = 1.4f

@Composable
private fun GoalRing(
    current: Int,
    goal: Int,
    label: String,
    ringColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val progress = current.toFloat() / goal
    val emoji = when {
        progress > 1.25f -> "\u2764\uFE0F\u200D\uD83D\uDD25"
        progress > 1f -> "\uD83E\uDD73\uD83C\uDF89"
        progress > 0.75f -> "\uD83C\uDF3B"
        progress > 0.5f -> "\uD83C\uDF37"
        progress > 0.25f -> "\uD83C\uDF40"
        else -> "\uD83C\uDF31"
    }

    val scale = LocalCompareScale.current
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "goalRing"
    )
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val fontGrowth = LocalDensity.current.fontScale.coerceIn(1f, GOAL_RING_MAX_FONT_GROWTH)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(104.dp * scale * fontGrowth)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(72.dp * scale)) {
                val stroke = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = animatedProgress * 360f,
                    useCenter = false,
                    style = stroke
                )
            }
            Text(
                text = emoji,
                fontSize = 26.sp * scale
            )
        }
        Spacer(modifier = Modifier.height(6.dp * scale))
        Text(
            text = "$current / $goal",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            fontSize = 15.sp * scale,
            lineHeight = 18.sp * scale
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            fontSize = 13.sp * scale,
            lineHeight = 16.sp * scale,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2
        )
    }
}

@Composable
internal fun UserGreetingBanner(
    username: String?,
    comparisonCount: Int?,
    weeklyComparisons: Int?,
    dailyComparisons: Int?,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    val scale = LocalCompareScale.current
    if (username != null) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp * scale)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                Text(
                    text = stringResource(R.string.compare_greeting, username),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (comparisonCount != null) {
                    Text(
                        text = stringResource(R.string.compare_greeting_comparisons, comparisonCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.CenterVertically),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp * scale)
            ) {
                if (dailyComparisons != null) {
                    GoalRing(
                        current = dailyComparisons,
                        goal = DAILY_GOAL,
                        label = stringResource(R.string.compare_goal_daily),
                        ringColor = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (weeklyComparisons != null) {
                    GoalRing(
                        current = weeklyComparisons,
                        goal = WEEKLY_GOAL,
                        label = stringResource(R.string.compare_goal_weekly)
                    )
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp * scale),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp * scale)
        ) {
            Text(
                text = stringResource(R.string.compare_login_prompt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp * scale)) {
                Button(
                    onClick = onLogin,
                    contentPadding = PaddingValues(horizontal = 16.dp * scale, vertical = 6.dp * scale)
                ) {
                    Text(text = stringResource(R.string.tournesol_login_button))
                }
                OutlinedButton(
                    onClick = onRegister,
                    contentPadding = PaddingValues(horizontal = 16.dp * scale, vertical = 6.dp * scale)
                ) {
                    Text(text = stringResource(R.string.tournesol_register_button))
                }
            }
        }
    }
}

@Composable
internal fun CompareStatusPanel(
    status: CompareScreenStatus,
    onRetry: () -> Unit
) {
    if (status == CompareScreenStatus.NeedsLogin || status == CompareScreenStatus.Ready) {
        return
    }
    val scale = LocalCompareScale.current
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp * scale, vertical = 32.dp * scale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp * scale)
    ) {
        if (status is CompareScreenStatus.NoPair) {
            Text(
                text = stringResource(status.messageRes),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                textAlign = TextAlign.Center
            )
            OutlinedButton(onClick = onRetry) {
                Text(text = stringResource(R.string.retry))
            }
        } else {
            LoadingIndicator()
        }
    }
}

@Composable
internal fun CompactInitialPromptHeader(
    modifier: Modifier = Modifier
) {
    val scale = LocalCompareScale.current
    val template = stringResource(R.string.compare_compact_initial_prompt)
    val parts = template.split("%1\$s", limit = 2)
    val prefix = parts.getOrNull(0).orEmpty()
    val suffix = parts.getOrNull(1).orEmpty()
    val textStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                style = textStyle,
                color = textColor,
                textAlign = TextAlign.Start
            )
        }
        Image(
            painter = painterResource(R.drawable.logo_small),
            contentDescription = null,
            modifier = Modifier
                .padding(horizontal = 4.dp * scale)
                .size(16.dp * scale)
        )
        if (suffix.isNotEmpty()) {
            Text(
                text = suffix,
                style = textStyle,
                color = textColor,
                textAlign = TextAlign.Start,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
