package org.schabi.newpipe.fragments.detail.compare

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.schabi.newpipe.R
import org.schabi.newpipe.fragments.detail.COMPACT_MAIN_CRITERION_ID
import org.schabi.newpipe.fragments.detail.CompareCriterion
import org.schabi.newpipe.fragments.detail.CriteriaScore
import org.schabi.newpipe.fragments.detail.EXTRA_CRITERIA
import org.schabi.newpipe.fragments.detail.LOLLIPOP_COLORS
import org.schabi.newpipe.fragments.detail.LollipopDescriptionRow

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
    val colors = dimensions.map { LOLLIPOP_COLORS[it.id] ?: Color.White }
    fun scoreAt(i: Int): Int = if (dimensions[i].id == COMPACT_MAIN_CRITERION_ID) {
        mainScore
    } else {
        scores[dimensions[i].id] ?: 0
    }
    fun setScoreAt(i: Int, value: Int) {
        if (dimensions[i].id == COMPACT_MAIN_CRITERION_ID) {
            onMainScoreChange(value)
        } else {
            onScoreChange(dimensions[i].id, value)
        }
    }
    val circleRadius = with(density) { 14.dp.toPx() }
    val barWidth = with(density) { 12.dp.toPx() }
    val iconSize = with(density) { 18.dp.toPx() }
    val strokeWidth = with(density) { 3.5.dp.toPx() }
    val textSizePx = with(density) { 10.dp.toPx() }
    val textGap = with(density) { 6.dp.toPx() }
    val topPaddingPx = with(density) { 22.dp.toPx() }
    val bottomPaddingPx = with(density) { 22.dp.toPx() }
    val totalHeight = 160.dp
    val activeIndex = hoistedActiveIndex ?: remember { mutableIntStateOf(-1) }
    val dragScore = hoistedDragScore ?: remember { mutableStateOf<Int?>(null) }
    val pendingTap = remember { mutableStateOf<PendingTap?>(null) }
    val currentOnScoreChange by rememberUpdatedState(onScoreChange)
    val currentOnMainScoreChange by rememberUpdatedState(onMainScoreChange)
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
                color = Color.White.copy(alpha = 0.25f),
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
    modifier: Modifier = Modifier,
    hoistedActiveIndex: MutableIntState? = null,
    hoistedDragScore: MutableState<Int?>? = null,
    hoistedPhase: MutableIntState? = null,
    hoistedTouchedCriteria: MutableState<Set<String>>? = null,
    showDescription: Boolean = true,
    onSubmit1Request: ((String, Int, () -> Unit) -> Unit)? = null,
    onSubmitExtrasBatch: ((List<CriteriaScore>, () -> Unit) -> Unit)? = null
) {
    val phase = hoistedPhase ?: remember { mutableIntStateOf(1) }
    val phase1ActiveIndex = hoistedActiveIndex ?: remember { mutableIntStateOf(-1) }
    val touchedCriteria = hoistedTouchedCriteria ?: remember { mutableStateOf(setOf<String>()) }
    val allExtrasDone = remember { mutableStateOf(false) }
    val submittedCriteria = remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(pairKey) {
        phase.intValue = 1
        phase1ActiveIndex.intValue = EXTRA_CRITERIA.size
        touchedCriteria.value = emptySet()
        allExtrasDone.value = false
        submittedCriteria.value = emptySet()
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
            onSubmit1 = { criterionId, value ->
                onSubmit1Request?.invoke(criterionId, value) {
                    submittedCriteria.value = submittedCriteria.value + criterionId
                }
            },
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
            hoistedActiveIndex = hoistedActiveIndex,
            hoistedDragScore = hoistedDragScore,
            showDescription = showDescription
        )
    }
}

