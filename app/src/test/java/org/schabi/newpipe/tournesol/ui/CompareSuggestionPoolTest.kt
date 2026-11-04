package org.schabi.newpipe.tournesol.ui

import java.time.OffsetDateTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.database.history.model.StreamHistoryEntry
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.tournesol.CompareComparisonVideo

class CompareSuggestionPoolTest {

    private val seeds = 0 until 50

    private fun video(uid: String) = CompareComparisonVideo(
        uid = uid,
        title = "title",
        uploader = "uploader",
        thumbnailUrl = null,
        videoUrl = "https://example.com/$uid"
    )

    private fun videos(count: Int) = List(count) { video("yt:$it") }

    private fun entry(url: String, serviceId: Int = 0, streamId: Long = 0) = StreamHistoryEntry(
        streamEntity = StreamEntity(
            serviceId = serviceId,
            url = url,
            title = "title",
            streamType = StreamType.VIDEO_STREAM,
            duration = 0,
            uploader = "uploader"
        ),
        streamId = streamId,
        accessDate = OffsetDateTime.MIN,
        repeatCount = 0
    )

    private fun history(count: Int) = List(count) { entry("https://example.com/$it") }

    private fun historyVideo(entry: StreamHistoryEntry) = CompareComparisonVideo(
        uid = "history:${entry.streamEntity.serviceId}:${entry.streamEntity.url}",
        title = entry.streamEntity.title,
        uploader = entry.streamEntity.uploader,
        thumbnailUrl = null,
        videoUrl = entry.streamEntity.url,
        serviceId = entry.streamEntity.serviceId
    )

    private fun newPool(seed: Int = 0, historyPairAllowed: Boolean = true) = CompareSuggestionPool(
        historyPairAllowed = historyPairAllowed,
        random = Random(seed),
        toVideo = ::historyVideo
    )

    private fun assertDistinctPair(pool: CompareSuggestionPool) {
        assertNotNull(pool.left)
        assertNotNull(pool.right)
        assertNotEquals(pool.left?.uid, pool.right?.uid)
    }

    private fun assertBothEmpty(pool: CompareSuggestionPool) {
        assertNull(pool.left)
        assertNull(pool.right)
    }

    @Test
    fun `an empty fetch leaves both slots empty`() {
        val pool = newPool()
        pool.requestPair()
        pool.onFetched(emptyList())
        assertBothEmpty(pool)
        assertFalse(pool.fetchInFlight)
    }

    @Test
    fun `a single video fills only the left slot`() {
        val pool = newPool()
        pool.requestPair()
        pool.onFetched(videos(1))
        assertEquals(video("yt:0"), pool.left)
        assertNull(pool.right)
    }

    @Test
    fun `two videos fill both slots`() {
        seeds.forEach { seed ->
            val pool = newPool(seed)
            pool.requestPair()
            pool.onFetched(videos(2))
            assertDistinctPair(pool)
        }
    }

    @Test
    fun `a large pool yields a distinct pair of fetched videos`() {
        seeds.forEach { seed ->
            val fetched = videos(20)
            val pool = newPool(seed)
            pool.requestPair()
            pool.onFetched(fetched)
            assertDistinctPair(pool)
            assertTrue(pool.left in fetched)
            assertTrue(pool.right in fetched)
        }
    }

    @Test
    fun `duplicate uids in a fetch never reach both slots`() {
        val pool = newPool()
        pool.requestPair()
        pool.onFetched(listOf(video("yt:a"), video("yt:a")))
        assertEquals(video("yt:a"), pool.left)
        assertNull(pool.right)
    }

    @Test
    fun `randomize never picks the video in the other slot`() {
        seeds.forEach { seed ->
            val pool = newPool(seed)
            pool.requestPair()
            pool.onFetched(videos(2))
            val right = pool.right
            val left = pool.left
            pool.randomize(SuggestionSlot.LEFT, emptyList())
            pool.onFetched(listOf(right!!))
            assertEquals(left, pool.left)
            assertEquals(right, pool.right)
        }
    }

