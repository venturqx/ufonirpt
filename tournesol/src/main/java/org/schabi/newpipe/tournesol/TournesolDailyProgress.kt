package org.schabi.newpipe.tournesol

data class DailyBaseline(val date: String, val count: Int)

data class DailyProgress(val count: Int, val baselineToPersist: DailyBaseline?)

fun dailyProgress(
    currentCount: Int?,
    today: String,
    storedDate: String?,
    storedCount: Int?
): DailyProgress {
    if (currentCount == null) {
        return DailyProgress(0, null)
    }
    if (storedDate != today) {
        return DailyProgress(0, DailyBaseline(today, currentCount))
    }
    val baseline = storedCount ?: currentCount
    return DailyProgress((currentCount - baseline).coerceAtLeast(0), null)
}
