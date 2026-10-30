package org.schabi.newpipe.fragments.list.kiosk

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.ui.theme.AppTheme
import org.schabi.newpipe.util.TournesolFilterConfig
import org.schabi.newpipe.util.TournesolFilterCriterion
import org.schabi.newpipe.util.TournesolHelper

class TournesolFilterFragment : BottomSheetDialogFragment() {
    fun interface FilterListener {
        fun onApply(config: TournesolFilterConfig)
    }

    private var listener: FilterListener? = null
    private var initialConfig: TournesolFilterConfig? = null

    fun setListener(listener: FilterListener) {
        this.listener = listener
    }

    fun setInitialData(config: TournesolFilterConfig) {
        this.initialConfig = config
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                // Ensure the parent bottom sheet is transparent so our background shows.
                bottomSheet.setBackgroundResource(android.R.color.transparent)
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val cfg = initialConfig ?: TournesolFilterConfig(
            languages = TournesolHelper.getDefaultFilterLanguages().split(",")
        )

        val appContext = requireContext().applicationContext
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    TournesolFilterSheet(
                        appContext = appContext,
                        initialLanguages = cfg.languages,
                        initialDateKey = cfg.dateKey,
                        initialIncludeLowScoreVideos = cfg.includeLowScoreVideos,
                        initialDurationMinSeconds = cfg.durationMinSeconds,
                        initialDurationMaxSeconds = cfg.durationMaxSeconds,
                        initialWeightLargelyRecommended = cfg.weightFor(TournesolFilterCriterion.LARGELY_RECOMMENDED),
                        initialWeightReliability = cfg.weightFor(TournesolFilterCriterion.RELIABILITY),
                        initialWeightImportance = cfg.weightFor(TournesolFilterCriterion.IMPORTANCE),
                        initialWeightPedagogy = cfg.weightFor(TournesolFilterCriterion.PEDAGOGY),
                        initialWeightLaymanFriendly = cfg.weightFor(TournesolFilterCriterion.LAYMAN_FRIENDLY),
                        initialWeightEntertainingRelaxing = cfg.weightFor(TournesolFilterCriterion.ENTERTAINING_RELAXING),
                        initialWeightEngaging = cfg.weightFor(TournesolFilterCriterion.ENGAGING),
                        initialWeightDiversityInclusion = cfg.weightFor(TournesolFilterCriterion.DIVERSITY_INCLUSION),
                        initialWeightBetterHabits = cfg.weightFor(TournesolFilterCriterion.BETTER_HABITS),
                        initialWeightBackfireRisk = cfg.weightFor(TournesolFilterCriterion.BACKFIRE_RISK),
                        onApply = {
                                languages,
                                dateKey,
                                includeLowScoreVideos,
                                durationMin,
                                durationMax,
                                wLR,
                                wRel,
                                wImp,
                                wPed,
                                wLay,
                                wEnt,
                                wEng,
                                wDiv,
                                wBet,
                                wBack
                            ->
                            listener?.onApply(
                                TournesolFilterConfig(
                                    languages = languages,
                                    dateKey = dateKey,
                                    includeLowScoreVideos = includeLowScoreVideos,
                                    durationMinSeconds = durationMin,
                                    durationMaxSeconds = durationMax,
                                    weights = mapOf(
                                        TournesolFilterCriterion.LARGELY_RECOMMENDED to wLR,
                                        TournesolFilterCriterion.RELIABILITY to wRel,
                                        TournesolFilterCriterion.IMPORTANCE to wImp,
                                        TournesolFilterCriterion.PEDAGOGY to wPed,
                                        TournesolFilterCriterion.LAYMAN_FRIENDLY to wLay,
                                        TournesolFilterCriterion.ENTERTAINING_RELAXING to wEnt,
                                        TournesolFilterCriterion.ENGAGING to wEng,
                                        TournesolFilterCriterion.DIVERSITY_INCLUSION to wDiv,
                                        TournesolFilterCriterion.BETTER_HABITS to wBet,
                                        TournesolFilterCriterion.BACKFIRE_RISK to wBack
                                    )
                                )
                            )
                        },
                        onClose = { dismiss() }
                    )
                }
            }
        }
    }
}

private data class FilterOption(val key: String, @StringRes val labelResId: Int)

