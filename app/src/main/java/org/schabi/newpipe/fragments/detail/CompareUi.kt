package org.schabi.newpipe.fragments.detail

import android.graphics.Paint as AndroidPaint
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.fragments.detail.compare.CompactInitialPromptHeader
import org.schabi.newpipe.fragments.detail.compare.CompareHistoryOverlayGridCard
import org.schabi.newpipe.fragments.detail.compare.DragAxis
import org.schabi.newpipe.fragments.detail.compare.LollipopPicker
import org.schabi.newpipe.fragments.detail.compare.OverlayTarget
import org.schabi.newpipe.fragments.detail.compare.UserGreetingBanner
import org.schabi.newpipe.fragments.detail.compare.compactDescriptionRes
import org.schabi.newpipe.fragments.detail.compare.computeCompareScale
import org.schabi.newpipe.fragments.detail.compare.requestDisallowParentIntercept
import org.schabi.newpipe.ui.components.items.Stream
import org.schabi.newpipe.ui.components.items.stream.StreamThumbnail
import org.schabi.newpipe.util.LoginDialogContent

internal const val COMPACT_MAIN_CRITERION_ID = "largely_recommended"

internal fun dimensionScore(state: CompareUiState, criterion: CompareCriterion): Int {
    return if (criterion.id == COMPACT_MAIN_CRITERION_ID) {
        state.score
    } else {
        state.extraScores[criterion.id] ?: 0
    }
}

