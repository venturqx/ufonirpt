package org.schabi.newpipe.fragments.detail

internal sealed interface AppBarChildLimit {
    data object Pinned : AppBarChildLimit
    data object ScrollsAway : AppBarChildLimit
    data class Collapsible(val minimumHeight: Int) : AppBarChildLimit
}

internal fun compareAppBarLimits(
    contentHeight: Int,
    visibleHeight: Int,
    childHeights: List<Int>
): List<AppBarChildLimit>? {
    if (visibleHeight <= 0) return null

    var overflow = (contentHeight - visibleHeight).coerceAtLeast(0)
    return childHeights.map { height ->
        val limit = when {
            overflow == 0 || height <= 0 -> AppBarChildLimit.Pinned
            overflow > height -> AppBarChildLimit.ScrollsAway
            else -> AppBarChildLimit.Collapsible(height - overflow)
        }
        overflow -= height.coerceIn(0, overflow)
        limit
    }
}
