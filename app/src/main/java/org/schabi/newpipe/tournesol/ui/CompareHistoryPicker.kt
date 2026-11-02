package org.schabi.newpipe.tournesol.ui

import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random
import org.schabi.newpipe.database.history.model.StreamHistoryEntry

internal class CompareHistoryPicker(private val random: Random) {
    private val recentKeys = ArrayDeque<Pair<Int, String>>()

    fun pickIndex(candidates: List<StreamHistoryEntry>): Int? {
        if (candidates.isEmpty()) {
            return null
        }
        val byRecency = candidates.indices.sortedByDescending { candidates[it].accessDate }
        val rank = IntArray(candidates.size)
        byRecency.forEachIndexed { position, index -> rank[index] = position }
        val fresh = byRecency.filter { historyKey(candidates[it]) !in recentKeys }.ifEmpty { byRecency }
        val weights = fresh.map { weight(rank[it], candidates[it].repeatCount) }
        var target = random.nextDouble() * weights.sum()
        var picked = fresh.last()
        for (i in fresh.indices) {
            target -= weights[i]
            if (target < 0) {
                picked = fresh[i]
                break
            }
        }
        remember(historyKey(candidates[picked]))
        return picked
    }

    private fun remember(key: Pair<Int, String>) {
        recentKeys.remove(key)
        recentKeys.addLast(key)
        if (recentKeys.size > RECENT_LIMIT) {
            recentKeys.removeFirst()
        }
    }

    private fun weight(rank: Int, repeatCount: Long): Double = 0.5.pow(rank / HALF_LIFE_RANKS) * (1 + ln(repeatCount.coerceAtLeast(1).toDouble()))

    private fun historyKey(entry: StreamHistoryEntry): Pair<Int, String> = entry.streamEntity.serviceId to entry.streamEntity.url

    private companion object {
        const val HALF_LIFE_RANKS = 15.0
        const val RECENT_LIMIT = 15
    }
}