@Composable
internal fun CompareComparisonRow(item: CompareRecommendationItem, index: Int = 0) {
    val score = item.largelyRecommendedScore
    val leftWins = score != null && score < 0
    val rightWins = score != null && score > 0
    val leftBorderColor = Color(0xFF42A5F5)
    val rightBorderColor = Color(0xFFE57373)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (index % 2 == 0) {
            MaterialTheme.colorScheme.surface
        } else {
            colorResource(R.color.tournesol_chip_bg_unselected)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left label (video A)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                CompareSideLabel(
                    title = item.videoA.title,
                    uploader = item.videoA.uploader,
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Center: thumbnails + VS badge
            Box(
                modifier = Modifier.weight(2f),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Left thumbnail (video A)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(2.dp, leftBorderColor),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(modifier = Modifier.padding(2.dp)) {
                                    AsyncImage(
                                        model = item.videoA.thumbnailUrl,
                                        contentDescription = null,
                                        placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
                                        error = painterResource(R.drawable.placeholder_thumbnail_video),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                            if (leftWins) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 4.dp)
                                        .fillMaxWidth(0.4f)
                                        .height(2.dp)
                                        .background(Color(0xFFFFD54F), RoundedCornerShape(1.dp))
                                )
                            }
                        }

                        // Right thumbnail (video B)
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(2.dp, rightBorderColor),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Box(modifier = Modifier.padding(2.dp)) {
                                    AsyncImage(
                                        model = item.videoB.thumbnailUrl,
                                        contentDescription = null,
                                        placeholder = painterResource(R.drawable.placeholder_thumbnail_video),
                                        error = painterResource(R.drawable.placeholder_thumbnail_video),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                            if (rightWins) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(y = 4.dp)
                                        .fillMaxWidth(0.4f)
                                        .height(2.dp)
                                        .background(Color(0xFFFFD54F), RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }

                    // VS badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(
                                colorResource(R.color.tournesol_chip_bg_selected),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp
                            ),
                            color = Color(0xFF1B1B1B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Right label (video B)
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                CompareSideLabel(
                    title = item.videoB.title,
                    uploader = item.videoB.uploader,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
internal fun CompareSideLabel(
    title: String,
    uploader: String,
    textAlign: TextAlign
) {
    val titleColor = compareMetaTitleColor()
    val uploaderColor = compareMetaUploaderColor()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = titleColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        if (uploader.isNotBlank()) {
            Text(
                text = uploader,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp
                ),
                color = uploaderColor,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun compareMetaTitleColor(): Color {
    return MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f)
}

@Composable
internal fun compareMetaUploaderColor(): Color {
    return lerp(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.primary,
        0.26f
    ).copy(alpha = 0.82f)
}

@Composable
internal fun TournesolLoginDialog(
    inProgress: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRegister: () -> Unit,
    onLogin: (String, String) -> Unit
) {
    Dialog(onDismissRequest = { if (!inProgress) onDismiss() }) {
        LoginDialogContent(
            inProgress = inProgress,
            errorMessage = errorMessage,
            onDismiss = onDismiss,
            onRegister = onRegister,
            onLogin = onLogin
        )
    }
}

internal val LOLLIPOP_COLORS = mapOf(
    "reliability" to Color(0xFF4F77DD),
    "pedagogy" to Color(0xFFC28BED),
    "importance" to Color(0xFFDC8A5D),
    "layman_friendly" to Color(0xFF4BB061),
    "entertaining_relaxing" to Color(0xFFD8B36D),
    "engaging" to Color(0xFFDFC642),
    "diversity_inclusion" to Color(0xFF76C6CB),
    "better_habits" to Color(0xFF9DD654),
    "backfire_risk" to Color(0xFFD37A80),
    "largely_recommended" to Color(0xFFFFCA1D)
)

@Composable
internal fun LollipopDescriptionRow(
    activeIndex: Int,
    modifier: Modifier = Modifier
) {
    val dimensions = remember {
        EXTRA_CRITERIA + CompareCriterion(
            id = COMPACT_MAIN_CRITERION_ID,
            labelRes = R.string.compare_criteria_largely_recommended,
            iconRes = R.drawable.logo_small
        )
    }
    if (activeIndex !in dimensions.indices) {
        CompactInitialPromptHeader(
            modifier = modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        return
    }
    val criterion = dimensions[activeIndex]
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(criterion.iconRes),
            contentDescription = null,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(criterion.labelRes),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 8.sp, lineHeight = 10.sp),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(compactDescriptionRes(criterion.id)),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, lineHeight = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }
    }
}

@Composable
internal fun CompareVideoThumbnailCard(
    entry: StreamHistoryEntry,
    thumbnailHeight: androidx.compose.ui.unit.Dp = 88.dp,
    showMeta: Boolean = true,
    contentScale: ContentScale = ContentScale.Crop
) {
    val stream = remember(entry) { Stream(entry.toStreamInfoItem()) }
    val thumbnailDescription = stringResource(R.string.compare_thumbnail_description)
    Column(modifier = if (showMeta) Modifier.fillMaxWidth() else Modifier) {
        StreamThumbnail(
            stream = stream,
            showProgress = false,
            durationAlignment = Alignment.BottomEnd,
            durationTextStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            contentScale = contentScale,
            modifier = Modifier
                .then(
                    if (showMeta) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.aspectRatio(16f / 9f)
                    }
                )
                .height(thumbnailHeight)
                .semantics {
                    contentDescription = thumbnailDescription
                }
        )
        if (showMeta) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stream.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stream.uploaderName.orEmpty(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color(0xFF64B5F6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun SuggestedVideoThumbnail(
    thumbnailUrl: String?,
    thumbnailHeight: androidx.compose.ui.unit.Dp = 52.dp
) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .build(),
        imageLoader = context.imageLoader,
        contentDescription = stringResource(R.string.compare_thumbnail_description),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .aspectRatio(16f / 9f)
            .height(thumbnailHeight)
    )
}

internal fun Modifier.tightBorderGlow(
    color: Color,
    cornerRadius: androidx.compose.ui.unit.Dp
): Modifier = this.drawBehind {
    val radiusPx = cornerRadius.toPx()
    val blurPx = 8.dp.toPx()
    val strokePx = 3.dp.toPx()
    val inset = -1.dp.toPx()
    drawIntoCanvas { canvas ->
        // Outer softer halo
        val outer = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            strokeWidth = strokePx
            this.color = color.copy(alpha = 0.55f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(
                blurPx,
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        // Inner bright core
        val inner = AndroidPaint().apply {
            isAntiAlias = true
            style = AndroidPaint.Style.STROKE
            strokeWidth = strokePx
            this.color = color.copy(alpha = 1f).toArgb()
            maskFilter = android.graphics.BlurMaskFilter(
                3.dp.toPx(),
                android.graphics.BlurMaskFilter.Blur.NORMAL
            )
        }
        val left = inset
        val top = inset
        val right = size.width - inset
        val bottom = size.height - inset
        canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radiusPx, radiusPx, outer)
        canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, radiusPx, radiusPx, inner)
    }
}