private const val DURATION_SLIDER_MAX = 120f

private const val PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED = "tournesol_filter_weights_enabled"
private const val PREF_WEIGHT_LEVEL_PREFIX = "tournesol_filter_weight_level_"

private val WEIGHT_LEVELS = intArrayOf(0, 25, 50, 75, 100)

private fun weightLevelLabel(value: Int): String = when (value) {
    0 -> "Ignore"
    25 -> "Not important"
    50 -> "Neutral"
    75 -> "Important"
    else -> "Crucial"
}

private fun quantizeToLevel(value: Int): Int {
    if (value < 0) return 50
    return WEIGHT_LEVELS.minByOrNull { kotlin.math.abs(it - value) } ?: 50
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TournesolFilterSheet(
    appContext: Context,
    initialLanguages: List<String>,
    initialDateKey: String,
    initialIncludeLowScoreVideos: Boolean,
    initialDurationMinSeconds: Int,
    initialDurationMaxSeconds: Int,
    initialWeightLargelyRecommended: Int,
    initialWeightReliability: Int,
    initialWeightImportance: Int,
    initialWeightPedagogy: Int,
    initialWeightLaymanFriendly: Int,
    initialWeightEntertainingRelaxing: Int,
    initialWeightEngaging: Int,
    initialWeightDiversityInclusion: Int,
    initialWeightBetterHabits: Int,
    initialWeightBackfireRisk: Int,
    onApply: (List<String>, String, Boolean, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int, Int) -> Unit,
    onClose: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colorResource(R.color.tournesol_chip_bg_selected),
        containerColor = colorResource(R.color.tournesol_chip_bg_unselected),
        selectedLabelColor = colorResource(R.color.tournesol_chip_text_selected),
        labelColor = MaterialTheme.colorScheme.onSurface
    )
    val chipShape = RoundedCornerShape(8.dp)
    val chipTextStyle = TextStyle(
        fontFamily = FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL)),
        fontSize = 14.sp,
        letterSpacing = TextUnit(0.04f, TextUnitType.Em)
    )
    val selectedLanguages = remember {
        mutableStateListOf<String>().apply { addAll(initialLanguages) }
    }
    var selectedDateKey by remember { mutableStateOf(initialDateKey) }
    var includeLowScoreVideos by remember { mutableStateOf(initialIncludeLowScoreVideos) }

    // Duration: store as minutes in slider, convert to seconds for API
    var durationMinMinutes by remember {
        mutableStateOf(if (initialDurationMinSeconds >= 0) initialDurationMinSeconds / 60f else 0f)
    }
    var durationMaxMinutes by remember {
        mutableStateOf(if (initialDurationMaxSeconds >= 0) initialDurationMaxSeconds / 60f else DURATION_SLIDER_MAX)
    }

    // Weights: always a level in WEIGHT_LEVELS. Persisted via fragment-local prefs so the
    // user's chosen levels survive even when the master toggle is disabled.
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(appContext) }
    fun loadLevel(key: String, fallback: Int): Int {
        val stored = prefs.getInt(PREF_WEIGHT_LEVEL_PREFIX + key, Int.MIN_VALUE)
        return if (stored == Int.MIN_VALUE) quantizeToLevel(fallback) else quantizeToLevel(stored)
    }
    var weightsEnabled by remember {
        mutableStateOf(prefs.getBoolean(PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED, false))
    }
    var weightLR by remember { mutableStateOf(loadLevel("largely_recommended", initialWeightLargelyRecommended)) }
    var weightRel by remember { mutableStateOf(loadLevel("reliability", initialWeightReliability)) }
    var weightImp by remember { mutableStateOf(loadLevel("importance", initialWeightImportance)) }
    var weightPed by remember { mutableStateOf(loadLevel("pedagogy", initialWeightPedagogy)) }
    var weightLay by remember { mutableStateOf(loadLevel("layman_friendly", initialWeightLaymanFriendly)) }
    var weightEnt by remember { mutableStateOf(loadLevel("entertaining_relaxing", initialWeightEntertainingRelaxing)) }
    var weightEng by remember { mutableStateOf(loadLevel("engaging", initialWeightEngaging)) }
    var weightDiv by remember { mutableStateOf(loadLevel("diversity_inclusion", initialWeightDiversityInclusion)) }
    var weightBet by remember { mutableStateOf(loadLevel("better_habits", initialWeightBetterHabits)) }
    var weightBack by remember { mutableStateOf(loadLevel("backfire_risk", initialWeightBackfireRisk)) }

    fun currentDurationMinSeconds(): Int = if (durationMinMinutes <= 0f) -1 else (durationMinMinutes * 60).toInt()
    fun currentDurationMaxSeconds(): Int = if (durationMaxMinutes >= DURATION_SLIDER_MAX) -1 else (durationMaxMinutes * 60).toInt()

    fun persistLevels() {
        prefs.edit()
            .putBoolean(PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED, weightsEnabled)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "largely_recommended", weightLR)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "reliability", weightRel)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "importance", weightImp)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "pedagogy", weightPed)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "layman_friendly", weightLay)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "entertaining_relaxing", weightEnt)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "engaging", weightEng)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "diversity_inclusion", weightDiv)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "better_habits", weightBet)
            .putInt(PREF_WEIGHT_LEVEL_PREFIX + "backfire_risk", weightBack)
            .apply()
    }

    fun applyAll() {
        persistLevels()
        val sentinel = -1
        onApply(
            selectedLanguages.toList(),
            selectedDateKey,
            includeLowScoreVideos,
            currentDurationMinSeconds(),
            currentDurationMaxSeconds(),
            if (weightsEnabled) weightLR else sentinel,
            if (weightsEnabled) weightRel else sentinel,
            if (weightsEnabled) weightImp else sentinel,
            if (weightsEnabled) weightPed else sentinel,
            if (weightsEnabled) weightLay else sentinel,
            if (weightsEnabled) weightEnt else sentinel,
            if (weightsEnabled) weightEng else sentinel,
            if (weightsEnabled) weightDiv else sentinel,
            if (weightsEnabled) weightBet else sentinel,
            if (weightsEnabled) weightBack else sentinel
        )
    }

    val languageOptions = remember {
        listOf(
            FilterOption("en", R.string.language_english),
            FilterOption("fr", R.string.language_french),
            FilterOption("es", R.string.language_spanish),
            FilterOption("de", R.string.language_german),
            FilterOption("it", R.string.language_italian),
            FilterOption("pt", R.string.language_portuguese)
        )
    }
    val dateOptions = remember {
        listOf(
            FilterOption("forever", R.string.date_since_forever),
            FilterOption("year", R.string.date_last_year),
            FilterOption("3_months", R.string.date_last_3_months),
            FilterOption("month", R.string.date_last_month),
            FilterOption("week", R.string.date_last_week),
            FilterOption("day", R.string.date_last_day)
        )
    }

    val criteria: List<Triple<Int, Int, (Int) -> Unit>> = listOf(
        Triple(R.string.filter_criteria_largely_recommended, weightLR, { v -> weightLR = v }),
        Triple(R.string.filter_criteria_reliability, weightRel, { v -> weightRel = v }),
        Triple(R.string.filter_criteria_importance, weightImp, { v -> weightImp = v }),
        Triple(R.string.filter_criteria_pedagogy, weightPed, { v -> weightPed = v }),
        Triple(R.string.filter_criteria_layman_friendly, weightLay, { v -> weightLay = v }),
        Triple(R.string.filter_criteria_entertaining_relaxing, weightEnt, { v -> weightEnt = v }),
        Triple(R.string.filter_criteria_engaging, weightEng, { v -> weightEng = v }),
        Triple(R.string.filter_criteria_diversity_inclusion, weightDiv, { v -> weightDiv = v }),
        Triple(R.string.filter_criteria_better_habits, weightBet, { v -> weightBet = v }),
        Triple(R.string.filter_criteria_backfire_risk, weightBack, { v -> weightBack = v })
    )

    val baseDensity = LocalDensity.current
    val scaledDensity = remember(baseDensity) {
        Density(
            density = baseDensity.density * 0.5f,
            fontScale = baseDensity.fontScale * 1.6f
        )
    }
    CompositionLocalProvider(LocalDensity provides scaledDensity) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.filter_tournesol),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose, modifier = Modifier.height(32.dp)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cancel),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Advanced
                CompactSectionHeader(stringResource(R.string.filter_advanced))
                FilterChip(
                    selected = includeLowScoreVideos,
                    onClick = {
                        includeLowScoreVideos = !includeLowScoreVideos
                        applyAll()
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.include_low_score_videos_short),
                            style = chipTextStyle
                        )
                    },
                    colors = chipColors,
                    shape = chipShape,
                    border = null,
                    modifier = Modifier.height(38.dp)
                )

                // Languages
                CompactSectionHeader(stringResource(R.string.filter_languages))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    languageOptions.forEach { option ->
                        val isSelected = selectedLanguages.contains(option.key)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    selectedLanguages.remove(option.key)
                                } else {
                                    selectedLanguages.add(option.key)
                                }
                                applyAll()
                            },
                            label = { Text(text = stringResource(option.labelResId), style = chipTextStyle) },
                            colors = chipColors,
                            shape = chipShape,
                            border = null,
                            modifier = Modifier.height(38.dp)
                        )
                    }
                }

                // Date
                CompactSectionHeader(stringResource(R.string.filter_date))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dateOptions.forEach { option ->
                        val isSelected = selectedDateKey == option.key
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (!isSelected) {
                                    selectedDateKey = option.key
                                    applyAll()
                                }
                            },
                            label = { Text(text = stringResource(option.labelResId), style = chipTextStyle) },
                            colors = chipColors,
                            shape = chipShape,
                            border = null,
                            modifier = Modifier.height(38.dp)
                        )
                    }
                }

                // Duration: single range slider
                CompactSectionHeader(stringResource(R.string.filter_duration))
                DurationRangeRow(
                    minMinutes = durationMinMinutes,
                    maxMinutes = durationMaxMinutes,
                    onValueChange = { lo, hi ->
                        durationMinMinutes = lo
                        durationMaxMinutes = hi
                    },
                    onValueChangeFinished = { applyAll() }
                )

                // Criteria weights: 2-column compact grid
                CompactSectionHeader(stringResource(R.string.filter_criteria))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Enable weighting",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = weightsEnabled,
                        onCheckedChange = {
                            weightsEnabled = it
                            applyAll()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorResource(R.color.tournesol_chip_bg_selected),
                            checkedTrackColor = colorResource(R.color.tournesol_chip_bg_selected).copy(alpha = 0.4f)
                        )
                    )
                }
                criteria.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { (labelRes, value, setter) ->
                            Box(modifier = Modifier.weight(1f)) {
                                CompactWeightSlider(
                                    label = stringResource(labelRes),
                                    value = value,
                                    enabled = weightsEnabled,
                                    onValueChange = setter,
                                    onValueChangeFinished = { if (weightsEnabled) applyAll() }
                                )
                            }
                        }
                        if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CompactSectionHeader(title: String) {
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = title.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun formatDurationLabel(minutes: Float, noLimitValue: Float): String {
    return if (minutes <= 0f && noLimitValue == 0f || minutes >= DURATION_SLIDER_MAX && noLimitValue == DURATION_SLIDER_MAX) {
        stringResource(R.string.filter_duration_no_limit)
    } else {
        stringResource(R.string.filter_duration_minutes, minutes.toInt())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationRangeRow(
    minMinutes: Float,
    maxMinutes: Float,
    onValueChange: (Float, Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = colorResource(R.color.tournesol_chip_bg_selected),
        activeTrackColor = colorResource(R.color.tournesol_chip_bg_selected)
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationLabel(minMinutes, 0f),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDurationLabel(maxMinutes, DURATION_SLIDER_MAX),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RangeSlider(
            value = minMinutes..maxMinutes,
            onValueChange = { onValueChange(it.start, it.endInclusive) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..DURATION_SLIDER_MAX,
            steps = 23,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )
    }
}

@Composable
private fun CompactWeightSlider(
    label: String,
    value: Int,
    enabled: Boolean = true,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val sliderColors = SliderDefaults.colors(
        thumbColor = colorResource(R.color.tournesol_chip_bg_selected),
        activeTrackColor = colorResource(R.color.tournesol_chip_bg_selected)
    )
    val level = quantizeToLevel(value)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = weightLevelLabel(level),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = level.toFloat(),
            onValueChange = { if (enabled) onValueChange(quantizeToLevel(it.toInt())) },
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            valueRange = 0f..100f,
            steps = 3,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth().height(20.dp)
        )
    }
}
