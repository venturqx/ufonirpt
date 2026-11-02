package org.schabi.newpipe.tournesol.ui

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.view.View
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.tournesol.CriteriaScore
import org.schabi.newpipe.tournesol.SCORE_MAX
import org.schabi.newpipe.tournesol.SCORE_MIN

@Composable
internal fun LollipopPicker(
    scores: Map<String, Int>,
    onScoreChange: (String, Int) -> Unit,
    mainScore: Int,
    onMainScoreChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hoistedActiveIndex: MutableIntState? = null,
    hoistedDragScore: MutableState<Int?>? = null,
    showDescription: Boolean = true
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val dimensions = remember {
        EXTRA_CRITERIA + CompareCriterion(
            id = COMPACT_MAIN_CRITERION_ID,
            labelRes = R.string.compare_criteria_largely_recommended,
            iconRes = R.drawable.logo_small
        )
    }
    val painters = dimensions.map { painterResource(it.iconRes) }
    val colors = dimensions.map { shadedAccent(LOLLIPOP_COLORS[it.id] ?: Color.White) }
    val contentColor = colorResource(R.color.tournesol_content)
    val headFillColor = colorResource(R.color.tournesol_surface_band)
    fun scoreAt(i: Int): Int = if (dimensions[i].id == COMPACT_MAIN_CRITERION_ID) {
        mainScore
    } else {
        scores[dimensions[i].id] ?: 0
    }
    val currentOnScoreChange by rememberUpdatedState(onScoreChange)
    val currentOnMainScoreChange by rememberUpdatedState(onMainScoreChange)
    fun setScoreAt(i: Int, value: Int) {
        if (dimensions[i].id == COMPACT_MAIN_CRITERION_ID) {
            currentOnMainScoreChange(value)
        } else {
            currentOnScoreChange(dimensions[i].id, value)
        }
    }
    val circleRadius = with(density) { 14.dp.toPx() }
    val barWidth = with(density) { 12.dp.toPx() }
    val iconSize = with(density) { 18.dp.toPx() }
    val strokeWidth = with(density) { 3.5.dp.toPx() }
    val textSizePx = with(density) { 10.sp.toPx() }
    val textGap = with(density) { 6.dp.toPx() }
    val topPaddingPx = with(density) { 22.dp.toPx() }
    val bottomPaddingPx = with(density) { 22.dp.toPx() }
    val totalHeight = 160.dp
    val activeIndex = hoistedActiveIndex ?: remember { mutableIntStateOf(-1) }
    val dragScore = hoistedDragScore ?: remember { mutableStateOf<Int?>(null) }
    val pendingTap = remember { mutableStateOf<PendingTap?>(null) }
    val labelPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    labelPaint.textSize = textSizePx

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(totalHeight)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                        val drawTop = topPaddingPx + circleRadius
                        val drawBottom = size.height - bottomPaddingPx - circleRadius
                        val i = slotIndexAt(
                            down.position.x,
                            size.width.toFloat(),
                            dimensions.size
                        )
                        activeIndex.intValue = i

                        // Wait for either touch slop (→ drag) or release (→ tap).
                        val slopChange = awaitTouchSlopOrCancellation(down.id) { c, _ ->
                            c.consume()
                        }
                        if (slopChange != null) {
                            // Drag: clear any pending single-tap so a subsequent
                            // tap on same bar isn't misread as double-tap.
                            pendingTap.value = null
                            val initial = positionToScore(
                                slopChange.position.y,
                                minScoreAt = drawBottom,
                                maxScoreAt = drawTop
                            )
                            setScoreAt(i, initial)
                            dragScore.value = initial
                            drag(slopChange.id) { change ->
                                val s = positionToScore(
                                    change.position.y,
                                    minScoreAt = drawBottom,
                                    maxScoreAt = drawTop
                                )
                                setScoreAt(i, s)
                                dragScore.value = s
                                change.consume()
                            }
                            dragScore.value = null
                        } else {
                            // Tap: handle single/double-tap reset.
                            val releasedAt = currentEvent.changes
                                .firstOrNull { it.id == down.id }
                                ?.uptimeMillis
                                ?: down.uptimeMillis
                            val tap = registerTap(pendingTap.value, i, releasedAt)
                            pendingTap.value = tap.pending
                            if (tap.isDoubleTap) {
                                setScoreAt(i, 0)
                            }
                        }
                    }
                }
        ) {
            val n = dimensions.size
            val w = size.width
            val h = size.height
            val drawTop = topPaddingPx + circleRadius
            val drawBottom = h - bottomPaddingPx - circleRadius
            val zeroY = (drawTop + drawBottom) / 2f
            val slotWidth = w / n

            // Zero line
            drawLine(
                color = contentColor.copy(alpha = 0.25f),
                start = Offset(slotWidth * 0.3f, zeroY),
                end = Offset(w - slotWidth * 0.3f, zeroY),
                strokeWidth = with(density) { 1.dp.toPx() }
            )

            for (i in 0 until n) {
                val score = scoreAt(i)
                val color = colors[i]
                val cx = slotWidth * (i + 0.5f)
                val cy = scoreToPosition(score, minScoreAt = drawBottom, maxScoreAt = drawTop)

                // Bar
                drawLine(
                    color = color,
                    start = Offset(cx, zeroY),
                    end = Offset(cx, cy),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Butt
                )

                drawLollipopHead(
                    center = Offset(cx, cy),
                    color = color,
                    fillColor = headFillColor,
                    score = score,
                    painter = painters[i],
                    dimensionId = dimensions[i].id,
                    selected = i == activeIndex.intValue,
                    circleRadius = circleRadius,
                    strokeWidth = strokeWidth,
                    iconSize = iconSize,
                    textSizePx = textSizePx,
                    textGap = textGap,
                    labelPaint = labelPaint
                )
            }
        }
        if (showDescription) {
            LollipopDescriptionRow(activeIndex = activeIndex.intValue)
        }
    }
}

