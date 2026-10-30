package org.schabi.newpipe.fragments.detail

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class CompareScoreMigrationTest {
    private val defaults = FakePrefs()
    private val dedicated = FakePrefs()

    private fun migrate() = migrateCompareScores(CompareScoreLegacyPrefs(defaults), CompareScorePrefs(dedicated))

    private fun newStore() = CompareScoreStore(CompareScorePrefs(dedicated))

    private fun seedV1() {
        defaults.values[V1_PAIRS] = setOf(PLAIN_STORED)
        defaults.values[V1_SCORES] = """{"$PLAIN_STORED":{"mainScore":-7,"extraScores":{"reliability":3}}}"""
    }

    private fun seedV2() {
        defaults.values[V2_PAIRS] = setOf(SLASH_STORED)
        defaults.values[V2_SCORES] = """{"$SLASH_STORED":{"mainScore":1}}"""
    }

    private fun assertNoCompareKeysIn(prefs: FakePrefs) {
        listOf(V1_PAIRS, V1_SCORES, V2_PAIRS, V2_SCORES).forEach { key ->
            assertFalse(key, key in prefs.values)
        }
    }

    @Test
    fun `a fresh install writes nothing anywhere`() {
        migrate()

        assertTrue(dedicated.values.isEmpty())
        assertEquals(0, dedicated.commits)
        assertEquals(0, defaults.applies)
        assertEquals(0, defaults.commits)
    }

    @Test
    fun `v2 moves verbatim to the dedicated file and leaves other settings alone`() {
        seedV2()
        defaults.values[UNRELATED_KEY] = "dark"
        val scores = defaults.values[V2_SCORES]

        migrate()

        assertEquals(setOf(SLASH_STORED), dedicated.values[V2_PAIRS])
        assertEquals(scores, dedicated.values[V2_SCORES])
        assertNoCompareKeysIn(defaults)
        assertEquals("dark", defaults.values[UNRELATED_KEY])
    }

    @Test
    fun `v1 moves to the dedicated file with swapped keys and unchanged scores`() {
        seedV1()

        migrate()

        val store = newStore()
        assertTrue(store.isSubmitted(PLAIN_SWAPPED_KEY))
        assertFalse(store.isSubmitted(PLAIN_KEY))
        assertEquals(ComparisonScores(-7, mapOf("reliability" to 3)), store.scoresFor(PLAIN_SWAPPED_KEY))
        assertNull(store.scoresFor(PLAIN_KEY))
        assertEquals(setOf(PLAIN_SWAPPED_STORED), dedicated.values[V2_PAIRS])
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `v2 wins over v1 and is not swapped`() {
        seedV1()
        seedV2()

        migrate()

        val store = newStore()
        assertTrue(store.isSubmitted(SLASH_KEY))
        assertFalse(store.isSubmitted(PLAIN_KEY))
        assertFalse(store.isSubmitted(PLAIN_SWAPPED_KEY))
        assertEquals(ComparisonScores(mainScore = 1), store.scoresFor(SLASH_KEY))
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `an emptied v2 store does not bring v1 data back`() {
        seedV1()
        defaults.values[V2_PAIRS] = emptySet<String>()
        defaults.values[V2_SCORES] = "{}"

        migrate()

        val store = newStore()
        assertFalse(store.isSubmitted(PLAIN_KEY))
        assertFalse(store.isSubmitted(PLAIN_SWAPPED_KEY))
        assertNull(store.scoresFor(PLAIN_SWAPPED_KEY))
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `only submitted pairs or only scores are both migrated`() {
        defaults.values[V2_PAIRS] = setOf(SLASH_STORED)

        migrate()

        assertTrue(newStore().isSubmitted(SLASH_KEY))
        assertNoCompareKeysIn(defaults)

        dedicated.values.clear()
        defaults.values[V2_SCORES] = """{"$SLASH_STORED":{"mainScore":1}}"""

        migrate()

        assertEquals(ComparisonScores(mainScore = 1), newStore().scoresFor(SLASH_KEY))
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `both keys land in one atomic commit`() {
        seedV2()

        migrate()

        assertEquals(1, dedicated.commits)
        assertEquals(0, dedicated.applies)
    }

    @Test
    fun `dedicated data wins and stale default keys are removed`() {
        dedicated.values[V2_PAIRS] = setOf(PLAIN_STORED)
        dedicated.values[V2_SCORES] = """{"$PLAIN_STORED":{"mainScore":10}}"""
        seedV1()
        seedV2()
        defaults.values[UNRELATED_KEY] = "dark"
        val before = dedicated.values.toMap()

        migrate()

        assertEquals(before, dedicated.values)
        assertEquals(0, dedicated.commits)
        assertNoCompareKeysIn(defaults)
        assertEquals("dark", defaults.values[UNRELATED_KEY])
    }

    @Test
    fun `a backup restored after clear does not bring data back`() {
        seedV2()
        migrate()
        newStore().clear()

        seedV2()
        migrate()

        val store = newStore()
        assertFalse(store.isSubmitted(SLASH_KEY))
        assertNull(store.scoresFor(SLASH_KEY))
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `a crash between write and delete resumes on the next launch`() {
        seedV1()
        defaults.loseApplies = true

        migrate()

        assertEquals(setOf(PLAIN_SWAPPED_STORED), dedicated.values[V2_PAIRS])
        assertEquals(setOf(PLAIN_STORED), defaults.values[V1_PAIRS])

        defaults.loseApplies = false
        migrate()

        assertEquals(1, dedicated.commits)
        assertEquals(setOf(PLAIN_SWAPPED_STORED), dedicated.values[V2_PAIRS])
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `a failed write keeps the source and retries on the next launch`() {
        seedV2()
        dedicated.commitSucceeds = false

        migrate()

        assertTrue(dedicated.values.isEmpty())
        assertEquals(setOf(SLASH_STORED), defaults.values[V2_PAIRS])
        assertEquals(0, defaults.applies)

        dedicated.commitSucceeds = true
        migrate()

        assertEquals(setOf(SLASH_STORED), dedicated.values[V2_PAIRS])
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `a second run is a no-op`() {
        seedV2()
        migrate()
        val commits = dedicated.commits
        val applies = defaults.applies
        val moved = dedicated.values.toMap()

        migrate()

        assertEquals(commits, dedicated.commits)
        assertEquals(applies, defaults.applies)
        assertEquals(moved, dedicated.values)
    }

    @Test
    fun `corrupt v1 entries are skipped and the rest migrates`() {
        defaults.values[V1_PAIRS] = setOf("no-separator", PLAIN_STORED)
        defaults.values[V1_SCORES] = """{"no-separator":{"mainScore":1},"$PLAIN_STORED":{"mainScore":10}}"""

        migrate()

        val store = newStore()
        assertTrue(store.isSubmitted(PLAIN_SWAPPED_KEY))
        assertEquals(ComparisonScores(mainScore = 10), store.scoresFor(PLAIN_SWAPPED_KEY))
        assertEquals(setOf(PLAIN_SWAPPED_STORED), dedicated.values[V2_PAIRS])
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `malformed scores do not stop the migration`() {
        defaults.values[V1_PAIRS] = setOf(PLAIN_STORED)
        defaults.values[V1_SCORES] = "not json"

        migrate()

        assertTrue(newStore().isSubmitted(PLAIN_SWAPPED_KEY))
        assertNull(newStore().scoresFor(PLAIN_SWAPPED_KEY))
        assertNoCompareKeysIn(defaults)

        dedicated.values.clear()
        defaults.values[V2_PAIRS] = setOf(SLASH_STORED)
        defaults.values[V2_SCORES] = "not json"

        migrate()

        assertTrue(newStore().isSubmitted(SLASH_KEY))
        assertNull(newStore().scoresFor(SLASH_KEY))
        assertNoCompareKeysIn(defaults)
    }

    @Test
    fun `the store works after migration and writes only to the dedicated file`() {
        seedV2()
        migrate()
        val store = newStore()
        val defaultsAfterMigration = defaults.values.toMap()

        store.markSubmitted(PLAIN_KEY)
        store.recordScores(PLAIN_KEY, mainScore = 10)

        val reloaded = newStore()
        assertTrue(reloaded.isSubmitted(SLASH_KEY))
        assertTrue(reloaded.isSubmitted(PLAIN_KEY))
        assertEquals(ComparisonScores(mainScore = 1), reloaded.scoresFor(SLASH_KEY))
        assertEquals(ComparisonScores(mainScore = 10), reloaded.scoresFor(PLAIN_KEY))
        assertEquals(defaultsAfterMigration, defaults.values)
    }

    @Test
    fun `clear does not bring migrated legacy data back`() {
        seedV1()
        migrate()
        val store = newStore()
        assertTrue(store.isSubmitted(PLAIN_SWAPPED_KEY))

        store.clear()

        migrate()
        val reloaded = newStore()
        assertFalse(reloaded.isSubmitted(PLAIN_SWAPPED_KEY))
        assertFalse(reloaded.isSubmitted(PLAIN_KEY))
        assertNull(reloaded.scoresFor(PLAIN_SWAPPED_KEY))
        assertNull(reloaded.scoresFor(PLAIN_KEY))
    }

    private companion object {
        const val V1_PAIRS = "compare_submitted_pairs_v1"
        const val V1_SCORES = "compare_submitted_scores_v1"
        const val V2_PAIRS = "compare_submitted_pairs_v2"
        const val V2_SCORES = "compare_submitted_scores_v2"
        const val UNRELATED_KEY = "theme"

        val SLASH_KEY = CompareKey("yt:aaa???", "yt:b>>>")
        val PLAIN_KEY = CompareKey("yt:dQw4w9WgXcQ", "yt:jNQXAC9IVRw")
        val PLAIN_SWAPPED_KEY = CompareKey("yt:jNQXAC9IVRw", "yt:dQw4w9WgXcQ")

        const val SLASH_STORED = "eXQ6YWFhPz8/|eXQ6Yj4+Pg=="
        const val PLAIN_STORED = "eXQ6ZFF3NHc5V2dYY1E=|eXQ6ak5RWEFDOUlWUnc="
        const val PLAIN_SWAPPED_STORED = "eXQ6ak5RWEFDOUlWUnc=|eXQ6ZFF3NHc5V2dYY1E="
    }
}

private class FakePrefs : SharedPreferences by Mockito.mock(SharedPreferences::class.java) {
    val values = mutableMapOf<String, Any>()
    var commits = 0
    var applies = 0
    var commitSucceeds = true
    var loseApplies = false

    override fun contains(key: String): Boolean = key in values

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        return (values[key] as Set<String>?)?.toMutableSet() ?: defValues
    }

    override fun edit(): SharedPreferences.Editor = FakeEditor(this)

    fun commit(pending: Map<String, Any?>): Boolean {
        commits++
        if (commitSucceeds) {
            store(pending)
        }
        return commitSucceeds
    }

    fun apply(pending: Map<String, Any?>) {
        applies++
        if (!loseApplies) {
            store(pending)
        }
    }

    private fun store(pending: Map<String, Any?>) {
        pending.forEach { (key, value) ->
            if (value == null) {
                values.remove(key)
            } else {
                values[key] = value
            }
        }
    }
}

private class FakeEditor(
    private val prefs: FakePrefs
) : SharedPreferences.Editor by Mockito.mock(SharedPreferences.Editor::class.java) {
    private val pending = mutableMapOf<String, Any?>()

    private fun put(key: String, value: Any?): SharedPreferences.Editor = also { pending[key] = value }

    override fun putString(key: String, value: String?) = put(key, value)

    override fun putStringSet(key: String, values: MutableSet<String>?) = put(key, values?.toSet())

    override fun remove(key: String) = put(key, null)

    override fun commit() = prefs.commit(pending)

    override fun apply() = prefs.apply(pending)
}
