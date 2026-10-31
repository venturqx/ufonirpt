package org.schabi.newpipe.fragments.detail

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry

@Stable
class CompareUiState {
    var historyEntries by mutableStateOf<List<StreamHistoryEntry>>(emptyList())
    var currentEntry by mutableStateOf<StreamHistoryEntry?>(null)
    var historyMessageRes by mutableStateOf<Int?>(R.string.compare_loading_history)
    var score by mutableIntStateOf(0)
    var extraScores by mutableStateOf(defaultExtraScores())
    var storedMainScore by mutableStateOf<Int?>(null)
    var storedExtraScores by mutableStateOf<Map<String, Int>>(emptyMap())
    var submitted by mutableStateOf(false)
    var extraSubmitted by mutableStateOf(false)
    var submitInProgress by mutableStateOf(false)
    var recommendations by mutableStateOf<List<CompareRecommendationItem>>(emptyList())
    var recommendationsTotalCount by mutableStateOf<Int?>(null)
    var recommendationsLoading by mutableStateOf(false)
    var recommendationsError by mutableStateOf<String?>(null)
    var showRecommendationsDialog by mutableStateOf(false)
    var showLoginDialog by mutableStateOf(false)
    var loginInProgress by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var suggestedLeft by mutableStateOf<CompareComparisonVideo?>(null)
    var suggestedRight by mutableStateOf<CompareComparisonVideo?>(null)
    var suggestionsLoading by mutableStateOf(false)
    var weeklyComparisons by mutableStateOf<Int?>(null)
    var dailyComparisons by mutableStateOf<Int?>(null)
    var username by mutableStateOf<String?>(null)
    var comparisonCount by mutableStateOf<Int?>(null)
}
