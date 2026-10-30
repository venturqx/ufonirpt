package org.schabi.newpipe.fragments.detail.compare

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlinx.coroutines.launch
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.fragments.detail.HistoryCard

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryWheel(
    entries: List<StreamHistoryEntry>,
    selectedIndex: Int,
    historyMessageRes: Int?,
    onSelectIndex: (Int) -> Unit,
    onNavigateToVideo: ((Int, String, String) -> Unit)? = null
) {
    val scale = LocalCompareScale.current
    val itemHeight = 64.dp * scale
    if (entries.isEmpty()) {
        val message = historyMessageRes?.let { stringResource(it) }.orEmpty()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = itemHeight),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
        }
        return
    }

    val density = LocalDensity.current
    val pagerState = rememberPagerState(
        initialPage = selectedIndex.coerceIn(0, entries.lastIndex)
    ) { entries.size }
    val coroutineScope = rememberCoroutineScope()
    val latestSelectedIndex by rememberUpdatedState(selectedIndex)
    var isDragging by remember { mutableStateOf(false) }
    val cameraDistance = with(density) { (22.dp * scale).toPx() }
    val depthShift = with(density) { (18.dp * scale).toPx() }
    val pageSizePx = with(density) { itemHeight.toPx() }
    val overlap = itemHeight * 0.75f
    val pageStepPx = with(density) { (itemHeight - overlap).toPx() }.coerceAtLeast(1f)
    val totalPages = entries.size

    LaunchedEffect(entries, selectedIndex, isDragging) {
        if (isDragging) {
            return@LaunchedEffect
        }
        val targetPage = selectedIndex.coerceIn(0, entries.lastIndex)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState, entries) {
        snapshotFlow { pagerState.currentPage }
            .collect { page ->
                val newIndex = page.coerceIn(0, entries.lastIndex)
                if (newIndex != latestSelectedIndex) {
                    onSelectIndex(newIndex)
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight * 2f)
            .pointerInput(pagerState, pageStepPx, entries.size) {
                val velocityTracker = VelocityTracker()
                var dragState = WheelDragState.start(0, 0f, entries.lastIndex)
                var dragGeneration = 0
                fun settle() {
                    val target = dragState.releaseTarget(
                        velocityTracker.calculateVelocity().y,
                        pageStepPx
                    )
                    val settleGeneration = dragGeneration
                    coroutineScope.launch {
                        try {
                            pagerState.animateScrollToPage(
                                target,
                                animationSpec = tween(
                                    durationMillis = 320,
                                    easing = LinearOutSlowInEasing
                                )
                            )
                        } finally {
                            if (settleGeneration == dragGeneration) {
                                isDragging = false
                            }
                        }
                    }
                }
                try {
                    detectDragGestures(
                        onDragStart = {
                            dragGeneration++
                            isDragging = true
                            velocityTracker.resetTracking()
                            dragState = WheelDragState.start(
                                pagerState.currentPage,
                                pagerState.currentPageOffsetFraction,
                                entries.lastIndex
                            )
                        },
                        onDrag = { change, dragAmount ->
                            change.consumeAllChanges()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragState = dragState.onDrag(
                                dragAmount.y,
                                change.uptimeMillis,
                                pageStepPx
                            )
                            val (targetPage, offset) = dragState.pageTarget
                            pagerState.requestScrollToPage(
                                targetPage,
                                offset
                            )
                        },
                        onDragCancel = { settle() },
                        onDragEnd = { settle() }
                    )
                } finally {
                    isDragging = false
                }
            }
    ) {
        val contentPadding = PaddingValues(vertical = itemHeight / 2)
        VerticalPager(
            state = pagerState,
            contentPadding = contentPadding,
            pageSize = PageSize.Fixed(itemHeight),
            pageSpacing = -overlap,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val layoutInfo = pagerState.layoutInfo
            val pageInfo = layoutInfo.visiblePagesInfo.firstOrNull { it.index == page }
            val rawOffset = if (pageInfo != null && pageStepPx > 0f) {
                val pageCenter = pageInfo.offset + pageSizePx / 2f
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                (pageCenter - viewportCenter) / pageStepPx
            } else {
                (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            }
            val rawOffsetAbs = abs(rawOffset)
            val pageOffset = rawOffsetAbs.coerceIn(0f, 1f)
            val focusSpan = 1.35f
            val visibilitySpan = 1.6f
            val focusOffset = (rawOffsetAbs / focusSpan).coerceIn(0f, 1f)
            val focus = 1f - focusOffset
            val visibility = ((visibilitySpan - rawOffsetAbs) / visibilitySpan)
                .coerceIn(0f, 1f)
            val direction = when {
                rawOffset > 0f -> 1f
                rawOffset < 0f -> -1f
                else -> 0f
            }
            val pageScale = lerp(1f, 0.88f, pageOffset)
            val alpha = lerp(1f, 0.35f, pageOffset)
            val rotationX = lerp(0f, 10f, pageOffset) * -direction
            val translation = depthShift * pageOffset * direction
            val elevation = (10f * (1f - pageOffset)).dp
            val contentAlpha = alpha * visibility
            val rawBackgroundAlpha = (1f - rawOffsetAbs).coerceIn(0f, 1f)
            val computedBackgroundAlpha = rawBackgroundAlpha * rawBackgroundAlpha
            val backgroundAlpha = if (pagerState.currentPage == page &&
                !pagerState.isScrollInProgress
            ) {
                1f
            } else {
                computedBackgroundAlpha
            }
            HistoryCard(
                entry = entries[page],
                focus = focus,
                contentAlpha = contentAlpha,
                backgroundAlpha = backgroundAlpha,
                elevation = elevation,
                onLongClick = {
                    val entity = entries[page].streamEntity
                    onNavigateToVideo?.invoke(
                        entity.serviceId,
                        entity.url,
                        entity.title
                    )
                },
                modifier = Modifier
                    .height(itemHeight)
                    .zIndex(focus)
                    .graphicsLayer(
                        scaleX = pageScale,
                        scaleY = pageScale,
                        rotationX = rotationX,
                        translationY = translation,
                        cameraDistance = cameraDistance
                    )
            )
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = "${pagerState.currentPage + 1}/$totalPages",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
