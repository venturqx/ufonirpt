package org.schabi.newpipe.util

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class TournesolFilterStoreTest {

    private val prefs = FakePrefs()
    private val store = TournesolFilterStore(prefs)
    private val unsetWeights = TournesolFilterCriterion.entries.associateWith { -1 }

    private fun defaults(vararg languages: String) = TournesolFilterConfig(
        languages = languages.toList(),
        dateKey = "3_months",
        includeLowScoreVideos = false,
        durationMinSeconds = -1,
        durationMaxSeconds = -1,
        weights = unsetWeights
    )

    private fun storeWith(vararg values: Pair<String, Any>) = TournesolFilterStore(FakePrefs(mutableMapOf(*values)))

    @Test
    fun `save then load returns an equal config with every field set`() {
        val config = TournesolFilterConfig(
            languages = listOf("en", "fr"),
            dateKey = "forever",
            includeLowScoreVideos = true,
            durationMinSeconds = 120,
            durationMaxSeconds = 1800,
            weights = TournesolFilterCriterion.entries.associateWith { it.ordinal * 10 }
        )

        store.save(config)

        assertEquals(config, store.load())
    }

    @Test
    fun `config shaped like the filter sheet output is unchanged by a round trip so onResume does not reload`() {
        val config = defaults().copy(
            weights = unsetWeights + (TournesolFilterCriterion.RELIABILITY to 75)
        )

        store.save(config)

        assertEquals(config, store.load())
    }

    @Test
    fun `sparse weights come back total with the same meaning`() {
        val config = TournesolFilterConfig(
            dateKey = "forever",
            weights = mapOf(TournesolFilterCriterion.RELIABILITY to 75)
        )

        store.save(config)
        val loaded = store.load()

        assertEquals(unsetWeights + config.weights, loaded.weights)
        assertEquals(
            TournesolHelper.buildTournesolUrl(config, 0L),
            TournesolHelper.buildTournesolUrl(loaded, 0L)
        )
    }

    @Test
    fun `empty prefs load the defaults with the given default languages`() {
        assertEquals(defaults("en", "de"), store.load(defaultLanguages = "en,de"))
    }

    @Test
    fun `partial prefs keep stored values and default the rest`() {
        val loaded = storeWith(
            "tournesol_filter_duration_max" to 600,
            "tournesol_filter_weight_pedagogy" to 40
        ).load(defaultLanguages = "fr")

        assertEquals(
            defaults("fr").copy(
                durationMaxSeconds = 600,
                weights = unsetWeights + (TournesolFilterCriterion.PEDAGOGY to 40)
            ),
            loaded
        )
    }

    @Test
    fun `legacy prefs load under their exact keys and save back identically`() {
        val legacy = mapOf<String, Any>(
            "tournesol_filter_languages" to "fr,de",
            "tournesol_filter_date_key" to "week",
            "tournesol_filter_include_low_score" to true,
            "tournesol_filter_duration_min" to 60,
            "tournesol_filter_duration_max" to 600,
            "tournesol_filter_weight_largely_recommended" to 1,
            "tournesol_filter_weight_reliability" to 2,
            "tournesol_filter_weight_importance" to 3,
            "tournesol_filter_weight_pedagogy" to 4,
            "tournesol_filter_weight_layman_friendly" to 5,
            "tournesol_filter_weight_entertaining_relaxing" to 6,
            "tournesol_filter_weight_engaging" to 7,
            "tournesol_filter_weight_diversity_inclusion" to 8,
            "tournesol_filter_weight_better_habits" to 9,
            "tournesol_filter_weight_backfire_risk" to 0
        )
        val legacyPrefs = FakePrefs(legacy.toMutableMap())
        val legacyStore = TournesolFilterStore(legacyPrefs)

        val loaded = legacyStore.load()

        assertEquals(
            TournesolFilterConfig(
                languages = listOf("fr", "de"),
                dateKey = "week",
                includeLowScoreVideos = true,
                durationMinSeconds = 60,
                durationMaxSeconds = 600,
                weights = mapOf(
                    TournesolFilterCriterion.LARGELY_RECOMMENDED to 1,
                    TournesolFilterCriterion.RELIABILITY to 2,
                    TournesolFilterCriterion.IMPORTANCE to 3,
                    TournesolFilterCriterion.PEDAGOGY to 4,
                    TournesolFilterCriterion.LAYMAN_FRIENDLY to 5,
                    TournesolFilterCriterion.ENTERTAINING_RELAXING to 6,
                    TournesolFilterCriterion.ENGAGING to 7,
                    TournesolFilterCriterion.DIVERSITY_INCLUSION to 8,
                    TournesolFilterCriterion.BETTER_HABITS to 9,
                    TournesolFilterCriterion.BACKFIRE_RISK to 0
                )
            ),
            loaded
        )

        legacyPrefs.values.clear()
        legacyStore.save(loaded)

        assertEquals(legacy, legacyPrefs.values)
    }

    @Test
    fun `explicit empty language pref means no languages rather than the default`() {
        val loaded = storeWith("tournesol_filter_languages" to "").load(defaultLanguages = "en")

        assertEquals(emptyList<String>(), loaded.languages)
    }

    @Test
    fun `empty language segments are dropped`() {
        val expectedByPref = mapOf(
            "en,,fr" to listOf("en", "fr"),
            ",en," to listOf("en"),
            "," to emptyList<String>()
        )

        for ((pref, expected) in expectedByPref) {
            val loaded = storeWith("tournesol_filter_languages" to pref).load(defaultLanguages = "de")
            assertEquals(pref, expected, loaded.languages)
        }
    }

    @Test
    fun `every criterion is persisted under its own prefKey`() {
        store.save(TournesolFilterConfig(weights = TournesolFilterCriterion.entries.associateWith { it.ordinal }))

        for (criterion in TournesolFilterCriterion.entries) {
            assertEquals(criterion.prefKey, criterion.ordinal, prefs.values[criterion.prefKey])
        }
    }
}

private class FakePrefs(
    val values: MutableMap<String, Any> = mutableMapOf()
) : SharedPreferences by Mockito.mock(SharedPreferences::class.java) {
    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)
}

private class FakeEditor(
    private val target: MutableMap<String, Any>
) : SharedPreferences.Editor by Mockito.mock(SharedPreferences.Editor::class.java) {
    private val pending = mutableMapOf<String, Any>()

    private fun put(key: String, value: Any): SharedPreferences.Editor = also { pending[key] = value }

    override fun putString(key: String, value: String?) = put(key, checkNotNull(value))

    override fun putBoolean(key: String, value: Boolean) = put(key, value)

    override fun putInt(key: String, value: Int) = put(key, value)

    override fun apply() = target.putAll(pending)
}
