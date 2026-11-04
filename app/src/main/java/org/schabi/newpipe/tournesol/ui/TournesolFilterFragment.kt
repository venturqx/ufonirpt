package org.schabi.newpipe.tournesol.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.schabi.newpipe.R
import org.schabi.newpipe.tournesol.DURATION_SLIDER_MAX
import org.schabi.newpipe.tournesol.PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED
import org.schabi.newpipe.tournesol.TournesolDateRange
import org.schabi.newpipe.tournesol.TournesolFilterConfig
import org.schabi.newpipe.tournesol.TournesolFilterCriterion
import org.schabi.newpipe.tournesol.TournesolFilterForm
import org.schabi.newpipe.tournesol.TournesolFilterStore
import org.schabi.newpipe.tournesol.quantizeToLevel
import org.schabi.newpipe.tournesol.weightLevelPrefKey
import org.schabi.newpipe.ui.theme.AppTheme

class TournesolFilterFragment : BottomSheetDialogFragment() {
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
            bottomSheetDialog.behavior.skipCollapsed = true
            bottomSheetDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val appContext = requireContext().applicationContext
        val store = TournesolFilterStore(PreferenceManager.getDefaultSharedPreferences(appContext))
        val cfg = store.load()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                AppTheme {
                    TournesolFilterSheet(
                        initialConfig = cfg,
                        appContext = appContext,
                        onApply = { config ->
                            store.save(config)
                            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle.EMPTY)
                        },
                        onClose = { dismiss() }
                    )
                }
            }
        }
    }

    companion object {
        const val REQUEST_KEY = "tournesol_filter_applied"
    }
}

private data class FilterOption(val key: String, @StringRes val labelResId: Int)

@StringRes
private fun weightLevelLabelRes(value: Int): Int = when (value) {
    0 -> R.string.filter_weight_ignore
    25 -> R.string.filter_weight_not_important
    50 -> R.string.filter_weight_neutral
    75 -> R.string.filter_weight_important
    else -> R.string.filter_weight_crucial
}

@StringRes
private fun dateRangeLabelRes(dateRange: TournesolDateRange): Int = when (dateRange) {
    TournesolDateRange.FOREVER -> R.string.date_since_forever
    TournesolDateRange.YEAR -> R.string.date_last_year
    TournesolDateRange.THREE_MONTHS -> R.string.date_last_3_months
    TournesolDateRange.MONTH -> R.string.date_last_month
    TournesolDateRange.WEEK -> R.string.date_last_week
    TournesolDateRange.DAY -> R.string.date_last_day
}

