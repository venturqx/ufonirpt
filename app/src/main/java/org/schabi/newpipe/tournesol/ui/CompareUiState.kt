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
import org.schabi.newpipe.tournesol.ComparePairSelection
import org.schabi.newpipe.tournesol.CompareRecommendationItem
import org.schabi.newpipe.tournesol.CompareSubmitFailure
import org.schabi.newpipe.tournesol.ComparisonScores
import org.schabi.newpipe.tournesol.compareKeyFor

internal data class CompareMessage(@StringRes val res: Int) {
    fun resolve(context: Context): String = context.getString(res)

    @Composable
    fun resolve(): String = stringResource(res)
}

internal fun compareFailedMessage(failure: CompareSubmitFailure): CompareMessage = if (failure == CompareSubmitFailure.Network) {
    CompareMessage(R.string.network_error)
} else {
    CompareMessage(R.string.compare_failed)
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

internal sealed interface CompareScreenStatus {
    data object Loading : CompareScreenStatus

    data object NeedsLogin : CompareScreenStatus

    data class NoPair(@StringRes val messageRes: Int) : CompareScreenStatus

    data object Ready : CompareScreenStatus
}

internal fun compareScreenStatus(
    state: CompareUiState,
    selection: ComparePairSelection?,
    uidOf: (url: String, serviceId: Int) -> String?
): CompareScreenStatus = when {
    !state.profileLoaded -> CompareScreenStatus.Loading
    state.username == null -> CompareScreenStatus.NeedsLogin
    selection != null && compareKeyFor(selection, uidOf) != null -> CompareScreenStatus.Ready
    state.suggestionsLoading || state.history == CompareHistoryState.Loading -> CompareScreenStatus.Loading
    selection != null -> CompareScreenStatus.NoPair(R.string.compare_service_not_supported)
    else -> CompareScreenStatus.NoPair(R.string.compare_no_pair)
}

@Stable
internal class ComparePickerProgress {
    var pairKey: Any? = null
    val activeIndex = mutableIntStateOf(EXTRA_CRITERIA.size)
    val touchedCriteria = mutableStateOf(setOf<String>())
    val allExtrasDone = mutableStateOf(false)
    val submittedCriteria = mutableStateOf(setOf<String>())
    val mainUnsaved = mutableStateOf(false)
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
    var profileLoaded by mutableStateOf(false)
    var comparisonCount by mutableStateOf<Int?>(null)
    internal val picker = ComparePickerProgress()

    fun updateExtraScore(criteria: String, value: Int) {
        extraScores = extraScores + (criteria to value)
    }
}
