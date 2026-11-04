package org.schabi.newpipe.tournesol.ui

import java.time.OffsetDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType

class CompareHistoryPickerTest {

    private val now = OffsetDateTime.parse("2026-11-03T12:00:00Z")

    private fun entry(id: Int, hoursAgo: Long = id.toLong(), repeatCount: Long = 1) = StreamHistoryEntry(
        streamEntity = StreamEntity(
            serviceId = 0,
            url = "https://example.com/$id",
            title = "title",
            streamType = StreamType.VIDEO_STREAM,
            duration = 0,
            uploader = "uploader"
        ),
        streamId = id.toLong(),
        accessDate = now.minusHours(hoursAgo),
        repeatCount = repeatCount
    )

    private fun firstPicks(candidates: List<StreamHistoryEntry>, draws: Int = 1000): List<Int> = List(draws) { seed -> CompareHistoryPicker(Random(seed)).pickIndex(candidates)!! }

    @Test
    fun `no candidates picks nothing`() {
        assertNull(CompareHistoryPicker(Random(0)).pickIndex(emptyList()))
    }

    @Test
    fun `recent videos are picked far more often than old ones`() {
        val candidates = List(200) { entry(it) }.reversed()
        val ids = firstPicks(candidates).map { candidates[it].streamId }
        assertTrue(ids.count { it < 30 } > 400)
        assertTrue(ids.count { it >= 150 } < 80)
    }

    @Test
    fun `old videos can still be picked`() {
        val candidates = List(200) { entry(it) }
        assertTrue(firstPicks(candidates).any { candidates[it].streamId >= 60 })
    }

    @Test
    fun `rewatched videos are favoured`() {
        val candidates = listOf(entry(0, repeatCount = 1), entry(1, repeatCount = 20))
        val picks = firstPicks(candidates)
        assertTrue(picks.count { it == 1 } > picks.count { it == 0 } * 2)
    }

    @Test
    fun `the last fifteen picks are not repeated`() {
        repeat(50) { seed ->
            val picker = CompareHistoryPicker(Random(seed))
            val candidates = List(30) { entry(it) }
            val picked = List(15) { candidates[picker.pickIndex(candidates)!!].streamId }
            assertEquals(15, picked.toSet().size)
        }
    }

    @Test
    fun `a small history still yields a pick when every video was recent`() {
        val picker = CompareHistoryPicker(Random(0))
        val candidates = List(3) { entry(it) }
        repeat(20) { assertNotNull(picker.pickIndex(candidates)) }
    }
}
