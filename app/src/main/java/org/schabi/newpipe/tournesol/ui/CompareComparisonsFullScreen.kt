package org.schabi.newpipe.tournesol.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.CompareComparisonVideo
import org.schabi.newpipe.tournesol.CompareRecommendationItem

@Composable
internal fun CompareComparisonsFullScreen(
    recommendations: List<CompareRecommendationItem>,
    totalCount: Int? = null,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onVideoClick: ((CompareComparisonVideo) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .zIndex(6f)
            .padding(horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLoading) {
                    val titleLine = with(LocalDensity.current) {
                        MaterialTheme.typography.titleMedium.lineHeight.toDp()
                    }
                    Box(modifier = Modifier.weight(1f).alpha(skeletonAlpha())) {
                        SkeletonLine(skeletonColor(), titleLine, 0.75f)
                    }
                } else {
                    val title = if (totalCount != null) {
                        stringResource(R.string.compare_you_submitted, totalCount)
                    } else {
                        stringResource(R.string.compare_title)
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.close),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            when {
                isLoading -> CompareComparisonsSkeleton()

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(onClick = onRetry) {
                            Text(text = stringResource(R.string.retry))
                        }
                    }
                }

                recommendations.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.compare_no_comparisons_available),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 48.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        itemsIndexed(
                            recommendations,
                            key = { index, item -> "${item.uid}-$index" }
                        ) { index, item ->
                            CompareComparisonRow(item, alternate = index % 2 == 1, onVideoClick = onVideoClick)
                        }
                    }
                }
            }
        }
    }
}

private const val SKELETON_ROWS = 8

@Composable
private fun skeletonAlpha(): Float {
    val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    return pulse
}

@Composable
private fun skeletonColor(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

@Composable
private fun CompareComparisonsSkeleton() {
    val color = skeletonColor()
    Column(modifier = Modifier.fillMaxWidth().alpha(skeletonAlpha())) {
        repeat(SKELETON_ROWS) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonLabel(color, Alignment.End, Modifier.weight(1f))
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(2f),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(16f / 9f)
                                .background(color, RoundedCornerShape(8.dp))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                SkeletonLabel(color, Alignment.Start, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkeletonLabel(color: Color, align: Alignment.Horizontal, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = align,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val density = LocalDensity.current
        val titleLine = with(density) { MaterialTheme.typography.labelMedium.lineHeight.toDp() }
        val uploaderLine = with(density) { MaterialTheme.typography.labelSmall.lineHeight.toDp() }
        Column(horizontalAlignment = align) {
            listOf(1f, 1f, 0.7f).forEach { SkeletonLine(color, titleLine, it) }
        }
        SkeletonLine(color, uploaderLine, 0.5f)
    }
}

@Composable
private fun SkeletonLine(color: Color, lineHeight: Dp, widthFraction: Float) {
    Box(
        modifier = Modifier.fillMaxWidth(widthFraction).height(lineHeight),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(lineHeight * 0.6f)
                .background(color, RoundedCornerShape(4.dp))
        )
    }
}
