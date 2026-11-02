package org.schabi.newpipe.tournesol.ui

import java.util.EnumMap
import kotlin.random.Random
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.tournesol.CompareComparisonVideo

internal enum class SuggestionSlot { LEFT, RIGHT }

internal enum class SuggestionFetch { START, NOT_NEEDED }

internal class CompareSuggestionPool(
    private val historyPairAllowed: Boolean,
    private val random: Random,
    private val toVideo: (StreamHistoryEntry) -> CompareComparisonVideo
) {
    private enum class Source { API, HISTORY, HISTORY_AWAITING_PAIR }

    private val historyPicker = CompareHistoryPicker(random)
    private val pool = mutableListOf<CompareComparisonVideo>()
    private val picks = EnumMap<SuggestionSlot, CompareComparisonVideo>(SuggestionSlot::class.java)
    private var source = Source.API
    private var slotsAwaitingFetch: Set<SuggestionSlot>? = null

    val left: CompareComparisonVideo?
        get() = picks[SuggestionSlot.LEFT]

    val right: CompareComparisonVideo?
        get() = picks[SuggestionSlot.RIGHT]

    val fetchInFlight: Boolean
        get() = slotsAwaitingFetch != null

    fun prefetch(): SuggestionFetch = awaitFetch(emptySet())

    fun requestPair(): SuggestionFetch = awaitFetch(SuggestionSlot.entries.toSet())

    fun randomize(slot: SuggestionSlot, history: List<StreamHistoryEntry>): SuggestionFetch {
        if (source != Source.API) {
            pickFromHistory(slot, history)
            return SuggestionFetch.NOT_NEEDED
        }
        return if (pickFromPool(slot)) SuggestionFetch.NOT_NEEDED else awaitFetch(setOf(slot))
    }

    fun onFetched(videos: List<CompareComparisonVideo>) {
        val slots = endFetch()
        source = Source.API
        pool.clear()
        pool.addAll(videos)
        slots.forEach { pickFromPool(it) }
    }

    fun onMissingToken(history: List<StreamHistoryEntry>) {
        val slots = endFetch()
        if (slots.isEmpty()) {
            return
        }
        pool.clear()
        if (loadHistoryPair(history)) {
            source = Source.HISTORY
        } else {
            source = Source.HISTORY_AWAITING_PAIR
            slots.forEach { pickFromHistory(it, history) }
        }
    }

    fun onFetchAbandoned() {
        slotsAwaitingFetch = null
    }

    fun onHistoryUpdated(history: List<StreamHistoryEntry>) {
        if (source == Source.HISTORY_AWAITING_PAIR && loadHistoryPair(history)) {
            source = Source.HISTORY
        }
    }

    fun clear() {
        pool.clear()
        picks.clear()
    }

    private fun awaitFetch(slots: Set<SuggestionSlot>): SuggestionFetch {
        val inFlight = slotsAwaitingFetch
        slotsAwaitingFetch = inFlight.orEmpty() + slots
        return if (inFlight == null) SuggestionFetch.START else SuggestionFetch.NOT_NEEDED
    }

    private fun endFetch(): List<SuggestionSlot> {
        val slots = slotsAwaitingFetch.orEmpty().sorted()
        slotsAwaitingFetch = null
        return slots
    }

    private fun pickFromPool(slot: SuggestionSlot): Boolean {
        val excludedUid = picks[slot.other()]?.uid
        val picked = pool.filter { it.uid != excludedUid }.randomOrNull(random) ?: return false
        pool.remove(picked)
        picks[slot] = picked
        return true
    }

    private fun pickFromHistory(slot: SuggestionSlot, history: List<StreamHistoryEntry>) {
        val excludedKeys = picks.values.mapNotNull { videoKey(it) }
        val candidates = history
            .distinctBy { entryKey(it) }
            .filter { entryKey(it) !in excludedKeys }
        val picked = historyPicker.pickIndex(candidates) ?: return
        picks[slot] = toVideo(candidates[picked])
    }

    private fun loadHistoryPair(history: List<StreamHistoryEntry>): Boolean {
        if (!historyPairAllowed) {
            return false
        }
        val candidates = history.distinctBy { entryKey(it) }.toMutableList()
        if (candidates.size < 2) {
            return false
        }
        val left = candidates.removeAt(historyPicker.pickIndex(candidates) ?: return false)
        val right = candidates.removeAt(historyPicker.pickIndex(candidates) ?: return false)
        picks[SuggestionSlot.LEFT] = toVideo(left)
        picks[SuggestionSlot.RIGHT] = toVideo(right)
        return true
    }

    private fun SuggestionSlot.other(): SuggestionSlot = if (this == SuggestionSlot.LEFT) SuggestionSlot.RIGHT else SuggestionSlot.LEFT

    private fun entryKey(entry: StreamHistoryEntry): Pair<Int, String> = entry.streamEntity.serviceId to entry.streamEntity.url

    private fun videoKey(video: CompareComparisonVideo): Pair<Int, String>? {
        val serviceId = video.serviceId ?: return null
        val videoUrl = video.videoUrl ?: return null
        return serviceId to videoUrl
    }
}
