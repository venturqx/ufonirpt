package org.schabi.newpipe.fragments.list.kiosk

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.util.TournesolDateRange
import org.schabi.newpipe.util.TournesolFilterConfig
import org.schabi.newpipe.util.TournesolFilterCriterion

class TournesolFilterFormTest {
    private val criteria = TournesolFilterCriterion.entries
    private val unsetWeights = criteria.associateWith { -1 }
    private val levels = criteria.associateWith { (it.ordinal % 5) * 25 }
    private val noStoredLevels: (TournesolFilterCriterion) -> Int? = { null }

    private fun form(
        durationMinMinutes: Float = 0f,
        durationMaxMinutes: Float = DURATION_SLIDER_MAX,
        weightsEnabled: Boolean = true
    ) = TournesolFilterForm(
        languages = listOf("en", "fr"),
        dateRange = TournesolDateRange.WEEK,
        includeLowScoreVideos = true,
        durationMinMinutes = durationMinMinutes,
        durationMaxMinutes = durationMaxMinutes,
        weightsEnabled = weightsEnabled,
        weightLevels = levels
    )

    private fun formFor(durationMinSeconds: Int = -1, durationMaxSeconds: Int = -1) = TournesolFilterForm.from(
        config = TournesolFilterConfig(
            durationMinSeconds = durationMinSeconds,
            durationMaxSeconds = durationMaxSeconds
        ),
        weightsEnabled = false,
        storedLevel = noStoredLevels
    )

    private fun storeShapedConfig(weights: Map<TournesolFilterCriterion, Int>) = TournesolFilterConfig(
        languages = listOf("en", "fr"),
        dateRange = TournesolDateRange.WEEK,
        includeLowScoreVideos = true,
        durationMinSeconds = -1,
        durationMaxSeconds = -1,
        weights = weights
    )

    @Test
    fun `quantize snaps to the nearest level on each side of every midpoint`() {
        val expectedByValue = mapOf(
            0 to 0,
            12 to 0,
            13 to 25,
            25 to 25,
            37 to 25,
            38 to 50,
            50 to 50,
            62 to 50,
            63 to 75,
            75 to 75,
            87 to 75,
            88 to 100,
            100 to 100
        )

        for ((value, expected) in expectedByValue) {
            assertEquals("$value", expected, quantizeToLevel(value))
        }
    }

    @Test
    fun `quantize clamps values above the top level`() {
        assertEquals(100, quantizeToLevel(101))
        assertEquals(100, quantizeToLevel(Int.MAX_VALUE))
    }

    @Test
    fun `quantize maps negatives to neutral`() {
        for (value in listOf(-1, -25, Int.MIN_VALUE)) {
            assertEquals("$value", 50, quantizeToLevel(value))
        }
    }

    @Test
    fun `min duration at the slider floor is the unset sentinel`() {
        assertEquals(-1, form(durationMinMinutes = 0f).toConfig().durationMinSeconds)
    }

    @Test
    fun `min duration converts mid range and top of the slider to seconds`() {
        assertEquals(1500, form(durationMinMinutes = 25f).toConfig().durationMinSeconds)
        assertEquals(7200, form(durationMinMinutes = DURATION_SLIDER_MAX).toConfig().durationMinSeconds)
    }

    @Test
    fun `max duration at the slider ceiling is the unset sentinel`() {
        assertEquals(-1, form(durationMaxMinutes = DURATION_SLIDER_MAX).toConfig().durationMaxSeconds)
    }

    @Test
    fun `max duration converts mid range and bottom of the slider to seconds`() {
        assertEquals(1500, form(durationMaxMinutes = 25f).toConfig().durationMaxSeconds)
        assertEquals(0, form(durationMaxMinutes = 0f).toConfig().durationMaxSeconds)
    }

    @Test
    fun `unset durations open the slider fully`() {
        val form = formFor(durationMinSeconds = -1, durationMaxSeconds = -1)

        assertEquals(0f, form.durationMinMinutes, 0f)
        assertEquals(DURATION_SLIDER_MAX, form.durationMaxMinutes, 0f)
    }

    @Test
    fun `set durations convert to slider minutes`() {
        assertEquals(0f, formFor(durationMinSeconds = 0).durationMinMinutes, 0f)
        assertEquals(0f, formFor(durationMaxSeconds = 0).durationMaxMinutes, 0f)
        assertEquals(25f, formFor(durationMinSeconds = 1500).durationMinMinutes, 0f)
        assertEquals(25f, formFor(durationMaxSeconds = 1500).durationMaxMinutes, 0f)
        assertEquals(DURATION_SLIDER_MAX, formFor(durationMinSeconds = 7200).durationMinMinutes, 0f)
        assertEquals(DURATION_SLIDER_MAX, formFor(durationMaxSeconds = 7200).durationMaxMinutes, 0f)
    }

    @Test
    fun `config survives a round trip through the form with weights enabled`() {
        val config = storeShapedConfig(levels).copy(durationMinSeconds = 300, durationMaxSeconds = 3600)

        val form = TournesolFilterForm.from(config, weightsEnabled = true, storedLevel = noStoredLevels)

        assertEquals(config, form.toConfig())
    }

