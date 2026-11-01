package org.schabi.newpipe.tournesol.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.CompareComparisonVideo
import org.schabi.newpipe.tournesol.CompareRecommendationItem
import org.schabi.newpipe.tournesol.ComparisonScores

internal data class CompareMessage(@StringRes val res: Int, val arg: String? = null) {
    fun resolve(context: Context): String = if (arg == null) context.getString(res) else context.getString(res, arg)

    @Composable
    fun resolve(): String = if (arg == null) stringResource(res) else stringResource(res, arg)
}

internal fun compareFailedMessage(detail: String?): CompareMessage = if (detail == null) {
    CompareMessage(R.string.compare_failed)
} else {
    CompareMessage(R.string.compare_failed_with_message, detail)
}

internal sealed interface CompareLoginState {
    data object Hidden : CompareLoginState

    data class Open(val error: CompareMessage? = null) : CompareLoginState

    data object Submitting : CompareLoginState
}

internal sealed interface CompareRecommendations {
    val totalCount: Int?
        get() = null

    data object Loading : CompareRecommendations

    data class Loaded(
        val items: List<CompareRecommendationItem> = emptyList(),
        override val totalCount: Int? = null
    ) : CompareRecommendations

    data class Failed(
        val message: CompareMessage,
        override val totalCount: Int? = null
    ) : CompareRecommendations
}

@Stable
class CompareUiState {
    internal var history by mutableStateOf<CompareHistoryState>(CompareHistoryState.Loading)
    val historyEntries: List<StreamHistoryEntry>
        get() = history.entries
    var currentEntry by mutableStateOf<StreamHistoryEntry?>(null)
    var score by mutableIntStateOf(0)
    var extraScores by mutableStateOf(defaultExtraScores())
    var storedScores by mutableStateOf(ComparisonScores())
    val extraSubmitted: Boolean
        get() = storedScores.extraScores.isNotEmpty()
    var submitted by mutableStateOf(false)
    var submitInProgress by mutableStateOf(false)
    internal var recommendations by mutableStateOf<CompareRecommendations>(CompareRecommendations.Loaded())
    var showRecommendationsDialog by mutableStateOf(false)
    internal var login by mutableStateOf<CompareLoginState>(CompareLoginState.Hidden)
    var suggestedLeft by mutableStateOf<CompareComparisonVideo?>(null)
    var suggestedRight by mutableStateOf<CompareComparisonVideo?>(null)
    var suggestionsLoading by mutableStateOf(false)
    var weeklyComparisons by mutableStateOf<Int?>(null)
    var dailyComparisons by mutableStateOf<Int?>(null)
    var username by mutableStateOf<String?>(null)
    var comparisonCount by mutableStateOf<Int?>(null)

    fun updateExtraScore(criteria: String, value: Int) {
        extraScores = extraScores + (criteria to value)
    }
}
