package org.schabi.newpipe.tournesol

import java.net.URLEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.schabi.newpipe.extractor.services.youtube.extractors.kiosk.TournesolKioskExtractor

class TournesolKioskQueryTest {

    private val fixedNowMillis = 1781524800000L

    private val hostileUploader = "AT&T = 100% \"Ünï\" + more"
    private val awkwardValues = listOf(
        "a&b",
        "a,b",
        ",",
        "a=b",
        "=",
        "100%",
        "%",
        "%2C",
        "%3A",
        "%zz",
        "a+b",
        "+",
        "a b",
        " ",
        "a:b",
        "a?b",
        "a#b",
        "Ünï",
        "日本語",
        "🙂",
        hostileUploader
    )

    private fun parse(url: String, defaultIncludeUnsafe: Boolean = false) = TournesolKioskQuery.parse(url, defaultIncludeUnsafe)

    @Test
    fun `toUrl round trips awkward values in every text field`() {
        val queries = awkwardValues.flatMap { value ->
            listOf(
                TournesolKioskQuery(includeUnsafe = false, uploader = value),
                TournesolKioskQuery(includeUnsafe = false, dateGte = value),
                TournesolKioskQuery(includeUnsafe = false, languages = listOf(value)),
                TournesolKioskQuery(includeUnsafe = false, languages = listOf("en", value, "fr")),
                TournesolKioskQuery(
                    includeUnsafe = true,
                    languages = listOf(value, value),
                    dateGte = value,
                    uploader = value
                )
            )
        }

        for (query in queries) {
            assertEquals(query.toString(), query, parse(query.toUrl()))
        }
    }

    @Test
    fun `toUrl round trips absent, empty, zero, and full fields`() {
        val queries = listOf(
            TournesolKioskQuery(includeUnsafe = false),
            TournesolKioskQuery(includeUnsafe = true),
            TournesolKioskQuery(includeUnsafe = false, languages = emptyList()),
            TournesolKioskQuery(includeUnsafe = false, dateGte = ""),
            TournesolKioskQuery(includeUnsafe = false, uploader = ""),
            TournesolKioskQuery(includeUnsafe = false, durationGte = 0, durationLte = 0),
            TournesolKioskQuery(
                includeUnsafe = false,
                weights = TournesolFilterCriterion.entries.associateWith { 0 }
            ),
            TournesolKioskQuery(
                includeUnsafe = true,
                languages = listOf("en", "fr"),
                dateGte = "2026-05-15T12:00:00.000Z",
                uploader = "ESA",
                durationGte = 60,
                durationLte = 1800,
                weights = TournesolFilterCriterion.entries.associateWith { it.ordinal }
            )
        )

        for (query in queries) {
            assertEquals(query.toString(), query, parse(query.toUrl()))
        }
    }

    @Test
    fun `toUrl keeps existing url shapes byte identical`() {
        val expectedByQuery = mapOf(
            TournesolKioskQuery(includeUnsafe = false, languages = emptyList()) to
                "Tournesol?languages=",
            TournesolKioskQuery(
                includeUnsafe = false,
                languages = listOf("en", "fr"),
                dateGte = "2026-05-15T12:00:00.000Z"
            ) to "Tournesol?languages=en,fr&date_gte=2026-05-15T12:00:00.000Z",
            TournesolKioskQuery(
                includeUnsafe = true,
                languages = listOf("zh-Hans"),
                durationGte = 60,
                durationLte = 1800
            ) to "Tournesol?languages=zh-Hans&unsafe=true&duration_gte=60&duration_lte=1800",
            TournesolKioskQuery.forUploader("ESA") to
                "Tournesol?languages=&uploader=ESA&unsafe=true",
            TournesolKioskQuery.forUploader("") to
                "Tournesol?languages=&uploader=&unsafe=true"
        )

        for ((query, expected) in expectedByQuery) {
            assertEquals(expected, query.toUrl())
        }
    }

    @Test
    fun `toUrl keeps colons in date_gte but encodes them in uploader`() {
        val query = TournesolKioskQuery(includeUnsafe = false, dateGte = "a:b", uploader = "a:b")

        assertEquals("Tournesol?date_gte=a:b&uploader=a%3Ab", query.toUrl())
    }

