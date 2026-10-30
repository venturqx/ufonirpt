package org.schabi.newpipe.fragments.detail.compare

import org.schabi.newpipe.fragments.detail.CompareComparisonVideo
import org.schabi.newpipe.fragments.detail.ComparePairSelection
import org.schabi.newpipe.fragments.detail.TournesolUid

internal fun suggestionServiceId(video: CompareComparisonVideo): Int? = video.serviceId ?: TournesolUid.parse(video.uid)?.serviceId

internal fun suggestedPair(
    left: CompareComparisonVideo,
    right: CompareComparisonVideo
): ComparePairSelection? {
    val leftUrl = left.videoUrl ?: return null
    val rightUrl = right.videoUrl ?: return null
    val leftServiceId = suggestionServiceId(left) ?: return null
    val rightServiceId = suggestionServiceId(right) ?: return null
    if (left.uid == right.uid) {
        return null
    }
    return ComparePairSelection(leftServiceId, leftUrl, rightServiceId, rightUrl)
}

internal fun historyPair(
    leftServiceId: Int,
    leftUrl: String,
    rightServiceId: Int,
    rightUrl: String
): ComparePairSelection? {
    if (leftServiceId == rightServiceId && leftUrl == rightUrl) {
        return null
    }
    return ComparePairSelection(leftServiceId, leftUrl, rightServiceId, rightUrl)
}