@Composable
internal fun LollipopPickerWithIntro(
    pairKey: Any?,
    scores: Map<String, Int>,
    onScoreChange: (String, Int) -> Unit,
    mainScore: Int,
    onMainScoreChange: (Int) -> Unit,
    mainSubmitted: Boolean,
    submitInProgress: Boolean,
    modifier: Modifier = Modifier,
    hoistedDragScore: MutableState<Int?>? = null,
    hoistedPhase: MutableIntState? = null,
    progress: ComparePickerProgress = remember { ComparePickerProgress() },
    showDescription: Boolean = true,
    onSubmit1Request: ((String, Int, () -> Unit) -> Unit)? = null,
    onSubmitExtrasBatch: ((List<CriteriaScore>, () -> Unit) -> Unit)? = null
) {
    val phase = hoistedPhase ?: remember { mutableIntStateOf(1) }
    val phase1ActiveIndex = progress.activeIndex
    val touchedCriteria = progress.touchedCriteria
    val allExtrasDone = progress.allExtrasDone
    val submittedCriteria = progress.submittedCriteria
    val mainUnsaved = progress.mainUnsaved
    LaunchedEffect(pairKey) {
        if (progress.pairKey != pairKey) {
            progress.pairKey = pairKey
            phase.intValue = 1
            phase1ActiveIndex.intValue = EXTRA_CRITERIA.size
            touchedCriteria.value = emptySet()
            allExtrasDone.value = false
            submittedCriteria.value = emptySet()
            mainUnsaved.value = false
        }
    }

    if (phase.intValue == 1) {
        LargelyRecommendedSlider(
            mainScore = mainScore,
            onMainScoreChange = onMainScoreChange,
            scores = scores,
            onScoreChange = onScoreChange,
            activeIndex = phase1ActiveIndex,
            touchedCriteria = touchedCriteria,
            allExtrasDone = allExtrasDone,
            onSubmit1 = { criterionId, value, onSaved ->
                mainUnsaved.value = true
                submittedCriteria.value = submittedCriteria.value - criterionId
                onSubmit1Request?.invoke(criterionId, value) {
                    mainUnsaved.value = false
                    submittedCriteria.value = submittedCriteria.value + criterionId
                    onSaved()
                }
            },
            submitInProgress = submitInProgress,
            isMainSaved = mainSubmitted && !mainUnsaved.value,
            onSubmitExtrasBatch = { batch ->
                onSubmitExtrasBatch?.invoke(batch) {
                    submittedCriteria.value =
                        submittedCriteria.value + batch.map { it.criteria }
                }
            },
            submittedCriteria = submittedCriteria,
            hoistedDragScore = hoistedDragScore,
            modifier = modifier
        )
    } else {
        LollipopPicker(
            scores = scores,
            onScoreChange = onScoreChange,
            mainScore = mainScore,
            onMainScoreChange = onMainScoreChange,
            modifier = modifier,
            hoistedActiveIndex = phase1ActiveIndex,
            hoistedDragScore = hoistedDragScore,
            showDescription = showDescription
        )
    }
}

