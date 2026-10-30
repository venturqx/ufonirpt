package org.schabi.newpipe.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournesolHelperTest {

    // Fixed UTC timestamp: 2026-06-15T12:00:00.000Z (1781524800000L)
    private val fixedNowMillis = 1781524800000L
    private val oneHourMillis = 3_600_000L
    private val datedKeys = listOf("day", "week", "month", "3_months", "year")

    private fun millisOf(instant: String) = Instant.parse(instant).toEpochMilli()

    @Test
    fun `getDefaultFilterLanguages returns single language for fr or en and en plus lang otherwise`() {
        assertEquals("en", TournesolHelper.getDefaultFilterLanguages("en"))
        assertEquals("fr", TournesolHelper.getDefaultFilterLanguages("fr"))
        assertEquals("en,de", TournesolHelper.getDefaultFilterLanguages("de"))
        assertEquals("en", TournesolHelper.getDefaultFilterLanguages(""))
    }

    @Test
    fun `calculateDateGte computes deterministic UTC timestamps and returns null for forever or invalid`() {
        assertNull(TournesolHelper.calculateDateGte("forever", fixedNowMillis))
        assertNull(TournesolHelper.calculateDateGte("unknown_corrupt_key", fixedNowMillis))
        assertEquals(
            "2025-06-15T12:00:00.000Z",
            TournesolHelper.calculateDateGte("year", fixedNowMillis)
        )
        assertEquals(
            "2026-03-15T12:00:00.000Z",
            TournesolHelper.calculateDateGte("3_months", fixedNowMillis)
        )
        assertEquals(
            "2026-05-15T12:00:00.000Z",
            TournesolHelper.calculateDateGte("month", fixedNowMillis)
        )
        assertEquals(
            "2026-06-08T12:00:00.000Z",
            TournesolHelper.calculateDateGte("week", fixedNowMillis)
        )
        assertEquals(
            "2026-06-14T12:00:00.000Z",
            TournesolHelper.calculateDateGte("day", fixedNowMillis)
        )
    }

    @Test
    fun `calculateDateGte zeroes minutes, seconds and millis`() {
        val midHour = millisOf("2026-06-15T12:42:17.123Z")

        assertEquals("2025-06-15T12:00:00.000Z", TournesolHelper.calculateDateGte("year", midHour))
        assertEquals("2026-03-15T12:00:00.000Z", TournesolHelper.calculateDateGte("3_months", midHour))
        assertEquals("2026-05-15T12:00:00.000Z", TournesolHelper.calculateDateGte("month", midHour))
        assertEquals("2026-06-08T12:00:00.000Z", TournesolHelper.calculateDateGte("week", midHour))
        assertEquals("2026-06-14T12:00:00.000Z", TournesolHelper.calculateDateGte("day", midHour))
    }

    @Test
    fun `calculateDateGte keeps the hour for day instead of rounding to the day`() {
        val expectedByNow = mapOf(
            "2026-06-15T00:30:00.000Z" to "2026-06-14T00:00:00.000Z",
            "2026-06-15T23:59:59.999Z" to "2026-06-14T23:00:00.000Z",
            "2026-03-01T00:15:00.000Z" to "2026-02-28T00:00:00.000Z",
            "2026-01-01T00:59:59.999Z" to "2025-12-31T00:00:00.000Z"
        )

        for ((now, expected) in expectedByNow) {
            assertEquals(now, expected, TournesolHelper.calculateDateGte("day", millisOf(now)))
        }
    }

    @Test
    fun `calculateDateGte truncates after clamping month-end dates`() {
        val cases = listOf(
            Triple("month", "2026-03-31T18:45:30.500Z", "2026-02-28T18:00:00.000Z"),
            Triple("month", "2024-03-31T18:45:30.500Z", "2024-02-29T18:00:00.000Z"),
            Triple("3_months", "2026-05-31T18:45:30.500Z", "2026-02-28T18:00:00.000Z"),
            Triple("year", "2024-02-29T18:45:30.500Z", "2023-02-28T18:00:00.000Z"),
            Triple("week", "2026-01-03T05:20:00.000Z", "2025-12-27T05:00:00.000Z")
        )

        for ((dateKey, now, expected) in cases) {
            assertEquals("$dateKey $now", expected, TournesolHelper.calculateDateGte(dateKey, millisOf(now)))
        }
    }

    @Test
    fun `buildTournesolUrl is stable within a UTC hour so the cache key does not change`() {
        for (dateKey in datedKeys) {
            val config = TournesolFilterConfig(languages = listOf("en"), dateKey = dateKey)

            assertEquals(
                dateKey,
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis),
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis + oneHourMillis - 1)
            )
        }
    }

    @Test
    fun `buildTournesolUrl changes in the next UTC hour`() {
        for (dateKey in datedKeys) {
            val config = TournesolFilterConfig(languages = listOf("en"), dateKey = dateKey)

            assertNotEquals(
                dateKey,
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis + oneHourMillis - 1),
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis + oneHourMillis)
            )
        }
    }

    @Test
    fun `every criterion prefKey is pinned to its persisted literal`() {
        val expected = mapOf(
            TournesolFilterCriterion.LARGELY_RECOMMENDED to "tournesol_filter_weight_largely_recommended",
            TournesolFilterCriterion.RELIABILITY to "tournesol_filter_weight_reliability",
            TournesolFilterCriterion.IMPORTANCE to "tournesol_filter_weight_importance",
            TournesolFilterCriterion.PEDAGOGY to "tournesol_filter_weight_pedagogy",
            TournesolFilterCriterion.LAYMAN_FRIENDLY to "tournesol_filter_weight_layman_friendly",
            TournesolFilterCriterion.ENTERTAINING_RELAXING to "tournesol_filter_weight_entertaining_relaxing",
            TournesolFilterCriterion.ENGAGING to "tournesol_filter_weight_engaging",
            TournesolFilterCriterion.DIVERSITY_INCLUSION to "tournesol_filter_weight_diversity_inclusion",
            TournesolFilterCriterion.BETTER_HABITS to "tournesol_filter_weight_better_habits",
            TournesolFilterCriterion.BACKFIRE_RISK to "tournesol_filter_weight_backfire_risk"
        )

        assertEquals(expected, TournesolFilterCriterion.entries.associateWith { it.prefKey })
    }

    @Test
    fun `buildTournesolUrl encodes languages, date, duration, unsafe, and typed criterion weights`() {
        val config = TournesolFilterConfig(
            languages = listOf("en", "fr"),
            dateKey = "month",
            includeLowScoreVideos = true,
            durationMinSeconds = 120,
            durationMaxSeconds = 1800,
            weights = mapOf(
                TournesolFilterCriterion.RELIABILITY to 75,
                TournesolFilterCriterion.PEDAGOGY to 100,
                TournesolFilterCriterion.BACKFIRE_RISK to 0
            )
        )

        val url = TournesolHelper.buildTournesolUrl(config, fixedNowMillis)

        assertEquals(
            "Tournesol?languages=en,fr" +
                "&date_gte=2026-05-15T12:00:00.000Z" +
                "&unsafe=true" +
                "&duration_gte=120" +
                "&duration_lte=1800" +
                "&weight_reliability=75" +
                "&weight_pedagogy=100" +
                "&weight_backfire_risk=0",
            url
        )
    }

    @Test
    fun `hasActiveAdvancedFilters detects non-quick dates, durations, or active weights`() {
        val quickKeys = setOf("day", "week", "month", "3_months", "year")

        val base = TournesolFilterConfig(dateKey = "3_months")
        assertFalse(base.hasActiveAdvancedFilters(quickKeys))

        assertTrue(base.copy(dateKey = "forever").hasActiveAdvancedFilters(quickKeys))
        assertTrue(base.copy(durationMinSeconds = 60).hasActiveAdvancedFilters(quickKeys))
        assertTrue(
            base.copy(weights = mapOf(TournesolFilterCriterion.IMPORTANCE to 50))
                .hasActiveAdvancedFilters(quickKeys)
        )
    }

    @Test
    fun `formatUnsafeReasonCodes deduplicates and maps known reason codes`() {
        assertEquals("", TournesolHelper.formatUnsafeReasonCodes(null))
        assertEquals("", TournesolHelper.formatUnsafeReasonCodes(emptyList()))
        assertEquals(
            "(Insufficient/Moderated)",
            TournesolHelper.formatUnsafeReasonCodes(
                listOf(
                    "insufficient_tournesol_score",
                    "insufficient_trust",
                    "moderation_by_contributors"
                )
            )
        )
        assertTrue(TournesolHelper.hasInsufficientReason(listOf("insufficient_trust")))
        assertFalse(TournesolHelper.hasInsufficientReason(listOf("moderation_by_contributors")))
    }

    @Test
    fun `TournesolLruScoreCache evicts least recently used entry when exceeding capacity`() {
        val cache = TournesolLruScoreCache(maxEntries = 2)
        cache.put("url1", 10L, listOf("insufficient_trust"))
        cache.put("url2", 20L)

        // Access url1 so url2 becomes the least-recently-used entry
        assertEquals(10L, cache.getEntry("url1")?.score)

        // Inserting url3 should evict url2
        cache.put("url3", 30L)

        assertEquals(10L, cache.getEntry("url1")?.score)
        assertNull(cache.getEntry("url2"))
        assertEquals(30L, cache.getEntry("url3")?.score)
        assertEquals(listOf("insufficient_trust"), cache.getEntry("url1")?.unsafeReasons)
    }
}