@StringRes
private fun criterionLabelRes(criterion: TournesolFilterCriterion): Int = when (criterion) {
    TournesolFilterCriterion.LARGELY_RECOMMENDED -> R.string.filter_criteria_largely_recommended
    TournesolFilterCriterion.RELIABILITY -> R.string.filter_criteria_reliability
    TournesolFilterCriterion.IMPORTANCE -> R.string.filter_criteria_importance
    TournesolFilterCriterion.PEDAGOGY -> R.string.filter_criteria_pedagogy
    TournesolFilterCriterion.LAYMAN_FRIENDLY -> R.string.filter_criteria_layman_friendly
    TournesolFilterCriterion.ENTERTAINING_RELAXING -> R.string.filter_criteria_entertaining_relaxing
    TournesolFilterCriterion.ENGAGING -> R.string.filter_criteria_engaging
    TournesolFilterCriterion.DIVERSITY_INCLUSION -> R.string.filter_criteria_diversity_inclusion
    TournesolFilterCriterion.BETTER_HABITS -> R.string.filter_criteria_better_habits
    TournesolFilterCriterion.BACKFIRE_RISK -> R.string.filter_criteria_backfire_risk
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TournesolFilterSheet(
    initialConfig: TournesolFilterConfig,
    appContext: Context,
    onApply: (TournesolFilterConfig) -> Unit,
    onClose: () -> Unit
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = colorResource(R.color.tournesol_chip_bg_selected),
        containerColor = colorResource(R.color.tournesol_chip_bg_unselected),
        selectedLabelColor = colorResource(R.color.tournesol_chip_text_selected),
        labelColor = MaterialTheme.colorScheme.onSurface
    )
    val chipShape = RoundedCornerShape(8.dp)
    val chipTextStyle = MaterialTheme.typography.labelLarge
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(appContext) }
    val initialForm = remember {
        TournesolFilterForm.from(
            config = initialConfig,
            weightsEnabled = prefs.getBoolean(PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED, false),
            storedLevel = { criterion ->
                prefs.getInt(criterion.weightLevelPrefKey, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
            }
        )
    }
    val selectedLanguages = remember {
        mutableStateListOf<String>().apply { addAll(initialForm.languages) }
    }
    var selectedDateRange by remember { mutableStateOf(initialForm.dateRange) }
    var includeLowScoreVideos by remember { mutableStateOf(initialForm.includeLowScoreVideos) }

    // Duration: store as minutes in slider, convert to seconds for API
    var durationMinMinutes by remember { mutableStateOf(initialForm.durationMinMinutes) }
    var durationMaxMinutes by remember { mutableStateOf(initialForm.durationMaxMinutes) }

    // Weights: always a level in WEIGHT_LEVELS. Persisted via fragment-local prefs so the
    // user's chosen levels survive even when the master toggle is disabled.
    var weightsEnabled by remember { mutableStateOf(initialForm.weightsEnabled) }
    val weightLevels = remember {
        mutableStateMapOf<TournesolFilterCriterion, Int>().apply { putAll(initialForm.weightLevels) }
    }

    fun persistLevels() {
        val editor = prefs.edit().putBoolean(PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED, weightsEnabled)
        for (criterion in TournesolFilterCriterion.entries) {
            editor.putInt(criterion.weightLevelPrefKey, weightLevels.getValue(criterion))
        }
        editor.apply()
    }

    fun applyAll() {
        persistLevels()
        onApply(
            TournesolFilterForm(
                languages = selectedLanguages.toList(),
                dateRange = selectedDateRange,
                includeLowScoreVideos = includeLowScoreVideos,
                durationMinMinutes = durationMinMinutes,
                durationMaxMinutes = durationMaxMinutes,
                weightsEnabled = weightsEnabled,
                weightLevels = weightLevels.toMap()
            ).toConfig()
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
    val dateOptions = TournesolDateRange.entries

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp, bottom = 4.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        RoundedCornerShape(2.dp)
                    )
            )
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.filter_tournesol),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
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
                border = null
            )

            // Languages
            CompactSectionHeader(stringResource(R.string.filter_languages))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        border = null
                    )
                }
            }

            // Date
            CompactSectionHeader(stringResource(R.string.filter_date))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                dateOptions.forEach { dateRange ->
                    val isSelected = selectedDateRange == dateRange
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (!isSelected) {
                                selectedDateRange = dateRange
                                applyAll()
                            }
                        },
                        label = { Text(text = stringResource(dateRangeLabelRes(dateRange)), style = chipTextStyle) },
                        colors = chipColors,
                        shape = chipShape,
                        border = null
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
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filter_weights_enable),
                    style = MaterialTheme.typography.bodyLarge,
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
                        checkedThumbColor = MaterialTheme.colorScheme.surface,
                        checkedTrackColor = colorResource(R.color.tournesol_chip_bg_selected),
                        checkedIconColor = colorResource(R.color.tournesol_chip_bg_selected)
                    )
                )
            }
            TournesolFilterCriterion.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    pair.forEach { criterion ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactWeightSlider(
                                label = stringResource(criterionLabelRes(criterion)),
                                value = weightLevels.getValue(criterion),
                                enabled = weightsEnabled,
                                onValueChange = { weightLevels[criterion] = it },
                                onValueChangeFinished = { if (weightsEnabled) applyAll() }
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CompactSectionHeader(title: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDurationLabel(maxMinutes, DURATION_SLIDER_MAX),
                style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.fillMaxWidth()
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
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(weightLevelLabelRes(level)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
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
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }
}
