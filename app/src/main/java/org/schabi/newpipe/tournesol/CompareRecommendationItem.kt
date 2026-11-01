package org.schabi.newpipe.tournesol

data class CompareComparisonVideo(
    val uid: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val videoUrl: String?,
    val serviceId: Int? = null
)

data class CompareRecommendationItem(
    val uid: String,
    val videoA: CompareComparisonVideo,
    val videoB: CompareComparisonVideo,
    val largelyRecommendedScore: Int?,
    val scoreMax: Int?
)

data class CompareComparisonsResult(
    val totalCount: Int?,
    val comparisons: List<CompareRecommendationItem>
)
