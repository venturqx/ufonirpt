package org.schabi.newpipe.util

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

class TournesolHelperTest {

    // Fixed UTC timestamp: 2026-06-15T12:00:00.000Z (1781524800000L)
    private val fixedNowMillis = 1781524800000L
    private val oneHourMillis = 3_600_000L
    private val datedRanges = listOf(
        TournesolDateRange.DAY,
        TournesolDateRange.WEEK,
        TournesolDateRange.MONTH,
        TournesolDateRange.THREE_MONTHS,
        TournesolDateRange.YEAR
    )

    private fun millisOf(instant: String) = Instant.parse(instant).toEpochMilli()

    @Test
    fun `getDefaultFilterLanguages returns single language for fr or en and en plus lang otherwise`() {
        assertEquals("en", TournesolHelper.getDefaultFilterLanguages("en"))
        assertEquals("fr", TournesolHelper.getDefaultFilterLanguages("fr"))
        assertEquals("en,de", TournesolHelper.getDefaultFilterLanguages("de"))
        assertEquals("en", TournesolHelper.getDefaultFilterLanguages(""))
    }

    @Test
    fun `calculateDateGte computes deterministic UTC timestamps and returns null for forever`() {
        assertNull(TournesolHelper.calculateDateGte(TournesolDateRange.FOREVER, fixedNowMillis))
        assertEquals(
            "2025-06-15T12:00:00.000Z",
            TournesolHelper.calculateDateGte(TournesolDateRange.YEAR, fixedNowMillis)
        )
        assertEquals(
            "2026-03-15T12:00:00.000Z",
            TournesolHelper.calculateDateGte(TournesolDateRange.THREE_MONTHS, fixedNowMillis)
        )
        assertEquals(
            "2026-05-15T12:00:00.000Z",
            TournesolHelper.calculateDateGte(TournesolDateRange.MONTH, fixedNowMillis)
        )
        assertEquals(
            "2026-06-08T12:00:00.000Z",
            TournesolHelper.calculateDateGte(TournesolDateRange.WEEK, fixedNowMillis)
        )
        assertEquals(
            "2026-06-14T12:00:00.000Z",
            TournesolHelper.calculateDateGte(TournesolDateRange.DAY, fixedNowMillis)
        )
    }

