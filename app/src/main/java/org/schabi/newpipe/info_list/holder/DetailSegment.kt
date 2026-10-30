package org.schabi.newpipe.info_list.holder

internal sealed interface DetailSegment {
    data class Score(val value: Long, val insufficient: Boolean) : DetailSegment

    data class Date(val text: String) : DetailSegment

    data class Views(val text: String) : DetailSegment

    data class Comparisons(val count: Int) : DetailSegment
}

internal fun detailSegments(
    score: Long?,
    insufficient: Boolean,
    date: String?,
    viewText: String?,
    comparisons: Int
): List<DetailSegment> = buildList {
    if (score != null) {
        add(DetailSegment.Score(score, insufficient))
    }
    if (!date.isNullOrEmpty()) {
        add(DetailSegment.Date(date))
    }
    if (viewText != null) {
        add(DetailSegment.Views(viewText))
    }
    if (comparisons >= 0) {
        add(DetailSegment.Comparisons(comparisons))
    }
}