    @Test
    fun `picked videos leave the pool`() {
        seeds.forEach { seed ->
            val pool = newPool(seed)
            pool.requestPair()
            pool.onFetched(videos(6))
            val seen = mutableSetOf(pool.left, pool.right)
            repeat(4) {
                assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, emptyList()))
                assertTrue(seen.add(pool.left))
            }
            assertEquals(SuggestionFetch.START, pool.randomize(SuggestionSlot.LEFT, emptyList()))
        }
    }

    @Test
    fun `a tap during the initial pair fetch keeps the other slot pending`() {
        val pool = newPool()
        assertEquals(SuggestionFetch.START, pool.requestPair())
        assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, emptyList()))
        assertTrue(pool.fetchInFlight)
        pool.onFetched(videos(5))
        assertDistinctPair(pool)
        assertFalse(pool.fetchInFlight)
    }

    @Test
    fun `a tap during a prefetch joins it instead of restarting`() {
        val pool = newPool()
        assertEquals(SuggestionFetch.START, pool.prefetch())
        assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.RIGHT, emptyList()))
        pool.onFetched(videos(2))
        assertNull(pool.left)
        assertNotNull(pool.right)
    }

    @Test
    fun `tapping both slots during one fetch fills both from the same result`() {
        val pool = newPool()
        assertEquals(SuggestionFetch.START, pool.randomize(SuggestionSlot.RIGHT, emptyList()))
        assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, emptyList()))
        pool.onFetched(videos(2))
        assertDistinctPair(pool)
    }

    @Test
    fun `a prefetch fills no slot but serves the next tap without fetching`() {
        val pool = newPool()
        pool.prefetch()
        pool.onFetched(videos(2))
        assertBothEmpty(pool)
        assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, emptyList()))
        assertNotNull(pool.left)
    }

    @Test
    fun `an abandoned fetch forgets its slots and allows a new fetch`() {
        val pool = newPool()
        pool.requestPair()
        pool.onFetchAbandoned()
        assertFalse(pool.fetchInFlight)
        assertEquals(SuggestionFetch.START, pool.randomize(SuggestionSlot.LEFT, emptyList()))
        pool.onFetched(videos(2))
        assertNotNull(pool.left)
        assertNull(pool.right)
    }

    @Test
    fun `missing token during a pair fetch loads a history pair`() {
        seeds.forEach { seed ->
            val pool = newPool(seed)
            pool.requestPair()
            pool.onMissingToken(history(3))
            assertDistinctPair(pool)
            assertFalse(pool.fetchInFlight)
        }
    }

    @Test
    fun `missing token during a prefetch does not activate the fallback`() {
        val pool = newPool()
        pool.prefetch()
        pool.onMissingToken(history(3))
        assertBothEmpty(pool)
        assertFalse(pool.fetchInFlight)
        assertEquals(SuggestionFetch.START, pool.randomize(SuggestionSlot.LEFT, history(3)))
    }

    @Test
    fun `missing token fills the tapped slot when a history pair is not allowed`() {
        val pool = newPool(historyPairAllowed = false)
        pool.randomize(SuggestionSlot.RIGHT, emptyList())
        pool.onMissingToken(history(3))
        assertNull(pool.left)
        assertNotNull(pool.right)
    }

    @Test
    fun `the fallback answers taps from history without fetching`() {
        seeds.forEach { seed ->
            val entries = history(3)
            val pool = newPool(seed)
            pool.requestPair()
            pool.onMissingToken(entries)
            val previous = listOf(pool.left, pool.right)
            assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, entries))
            assertFalse(pool.left in previous)
            assertDistinctPair(pool)
        }
    }

    @Test
    fun `the fallback keeps the slot when history has no other video`() {
        val entries = history(2)
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(entries)
        val left = pool.left
        val right = pool.right
        pool.randomize(SuggestionSlot.LEFT, entries)
        assertEquals(left, pool.left)
        assertEquals(right, pool.right)
    }

    @Test
    fun `the history pair waits until two videos are available`() {
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(emptyList())
        assertBothEmpty(pool)
        pool.onHistoryUpdated(history(1))
        assertBothEmpty(pool)
        pool.onHistoryUpdated(history(3))
        assertDistinctPair(pool)
    }

    @Test
    fun `the history pair is loaded only once`() {
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(emptyList())
        pool.onHistoryUpdated(history(20))
        val left = pool.left
        val right = pool.right
        pool.onHistoryUpdated(history(20).reversed())
        assertEquals(left, pool.left)
        assertEquals(right, pool.right)
    }

    @Test
    fun `history updates load no pair when a pair is not allowed`() {
        val pool = newPool(historyPairAllowed = false)
        pool.randomize(SuggestionSlot.LEFT, emptyList())
        pool.onMissingToken(emptyList())
        pool.onHistoryUpdated(history(3))
        assertBothEmpty(pool)
    }

    @Test
    fun `duplicate history entries count as one video`() {
        val entries = listOf(entry("a", streamId = 1), entry("a", streamId = 2))
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(entries)
        assertNotNull(pool.left)
        assertNull(pool.right)
    }

    @Test
    fun `duplicate history entries never reach both slots`() {
        seeds.forEach { seed ->
            val entries = listOf(entry("a"), entry("a"), entry("b"), entry("b"))
            val pool = newPool(seed)
            pool.requestPair()
            pool.onMissingToken(entries)
            assertDistinctPair(pool)
        }
    }

    @Test
    fun `the same url on another service is a different history video`() {
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(listOf(entry("a", serviceId = 0), entry("a", serviceId = 1)))
        assertDistinctPair(pool)
    }

    @Test
    fun `a successful fetch leaves the fallback`() {
        val fetched = videos(3)
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(history(3))
        assertEquals(SuggestionFetch.START, pool.prefetch())
        pool.onFetched(fetched)
        assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, history(3)))
        assertTrue(pool.left in fetched)
    }

    @Test
    fun `a successful fetch cancels the waiting history pair`() {
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(emptyList())
        pool.prefetch()
        pool.onFetched(videos(2))
        pool.onHistoryUpdated(history(3))
        assertBothEmpty(pool)
    }

    @Test
    fun `drop empties only the dropped slot`() {
        val pool = newPool()
        pool.requestPair()
        pool.onFetched(videos(2))
        val right = pool.right
        pool.drop(SuggestionSlot.LEFT)
        assertNull(pool.left)
        assertEquals(right, pool.right)
    }

    @Test
    fun `a dropped slot is not refilled by the fetch in flight`() {
        val pool = newPool()
        pool.requestPair()
        pool.drop(SuggestionSlot.LEFT)
        assertTrue(pool.fetchInFlight)
        pool.onFetched(videos(5))
        assertNull(pool.left)
        assertNotNull(pool.right)
    }

    @Test
    fun `clear drops the pool and the suggestions`() {
        val pool = newPool()
        pool.requestPair()
        pool.onFetched(videos(5))
        pool.clear()
        assertBothEmpty(pool)
        assertEquals(SuggestionFetch.START, pool.randomize(SuggestionSlot.LEFT, emptyList()))
    }

    @Test
    fun `clear keeps the fallback active`() {
        val entries = history(3)
        val pool = newPool()
        pool.requestPair()
        pool.onMissingToken(entries)
        pool.clear()
        assertBothEmpty(pool)
        assertEquals(SuggestionFetch.NOT_NEEDED, pool.randomize(SuggestionSlot.LEFT, entries))
        assertNotNull(pool.left)
    }
}