@Composable
private fun LargelyRecommendedSlider(
    mainScore: Int,
    onMainScoreChange: (Int) -> Unit,
    scores: Map<String, Int>,
    onScoreChange: (String, Int) -> Unit,
    activeIndex: MutableIntState,
    touchedCriteria: MutableState<Set<String>>,
    allExtrasDone: MutableState<Boolean>,
    onSubmit1: (String, Int) -> Unit,
    onSubmitExtrasBatch: (List<CriteriaScore>) -> Unit,
    submittedCriteria: MutableState<Set<String>>,
    hoistedDragScore: MutableState<Int?>? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hostView = LocalView.current
    val blue = Color(0xFF4F9EFF)
    val red = Color(0xFFFF5A5F)
    val trackBg = Color.White.copy(alpha = 0.10f)
    val thumbColor = Color.White
    val neutralLabel = Color.White.copy(alpha = 0.55f)
    val trackHeightPx = with(density) { 6.dp.toPx() }
    val thumbWidthPx = with(density) { 4.dp.toPx() }
    val thumbHeightPx = with(density) { 22.dp.toPx() }
    val fillThumbGapPx = with(density) { 5.dp.toPx() }
    val sidePaddingPx = with(density) { 28.dp.toPx() }
    val canvasHeight = 32.dp

    val mainDescIndex = EXTRA_CRITERIA.size
    val activeIdx = activeIndex.intValue
    val selectedCriterionId = if (activeIdx < 0 || activeIdx == mainDescIndex) {
        COMPACT_MAIN_CRITERION_ID
    } else {
        EXTRA_CRITERIA[activeIdx].id
    }
    val isMain = selectedCriterionId == COMPACT_MAIN_CRITERION_ID
    val currentScore = if (isMain) mainScore else (scores[selectedCriterionId] ?: 0)

    val currentOnMainChange by rememberUpdatedState(onMainScoreChange)
    val currentOnExtraChange by rememberUpdatedState(onScoreChange)
    val currentOnSubmit1 by rememberUpdatedState(onSubmit1)
    val dispatchChange: (Int) -> Unit = { v ->
        if (isMain) currentOnMainChange(v) else currentOnExtraChange(selectedCriterionId, v)
    }

    val title = criterionQuestion(selectedCriterionId)
    val qLine = buildQualifierLine(selectedCriterionId, currentScore)
    val scoreColor = when {
        currentScore < 0 -> blue
        currentScore > 0 -> red
        else -> neutralLabel
    }
    var showCriteriaDesc by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var isInCancelZone by remember { mutableStateOf(false) }
    LaunchedEffect(activeIndex.intValue) { showCriteriaDesc = false }

    Column(modifier = modifier.fillMaxWidth()) {
        if (allExtrasDone.value) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "You scored every criteria! Good job. Submit it now?",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterChip(
                    selected = true,
                    onClick = {
                        val batch = EXTRA_CRITERIA.mapNotNull { c ->
                            if (c.id in touchedCriteria.value) {
                                CriteriaScore(c.id, scores[c.id] ?: 0)
                            } else {
                                null
                            }
                        }
                        onSubmitExtrasBatch(batch)
                    },
                    label = { Text(text = "Submit1") },
                    colors = FilterChipDefaults.filterChipColors(),
                    shape = RoundedCornerShape(8.dp),
                    border = null
                )
            }
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
            if (showCriteriaDesc) {
                LollipopDescriptionRow(activeIndex = activeIndex.intValue)
            }
            return@Column
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp
            )
            Box(modifier = Modifier.width(48.dp)) {
                Text(
                    text = currentScore.toString(),
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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(canvasHeight)
                .pointerInput(selectedCriterionId) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        hostView.parent?.requestDisallowInterceptTouchEvent(true)
                        val left = sidePaddingPx
                        val right = size.width - sidePaddingPx
                        val canvasBottom = size.height.toFloat()
                        fun xToScore(x: Float): Int = positionToScore(x, left, right)
                        val scoreBeforeDrag = currentScore
                        var finalScore = xToScore(down.position.x)
                        isDragging = true
                        isInCancelZone = false
                        hoistedDragScore?.value = finalScore
                        dispatchChange(finalScore)
                        val newTouched = touchedCriteria.value + selectedCriterionId
                        touchedCriteria.value = newTouched
                        drag(down.id) { change ->
                            finalScore = xToScore(change.position.x)
                            isInCancelZone = change.position.y < 0f
                            hoistedDragScore?.value = finalScore
                            dispatchChange(finalScore)
                            change.consume()
                        }
                        isDragging = false
                        hoistedDragScore?.value = null
                        if (isInCancelZone) {
                            // Cancel: restore original score, don't advance
                            isInCancelZone = false
                            dispatchChange(scoreBeforeDrag)
                            touchedCriteria.value = touchedCriteria.value - selectedCriterionId
                        } else {
                            if (isMain) {
                                currentOnSubmit1(selectedCriterionId, finalScore)
                                val firstUntouched = EXTRA_CRITERIA.indexOfFirst {
                                    it.id !in newTouched
                                }
                                if (firstUntouched >= 0) {
                                    activeIndex.intValue = firstUntouched
                                } else {
                                    allExtrasDone.value = true
                                }
                            } else {
                                val nextIdx = EXTRA_CRITERIA.indexOfFirst {
                                    it.id !in newTouched
                                }
                                if (nextIdx >= 0) {
                                    activeIndex.intValue = nextIdx
                                } else {
                                    allExtrasDone.value = true
                                }
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height
            val left = sidePaddingPx
            val right = w - sidePaddingPx
            val centerX = (left + right) / 2f
            val cy = h / 2f
            val trackTop = cy - trackHeightPx / 2f
            val trackRadius = CornerRadius(trackHeightPx / 2f, trackHeightPx / 2f)

            // Background track
            drawRoundRect(
                color = trackBg,
                topLeft = Offset(left, trackTop),
                size = Size(right - left, trackHeightPx),
                cornerRadius = trackRadius
            )

            // Fill from center toward thumb, stopping short to leave a gap
            val thumbX = scoreToPosition(currentScore, minScoreAt = left, maxScoreAt = right)
            if (currentScore != 0) {
                val fillColor = if (currentScore < 0) blue else red
                val rawFillLeft: Float
                val rawFillRight: Float
                if (currentScore < 0) {
                    rawFillLeft = thumbX + fillThumbGapPx
                    rawFillRight = centerX
                } else {
                    rawFillLeft = centerX
                    rawFillRight = thumbX - fillThumbGapPx
                }
                if (rawFillRight > rawFillLeft) {
                    clipRect(
                        left = rawFillLeft,
                        top = trackTop,
                        right = rawFillRight,
                        bottom = trackTop + trackHeightPx
                    ) {
                        drawRoundRect(
                            color = fillColor,
                            topLeft = Offset(left, trackTop),
                            size = Size(right - left, trackHeightPx),
                            cornerRadius = trackRadius
                        )
                    }
                }
            }

            // Thumb: tall white pill
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX - thumbWidthPx / 2f, cy - thumbHeightPx / 2f),
                size = Size(thumbWidthPx, thumbHeightPx),
                cornerRadius = CornerRadius(thumbWidthPx / 2f, thumbWidthPx / 2f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isDragging && isInCancelZone) {
                Text(
                    text = "CANCEL",
                    color = red,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = qLine.leftArrow,
                    color = blue,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(qLine.qualifier)
                            }
                            append(qLine.tail)
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = " ⓘ",
                        color = Color.White.copy(alpha = if (showCriteriaDesc) 0.5f else 0.2f),
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { showCriteriaDesc = !showCriteriaDesc }
                    )
                }
                Text(
                    text = qLine.rightArrow,
                    color = red,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        LollipopHeadsRow(
            scores = scores,
            mainScore = mainScore,
            activeIndex = activeIndex,
            submittedCriteria = submittedCriteria.value,
            touchedCriteria = touchedCriteria.value,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (showCriteriaDesc) {
            LollipopDescriptionRow(activeIndex = activeIndex.intValue)
        }
    }
}

private fun criterionQuestion(criterionId: String): String = when (criterionId) {
    "largely_recommended" -> "Should be recommended..?"
    "reliability" -> "Is more reliable..?"
    "pedagogy" -> "Is more pedagological..?"
    "importance" -> "Is more important..?"
    "layman_friendly" -> "Is more accessible..?"
    "entertaining_relaxing" -> "Is more entertaining..?"
    "engaging" -> "Is more engaging..?"
    "diversity_inclusion" -> "Is more inclusive..?"
    "better_habits" -> "Encourages better habits..?"
    "backfire_risk" -> "Does not backfire..?"
    else -> "Should be recommended..?"
}

private data class QualifierLine(
    val leftArrow: String,
    val rightArrow: String,
    val qualifier: String,
    val tail: String
)

private fun buildQualifierLine(criterionId: String, score: Int): QualifierLine {
    val leftArrow = when {
        score <= -70 -> "<<"
        score <= -16 -> "<"
        else -> ""
    }
    val rightArrow = when {
        score >= 70 -> ">>"
        score >= 16 -> ">"
        else -> ""
    }
    val goodnessLike = criterionId == "better_habits" || criterionId == "backfire_risk"
    if (goodnessLike) {
        val qualifier = when {
            score >= 70 -> "much better"
            score >= 16 -> "slightly better"
            score >= -15 -> "as good"
            score >= -69 -> "slightly better"
            else -> "much better"
        }
        return QualifierLine(leftArrow, rightArrow, qualifier, "")
    }
    val qualifier = when {
        score >= 70 -> "much more"
        score >= 16 -> "slightly more"
        score >= -15 -> "just as"
        score >= -69 -> "slightly more"
        else -> "much more"
    }
    val tail = when (criterionId) {
        "largely_recommended" -> " recommended"
        "reliability" -> " reliable"
        "pedagogy" -> " pedagogical"
        "importance" -> " important"
        "layman_friendly" -> " accessible"
        "entertaining_relaxing" -> " entertaining"
        "engaging" -> " engaging"
        "diversity_inclusion" -> " inclusive"
        else -> " recommended"
    }
    return QualifierLine(leftArrow, rightArrow, qualifier, tail)
}

private fun DrawScope.drawLollipopHead(
    center: Offset,
    color: Color,
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
    scale: Float = 1f
) {
    val cx = center.x
    val cy = center.y
    // Three-state opacity: untouched → edited → submitted.
    val ringAlpha = when {
        submitted -> 1f
        touched -> 0.65f
        else -> 0.30f
    }
    val iconAlpha = if (touched) 1f else 0.30f
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
        color = Color(0xFF0F0F0F),
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
        cy + circleRadius + textSizePx + textGap * 0.3f
    }
    drawContext.canvas.nativeCanvas.drawText(txt, cx, labelY, labelPaint)
}

@Composable
private fun LollipopHead(
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
    val textSizePx = with(density) { (10.dp * scale).toPx() }
    val textGap = with(density) { (6.dp * scale).toPx() }
    val labelPaint = remember {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
    labelPaint.textSize = textSizePx
    val interactionSource = remember { MutableInteractionSource() }
    Canvas(
        modifier = modifier
            .size(width = 36.dp * scale, height = 54.dp * scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        drawLollipopHead(
            center = Offset(size.width / 2f, size.height / 2f),
            color = color,
            score = score,
            painter = painter,
            dimensionId = dimensionId,
            selected = selected,
            circleRadius = circleRadius,
            strokeWidth = strokeWidth,
            iconSize = iconSize,
            textSizePx = textSizePx,
            textGap = textGap,
            labelPaint = labelPaint,
            submitted = submitted,
            touched = touched,
            scale = scale
        )
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
                    painter = painters[rowIdx],
                    color = LOLLIPOP_COLORS[dim.id] ?: Color.White,
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