    @Test
    fun `toUrl writes weights in criterion order and skips unset ones`() {
        val query = TournesolKioskQuery(
            includeUnsafe = false,
            weights = mapOf(
                TournesolFilterCriterion.BACKFIRE_RISK to 0,
                TournesolFilterCriterion.PEDAGOGY to -1,
                TournesolFilterCriterion.RELIABILITY to 75
            )
        )

        assertEquals("Tournesol?weight_reliability=75&weight_backfire_risk=0", query.toUrl())
    }

    @Test
    fun `forUploader encodes a hostile uploader exactly like the legacy rank url`() {
        val url = TournesolKioskQuery.forUploader(hostileUploader).toUrl()

        assertEquals(
            "Tournesol?languages=&uploader=AT%26T+%3D+100%25+%22%C3%9Cn%C3%AF%22+%2B+more&unsafe=true",
            url
        )
        assertEquals(
            TournesolHelper.KIOSK_ID + "?languages=&uploader=" +
                URLEncoder.encode(hostileUploader, Charsets.UTF_8.name()) + "&unsafe=true",
            url
        )
    }

    @Test
    fun `forUploader round trips and survives a default of false`() {
        val expected = TournesolKioskQuery(
            includeUnsafe = true,
            languages = emptyList(),
            uploader = hostileUploader
        )

        val url = TournesolKioskQuery.forUploader(hostileUploader).toUrl()

        assertEquals(expected, parse(url, defaultIncludeUnsafe = false))
        assertEquals(expected, parse(url, defaultIncludeUnsafe = true))
    }

    @Test
    fun `includeUnsafe false is written as absent so parse falls back to the default`() {
        val url = TournesolKioskQuery(includeUnsafe = false).toUrl()

        assertFalse(parse(url, defaultIncludeUnsafe = false).includeUnsafe)
        assertTrue(parse(url, defaultIncludeUnsafe = true).includeUnsafe)
    }

    @Test
    fun `an encoded comma stays inside one language`() {
        assertEquals(listOf("a,b", "c"), parse("Tournesol?languages=a%2Cb,c").languages)
    }

    @Test
    fun `malformed percent sequences keep the raw value field by field`() {
        assertEquals("%zz", parse("Tournesol?uploader=%zz").uploader)
        assertEquals("2026%", parse("Tournesol?date_gte=2026%").dateGte)
        assertEquals(
            listOf("en", "100%", "a b"),
            parse("Tournesol?languages=en,100%,a%20b").languages
        )
    }

    @Test
    fun `a plus decodes to a space and an encoded plus stays a plus`() {
        assertEquals("a b+c", parse("Tournesol?uploader=a+b%2Bc").uploader)
    }

    @Test
    fun `round trips every filter built by buildTournesolUrl`() {
        val weights = TournesolFilterCriterion.entries
            .withIndex()
            .associate { (index, criterion) -> criterion to index * 10 }
        val config = TournesolFilterConfig(
            languages = listOf("en", "fr"),
            dateRange = TournesolDateRange.MONTH,
            includeLowScoreVideos = true,
            durationMinSeconds = 120,
            durationMaxSeconds = 1800,
            weights = weights
        )

        val query = parse(TournesolHelper.buildTournesolUrl(config, fixedNowMillis))

        assertEquals(
            TournesolKioskQuery(
                includeUnsafe = true,
                languages = listOf("en", "fr"),
                dateGte = "2026-05-15T12:00:00.000Z",
                durationGte = 120,
                durationLte = 1800,
                weights = weights
            ),
            query
        )
    }

    @Test
    fun `round trips an unfiltered config with no date, no languages, and no weights`() {
        val config = TournesolFilterConfig(dateRange = TournesolDateRange.FOREVER)

        val query = parse(TournesolHelper.buildTournesolUrl(config, fixedNowMillis))

        assertEquals(TournesolKioskQuery(includeUnsafe = false, languages = emptyList()), query)
    }

    @Test
    fun `parses the ChannelRankFragment url and decodes the uploader`() {
        val uploader = "Science & Vie+"
        val url = TournesolHelper.KIOSK_ID + "?languages=&uploader=" +
            URLEncoder.encode(uploader, Charsets.UTF_8.name()) + "&unsafe=true"

        val query = parse(url)

        assertEquals(
            TournesolKioskQuery(includeUnsafe = true, languages = emptyList(), uploader = uploader),
            query
        )
    }

