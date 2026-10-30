package org.schabi.newpipe.fragments.detail.compare

import android.graphics.Typeface
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlin.math.abs
import kotlin.math.roundToInt
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.detail.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.fragments.detail.CompareCriterion
import org.schabi.newpipe.fragments.detail.ComparePairSelection
import org.schabi.newpipe.fragments.detail.CompareSideLabel
import org.schabi.newpipe.fragments.detail.CompareUiState
import org.schabi.newpipe.fragments.detail.CompareVideoThumbnailCard
import org.schabi.newpipe.fragments.detail.CriteriaScore
import org.schabi.newpipe.fragments.detail.EXTRA_CRITERIA
import org.schabi.newpipe.fragments.detail.LollipopDescriptionRow
import org.schabi.newpipe.fragments.detail.SCORE_MAX
import org.schabi.newpipe.fragments.detail.SCORE_MIN
import org.schabi.newpipe.fragments.detail.SuggestedVideoThumbnail
import org.schabi.newpipe.fragments.detail.TournesolLoginDialog
import org.schabi.newpipe.fragments.detail.dimensionScore
import org.schabi.newpipe.fragments.detail.tightBorderGlow

private val COMPACT_DIMENSIONS = listOf(
    CompareCriterion(
        id = COMPACT_MAIN_CRITERION_ID,
        labelRes = R.string.compare_criteria_largely_recommended,
        iconRes = R.drawable.logo_small
    )
) + EXTRA_CRITERIA

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun CompareCompactScreen(
    state: CompareUiState,
    onScoreChange: (Int) -> Unit,
    onExtraScoreChange: (String, Int) -> Unit,
    onSubmitCriterion: (String, Int, () -> Unit) -> Unit,
    onSubmitExtrasBatch: (List<CriteriaScore>, () -> Unit) -> Unit,
    onPairSelectionChange: (ComparePairSelection?) -> Unit,
    onDismissRecommendations: () -> Unit,
    onDismissLogin: () -> Unit,
    onShowLogin: () -> Unit,
    onRegister: () -> Unit,
    onLogin: (String, String) -> Unit,
    onNavigateToVideo: ((Int, String, String) -> Unit)? = null,
    onRandomizeLeft: () -> Unit = {},
    onRandomizeRight: () -> Unit = {},
    showGreeting: Boolean = true,
    reserveMiniPlayerSpace: Boolean = true,
    externalScroll: Boolean = false,
    onContentMeasured: ((Int) -> Unit)? = null
) {
    val dimensions = remember { COMPACT_DIMENSIONS }
    var activeIndex by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(dimensions.size) {
        if (activeIndex !in dimensions.indices) {
            activeIndex = 0
        }
    }
    val activeIndexSafe = activeIndex.coerceIn(0, dimensions.lastIndex)
    val activeDimension = dimensions[activeIndexSafe]
    val activeScore = dimensionScore(state, activeDimension)
    val animatedScore by animateFloatAsState(
        targetValue = activeScore.toFloat(),
        animationSpec = tween(durationMillis = 90),
        label = "compactScore"
    )
    val displayScore = animatedScore.roundToInt()
    val activeDescription = stringResource(compactDescriptionRes(activeDimension.id))
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val onSelectionChange: (String, Boolean) -> Unit = { id, selected ->
        selectedIds = if (selected) {
            selectedIds + id
        } else {
            selectedIds - id
        }
        if (!selected) {
            if (id == COMPACT_MAIN_CRITERION_ID) {
                onScoreChange(0)
            } else {
                onExtraScoreChange(id, 0)
            }
        }
    }
    val latestSelectionUpdater by rememberUpdatedState(onSelectionChange)
    val latestActiveDimensionId by rememberUpdatedState(activeDimension.id)
    val maxIndex = dimensions.lastIndex
    val context = LocalContext.current
    val density = LocalDensity.current
    val pxPerScore = with(density) { 4.dp.toPx() } / 2f
    val verticalStepPx = with(density) { 28.dp.toPx() } / 0.6f
    val onActiveScoreChange: (Int) -> Unit = { newValue ->
        val clamped = newValue.coerceIn(SCORE_MIN, SCORE_MAX)
        if (activeDimension.id == COMPACT_MAIN_CRITERION_ID) {
            onScoreChange(clamped)
        } else {
            onExtraScoreChange(activeDimension.id, clamped)
        }
    }
    val latestActiveScore by rememberUpdatedState(activeScore)
    val latestActiveIndex by rememberUpdatedState(activeIndexSafe)
    val latestScoreUpdater by rememberUpdatedState(onActiveScoreChange)
    val latestIndexUpdater by rememberUpdatedState { index: Int -> activeIndex = index }
    val latestMaxIndex by rememberUpdatedState(maxIndex)
    val miniPlayerHeight = dimensionResource(R.dimen.mini_player_height)
    val bottomContentPadding = if (reserveMiniPlayerSpace) miniPlayerHeight + 12.dp else 0.dp
    val embedInDetail = !reserveMiniPlayerSpace
    val pickerActiveIndex = remember { mutableIntStateOf(-1) }
    val pickerDragScore = remember { mutableStateOf<Int?>(null) }
    val pickerTouchedCriteria = remember { mutableStateOf(setOf<String>()) }
    var showHistoryOverlay by remember { mutableStateOf(false) }
    var hasOpenedHistoryOverlay by rememberSaveable { mutableStateOf(false) }
    val overlayGridColumns = 1
    var overlayRowsPerPage by rememberSaveable { mutableIntStateOf(4) }
    val overlayPageSize = overlayGridColumns * overlayRowsPerPage
    val leftEntries = remember(state.historyEntries, state.currentEntry) {
        val current = state.currentEntry
        if (current == null) {
            state.historyEntries
        } else {
            listOf(current) + state.historyEntries
        }
    }
    val rightEntries = state.historyEntries
    var leftHistoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var rightHistoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var initialPairSeeded by rememberSaveable { mutableStateOf(false) }
    var historyOverlayIndex by rememberSaveable { mutableIntStateOf(0) }
    var historyOverlayPage by rememberSaveable { mutableIntStateOf(0) }
    var activeOverlayTarget by remember { mutableStateOf(OverlayTarget.LEFT) }
    val overlayEntries = if (activeOverlayTarget == OverlayTarget.RIGHT) {
        rightEntries
    } else {
        leftEntries
    }
    val selectedHistoryEntryLeft = leftEntries.getOrNull(leftHistoryIndex)
        ?: leftEntries.firstOrNull()
    val selectedHistoryEntryRight = rightEntries.getOrNull(rightHistoryIndex)
        ?: rightEntries.firstOrNull()
    val hasSuggestedLeft = state.suggestedLeft != null
    val hasSuggestedRight = state.suggestedRight != null
    val hasSuggestions = hasSuggestedLeft || hasSuggestedRight
    val suggestedLeftTitle = state.suggestedLeft?.title.orEmpty()
    val suggestedLeftUploader = state.suggestedLeft?.uploader.orEmpty()
    val suggestedRightTitle = state.suggestedRight?.title.orEmpty()
    val suggestedRightUploader = state.suggestedRight?.uploader.orEmpty()
    val currentPairSelection = if (hasSuggestedLeft && hasSuggestedRight) {
        suggestedPair(state.suggestedLeft, state.suggestedRight)
    } else if (selectedHistoryEntryLeft != null && selectedHistoryEntryRight != null) {
        historyPair(
            leftServiceId = selectedHistoryEntryLeft.streamEntity.serviceId,
            leftUrl = selectedHistoryEntryLeft.streamEntity.url,
            rightServiceId = selectedHistoryEntryRight.streamEntity.serviceId,
            rightUrl = selectedHistoryEntryRight.streamEntity.url
        )
    } else {
        null
    }
    val currentEntry = state.currentEntry
    val isLeftCurrent = !hasSuggestedLeft && selectedHistoryEntryLeft != null &&
        currentEntry != null &&
        selectedHistoryEntryLeft.streamEntity.serviceId == currentEntry.streamEntity.serviceId &&
        selectedHistoryEntryLeft.streamEntity.url == currentEntry.streamEntity.url
    val isRightCurrent = !hasSuggestedRight && selectedHistoryEntryRight != null &&
        currentEntry != null &&
        selectedHistoryEntryRight.streamEntity.serviceId == currentEntry.streamEntity.serviceId &&
        selectedHistoryEntryRight.streamEntity.url == currentEntry.streamEntity.url
    val leftStreamId = selectedHistoryEntryLeft?.streamId
    val rightStreamId = selectedHistoryEntryRight?.streamId
    var lastLeftStreamId by remember { mutableStateOf(leftStreamId) }
    var lastRightStreamId by remember { mutableStateOf(rightStreamId) }
    var overlayGridBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayGridSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayDragPreviewIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(leftEntries, rightEntries) {
        if (leftEntries.isEmpty() && rightEntries.isEmpty()) {
            leftHistoryIndex = 0
            rightHistoryIndex = 0
            historyOverlayIndex = 0
            historyOverlayPage = 0
        } else {
            if (leftEntries.isNotEmpty()) {
                val leftMax = leftEntries.lastIndex
                leftHistoryIndex = leftHistoryIndex.coerceIn(0, leftMax)
            } else {
                leftHistoryIndex = 0
            }
            if (rightEntries.isNotEmpty()) {
                val rightMax = rightEntries.lastIndex
                rightHistoryIndex = rightHistoryIndex.coerceIn(0, rightMax)
            } else {
                rightHistoryIndex = 0
            }
            if (!initialPairSeeded &&
                state.currentEntry == null &&
                !hasSuggestedLeft && !hasSuggestedRight &&
                leftEntries.isNotEmpty() && rightEntries.size >= 2 &&
                leftHistoryIndex == 0 && rightHistoryIndex == 0
            ) {
                rightHistoryIndex = 1
                initialPairSeeded = true
            }
        }
    }
    BackHandler(enabled = showHistoryOverlay) {
        showHistoryOverlay = false
    }
    LaunchedEffect(showHistoryOverlay) {
        if (showHistoryOverlay) {
            hasOpenedHistoryOverlay = true
        } else {
            overlayGridBounds = null
            overlayGridSize = IntSize.Zero
            overlayDragPreviewIndex = null
        }
    }
    LaunchedEffect(leftStreamId, rightStreamId) {
        val hasChanged =
            leftStreamId != lastLeftStreamId || rightStreamId != lastRightStreamId
        if (hasChanged) {
            lastLeftStreamId = leftStreamId
            lastRightStreamId = rightStreamId
            onScoreChange(0)
            COMPACT_DIMENSIONS.forEach { criterion ->
                if (criterion.id != COMPACT_MAIN_CRITERION_ID) {
                    onExtraScoreChange(criterion.id, 0)
                }
            }
            selectedIds = emptySet()
        }
    }
    LaunchedEffect(showHistoryOverlay, activeOverlayTarget, overlayEntries.size, overlayPageSize) {
        if (showHistoryOverlay && overlayEntries.isNotEmpty()) {
            historyOverlayIndex = historyOverlayIndex.coerceIn(0, overlayEntries.lastIndex)
            val maxPage = ((overlayEntries.size - 1) / overlayPageSize).coerceAtLeast(0)
            historyOverlayPage = (historyOverlayIndex / overlayPageSize).coerceIn(0, maxPage)
        }
    }
    LaunchedEffect(currentPairSelection) {
        onPairSelectionChange(currentPairSelection)
    }
    val hasStoredScores = state.storedMainScore != null || state.storedExtraScores.isNotEmpty()
    val hasValidPairSelection = currentPairSelection != null
    val openHistoryOverlay: (OverlayTarget) -> Unit = { target ->
        val targetEntries = if (target == OverlayTarget.RIGHT) {
            rightEntries
        } else {
            leftEntries
        }
        if (targetEntries.isNotEmpty()) {
            activeOverlayTarget = target
            historyOverlayIndex = when (target) {
                OverlayTarget.LEFT -> leftHistoryIndex
                OverlayTarget.RIGHT -> rightHistoryIndex
            }.coerceIn(0, targetEntries.lastIndex)
            historyOverlayPage = historyOverlayIndex / overlayPageSize.coerceAtLeast(1)
            hasOpenedHistoryOverlay = true
            showHistoryOverlay = true
        }
    }

    val gestureModifier = Modifier.pointerInput(Unit) {
        val horizontalSensitivity = 1.2f
        val verticalSensitivity = 1.3f
        var dragAxis: DragAxis? = null
        var accumulatedX = 0f
        var accumulatedY = 0f
        var totalDragX = 0f
        var totalDragY = 0f
        var verticalStepTriggered = false
        var currentIndex = 0
        var currentValue = 0
        var markedSelected = false
        detectDragGestures(
            onDragStart = {
                dragAxis = null
                accumulatedX = 0f
                accumulatedY = 0f
                totalDragX = 0f
                totalDragY = 0f
                verticalStepTriggered = false
                currentIndex = latestActiveIndex
                currentValue = latestActiveScore
                markedSelected = false
            },
            onDragEnd = {
                if (dragAxis == DragAxis.VERTICAL &&
                    !verticalStepTriggered &&
                    abs(totalDragY) > abs(totalDragX) &&
                    abs(totalDragY) > 0f &&
                    abs(totalDragY) < verticalStepPx
                ) {
                    val step = if (totalDragY > 0f) 1 else -1
                    val fallbackIndex = (currentIndex + step).coerceIn(0, latestMaxIndex)
                    if (fallbackIndex != currentIndex) {
                        latestIndexUpdater(fallbackIndex)
                    }
                }
                dragAxis = null
                accumulatedX = 0f
                accumulatedY = 0f
                totalDragX = 0f
                totalDragY = 0f
                verticalStepTriggered = false
            },
            onDragCancel = {
                dragAxis = null
                accumulatedX = 0f
                accumulatedY = 0f
                totalDragX = 0f
                totalDragY = 0f
                verticalStepTriggered = false
            },
            onDrag = { change, dragAmount ->
                totalDragX += dragAmount.x
                totalDragY += dragAmount.y
                if (dragAxis == null) {
                    dragAxis = if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                        DragAxis.HORIZONTAL
                    } else {
                        DragAxis.VERTICAL
                    }
                    if (dragAxis == DragAxis.HORIZONTAL && !markedSelected) {
                        latestSelectionUpdater(latestActiveDimensionId, true)
                        markedSelected = true
                    }
                }
                when (dragAxis) {
                    DragAxis.HORIZONTAL -> {
                        // Horizontal drags belong to the criteria slider — consume them
                        // so the parent pager/scroll never steals them.
                        change.consume()
                        accumulatedX += dragAmount.x * horizontalSensitivity
                        val steps = (accumulatedX / pxPerScore).toInt()
                        if (steps != 0) {
                            currentValue =
                                (currentValue + steps).coerceIn(SCORE_MIN, SCORE_MAX)
                            latestScoreUpdater(currentValue)
                            if (!markedSelected) {
                                latestSelectionUpdater(latestActiveDimensionId, true)
                                markedSelected = true
                            }
                            accumulatedX -= steps * pxPerScore
                        }
                    }

                    DragAxis.VERTICAL -> {
                        accumulatedY += dragAmount.y * verticalSensitivity
                        var consumedStep = false
                        while (abs(accumulatedY) >= verticalStepPx) {
                            val step = if (accumulatedY > 0f) 1 else -1
                            val nextIndex = (currentIndex + step).coerceIn(0, latestMaxIndex)
                            if (nextIndex != currentIndex) {
                                currentIndex = nextIndex
                                latestIndexUpdater(currentIndex)
                                verticalStepTriggered = true
                                consumedStep = true
                            }
                            accumulatedY -= step * verticalStepPx
                        }
                        // Only consume when we actually stepped criteria selection.
                        // Otherwise leave the change unconsumed so the page's
                        // verticalScroll / nested scroll can take over.
                        if (consumedStep) {
                            change.consume()
                        }
                    }

                    else -> { /* dragAxis was assigned above — this branch is unreachable */ }
                }
            }
        )
    }
    val hostView = LocalView.current
    val criteriaTouchLockModifier = Modifier.pointerInteropFilter { motionEvent ->
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                requestDisallowParentIntercept(hostView, true)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                requestDisallowParentIntercept(hostView, false)
            }
        }
        false
    }
    val compactNestedScrollInterop = rememberNestedScrollInteropConnection()
    BoxWithConstraints(
        modifier = Modifier
            .then(if (externalScroll) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize())
            .then(
                if (reserveMiniPlayerSpace) Modifier.navigationBarsPadding() else Modifier
            )
    ) {
        val scale = computeCompareScale(maxWidth.value)
        CompositionLocalProvider(LocalCompareScale provides scale) {
            Box(
                modifier = Modifier
                    .then(if (embedInDetail || externalScroll) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                    .padding(
                        start = 16.dp * scale,
                        end = 16.dp * scale,
                        top = 12.dp * scale
                    )
            ) {
                Column(
                    modifier = Modifier
                        .then(if (embedInDetail || externalScroll) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                        .zIndex(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (externalScroll) {
                                    Modifier
                                        .nestedScroll(compactNestedScrollInterop)
                                        .onGloballyPositioned { coords ->
                                            onContentMeasured?.invoke(coords.size.height)
                                        }
                                } else if (embedInDetail) {
                                    Modifier
                                        .nestedScroll(compactNestedScrollInterop)
                                        .verticalScroll(rememberScrollState())
                                        .onGloballyPositioned { coords ->
                                            onContentMeasured?.invoke(coords.size.height)
                                        }
                                } else {
                                    Modifier
                                        .weight(1f, fill = false)
                                        .nestedScroll(compactNestedScrollInterop)
                                        .verticalScroll(rememberScrollState())
                                }
                            )
                            .padding(bottom = bottomContentPadding),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (showGreeting) {
                            UserGreetingBanner(
                                username = state.username,
                                comparisonCount = state.comparisonCount,
                                weeklyComparisons = state.weeklyComparisons,
                                dailyComparisons = state.dailyComparisons,
                                onLogin = onShowLogin,
                                onRegister = onRegister
                            )
                        }

                        val showLeftContent = hasSuggestedLeft || selectedHistoryEntryLeft != null
                        val showRightContent = hasSuggestedRight || selectedHistoryEntryRight != null
                        if (showLeftContent || showRightContent) {
                            val effectiveLeftTitle = if (hasSuggestedLeft) {
                                suggestedLeftTitle
                            } else {
                                selectedHistoryEntryLeft?.streamEntity?.title.orEmpty()
                            }
                            val effectiveLeftUploader = if (hasSuggestedLeft) {
                                suggestedLeftUploader
                            } else {
                                selectedHistoryEntryLeft?.streamEntity?.uploader.orEmpty()
                            }
                            val effectiveRightTitle = if (hasSuggestedRight) {
                                suggestedRightTitle
                            } else {
                                selectedHistoryEntryRight?.streamEntity?.title.orEmpty()
                            }
                            val effectiveRightUploader = if (hasSuggestedRight) {
                                suggestedRightUploader
                            } else {
                                selectedHistoryEntryRight?.streamEntity?.uploader.orEmpty()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp * scale),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .combinedClickable(
                                            onClick = {
                                                openHistoryOverlay(OverlayTarget.LEFT)
                                            },
                                            onLongClick = {
                                                if (hasSuggestedLeft) {
                                                    val sl = state.suggestedLeft
                                                    val slServiceId = suggestionServiceId(sl)
                                                    if (sl.videoUrl != null && slServiceId != null) {
                                                        onNavigateToVideo?.invoke(
                                                            slServiceId,
                                                            sl.videoUrl,
                                                            sl.title
                                                        )
                                                    }
                                                } else {
                                                    selectedHistoryEntryLeft?.streamEntity?.let {
                                                        onNavigateToVideo?.invoke(it.serviceId, it.url, it.title)
                                                    }
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    CompareSideLabel(
                                        title = effectiveLeftTitle,
                                        uploader = effectiveLeftUploader,
                                        textAlign = TextAlign.End
                                    )
                                }

                                Box(
                                    modifier = Modifier.weight(2f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (showLeftContent) {
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val leftDragScore = pickerDragScore.value
                                                    if (leftDragScore != null && leftDragScore < 0) {
                                                        Image(
                                                            painter = painterResource(R.drawable.ic_arrow_drop_down),
                                                            contentDescription = null,
                                                            colorFilter = ColorFilter.tint(Color(0xFF64B5F6)),
                                                            modifier = Modifier
                                                                .align(Alignment.TopCenter)
                                                                .offset(y = (-18).dp)
                                                                .size(28.dp)
                                                                .zIndex(2f)
                                                        )
                                                    }
                                                    Surface(
                                                        modifier = Modifier
                                                            .combinedClickable(
                                                                onClick = {
                                                                    openHistoryOverlay(OverlayTarget.LEFT)
                                                                },
                                                                onLongClick = {
                                                                    if (hasSuggestedLeft) {
                                                                        val sl = state.suggestedLeft
                                                                        val slServiceId = suggestionServiceId(sl)
                                                                        if (sl.videoUrl != null && slServiceId != null) {
                                                                            onNavigateToVideo?.invoke(
                                                                                slServiceId,
                                                                                sl.videoUrl,
                                                                                sl.title
                                                                            )
                                                                        }
                                                                    } else {
                                                                        selectedHistoryEntryLeft?.streamEntity?.let {
                                                                            onNavigateToVideo?.invoke(it.serviceId, it.url, it.title)
                                                                        }
                                                                    }
                                                                }
                                                            ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = BorderStroke(2.dp, Color(0xFF42A5F5)),
                                                        color = MaterialTheme.colorScheme.surface
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(2.dp)
                                                        ) {
                                                            if (hasSuggestedLeft) {
                                                                SuggestedVideoThumbnail(
                                                                    thumbnailUrl = state.suggestedLeft.thumbnailUrl,
                                                                    thumbnailHeight = 52.dp * scale
                                                                )
                                                            } else {
                                                                CompareVideoThumbnailCard(
                                                                    entry = selectedHistoryEntryLeft!!,
                                                                    thumbnailHeight = 52.dp * scale,
                                                                    showMeta = false,
                                                                    contentScale = ContentScale.Fit
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (isLeftCurrent) {
                                                        Text(
                                                            text = "playing..",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFF42A5F5),
                                                            modifier = Modifier
                                                                .align(Alignment.BottomCenter)
                                                                .offset(y = 10.dp)
                                                                .height(0.dp)
                                                                .wrapContentHeight(unbounded = true)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }

                                            if (showRightContent) {
                                                Box(
                                                    modifier = Modifier.weight(1f),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val rightDragScore = pickerDragScore.value
                                                    if (rightDragScore != null && rightDragScore > 0) {
                                                        Image(
                                                            painter = painterResource(R.drawable.ic_arrow_drop_down),
                                                            contentDescription = null,
                                                            colorFilter = ColorFilter.tint(Color(0xFFE57373)),
                                                            modifier = Modifier
                                                                .align(Alignment.TopCenter)
                                                                .offset(y = (-18).dp)
                                                                .size(28.dp)
                                                                .zIndex(2f)
                                                        )
                                                    }
                                                    Surface(
                                                        modifier = Modifier
                                                            .combinedClickable(
                                                                onClick = {
                                                                    openHistoryOverlay(OverlayTarget.RIGHT)
                                                                },
                                                                onLongClick = {
                                                                    if (hasSuggestedRight) {
                                                                        val sr = state.suggestedRight
                                                                        val srServiceId = suggestionServiceId(sr)
                                                                        if (sr.videoUrl != null && srServiceId != null) {
                                                                            onNavigateToVideo?.invoke(
                                                                                srServiceId,
                                                                                sr.videoUrl,
                                                                                sr.title
                                                                            )
                                                                        }
                                                                    } else {
                                                                        selectedHistoryEntryRight?.streamEntity?.let {
                                                                            onNavigateToVideo?.invoke(it.serviceId, it.url, it.title)
                                                                        }
                                                                    }
                                                                }
                                                            ),
                                                        shape = RoundedCornerShape(6.dp),
                                                        border = BorderStroke(2.dp, Color(0xFFE57373)),
                                                        color = MaterialTheme.colorScheme.surface
                                                    ) {
                                                        Box(
                                                            modifier = Modifier.padding(2.dp)
                                                        ) {
                                                            if (hasSuggestedRight) {
                                                                SuggestedVideoThumbnail(
                                                                    thumbnailUrl = state.suggestedRight.thumbnailUrl,
                                                                    thumbnailHeight = 52.dp * scale
                                                                )
                                                            } else {
                                                                CompareVideoThumbnailCard(
                                                                    entry = selectedHistoryEntryRight!!,
                                                                    thumbnailHeight = 52.dp * scale,
                                                                    showMeta = false,
                                                                    contentScale = ContentScale.Fit
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (isRightCurrent) {
                                                        Text(
                                                            text = "playing..",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = Color(0xFFE57373),
                                                            modifier = Modifier
                                                                .align(Alignment.BottomCenter)
                                                                .offset(y = 10.dp)
                                                                .height(0.dp)
                                                                .wrapContentHeight(unbounded = true)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .background(colorResource(R.color.tournesol_chip_bg_selected), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "VS",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = Color(0xFF1B1B1B)
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .combinedClickable(
                                            onClick = {
                                                openHistoryOverlay(OverlayTarget.RIGHT)
                                            },
                                            onLongClick = {
                                                if (hasSuggestedRight) {
                                                    val sr = state.suggestedRight
                                                    val srServiceId = suggestionServiceId(sr)
                                                    if (sr.videoUrl != null && srServiceId != null) {
                                                        onNavigateToVideo?.invoke(
                                                            srServiceId,
                                                            sr.videoUrl,
                                                            sr.title
                                                        )
                                                    }
                                                } else {
                                                    selectedHistoryEntryRight?.streamEntity?.let {
                                                        onNavigateToVideo?.invoke(it.serviceId, it.url, it.title)
                                                    }
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    CompareSideLabel(
                                        title = effectiveRightTitle,
                                        uploader = effectiveRightUploader,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            }
                        }

                        val horizontalPadding = 16.dp * scale
                        Box(
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    val extraPx = (horizontalPadding * 2)
                                        .roundToPx()
                                    val expandedWidth = constraints.maxWidth + extraPx
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = expandedWidth,
                                            maxWidth = expandedWidth
                                        )
                                    )
                                    layout(constraints.maxWidth, placeable.height) {
                                        placeable.place(-horizontalPadding.roundToPx(), 0)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF181818))
                            ) {
                                LollipopPickerWithIntro(
                                    pairKey = currentPairSelection,
                                    scores = state.extraScores,
                                    onScoreChange = onExtraScoreChange,
                                    mainScore = state.score,
                                    onMainScoreChange = onScoreChange,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = horizontalPadding),
                                    hoistedActiveIndex = if (embedInDetail) pickerActiveIndex else null,
                                    hoistedDragScore = pickerDragScore,
                                    hoistedTouchedCriteria = pickerTouchedCriteria,
                                    showDescription = !embedInDetail,
                                    onSubmit1Request = onSubmitCriterion,
                                    onSubmitExtrasBatch = onSubmitExtrasBatch
                                )
                            }
                        }

                        // Status line: "{text A} {text B}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentExtra = (pickerActiveIndex.intValue + 1).coerceIn(0, 9)
                            val statusAlpha = 0.55f
                            val isDragging = pickerDragScore.value != null
                            if (isDragging) {
                                val cancelText = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.White.copy(alpha = statusAlpha))) {
                                        append("Slide up to ")
                                    }
                                    withStyle(SpanStyle(color = Color(0xFFE57373))) {
                                        append("CANCEL")
                                    }
                                    withStyle(SpanStyle(color = Color.White.copy(alpha = statusAlpha))) {
                                        append(".")
                                    }
                                }
                                Text(text = cancelText, fontSize = 11.sp)
                            } else {
                                // Text A — main criterion
                                val textA = when {
                                    state.submitInProgress && !state.submitted ->
                                        "Submitting main"

                                    state.submitted -> "Submitted main"

                                    else -> "Please rate"
                                }
                                Text(
                                    text = "$textA ",
                                    color = Color.White.copy(alpha = statusAlpha),
                                    fontSize = 11.sp
                                )
                                Image(
                                    painter = painterResource(R.drawable.logo_small),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                // Text B — extra criteria
                                val textB = when {
                                    state.submitMoreInProgress -> " - Submitting extra.."
                                    state.extraSubmitted -> " - Submitted extra"
                                    else -> " - Rating extra $currentExtra/9"
                                }
                                Text(
                                    text = textB,
                                    color = Color.White.copy(alpha = statusAlpha),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val diceEnabled = !state.suggestionsLoading
                            val randomizeLeftFallback: () -> Unit = {
                                if (hasSuggestedLeft || hasSuggestedRight) {
                                    onRandomizeLeft()
                                } else if (leftEntries.size >= 2) {
                                    val excluded = rightHistoryIndex
                                        .takeIf { rightEntries.isNotEmpty() }
                                        ?.let { rightEntries.getOrNull(it)?.streamId }
                                    val candidates = leftEntries.indices.filter { idx ->
                                        idx != leftHistoryIndex &&
                                            (excluded == null || leftEntries[idx].streamId != excluded)
                                    }
                                    val pool = if (candidates.isNotEmpty()) {
                                        candidates
                                    } else {
                                        leftEntries.indices.filter { it != leftHistoryIndex }
                                    }
                                    if (pool.isNotEmpty()) {
                                        leftHistoryIndex = pool[(Math.random() * pool.size).toInt()]
                                    }
                                }
                            }
                            val randomizeRightFallback: () -> Unit = {
                                if (hasSuggestedLeft || hasSuggestedRight) {
                                    onRandomizeRight()
                                } else if (rightEntries.size >= 2) {
                                    val excluded = leftHistoryIndex
                                        .takeIf { leftEntries.isNotEmpty() }
                                        ?.let { leftEntries.getOrNull(it)?.streamId }
                                    val candidates = rightEntries.indices.filter { idx ->
                                        idx != rightHistoryIndex &&
                                            (excluded == null || rightEntries[idx].streamId != excluded)
                                    }
                                    val pool = if (candidates.isNotEmpty()) {
                                        candidates
                                    } else {
                                        rightEntries.indices.filter { it != rightHistoryIndex }
                                    }
                                    if (pool.isNotEmpty()) {
                                        rightHistoryIndex = pool[(Math.random() * pool.size).toInt()]
                                    }
                                }
                            }
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(enabled = diceEnabled) { randomizeLeftFallback() },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF42A5F5).copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_casino),
                                        contentDescription = stringResource(R.string.compare_randomize_left),
                                        colorFilter = ColorFilter.tint(Color(0xFF42A5F5)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable(enabled = diceEnabled) { randomizeRightFallback() },
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE57373).copy(alpha = 0.15f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_casino),
                                        contentDescription = stringResource(R.string.compare_randomize_right),
                                        colorFilter = ColorFilter.tint(Color(0xFFE57373)),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasOpenedHistoryOverlay) {
                    val accentColor = if (activeOverlayTarget == OverlayTarget.RIGHT) {
                        Color(0xFFE57373)
                    } else {
                        Color(0xFF42A5F5)
                    }
                    val overlayBackground = Color(0xFF0B0B0B)
                    val overlayRowSpacing = 6.dp
                    val overlayColumnSpacing = 6.dp
                    val overlayCardHeight = 54.dp
                    val overlayRowSpacingPx = with(density) { overlayRowSpacing.roundToPx() }
                    val overlayColumnSpacingPx = with(density) { overlayColumnSpacing.roundToPx() }
                    val overlayCardHeightPx = with(density) { overlayCardHeight.roundToPx() }
                    val overlayBody: @Composable () -> Unit = {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                        ) {
                            val visibleRowsPerPage = if (overlayGridBounds == null) {
                                overlayRowsPerPage.coerceAtLeast(1).coerceAtMost(16)
                            } else {
                                val gridViewportHeightPx = overlayGridBounds!!.height
                                    .roundToInt()
                                    .coerceAtLeast(0)
                                (
                                    (gridViewportHeightPx + overlayRowSpacingPx) /
                                        (overlayCardHeightPx + overlayRowSpacingPx)
                                    )
                                    .coerceAtLeast(1)
                                    .coerceAtMost(16)
                            }
                            LaunchedEffect(visibleRowsPerPage) {
                                if (overlayRowsPerPage != visibleRowsPerPage) {
                                    overlayRowsPerPage = visibleRowsPerPage
                                }
                            }
                            val visiblePageSize = overlayGridColumns * visibleRowsPerPage
                            val pageCount = ((overlayEntries.size + visiblePageSize - 1) / visiblePageSize)
                                .coerceAtLeast(1)
                            val currentPage = (historyOverlayIndex / visiblePageSize)
                                .coerceIn(0, pageCount - 1)
                            LaunchedEffect(currentPage) {
                                if (historyOverlayPage != currentPage) {
                                    historyOverlayPage = currentPage
                                }
                            }
                            val pageStart = currentPage * visiblePageSize
                            val pageEnd = kotlin.math.min(pageStart + visiblePageSize, overlayEntries.size)
                            val pageEntries = overlayEntries.subList(pageStart, pageEnd)
                            val selectedAbsoluteIndex = (overlayDragPreviewIndex ?: historyOverlayIndex)
                                .coerceIn(pageStart, (pageEnd - 1).coerceAtLeast(pageStart))
                            val selectedOffsetInPage = (selectedAbsoluteIndex - pageStart)
                                .coerceIn(0, (pageEntries.size - 1).coerceAtLeast(0))
                            val pageRows = ((pageEntries.size + overlayGridColumns - 1) / overlayGridColumns)
                                .coerceAtLeast(1)
                            LaunchedEffect(
                                showHistoryOverlay,
                                activeOverlayTarget,
                                currentPage,
                                visiblePageSize,
                                overlayEntries.size
                            ) {
                                if (!showHistoryOverlay || overlayEntries.isEmpty()) {
                                    return@LaunchedEffect
                                }

                                val pagesToPrefetch = LinkedHashSet<Int>().apply {
                                    add(currentPage)
                                    if (currentPage < pageCount - 1) {
                                        add(currentPage + 1)
                                    }
                                }
                                val urlsToPrefetch = LinkedHashSet<String>()
                                pagesToPrefetch.forEach { page ->
                                    val start = page * visiblePageSize
                                    val end = kotlin.math.min(start + visiblePageSize, overlayEntries.size)
                                    for (index in start until end) {
                                        val url = overlayEntries[index].streamEntity.thumbnailUrl
                                        if (!url.isNullOrBlank()) {
                                            urlsToPrefetch.add(url)
                                        }
                                    }
                                }

                                val imageLoader = context.imageLoader
                                urlsToPrefetch.forEach { url ->
                                    imageLoader.enqueue(
                                        ImageRequest.Builder(context)
                                            .data(url)
                                            .memoryCacheKey(url)
                                            .diskCacheKey(url)
                                            .build()
                                    )
                                }
                            }
                            LaunchedEffect(pageStart, pageEnd) {
                                overlayDragPreviewIndex = null
                            }
                            val hitTestOverlayIndex: (Offset) -> Int? = { localPosition ->
                                val gridWidthPx = overlayGridSize.width.toFloat()
                                val gridHeightPx = overlayGridSize.height.toFloat()
                                if (gridWidthPx <= 0f ||
                                    gridHeightPx <= 0f ||
                                    localPosition.x < 0f ||
                                    localPosition.y < 0f ||
                                    localPosition.x > gridWidthPx ||
                                    localPosition.y > gridHeightPx
                                ) {
                                    null
                                } else {
                                    val rowStridePx = overlayCardHeightPx + overlayRowSpacingPx
                                    val row = (localPosition.y / rowStridePx).toInt()
                                    val yInRow = localPosition.y - row * rowStridePx
                                    if (yInRow > overlayCardHeightPx) {
                                        null
                                    } else {
                                        val cardWidthPx = (
                                            gridWidthPx - overlayColumnSpacingPx * (overlayGridColumns - 1)
                                            ) / overlayGridColumns
                                        if (cardWidthPx <= 0f) {
                                            null
                                        } else {
                                            val colStridePx = cardWidthPx + overlayColumnSpacingPx
                                            val col = (localPosition.x / colStridePx).toInt()
                                            if (col < 0 || col >= overlayGridColumns) {
                                                null
                                            } else {
                                                val xInCol = localPosition.x - col * colStridePx
                                                if (xInCol > cardWidthPx) {
                                                    null
                                                } else {
                                                    val offsetInPage = row * overlayGridColumns + col
                                                    if (offsetInPage < 0 || offsetInPage >= pageEntries.size) {
                                                        null
                                                    } else {
                                                        pageStart + offsetInPage
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding()
                                    .padding(horizontal = 6.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = accentColor.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.5f)),
                                        modifier = Modifier.align(Alignment.Center)
                                    ) {
                                        Text(
                                            text = "${currentPage + 1}/$pageCount",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = Color(0xFFEDEDED),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    Image(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.close),
                                        colorFilter = ColorFilter.tint(Color(0xFFEDEDED)),
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(22.dp)
                                            .clickable { showHistoryOverlay = false }
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .onGloballyPositioned { coordinates ->
                                            if (showHistoryOverlay) {
                                                val bounds = coordinates.boundsInRoot()
                                                overlayGridBounds = bounds
                                                overlayGridSize = coordinates.size
                                            }
                                        }
                                        .pointerInput(
                                            showHistoryOverlay,
                                            pageStart,
                                            pageEnd,
                                            pageEntries.size,
                                            overlayGridSize,
                                            overlayGridColumns,
                                            overlayCardHeightPx,
                                            overlayRowSpacingPx,
                                            overlayColumnSpacingPx
                                        ) {
                                            if (!showHistoryOverlay) {
                                                return@pointerInput
                                            }
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var pointerId = down.id
                                                var releasedOnIndex: Int? = null
                                                var previewIndex = hitTestOverlayIndex(down.position)
                                                overlayDragPreviewIndex = previewIndex
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes
                                                        .firstOrNull { it.id == pointerId }
                                                        ?: break
                                                    pointerId = change.id
                                                    val hoveredIndex = hitTestOverlayIndex(change.position)
                                                    val insideGrid = change.position.x >= 0f &&
                                                        change.position.y >= 0f &&
                                                        change.position.x <= overlayGridSize.width.toFloat() &&
                                                        change.position.y <= overlayGridSize.height.toFloat()
                                                    previewIndex = when {
                                                        hoveredIndex != null -> hoveredIndex
                                                        insideGrid -> previewIndex
                                                        else -> null
                                                    }
                                                    overlayDragPreviewIndex = previewIndex
                                                    if (!change.pressed) {
                                                        releasedOnIndex = hoveredIndex
                                                        break
                                                    }
                                                }
                                                overlayDragPreviewIndex = null
                                                if (releasedOnIndex != null) {
                                                    val absoluteIndex = releasedOnIndex
                                                    when (activeOverlayTarget) {
                                                        OverlayTarget.LEFT -> leftHistoryIndex = absoluteIndex
                                                        OverlayTarget.RIGHT -> rightHistoryIndex = absoluteIndex
                                                    }
                                                    historyOverlayIndex = absoluteIndex
                                                    showHistoryOverlay = false
                                                }
                                            }
                                        },
                                    verticalArrangement = Arrangement.spacedBy(overlayRowSpacing)
                                ) {
                                    for (row in 0 until pageRows) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(overlayColumnSpacing)
                                        ) {
                                            for (column in 0 until overlayGridColumns) {
                                                val offsetInPage = row * overlayGridColumns + column
                                                if (offsetInPage < pageEntries.size) {
                                                    val absoluteIndex = pageStart + offsetInPage
                                                    CompareHistoryOverlayGridCard(
                                                        entry = pageEntries[offsetInPage],
                                                        selected = offsetInPage == selectedOffsetInPage,
                                                        accentColor = accentColor,
                                                        onClick = {
                                                            when (activeOverlayTarget) {
                                                                OverlayTarget.LEFT -> leftHistoryIndex = absoluteIndex
                                                                OverlayTarget.RIGHT -> rightHistoryIndex = absoluteIndex
                                                            }
                                                            historyOverlayIndex = absoluteIndex
                                                            showHistoryOverlay = false
                                                        },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(overlayCardHeight)
                                                    )
                                                } else {
                                                    Spacer(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(overlayCardHeight)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (pageCount > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val targetPage = (currentPage - 1).coerceAtLeast(0)
                                                val targetStart = targetPage * visiblePageSize
                                                val targetPageCount = kotlin.math.min(
                                                    visiblePageSize,
                                                    overlayEntries.size - targetStart
                                                ).coerceAtLeast(1)
                                                val targetOffset = selectedOffsetInPage.coerceIn(
                                                    0,
                                                    targetPageCount - 1
                                                )
                                                historyOverlayPage = targetPage
                                                historyOverlayIndex = targetStart + targetOffset
                                            },
                                            enabled = currentPage > 0,
                                            contentPadding = PaddingValues(
                                                horizontal = 14.dp,
                                                vertical = 4.dp
                                            )
                                        ) {
                                            Text("<<")
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        OutlinedButton(
                                            onClick = {
                                                val targetPage = (currentPage + 1).coerceAtMost(pageCount - 1)
                                                val targetStart = targetPage * visiblePageSize
                                                val targetPageCount = kotlin.math.min(
                                                    visiblePageSize,
                                                    overlayEntries.size - targetStart
                                                ).coerceAtLeast(1)
                                                val targetOffset = selectedOffsetInPage.coerceIn(
                                                    0,
                                                    targetPageCount - 1
                                                )
                                                historyOverlayPage = targetPage
                                                historyOverlayIndex = targetStart + targetOffset
                                            },
                                            enabled = currentPage < pageCount - 1,
                                            contentPadding = PaddingValues(
                                                horizontal = 14.dp,
                                                vertical = 4.dp
                                            )
                                        ) {
                                            Text(">>")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (state.compactPopupVisible) {
                        val overlayModifier = if (showHistoryOverlay) {
                            Modifier
                                .fillMaxSize()
                                .background(overlayBackground)
                                .zIndex(4f)
                        } else {
                            Modifier
                                .size(1.dp)
                                .graphicsLayer { alpha = 0f }
                                .zIndex(-1f)
                        }
                        Box(
                            modifier = overlayModifier,
                            contentAlignment = Alignment.TopCenter
                        ) {
                            overlayBody()
                        }
                    } else if (showHistoryOverlay) {
                        Dialog(
                            onDismissRequest = { showHistoryOverlay = false },
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = false
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(overlayBackground)
                                    .navigationBarsPadding(),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                overlayBody()
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showRecommendationsDialog) {
        CompareComparisonsFullScreen(
            recommendations = state.recommendations,
            totalCount = state.recommendationsTotalCount,
            isLoading = state.recommendationsLoading,
            errorMessage = state.recommendationsError,
            onDismiss = onDismissRecommendations
        )
    }

    if (state.showLoginDialog) {
        TournesolLoginDialog(
            inProgress = state.loginInProgress,
            errorMessage = state.loginError,
            onDismiss = onDismissLogin,
            onRegister = onRegister,
            onLogin = onLogin
        )
    }
}