private val SLIDER_BLUE = Color(0xFF4F9EFF)
private val SLIDER_RED = Color(0xFFFF5A5F)
private val SLIDER_SIDE_PADDING = 28.dp

@Composable
private fun LargelyRecommendedSlider(
    mainScore: Int,
    onMainScoreChange: (Int) -> Unit,
    scores: Map<String, Int>,
    onScoreChange: (String, Int) -> Unit,
    activeIndex: MutableIntState,
    touchedCriteria: MutableState<Set<String>>,
    allExtrasDone: MutableState<Boolean>,
    onSubmit1: (String, Int, () -> Unit) -> Unit,
    submitInProgress: Boolean,
    isMainSaved: Boolean,
    onSubmitExtrasBatch: (List<CriteriaScore>) -> Unit,
    submittedCriteria: MutableState<Set<String>>,
    hoistedDragScore: MutableState<Int?>? = null,
    modifier: Modifier = Modifier
) {
    val selectedCriterionId = criterionIdAt(activeIndex.intValue)
    val isMain = selectedCriterionId == COMPACT_MAIN_CRITERION_ID
    val currentScore = criterionScore(selectedCriterionId, mainScore, scores)

    val currentOnMainChange by rememberUpdatedState(onMainScoreChange)
    val currentOnExtraChange by rememberUpdatedState(onScoreChange)
    val currentOnSubmit1 by rememberUpdatedState(onSubmit1)
    val currentSubmitInProgress by rememberUpdatedState(submitInProgress)
    val currentIsMainSaved by rememberUpdatedState(isMainSaved)
    val latestScore by rememberUpdatedState(currentScore)
    val dispatchChange: (Int) -> Unit = { v ->
        if (isMain) currentOnMainChange(v) else currentOnExtraChange(selectedCriterionId, v)
    }

    var showCriteriaDesc by remember { mutableStateOf(false) }
    val isDragging = remember { mutableStateOf(false) }
    val isInCancelZone = remember { mutableStateOf(false) }
    LaunchedEffect(activeIndex.intValue) { showCriteriaDesc = false }

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (allExtrasDone.value) {
            AllCriteriaScoredMessage()
            SubmitExtrasChip(
                onClick = { onSubmitExtrasBatch(touchedExtraScores(touchedCriteria.value, scores)) }
            )
            LollipopHeadsRow(
                scores = scores,
                mainScore = mainScore,
                activeIndex = activeIndex,
                submittedCriteria = submittedCriteria.value,
                touchedCriteria = touchedCriteria.value,
                modifier = Modifier.padding(top = 4.dp),
                onHeadClick = { idx ->
                    allExtrasDone.value = false
                    activeIndex.intValue = idx
                }
            )
        } else {
            val qualifierLine = buildQualifierLine(selectedCriterionId, currentScore)
            val qualifierText = AnnotatedString.fromHtml(stringResource(qualifierLine.textRes))
            CriterionScoreHeader(
                title = stringResource(criterionQuestionRes(selectedCriterionId)),
                score = currentScore
            )
            CriterionTrack(
                score = currentScore,
                line = qualifierLine,
                qualifier = qualifierText.text,
                dragController = SliderDragController(
                    criterionId = selectedCriterionId,
                    latestScore = { latestScore },
                    dispatchChange = dispatchChange,
                    onSubmit1 = { criterionId, value, onSaved -> currentOnSubmit1(criterionId, value, onSaved) },
                    isSubmitting = { currentSubmitInProgress },
                    isMainSaved = { currentIsMainSaved },
                    touchedCriteria = touchedCriteria,
                    activeIndex = activeIndex,
                    allExtrasDone = allExtrasDone,
                    isDragging = isDragging,
                    isInCancelZone = isInCancelZone,
                    dragScore = hoistedDragScore
                )
            )
            QualifierRow(
                line = qualifierLine,
                qualifier = qualifierText,
                showCancel = isDragging.value && isInCancelZone.value,
                descriptionShown = showCriteriaDesc,
                onToggleDescription = { showCriteriaDesc = !showCriteriaDesc }
            )
            LollipopHeadsRow(
                scores = scores,
                mainScore = mainScore,
                activeIndex = activeIndex,
                submittedCriteria = submittedCriteria.value,
                touchedCriteria = touchedCriteria.value,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (showCriteriaDesc) {
            LollipopDescriptionRow(activeIndex = activeIndex.intValue)
        }
    }
}

@Composable
private fun AllCriteriaScoredMessage() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.compare_all_criteria_scored),
            color = colorResource(R.color.tournesol_content).copy(alpha = 0.9f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SubmitExtrasChip(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        FilterChip(
            selected = true,
            onClick = onClick,
            label = { Text(text = stringResource(R.string.compare_submit_extras)) },
            colors = FilterChipDefaults.filterChipColors(),
            shape = RoundedCornerShape(8.dp),
            border = null
        )
    }
}