    @Test
    fun `url without a query keeps defaults and leaves languages unspecified`() {
        assertEquals(TournesolKioskQuery(includeUnsafe = true), parse("Tournesol", true))
        assertEquals(TournesolKioskQuery(includeUnsafe = false), parse("Tournesol?", false))
    }

    @Test
    fun `unsafe param overrides the default in both directions`() {
        assertTrue(parse("Tournesol?languages=en", true).includeUnsafe)
        assertFalse(parse("Tournesol?unsafe=false", true).includeUnsafe)
        assertFalse(parse("Tournesol?unsafe=1", true).includeUnsafe)
        assertTrue(parse("Tournesol?unsafe=TRUE", false).includeUnsafe)
    }

    @Test
    fun `malformed integers become -1`() {
        val query = parse(
            "Tournesol?duration_gte=abc&duration_lte=&weight_reliability=99999999999"
        )

        assertEquals(-1, query.durationGte)
        assertEquals(-1, query.durationLte)
        assertEquals(-1, query.weightFor(TournesolFilterCriterion.RELIABILITY))
    }

    @Test
    fun `pairs without exactly one equals sign are ignored`() {
        val query = parse("Tournesol?date_gte&uploader=a=b&&=&languages=en")

        assertNull(query.dateGte)
        assertNull(query.uploader)
        assertEquals(listOf("en"), query.languages)
    }

    @Test
    fun `last duplicate key wins`() {
        val query = parse(
            "Tournesol?languages=en&languages=&duration_gte=60&duration_gte=abc&unsafe=true&unsafe=false"
        )

        assertEquals(emptyList<String>(), query.languages)
        assertEquals(-1, query.durationGte)
        assertFalse(query.includeUnsafe)
    }

    @Test
    fun `invalid percent encoding keeps the raw value`() {
        assertEquals("100%", parse("Tournesol?uploader=100%").uploader)
    }

    @Test
    fun `languages drop trailing empty entries and keep leading ones`() {
        assertEquals(listOf("en"), parse("Tournesol?languages=en,").languages)
        assertEquals(emptyList<String>(), parse("Tournesol?languages=,").languages)
        assertEquals(listOf("", "en"), parse("Tournesol?languages=,en").languages)
    }

    @Test
    fun `unknown keys are ignored`() {
        assertEquals(
            TournesolKioskQuery(includeUnsafe = false),
            parse("Tournesol?weight_unknown=5&reliability=5&WEIGHT_RELIABILITY=5")
        )
    }

    @Test
    fun `applyTo maps every field to its own extractor setter`() {
        val extractor = mock(TournesolKioskExtractor::class.java)
        val query = TournesolKioskQuery(
            includeUnsafe = true,
            languages = listOf("en"),
            dateGte = "2026-05-15T12:00:00.000Z",
            uploader = "ESA",
            durationGte = 1,
            durationLte = 2,
            weights = TournesolFilterCriterion.entries.associateWith { 10 + it.ordinal }
        )

        query.applyTo(extractor)

        verify(extractor).setLanguages(listOf("en"))
        verify(extractor).setDateGte("2026-05-15T12:00:00.000Z")
        verify(extractor).setUploader("ESA")
        verify(extractor).setIncludeUnsafe(true)
        verify(extractor).setDurationGte(1)
        verify(extractor).setDurationLte(2)
        verify(extractor).setWeightLargelyRecommended(10)
        verify(extractor).setWeightReliability(11)
        verify(extractor).setWeightImportance(12)
        verify(extractor).setWeightPedagogy(13)
        verify(extractor).setWeightLaymanFriendly(14)
        verify(extractor).setWeightEntertainingRelaxing(15)
        verify(extractor).setWeightEngaging(16)
        verify(extractor).setWeightDiversityInclusion(17)
        verify(extractor).setWeightBetterHabits(18)
        verify(extractor).setWeightBackfireRisk(19)
    }

    @Test
    fun `applyTo resets the date and keeps extractor default languages when absent`() {
        val extractor = mock(TournesolKioskExtractor::class.java)

        TournesolKioskQuery(includeUnsafe = false).applyTo(extractor)

        verify(extractor, never()).setLanguages(anyList())
        verify(extractor).setDateGte(null)
    }

    @Test
    fun `applyTo clears extractor languages when explicitly empty`() {
        val extractor = mock(TournesolKioskExtractor::class.java)

        TournesolKioskQuery(includeUnsafe = false, languages = emptyList()).applyTo(extractor)

        verify(extractor).setLanguages(emptyList())
    }
}