    @Test
    fun `calculateDateGte zeroes minutes, seconds and millis`() {
        val midHour = millisOf("2026-06-15T12:42:17.123Z")

        assertEquals("2025-06-15T12:00:00.000Z", TournesolHelper.calculateDateGte(TournesolDateRange.YEAR, midHour))
        assertEquals("2026-03-15T12:00:00.000Z", TournesolHelper.calculateDateGte(TournesolDateRange.THREE_MONTHS, midHour))
        assertEquals("2026-05-15T12:00:00.000Z", TournesolHelper.calculateDateGte(TournesolDateRange.MONTH, midHour))
        assertEquals("2026-06-08T12:00:00.000Z", TournesolHelper.calculateDateGte(TournesolDateRange.WEEK, midHour))
        assertEquals("2026-06-14T12:00:00.000Z", TournesolHelper.calculateDateGte(TournesolDateRange.DAY, midHour))
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
            assertEquals(now, expected, TournesolHelper.calculateDateGte(TournesolDateRange.DAY, millisOf(now)))
        }
    }

    @Test
    fun `calculateDateGte truncates after clamping month-end dates`() {
        val cases = listOf(
            Triple(TournesolDateRange.MONTH, "2026-03-31T18:45:30.500Z", "2026-02-28T18:00:00.000Z"),
            Triple(TournesolDateRange.MONTH, "2024-03-31T18:45:30.500Z", "2024-02-29T18:00:00.000Z"),
            Triple(TournesolDateRange.THREE_MONTHS, "2026-05-31T18:45:30.500Z", "2026-02-28T18:00:00.000Z"),
            Triple(TournesolDateRange.YEAR, "2024-02-29T18:45:30.500Z", "2023-02-28T18:00:00.000Z"),
            Triple(TournesolDateRange.WEEK, "2026-01-03T05:20:00.000Z", "2025-12-27T05:00:00.000Z")
        )

        for ((dateRange, now, expected) in cases) {
            assertEquals("$dateRange $now", expected, TournesolHelper.calculateDateGte(dateRange, millisOf(now)))
        }
    }

    @Test
    fun `buildTournesolUrl is stable within a UTC hour so the cache key does not change`() {
        for (dateRange in datedRanges) {
            val config = TournesolFilterConfig(languages = listOf("en"), dateRange = dateRange)

            assertEquals(
                dateRange.key,
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis),
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis + oneHourMillis - 1)
            )
        }
    }

    @Test
    fun `buildTournesolUrl changes in the next UTC hour`() {
        for (dateRange in datedRanges) {
            val config = TournesolFilterConfig(languages = listOf("en"), dateRange = dateRange)

            assertNotEquals(
                dateRange.key,
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis + oneHourMillis - 1),
                TournesolHelper.buildTournesolUrl(config, fixedNowMillis + oneHourMillis)
            )
        }
    }

    @Test
    fun `every date range key is pinned to its persisted literal`() {
        val expected = mapOf(
            TournesolDateRange.FOREVER to "forever",
            TournesolDateRange.YEAR to "year",
            TournesolDateRange.THREE_MONTHS to "3_months",
            TournesolDateRange.MONTH to "month",
            TournesolDateRange.WEEK to "week",
            TournesolDateRange.DAY to "day"
        )

        assertEquals(expected, TournesolDateRange.entries.associateWith { it.key })
    }

    @Test
    fun `every date range key round trips through the parser`() {
        for (dateRange in TournesolDateRange.entries) {
            assertEquals(dateRange.key, dateRange, TournesolDateRange.fromKey(dateRange.key))
        }
    }

    @Test
    fun `null, empty, and unknown date keys parse to the three months default`() {
        for (key in listOf(null, "", "garbage", "3_MONTHS", "FOREVER", " day")) {
            assertEquals("$key", TournesolDateRange.THREE_MONTHS, TournesolDateRange.fromKey(key))
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
            dateRange = TournesolDateRange.MONTH,
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
    fun `buildTournesolUrl keeps an empty languages param and the raw date colons`() {
        val config = TournesolFilterConfig()

        assertEquals(
            "Tournesol?languages=&date_gte=2026-03-15T12:00:00.000Z",
            TournesolHelper.buildTournesolUrl(config, fixedNowMillis)
        )
    }

    @Test
    fun `buildTournesolUrl omits unset durations, unset store weights, and a forever date`() {
        val config = TournesolFilterConfig(
            languages = listOf("en", "de"),
            dateRange = TournesolDateRange.FOREVER,
            weights = TournesolFilterCriterion.entries.associateWith { -1 }
        )

        assertEquals(
            "Tournesol?languages=en,de",
            TournesolHelper.buildTournesolUrl(config, fixedNowMillis)
        )
    }

    @Test
    fun `withLowScoreSearchFilter adds the unsafe filter only to an opted-in Tournesol search`() {
        val tournesol = YoutubeSearchQueryHandlerFactory.TOURNESOL
        val unsafe = YoutubeSearchQueryHandlerFactory.TOURNESOL_UNSAFE
        val cases = listOf(
            Triple(listOf(tournesol), true, listOf(tournesol, unsafe)),
            Triple(listOf(tournesol), false, listOf(tournesol)),
            Triple(listOf(tournesol, unsafe), true, listOf(tournesol, unsafe)),
            Triple(listOf(tournesol, unsafe), false, listOf(tournesol, unsafe)),
            Triple(listOf("videos"), true, listOf("videos")),
            Triple(listOf("videos", tournesol), true, listOf("videos", tournesol)),
            Triple(emptyList<String>(), true, emptyList<String>())
        )

        for ((filters, includeLowScoreVideos, expected) in cases) {
            assertEquals(
                "$filters $includeLowScoreVideos",
                expected,
                TournesolHelper.withLowScoreSearchFilter(filters, includeLowScoreVideos)
            )
        }
    }

    @Test
    fun `hasActiveAdvancedFilters detects non-quick dates, durations, or active weights`() {
        val base = TournesolFilterConfig(dateRange = TournesolDateRange.THREE_MONTHS)
        assertFalse(base.hasActiveAdvancedFilters())

        assertTrue(base.copy(dateRange = TournesolDateRange.FOREVER).hasActiveAdvancedFilters())
        assertTrue(base.copy(durationMinSeconds = 60).hasActiveAdvancedFilters())
        assertTrue(
            base.copy(weights = mapOf(TournesolFilterCriterion.IMPORTANCE to 50))
                .hasActiveAdvancedFilters()
        )
    }

    @Test
    fun `hasActiveAdvancedFilters is false for every quick range and true for forever`() {
        for (dateRange in datedRanges) {
            assertFalse(dateRange.key, TournesolFilterConfig(dateRange = dateRange).hasActiveAdvancedFilters())
        }

        assertTrue(TournesolFilterConfig(dateRange = TournesolDateRange.FOREVER).hasActiveAdvancedFilters())
    }

    @Test
    fun `hasInsufficientReason matches only insufficient reason codes`() {
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
