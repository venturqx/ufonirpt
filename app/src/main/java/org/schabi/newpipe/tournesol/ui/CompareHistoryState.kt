package org.schabi.newpipe.tournesol.ui

import androidx.annotation.StringRes
import java.time.OffsetDateTime
import kotlin.math.abs
import org.schabi.newpipe.R
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.CompareComparisonVideo

internal sealed interface CompareHistoryState {
    val entries: List<StreamHistoryEntry>
        get() = emptyList()

    @get:StringRes
    val noPairMessageRes: Int

    data object Loading : CompareHistoryState {
        override val noPairMessageRes = R.string.compare_loading_history
    }

    data object Unavailable : CompareHistoryState {
        override val noPairMessageRes = R.string.compare_history_unavailable
    }

    data object Empty : CompareHistoryState {
        override val noPairMessageRes = R.string.compare_history_empty
    }

    data class Ready(override val entries: List<StreamHistoryEntry>) : CompareHistoryState {
        override val noPairMessageRes = R.string.compare_history_unavailable
    }
}

internal fun CompareHistoryState.released(): CompareHistoryState = if (this is CompareHistoryState.Ready) CompareHistoryState.Unavailable else this

internal fun prepareHistory(
    entries: List<StreamHistoryEntry>,
    current: TournesolVideoRef?
): CompareHistoryState {
    val sorted = entries.sortedByDescending { it.accessDate }
    val filtered = if (current == null) {
        sorted
    } else {
        sorted.filterNot { entry ->
            entry.streamEntity.serviceId == current.serviceId &&
                entry.streamEntity.url == current.url
        }
    }
    return if (filtered.isEmpty()) CompareHistoryState.Empty else CompareHistoryState.Ready(filtered)
}

internal fun nextSelectedStream(selected: Long?, entries: List<StreamHistoryEntry>): Long? {
    val existing = selected?.takeIf { id -> entries.any { it.streamId == id } }
    return existing ?: entries.firstOrNull()?.streamId
}

internal fun buildCurrentEntry(video: TournesolVideoRef, now: OffsetDateTime): StreamHistoryEntry {
    val hash = video.url.hashCode().toLong()
    val syntheticId = if (hash == 0L) -1L else -abs(hash)
    return StreamHistoryEntry(
        streamEntity = video.stream,
        streamId = syntheticId,
        accessDate = now,
        repeatCount = 0
    )
}

internal fun buildHistorySuggestion(
    entry: StreamHistoryEntry,
    uidOf: (url: String, serviceId: Int) -> String?
): CompareComparisonVideo {
    val entity = entry.streamEntity
    val uid = uidOf(entity.url, entity.serviceId)
        ?: "history:${entity.serviceId}:${entry.streamId}"
    return CompareComparisonVideo(
        uid = uid,
        title = entity.title,
        uploader = entity.uploader,
        thumbnailUrl = entity.thumbnailUrl,
        videoUrl = entity.url,
        serviceId = entity.serviceId
    )
}