@Composable
private fun CriterionScoreHeader(title: String, score: Int) {
    val contentColor = colorResource(R.color.tournesol_content)
    val scoreColor = when {
        score < 0 -> shadedAccent(SLIDER_BLUE)
        score > 0 -> shadedAccent(SLIDER_RED)
        else -> contentColor.copy(alpha = 0.55f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            color = contentColor.copy(alpha = 0.75f),
            fontSize = 13.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
        Box(modifier = Modifier.width(48.dp)) {
            Text(
                text = score.toString(),
                color = scoreColor,
                fontSize = 13.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun CriterionTrack(
    score: Int,
    line: QualifierLine,
    qualifier: String,
    dragController: SliderDragController
) {
    val hostView = LocalView.current
    val label = stringResource(criterionLabelRes(dragController.criterionId))
    val state = when {
        line.leftArrow.isNotEmpty() -> stringResource(R.string.compare_score_state_left, score, qualifier)
        line.rightArrow.isNotEmpty() -> stringResource(R.string.compare_score_state_right, score, qualifier)
        else -> stringResource(R.string.compare_score_state_even, score, qualifier)
    }
    val confirmLabel = stringResource(R.string.compare_score_confirm)
    val contentColor = colorResource(R.color.tournesol_content)
    val leftColor = shadedAccent(SLIDER_BLUE)
    val rightColor = shadedAccent(SLIDER_RED)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .sliderDragGesture(hostView, dragController)
            .semantics {
                contentDescription = label
                stateDescription = state
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = score.toFloat(),
                    range = SCORE_MIN.toFloat()..SCORE_MAX.toFloat()
                )
                setProgress { target ->
                    dragController.adjust(target)
                    true
                }
                onClick(label = confirmLabel) {
                    dragController.confirm()
                    true
                }
            }
    ) {
        drawCriterionTrack(score, contentColor, leftColor, rightColor)
    }
}

private fun Modifier.sliderDragGesture(hostView: View, controller: SliderDragController): Modifier = pointerInput(controller.criterionId) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (controller.isSubmitting()) {
            return@awaitEachGesture
        }
        hostView.parent?.requestDisallowInterceptTouchEvent(true)
        val left = SLIDER_SIDE_PADDING.toPx()
        val right = size.width - left
        fun xToScore(x: Float): Int = positionToScore(x, left, right)
        val scoreBeforeDrag = controller.latestScore()
        var finalScore = xToScore(down.position.x)
        val newTouched = controller.start(finalScore)
        drag(down.id) { change ->
            finalScore = xToScore(change.position.x)
            controller.move(finalScore, inCancelZone = change.position.y < 0f)
            change.consume()
        }
        controller.release(scoreBeforeDrag, finalScore, newTouched)
    }
}

private fun DrawScope.drawCriterionTrack(
    score: Int,
    contentColor: Color,
    leftColor: Color,
    rightColor: Color
) {
    val trackHeightPx = 6.dp.toPx()
    val thumbWidthPx = 4.dp.toPx()
    val thumbHeightPx = 22.dp.toPx()
    val left = SLIDER_SIDE_PADDING.toPx()
    val right = size.width - left
    val centerX = (left + right) / 2f
    val cy = size.height / 2f
    val trackTop = cy - trackHeightPx / 2f
    val trackRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

    // Background track
    drawRoundRect(
        color = contentColor.copy(alpha = 0.10f),
        topLeft = Offset(left, trackTop),
        size = Size(right - left, trackHeightPx),
        cornerRadius = trackRadius
    )

    // Fill from center toward thumb, stopping short to leave a gap
    val thumbX = scoreToPosition(score, minScoreAt = left, maxScoreAt = right)
    trackFill(score, thumbX, centerX, thumbGap = 5.dp.toPx())?.let { fill ->
        clipRect(
            left = fill.left,
            top = trackTop,
            right = fill.right,
            bottom = trackTop + trackHeightPx
        ) {
            drawRoundRect(
                color = if (score < 0) leftColor else rightColor,
                topLeft = Offset(left, trackTop),
                size = Size(right - left, trackHeightPx),
                cornerRadius = trackRadius
            )
        }
    }

    // Thumb: tall pill
    drawRoundRect(
        color = contentColor,
        topLeft = Offset(thumbX - thumbWidthPx / 2f, cy - thumbHeightPx / 2f),
        size = Size(thumbWidthPx, thumbHeightPx),
        cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
    )
}

@Composable
private fun QualifierRow(
    line: QualifierLine,
    qualifier: AnnotatedString,
    showCancel: Boolean,
    descriptionShown: Boolean,
    onToggleDescription: () -> Unit
) {
    val contentColor = colorResource(R.color.tournesol_content)
    val leftColor = shadedAccent(SLIDER_BLUE)
    val rightColor = shadedAccent(SLIDER_RED)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showCancel) {
            Text(
                text = stringResource(R.string.compare_cancel),
                color = rightColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = line.leftArrow,
                color = leftColor,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = qualifier,
                    color = contentColor.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "ⓘ",
                    color = contentColor.copy(alpha = if (descriptionShown) 0.85f else 0.45f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleDescription)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = line.rightArrow,
                color = rightColor,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@StringRes
private fun criterionQuestionRes(criterionId: String): Int = when (criterionId) {
    "reliability" -> R.string.compare_question_reliability
    "pedagogy" -> R.string.compare_question_pedagogy
    "importance" -> R.string.compare_question_importance
    "layman_friendly" -> R.string.compare_question_layman_friendly
    "entertaining_relaxing" -> R.string.compare_question_entertaining_relaxing
    "engaging" -> R.string.compare_question_engaging
    "diversity_inclusion" -> R.string.compare_question_diversity_inclusion
    "better_habits" -> R.string.compare_question_better_habits
    "backfire_risk" -> R.string.compare_question_backfire_risk
    else -> R.string.compare_question_largely_recommended
}

private fun DrawScope.drawLollipopHead(
    center: Offset,
    color: Color,
    fillColor: Color,
    score: Int,
    painter: Painter,
    dimensionId: String,
    selected: Boolean,
    circleRadius: Float,
    strokeWidth: Float,
    iconSize: Float,
    textSizePx: Float,
    textGap: Float,
    labelPaint: AndroidPaint,
    submitted: Boolean = false,
    touched: Boolean = true,
    iconHighlighted: Boolean = false,
    belowLabelShift: Float = 0f,
    scale: Float = 1f
) {
    val cx = center.x
    val cy = center.y
    // Three-state opacity: untouched → edited → submitted.
    val lightSurface = fillColor.luminance() > 0.5f
    val untouchedAlpha = if (lightSurface) 0.55f else 0.30f
    val ringAlpha = when {
        submitted -> 1f
        touched -> if (lightSurface) 0.8f else 0.65f
        else -> untouchedAlpha
    }
    val iconAlpha = if (touched || iconHighlighted) 1f else untouchedAlpha
    // Selected outer ring (drawn first so head circle overdraws inner half,
    // leaving a halo flush with the border outer edge)
    if (selected) {
        drawCircle(
            color = color.copy(alpha = (160f / 255f) * ringAlpha),
            radius = circleRadius + (3.dp * scale).toPx(),
            center = Offset(cx, cy),
            style = Stroke(width = (4.dp * scale).toPx())
        )
    }
    drawCircle(
        color = fillColor,
        radius = circleRadius,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = color.copy(alpha = ringAlpha),
        radius = circleRadius,
        center = Offset(cx, cy),
        style = Stroke(width = strokeWidth)
    )

    val strokeInset = strokeWidth / 2f
    var halfW = iconSize / 2f - strokeInset
    var halfH = iconSize / 2f - strokeInset
    var iconOffsetY = 0f
    when (dimensionId) {
        "layman_friendly" -> {
            halfW *= 0.88f
            halfH *= 0.88f
        }

        "backfire_risk" -> {
            iconOffsetY = (1.dp * scale).toPx()
        }
    }
    translate(left = cx - halfW, top = cy - halfH + iconOffsetY) {
        with(painter) {
            draw(size = Size(halfW * 2f, halfH * 2f), alpha = iconAlpha)
        }
    }

    labelPaint.color = color.copy(alpha = ringAlpha).toArgb()
    val txt = score.toString()
    val labelY = if (score >= 0) {
        cy - circleRadius - textGap
    } else {
        cy + circleRadius + textSizePx + textGap * 0.3f + belowLabelShift
    }
    drawContext.canvas.nativeCanvas.drawText(txt, cx, labelY, labelPaint)
}

@Composable
private fun LollipopHead(
    label: String,
    painter: Painter,
    color: Color,
    score: Int,
    dimensionId: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    submitted: Boolean = false,
    touched: Boolean = true,
    scale: Float = 1f
) {
    val density = LocalDensity.current
    val circleRadius = with(density) { (14.dp * scale).toPx() }
    val strokeWidth = with(density) { (3.5.dp * scale).toPx() }
    val iconSize = with(density) { (18.dp * scale).toPx() }
    val textSizePx = with(density) { (10.sp * scale).toPx() }
    val textHeight = with(density) { textSizePx.toDp() }
    val textGap = with(density) { (6.dp * scale).toPx() }
    val indicatorSpace = 8.dp * scale
    val indicatorSpacePx = with(density) { indicatorSpace.toPx() }
    val indicatorGapPx = with(density) { (3.dp * scale).toPx() }
    val indicatorWidthPx = with(density) { (12.dp * scale).toPx() }
    val indicatorHeightPx = with(density) { (3.dp * scale).toPx() }
    val labelPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    labelPaint.textSize = textSizePx
    val headFillColor = colorResource(R.color.tournesol_surface_band)
    val interactionSource = remember { MutableInteractionSource() }
    Canvas(
        modifier = modifier
            .size(width = 36.dp * scale, height = 34.dp * scale + textHeight * 2 + indicatorSpace)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .semantics {
                contentDescription = label
                stateDescription = score.toString()
            }
    ) {
        val center = Offset(size.width / 2f, (size.height - indicatorSpacePx) / 2f)
        drawLollipopHead(
            center = center,
            color = color,
            fillColor = headFillColor,
            score = score,
            painter = painter,
            dimensionId = dimensionId,
            selected = false,
            circleRadius = circleRadius,
            strokeWidth = strokeWidth,
            iconSize = iconSize,
            textSizePx = textSizePx,
            textGap = textGap,
            labelPaint = labelPaint,
            submitted = submitted,
            touched = touched,
            iconHighlighted = selected,
            belowLabelShift = indicatorSpacePx,
            scale = scale
        )
        if (selected) {
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    center.x - indicatorWidthPx / 2f,
                    center.y + circleRadius + strokeWidth / 2f + indicatorGapPx
                ),
                size = Size(indicatorWidthPx, indicatorHeightPx),
                cornerRadius = CornerRadius(indicatorHeightPx / 2f)
            )
        }
    }
}

@Composable
private fun LollipopHeadsRow(
    scores: Map<String, Int>,
    mainScore: Int,
    activeIndex: MutableIntState,
    submittedCriteria: Set<String> = emptySet(),
    touchedCriteria: Set<String>? = null,
    modifier: Modifier = Modifier,
    onHeadClick: ((Int) -> Unit)? = null
) {
    val mainCriterion = remember {
        CompareCriterion(
            id = COMPACT_MAIN_CRITERION_ID,
            labelRes = R.string.compare_criteria_largely_recommended,
            iconRes = R.drawable.logo_small
        )
    }
    // Row order: largely_recommended first, then the 10 extra criteria.
    // LollipopDescriptionRow indexes into (EXTRA_CRITERIA + main), so the
    // main head at row position 0 maps to description index EXTRA_CRITERIA.size.
    val rowDimensions = remember(mainCriterion) { listOf(mainCriterion) + EXTRA_CRITERIA }
    val mainDescIndex = EXTRA_CRITERIA.size
    val painters = rowDimensions.map { painterResource(it.iconRes) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Each head is 36.dp wide by design. If the screen can't fit that many
        // heads side-by-side, scale them down so they all fit on one line.
        val count = rowDimensions.size
        val slotWidth = maxWidth / count
        val scale = (slotWidth / 36.dp).coerceAtMost(1f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            rowDimensions.forEachIndexed { rowIdx, dim ->
                val descIdx = if (rowIdx == 0) mainDescIndex else rowIdx - 1
                val score = if (dim.id == COMPACT_MAIN_CRITERION_ID) {
                    mainScore
                } else {
                    scores[dim.id] ?: 0
                }
                LollipopHead(
                    label = stringResource(dim.labelRes),
                    painter = painters[rowIdx],
                    color = shadedAccent(LOLLIPOP_COLORS[dim.id] ?: Color.White),
                    score = score,
                    dimensionId = dim.id,
                    selected = activeIndex.intValue == descIdx,
                    submitted = dim.id in submittedCriteria,
                    touched = touchedCriteria?.let { dim.id in it } ?: true,
                    onClick = {
                        if (onHeadClick != null) {
                            onHeadClick(descIdx)
                        } else {
                            activeIndex.intValue = descIdx
                        }
                    },
                    scale = scale
                )
            }
        }
    }
}
