package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournesolHelperTest {

    // Fixed UTC timestamp: 2026-06-15T12:00:00.000Z (1781524800000L)
    private val fixedNowMillis = 1781524800000L

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
