package org.schabi.newpipe.tournesol.ui

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import kotlin.random.Random
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareRepository
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.suggestionServiceId

private val COMPACT_DIMENSIONS = listOf(
    CompareCriterion(
        id = COMPACT_MAIN_CRITERION_ID,
        labelRes = R.string.compare_criteria_largely_recommended,
        iconRes = R.drawable.logo_small
    )
) + EXTRA_CRITERIA

@Composable
private fun HistoryStepButtons(
    tint: Color,
    canStepUp: Boolean,
    canStepDown: Boolean,
    playing: Boolean,
    onStepUp: () -> Unit,
    onStepDown: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        Column {
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = canStepUp, onClick = onStepUp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.compare_history_previous),
                    tint = tint.copy(alpha = if (canStepUp) 0.85f else 0.25f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 32.dp, height = 40.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(enabled = canStepDown, onClick = onStepDown),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.compare_history_next),
                    tint = tint.copy(alpha = if (canStepDown) 0.85f else 0.25f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        if (playing) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(R.string.compare_playing),
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun WithScores(
    state: CompareUiState,
    content: @Composable (scores: Map<String, Int>, mainScore: Int) -> Unit
) {
    content(state.extraScores, state.score)
}

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
    onRetryPair: () -> Unit = {},
    onRetryRecommendations: () -> Unit = {},
    showGreeting: Boolean = true,
    reserveMiniPlayerSpace: Boolean = true,
    externalScroll: Boolean = false,
    onContentMeasured: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val miniPlayerHeight = dimensionResource(R.dimen.mini_player_height)
    val bottomContentPadding = if (reserveMiniPlayerSpace) miniPlayerHeight + 12.dp else 0.dp
    val embedInDetail = !reserveMiniPlayerSpace
    val pickerDragScore = remember { mutableStateOf<Int?>(null) }
    val isDragging by remember { derivedStateOf { pickerDragScore.value != null } }
    val isDraggingLeft by remember { derivedStateOf { (pickerDragScore.value ?: 0) < 0 } }
    val isDraggingRight by remember { derivedStateOf { (pickerDragScore.value ?: 0) > 0 } }
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
    val historyPicker = remember { CompareHistoryPicker(Random.Default) }
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
    val suggestedLeft = state.suggestedLeft
    val suggestedRight = state.suggestedRight
    val hasSuggestedLeft = suggestedLeft != null
    val hasSuggestedRight = suggestedRight != null
    val leftAvoid = selectedHistoryEntryRight.takeIf { !hasSuggestedRight }
    val rightAvoid = selectedHistoryEntryLeft.takeIf { !hasSuggestedLeft }
    val suggestedLeftTitle = suggestedLeft?.title.orEmpty()
    val suggestedLeftUploader = suggestedLeft?.uploader.orEmpty()
    val suggestedRightTitle = suggestedRight?.title.orEmpty()
    val suggestedRightUploader = suggestedRight?.uploader.orEmpty()
    val currentPairSelection = comparePairSelection(
        suggestedLeft,
        suggestedRight,
        selectedHistoryEntryLeft,
        selectedHistoryEntryRight
    )
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
            initialPairSeeded = false
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
    LaunchedEffect(selectedHistoryEntryLeft, selectedHistoryEntryRight, hasSuggestedLeft, hasSuggestedRight) {
        if (!hasSuggestedLeft && !hasSuggestedRight &&
            isSameVideo(selectedHistoryEntryLeft, selectedHistoryEntryRight)
        ) {
            distinctHistoryIndex(rightEntries, rightHistoryIndex, selectedHistoryEntryLeft)
                ?.let { rightHistoryIndex = it }
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
    val screenStatus = compareScreenStatus(state, currentPairSelection, CompareRepository::buildTournesolUid)
    LaunchedEffect(screenStatus) {
        if (screenStatus != CompareScreenStatus.Ready) {
            pickerDragScore.value = null
        }
    }
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

    val compactNestedScrollInterop = rememberNestedScrollInteropConnection()
    BoxWithConstraints(
        modifier = Modifier
            .then(if (externalScroll) Modifier.fillMaxWidth().wrapContentHeight() else Modifier.fillMaxSize())
            .then(
                if (reserveMiniPlayerSpace) Modifier.navigationBarsPadding() else Modifier
            )
    ) {
        val scale = computeCompareScale(maxWidth.value)
        val windowHeight = with(density) { LocalWindowInfo.current.containerSize.height.toDp() }
        val wideLayout = isCompareWideLayout(maxWidth.value, windowHeight.value)
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
                        val greetingSection: @Composable () -> Unit = {
                            if (showGreeting || screenStatus == CompareScreenStatus.NeedsLogin) {
                                UserGreetingBanner(
                                    username = state.username,
                                    comparisonCount = state.comparisonCount,
                                    weeklyComparisons = state.weeklyComparisons,
                                    dailyComparisons = state.dailyComparisons,
                                    onLogin = onShowLogin,
                                    onRegister = onRegister
                                )
                            }
                        }
                        val pairSection: @Composable () -> Unit = {
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
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp * scale)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (showLeftContent) {
                                                    HistoryStepButtons(
                                                        tint = Color(0xFF42A5F5),
                                                        canStepUp = !hasSuggestedLeft &&
                                                            stepHistoryIndex(leftEntries, leftHistoryIndex, -1, leftAvoid) != null,
                                                        canStepDown = !hasSuggestedLeft &&
                                                            stepHistoryIndex(leftEntries, leftHistoryIndex, 1, leftAvoid) != null,
                                                        playing = isLeftCurrent,
                                                        onStepUp = {
                                                            stepHistoryIndex(leftEntries, leftHistoryIndex, -1, leftAvoid)
                                                                ?.let { leftHistoryIndex = it }
                                                        },
                                                        onStepDown = {
                                                            stepHistoryIndex(leftEntries, leftHistoryIndex, 1, leftAvoid)
                                                                ?.let { leftHistoryIndex = it }
                                                        }
                                                    )
                                                    Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isDraggingLeft) {
                                                            Image(
                                                                painter = painterResource(R.drawable.ic_arrow_drop_down),
                                                                contentDescription = null,
                                                                colorFilter = ColorFilter.tint(Color(0xFF42A5F5)),
                                                                modifier = Modifier
                                                                    .align(Alignment.TopCenter)
                                                                    .offset(y = (-18).dp)
                                                                    .size(28.dp)
                                                                    .zIndex(2f)
                                                            )
                                                        }
                                                        Surface(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .combinedClickable(
                                                                    onClick = {
                                                                        openHistoryOverlay(OverlayTarget.LEFT)
                                                                    },
                                                                    onLongClick = {
                                                                        if (hasSuggestedLeft) {
                                                                            val sl = suggestedLeft
                                                                            val slServiceId = suggestionServiceId(sl)
                                                                            val slUrl = sl.videoUrl
                                                                            if (slUrl != null && slServiceId != null) {
                                                                                onNavigateToVideo?.invoke(
                                                                                    slServiceId,
                                                                                    slUrl,
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
                                                            shape = RoundedCornerShape(10.dp),
                                                            border = BorderStroke(2.dp, Color(0xFF42A5F5)),
                                                            color = MaterialTheme.colorScheme.surface
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(2.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                            ) {
                                                                if (hasSuggestedLeft) {
                                                                    SuggestedVideoThumbnail(
                                                                        thumbnailUrl = suggestedLeft.thumbnailUrl,
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
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }

                                                if (showLeftContent && showRightContent) {
                                                    Text(
                                                        text = stringResource(R.string.compare_vs),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold
                                                        ),
                                                        color = colorResource(R.color.tournesol_chip_bg_selected)
                                                    )
                                                }

                                                if (showRightContent) {
                                                    Box(
                                                        modifier = Modifier.weight(1f),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isDraggingRight) {
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
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .combinedClickable(
                                                                    onClick = {
                                                                        openHistoryOverlay(OverlayTarget.RIGHT)
                                                                    },
                                                                    onLongClick = {
                                                                        if (hasSuggestedRight) {
                                                                            val sr = suggestedRight
                                                                            val srServiceId = suggestionServiceId(sr)
                                                                            val srUrl = sr.videoUrl
                                                                            if (srUrl != null && srServiceId != null) {
                                                                                onNavigateToVideo?.invoke(
                                                                                    srServiceId,
                                                                                    srUrl,
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
                                                            shape = RoundedCornerShape(10.dp),
                                                            border = BorderStroke(2.dp, Color(0xFFE57373)),
                                                            color = MaterialTheme.colorScheme.surface
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(2.dp)
                                                                    .clip(RoundedCornerShape(8.dp))
                                                            ) {
                                                                if (hasSuggestedRight) {
                                                                    SuggestedVideoThumbnail(
                                                                        thumbnailUrl = suggestedRight.thumbnailUrl,
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
                                                    }
                                                    HistoryStepButtons(
                                                        tint = Color(0xFFE57373),
                                                        canStepUp = !hasSuggestedRight &&
                                                            stepHistoryIndex(rightEntries, rightHistoryIndex, -1, rightAvoid) != null,
                                                        canStepDown = !hasSuggestedRight &&
                                                            stepHistoryIndex(rightEntries, rightHistoryIndex, 1, rightAvoid) != null,
                                                        playing = isRightCurrent,
                                                        onStepUp = {
                                                            stepHistoryIndex(rightEntries, rightHistoryIndex, -1, rightAvoid)
                                                                ?.let { rightHistoryIndex = it }
                                                        },
                                                        onStepDown = {
                                                            stepHistoryIndex(rightEntries, rightHistoryIndex, 1, rightAvoid)
                                                                ?.let { rightHistoryIndex = it }
                                                        }
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .combinedClickable(
                                                    onClick = {
                                                        openHistoryOverlay(OverlayTarget.LEFT)
                                                    },
                                                    onLongClick = {
                                                        if (hasSuggestedLeft) {
                                                            val sl = suggestedLeft
                                                            val slServiceId = suggestionServiceId(sl)
                                                            val slUrl = sl.videoUrl
                                                            if (slUrl != null && slServiceId != null) {
                                                                onNavigateToVideo?.invoke(
                                                                    slServiceId,
                                                                    slUrl,
                                                                    sl.title
                                                                )
                                                            }
                                                        } else {
                                                            selectedHistoryEntryLeft?.streamEntity?.let {
                                                                onNavigateToVideo?.invoke(it.serviceId, it.url, it.title)
                                                            }
                                                        }
                                                    }
                                                )
                                        ) {
                                            CompareSideLabel(
                                                title = effectiveLeftTitle,
                                                uploader = effectiveLeftUploader,
                                                textAlign = TextAlign.Start,
                                                fixedTitleLines = 2
                                            )
                                        }
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .combinedClickable(
                                                    onClick = {
                                                        openHistoryOverlay(OverlayTarget.RIGHT)
                                                    },
                                                    onLongClick = {
                                                        if (hasSuggestedRight) {
                                                            val sr = suggestedRight
                                                            val srServiceId = suggestionServiceId(sr)
                                                            val srUrl = sr.videoUrl
                                                            if (srUrl != null && srServiceId != null) {
                                                                onNavigateToVideo?.invoke(
                                                                    srServiceId,
                                                                    srUrl,
                                                                    sr.title
                                                                )
                                                            }
                                                        } else {
                                                            selectedHistoryEntryRight?.streamEntity?.let {
                                                                onNavigateToVideo?.invoke(it.serviceId, it.url, it.title)
                                                            }
                                                        }
                                                    }
                                                )
                                        ) {
                                            CompareSideLabel(
                                                title = effectiveRightTitle,
                                                uploader = effectiveRightUploader,
                                                textAlign = TextAlign.Start,
                                                fixedTitleLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        val ratingSection: @Composable () -> Unit = ratingSection@{
                            if (screenStatus != CompareScreenStatus.Ready) {
                                CompareStatusPanel(status = screenStatus, onRetry = onRetryPair)
                                return@ratingSection
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
                                        .background(colorResource(R.color.tournesol_surface_band))
                                ) {
                                    WithScores(state) { scores, mainScore ->
                                        LollipopPickerWithIntro(
                                            pairKey = currentPairSelection,
                                            scores = scores,
                                            onScoreChange = onExtraScoreChange,
                                            mainScore = mainScore,
                                            onMainScoreChange = onScoreChange,
                                            mainSubmitted = state.submitted,
                                            submitInProgress = state.submitInProgress,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = horizontalPadding),
                                            hoistedDragScore = pickerDragScore,
                                            progress = state.picker,
                                            showDescription = !embedInDetail,
                                            onSubmit1Request = onSubmitCriterion,
                                            onSubmitExtrasBatch = onSubmitExtrasBatch
                                        )
                                    }
                                }
                            }

                            // Status line: "{text A} {text B}"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val touchedExtras = touchedExtraCount(state.picker.touchedCriteria.value)
                                val statusColor = colorResource(R.color.tournesol_content).copy(alpha = 0.55f)
                                if (isDragging) {
                                    val cancelColor = shadedAccent(Color(0xFFE57373))
                                    val cancelParts = stringResource(R.string.compare_slide_up_to_cancel)
                                        .split("%1\$s", limit = 2)
                                    val cancelLabel = stringResource(R.string.compare_cancel)
                                    val cancelText = buildAnnotatedString {
                                        withStyle(SpanStyle(color = statusColor)) {
                                            append(cancelParts.getOrNull(0).orEmpty())
                                        }
                                        withStyle(SpanStyle(color = cancelColor)) {
                                            append(cancelLabel)
                                        }
                                        withStyle(SpanStyle(color = statusColor)) {
                                            append(cancelParts.getOrNull(1).orEmpty())
                                        }
                                    }
                                    Text(text = cancelText, style = MaterialTheme.typography.labelSmall)
                                } else {
                                    // Text A — main criterion
                                    val textA = when {
                                        state.submitInProgress && !state.submitted ->
                                            stringResource(R.string.compare_status_submitting_main)

                                        state.submitted -> stringResource(R.string.compare_status_submitted_main)

                                        else -> stringResource(R.string.compare_status_please_rate)
                                    }
                                    Text(
                                        text = "$textA ",
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Image(
                                        painter = painterResource(R.drawable.logo_small),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    // Text B — extra criteria
                                    val textB = when {
                                        state.extraSubmitted -> stringResource(
                                            R.string.compare_status_submitted_extra,
                                            touchedExtras,
                                            EXTRA_CRITERIA.size
                                        )

                                        else -> stringResource(
                                            R.string.compare_status_rating_extra,
                                            touchedExtras,
                                            EXTRA_CRITERIA.size
                                        )
                                    }
                                    Text(
                                        text = " · $textB",
                                        color = statusColor,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (state.extraSubmitted) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = shadedAccent(Color(0xFF81C784)),
                                            modifier = Modifier.padding(start = 2.dp).size(14.dp)
                                        )
                                    }
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
                                        historyPicker.pickIndex(pool.map { leftEntries[it] })
                                            ?.let { leftHistoryIndex = pool[it] }
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
                                        historyPicker.pickIndex(pool.map { rightEntries[it] })
                                            ?.let { rightHistoryIndex = pool[it] }
                                    }
                                }
                                Surface(
                                    onClick = { randomizeLeftFallback() },
                                    enabled = diceEnabled,
                                    modifier = Modifier.size(width = 56.dp, height = 40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF42A5F5).copy(alpha = if (diceEnabled) 0.15f else 0.08f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_casino),
                                            contentDescription = stringResource(R.string.compare_randomize_left),
                                            colorFilter = ColorFilter.tint(
                                                Color(0xFF42A5F5).copy(alpha = if (diceEnabled) 1f else 0.38f)
                                            ),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Surface(
                                    onClick = { randomizeRightFallback() },
                                    enabled = diceEnabled,
                                    modifier = Modifier.size(width = 56.dp, height = 40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFE57373).copy(alpha = if (diceEnabled) 0.15f else 0.08f)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Image(
                                            painter = painterResource(R.drawable.ic_casino),
                                            contentDescription = stringResource(R.string.compare_randomize_right),
                                            colorFilter = ColorFilter.tint(
                                                Color(0xFFE57373).copy(alpha = if (diceEnabled) 1f else 0.38f)
                                            ),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (wideLayout) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp * scale)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    pairSection()
                                    greetingSection()
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    ratingSection()
                                }
                            }
                        } else {
                            greetingSection()
                            pairSection()
                            ratingSection()
                        }
                    }
                }

                if (hasOpenedHistoryOverlay) {
                    val accentColor = if (activeOverlayTarget == OverlayTarget.RIGHT) {
                        Color(0xFFE57373)
                    } else {
                        Color(0xFF42A5F5)
                    }
                    val overlayAvoid = if (activeOverlayTarget == OverlayTarget.RIGHT) rightAvoid else leftAvoid
                    val overlayBackground = colorResource(R.color.tournesol_overlay_surface)
                    val overlayRowSpacing = 0.dp
                    val overlayColumnSpacing = 6.dp
                    val overlayCardHeight = 62.dp
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
                            val visibleIndices = remember(overlayEntries, overlayAvoid) {
                                visibleHistoryIndices(overlayEntries, overlayAvoid)
                            }
                            val positionOf: (Int) -> Int = { index ->
                                visibleIndices.indexOfFirst { it >= index }
                                    .let { if (it < 0) visibleIndices.lastIndex else it }
                                    .coerceAtLeast(0)
                            }
                            val visiblePageSize = overlayGridColumns * visibleRowsPerPage
                            val pageCount = ((visibleIndices.size + visiblePageSize - 1) / visiblePageSize)
                                .coerceAtLeast(1)
                            val currentPage = (positionOf(historyOverlayIndex) / visiblePageSize)
                                .coerceIn(0, pageCount - 1)
                            LaunchedEffect(currentPage) {
                                if (historyOverlayPage != currentPage) {
                                    historyOverlayPage = currentPage
                                }
                            }
                            val pageStart = currentPage * visiblePageSize
                            val pageEnd = kotlin.math.min(pageStart + visiblePageSize, visibleIndices.size)
                            val pageEntries = visibleIndices.subList(pageStart, pageEnd).map { overlayEntries[it] }
                            val selectedPosition = positionOf(overlayDragPreviewIndex ?: historyOverlayIndex)
                                .coerceIn(pageStart, (pageEnd - 1).coerceAtLeast(pageStart))
                            val selectedOffsetInPage = (selectedPosition - pageStart)
                                .coerceIn(0, (pageEntries.size - 1).coerceAtLeast(0))
                            val pageRows = ((pageEntries.size + overlayGridColumns - 1) / overlayGridColumns)
                                .coerceAtLeast(1)
                            LaunchedEffect(
                                showHistoryOverlay,
                                activeOverlayTarget,
                                currentPage,
                                visiblePageSize,
                                visibleIndices
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
                                    val end = kotlin.math.min(start + visiblePageSize, visibleIndices.size)
                                    for (index in start until end) {
                                        val url = overlayEntries[visibleIndices[index]].streamEntity.thumbnailUrl
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
                                                        visibleIndices[pageStart + offsetInPage]
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
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                            color = colorResource(R.color.tournesol_on_overlay),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                        )
                                    }
                                    Image(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.close),
                                        colorFilter = ColorFilter.tint(colorResource(R.color.tournesol_on_overlay)),
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(50))
                                            .clickable { showHistoryOverlay = false }
                                            .padding(12.dp)
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
                                            overlayColumnSpacingPx,
                                            overlayAvoid
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
                                                    val absoluteIndex = visibleIndices[pageStart + offsetInPage]
                                                    CompareHistoryOverlayGridCard(
                                                        entry = pageEntries[offsetInPage],
                                                        selected = offsetInPage == selectedOffsetInPage,
                                                        accentColor = accentColor,
                                                        alternate = row % 2 == 1,
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
                                                    visibleIndices.size - targetStart
                                                ).coerceAtLeast(1)
                                                val targetOffset = selectedOffsetInPage.coerceIn(
                                                    0,
                                                    targetPageCount - 1
                                                )
                                                historyOverlayPage = targetPage
                                                historyOverlayIndex = visibleIndices[targetStart + targetOffset]
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
                                                    visibleIndices.size - targetStart
                                                ).coerceAtLeast(1)
                                                val targetOffset = selectedOffsetInPage.coerceIn(
                                                    0,
                                                    targetPageCount - 1
                                                )
                                                historyOverlayPage = targetPage
                                                historyOverlayIndex = visibleIndices[targetStart + targetOffset]
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
                    if (showHistoryOverlay) {
                        Dialog(
                            onDismissRequest = { showHistoryOverlay = false },
                            properties = DialogProperties(
                                usePlatformDefaultWidth = false,
                                decorFitsSystemWindows = false,
                                dismissOnBackPress = true,
                                dismissOnClickOutside = false
                            )
                        ) {
                            val overlayView = LocalView.current
                            val lightOverlay = !isSystemInDarkTheme()
                            SideEffect {
                                (overlayView.parent as? DialogWindowProvider)?.window?.let { window ->
                                    WindowCompat.getInsetsController(window, overlayView)
                                        .isAppearanceLightStatusBars = lightOverlay
                                }
                            }
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
        val comparisons = state.recommendations
        CompareComparisonsFullScreen(
            recommendations = (comparisons as? CompareRecommendations.Loaded)?.items.orEmpty(),
            totalCount = comparisons.totalCount,
            isLoading = comparisons == CompareRecommendations.Loading,
            errorMessage = (comparisons as? CompareRecommendations.Failed)?.message?.resolve(),
            onRetry = onRetryRecommendations,
            onDismiss = onDismissRecommendations
        )
    }

    val login = state.login
    if (login != CompareLoginState.Hidden) {
        TournesolLoginDialog(
            inProgress = login == CompareLoginState.Submitting,
            errorMessage = (login as? CompareLoginState.Open)?.error?.resolve(),
            onDismiss = onDismissLogin,
            onRegister = onRegister,
            onLogin = onLogin
        )
    }
}