    @Test
    fun `config survives a round trip through the form with weights disabled`() {
        val config = storeShapedConfig(unsetWeights)

        val form = TournesolFilterForm.from(config, weightsEnabled = false, storedLevel = noStoredLevels)

        assertEquals(config, form.toConfig())
    }

    @Test
    fun `disabled weights emit the sentinel for every criterion and keep the chosen levels`() {
        val form = form(weightsEnabled = false)

        assertEquals(unsetWeights, form.toConfig().weights)
        assertEquals(levels, form.weightLevels)
    }

    @Test
    fun `enabled weights emit the chosen levels`() {
        assertEquals(levels, form(weightsEnabled = true).toConfig().weights)
    }

    @Test
    fun `output equals a store shaped config so onResume does not reload`() {
        assertEquals(storeShapedConfig(unsetWeights), form(weightsEnabled = false).toConfig())
        assertEquals(storeShapedConfig(levels), form(weightsEnabled = true).toConfig())
    }

    @Test
    fun `output has an entry for every criterion in both modes`() {
        for (weightsEnabled in listOf(true, false)) {
            val weights = form(weightsEnabled = weightsEnabled).toConfig().weights

            assertEquals("$weightsEnabled", criteria.toSet(), weights.keys)
        }
    }

    @Test
    fun `stored level wins over the config weight and is quantized`() {
        val config = TournesolFilterConfig(weights = criteria.associateWith { 100 })

        val form = TournesolFilterForm.from(config, weightsEnabled = true, storedLevel = { 30 })

        assertEquals(criteria.associateWith { 25 }, form.weightLevels)
    }

    @Test
    fun `missing stored level falls back to the quantized config weight`() {
        val config = TournesolFilterConfig(weights = criteria.associateWith { 70 })

        val form = TournesolFilterForm.from(config, weightsEnabled = true, storedLevel = noStoredLevels)

        assertEquals(criteria.associateWith { 75 }, form.weightLevels)
    }

    @Test
    fun `sparse config still gives every criterion a neutral level`() {
        val form = TournesolFilterForm.from(TournesolFilterConfig(), weightsEnabled = false, storedLevel = noStoredLevels)

        assertEquals(criteria.associateWith { 50 }, form.weightLevels)
    }

    @Test
    fun `weight pref keys are stable`() {
        val expected = mapOf(
            TournesolFilterCriterion.LARGELY_RECOMMENDED to "tournesol_filter_weight_level_largely_recommended",
            TournesolFilterCriterion.RELIABILITY to "tournesol_filter_weight_level_reliability",
            TournesolFilterCriterion.IMPORTANCE to "tournesol_filter_weight_level_importance",
            TournesolFilterCriterion.PEDAGOGY to "tournesol_filter_weight_level_pedagogy",
            TournesolFilterCriterion.LAYMAN_FRIENDLY to "tournesol_filter_weight_level_layman_friendly",
            TournesolFilterCriterion.ENTERTAINING_RELAXING to "tournesol_filter_weight_level_entertaining_relaxing",
            TournesolFilterCriterion.ENGAGING to "tournesol_filter_weight_level_engaging",
            TournesolFilterCriterion.DIVERSITY_INCLUSION to "tournesol_filter_weight_level_diversity_inclusion",
            TournesolFilterCriterion.BETTER_HABITS to "tournesol_filter_weight_level_better_habits",
            TournesolFilterCriterion.BACKFIRE_RISK to "tournesol_filter_weight_level_backfire_risk"
        )

        assertEquals(expected, criteria.associateWith { it.weightLevelPrefKey })
    }

    @Test
    fun `weights enabled pref key is stable`() {
        assertEquals("tournesol_filter_weights_enabled", PREF_TOURNESOL_FILTER_WEIGHTS_ENABLED)
    }

    @Test
    fun `date range enum order is the display order of the date chips`() {
        assertEquals(
            listOf(
                TournesolDateRange.FOREVER,
                TournesolDateRange.YEAR,
                TournesolDateRange.THREE_MONTHS,
                TournesolDateRange.MONTH,
                TournesolDateRange.WEEK,
                TournesolDateRange.DAY
            ),
            TournesolDateRange.entries.toList()
        )
    }

    @Test
    fun `enum order is the display order of the criteria grid`() {
        assertEquals(
            listOf(
                TournesolFilterCriterion.LARGELY_RECOMMENDED,
                TournesolFilterCriterion.RELIABILITY,
                TournesolFilterCriterion.IMPORTANCE,
                TournesolFilterCriterion.PEDAGOGY,
                TournesolFilterCriterion.LAYMAN_FRIENDLY,
                TournesolFilterCriterion.ENTERTAINING_RELAXING,
                TournesolFilterCriterion.ENGAGING,
                TournesolFilterCriterion.DIVERSITY_INCLUSION,
                TournesolFilterCriterion.BETTER_HABITS,
                TournesolFilterCriterion.BACKFIRE_RISK
            ),
            criteria.toList()
        )
    }
}
