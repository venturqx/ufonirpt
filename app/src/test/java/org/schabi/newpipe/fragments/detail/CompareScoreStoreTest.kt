package org.schabi.newpipe.fragments.detail

import com.grack.nanojson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareScoreStoreTest {
    private class FakeBackend(
        var submitted: Set<String> = emptySet(),
        var scores: String? = null,
        val legacySubmitted: Set<String> = emptySet(),
        val legacyScores: String? = null
    ) : CompareScoreBackend {
        var submittedWrites = 0
        var scoresWrites = 0

        override fun readSubmitted() = submitted

        override fun writeSubmitted(pairs: Set<String>) {
            submittedWrites++
            submitted = pairs
        }

        override fun readScores() = scores

        override fun writeScores(json: String) {
            scoresWrites++
            scores = json
        }

        override fun hasCurrentData() = submitted.isNotEmpty() || scores != null

        override fun readLegacySubmitted() = legacySubmitted

        override fun readLegacyScores() = legacyScores
    }

    @Test
    fun `reads data written by the legacy android implementation`() {
        val store = CompareScoreStore(
            FakeBackend(submitted = setOf(SLASH_STORED, PLAIN_STORED), scores = LEGACY_SCORES)
        )

        assertTrue(store.isSubmitted(SLASH_KEY))
        assertTrue(store.isSubmitted(PLAIN_KEY))
        assertFalse(store.isSubmitted(EXTRAS_KEY))
        assertEquals(
            ComparisonScores(-7, mapOf("reliability" to 3, "pedagogy" to 0)),
            store.scoresFor(SLASH_KEY)
        )
        assertEquals(ComparisonScores(mainScore = 10), store.scoresFor(PLAIN_KEY))
        assertEquals(
            ComparisonScores(extraScores = mapOf("importance" to 5)),
            store.scoresFor(EXTRAS_KEY)
        )
    }

    @Test
    fun `writes the legacy on-disk format`() {
        val backend = FakeBackend()
        val store = CompareScoreStore(backend)

        store.markSubmitted(PLAIN_KEY)
        store.recordScores(PLAIN_KEY, mainScore = 10, extraScores = mapOf("importance" to 5))

        assertEquals(setOf(PLAIN_STORED), backend.submitted)
        assertEquals(
            """{"$PLAIN_STORED":{"mainScore":10,"extraScores":{"importance":5}}}""",
            backend.scores
        )
    }

    @Test
    fun `survives a round trip through the backend`() {
        val backend = FakeBackend()
        val store = CompareScoreStore(backend)
        val keys = listOf(SLASH_KEY, CompareKey("yt:a|b", "é中😀"), CompareKey("", "yt:x"))
        val scores = ComparisonScores(-3, mapOf("reliability" to 2))

        keys.forEach { key ->
            store.markSubmitted(key)
            store.recordScores(key, scores.mainScore, scores.extraScores)
        }

        val reloaded = CompareScoreStore(backend)
        keys.forEach { key ->
            assertTrue(reloaded.isSubmitted(key))
            assertEquals(scores, reloaded.scoresFor(key))
        }
    }

    @Test
    fun `a corrupt entry drops only itself`() {
        val corruptEntries = listOf(
            """"not an object"""",
            """{"mainScore":"high"}""",
            """{"extraScores":{"reliability":"high"}}""",
            """{"mainScore":5,"extraScores":[1]}"""
        )

        corruptEntries.forEach { corrupt ->
            val store = CompareScoreStore(
                FakeBackend(
                    scores = """{"$SLASH_STORED":$corrupt,"$PLAIN_STORED":{"mainScore":10}}"""
                )
            )

            assertNull(corrupt, store.scoresFor(SLASH_KEY))
            assertEquals(corrupt, ComparisonScores(mainScore = 10), store.scoresFor(PLAIN_KEY))
        }
    }

    @Test
    fun `undecodable keys are dropped and never written back`() {
        val backend = FakeBackend(
            submitted = setOf("no-separator", "!!!!|eXQ6b25seQ==", PLAIN_STORED),
            scores = """{"no-separator":{"mainScore":1},"!!!!|eXQ6b25seQ==":{"mainScore":2},""" +
                """"$PLAIN_STORED":{"mainScore":10}}"""
        )
        val store = CompareScoreStore(backend)

        assertEquals(ComparisonScores(mainScore = 10), store.scoresFor(PLAIN_KEY))

        store.markSubmitted(SLASH_KEY)
        store.recordScores(SLASH_KEY, mainScore = 1)

        assertEquals(setOf(PLAIN_STORED, SLASH_STORED), backend.submitted)
        assertEquals(2, JsonParser.`object`().from(backend.scores).size)
    }

    @Test
    fun `an unreadable document loads as empty without writing`() {
        listOf(null, "", "   ", "{", "not json", "[1]", """{"a":{t""").forEach { raw ->
            val backend = FakeBackend(scores = raw)

            assertNull(CompareScoreStore(backend).scoresFor(PLAIN_KEY))
            assertEquals(0, backend.scoresWrites)
        }
    }

    @Test
    fun `recordScores keeps what is omitted and replaces extras wholesale`() {
        val store = CompareScoreStore(FakeBackend())

        assertEquals(
            ComparisonScores(mainScore = 1),
            store.recordScores(PLAIN_KEY, mainScore = 1)
        )
        assertEquals(
            ComparisonScores(1, mapOf("a" to 2)),
            store.recordScores(PLAIN_KEY, extraScores = mapOf("a" to 2))
        )
        assertEquals(
            ComparisonScores(3, mapOf("a" to 2)),
            store.recordScores(PLAIN_KEY, mainScore = 3)
        )
        assertEquals(
            ComparisonScores(3, mapOf("b" to 4)),
            store.recordScores(PLAIN_KEY, extraScores = mapOf("b" to 4))
        )
        assertEquals(
            ComparisonScores(mainScore = 3),
            store.recordScores(PLAIN_KEY, extraScores = emptyMap())
        )
        assertEquals(ComparisonScores(mainScore = 3), store.scoresFor(PLAIN_KEY))
    }

    @Test
    fun `recordScores with nothing to store changes nothing`() {
        val backend = FakeBackend()
        val store = CompareScoreStore(backend)

        assertNull(store.recordScores(PLAIN_KEY))
        assertNull(store.recordScores(PLAIN_KEY, extraScores = emptyMap()))

        assertNull(store.scoresFor(PLAIN_KEY))
        assertEquals(0, backend.scoresWrites)
    }

    @Test
    fun `markSubmitted writes only when the key is new`() {
        val backend = FakeBackend()
        val store = CompareScoreStore(backend)

        store.markSubmitted(PLAIN_KEY)
        store.markSubmitted(PLAIN_KEY)

        assertEquals(1, backend.submittedWrites)
    }

    @Test
    fun `forget removes the key from both sets and persists only real changes`() {
        val backend = FakeBackend(
            submitted = setOf(PLAIN_STORED, SLASH_STORED),
            scores = LEGACY_SCORES
        )
        val store = CompareScoreStore(backend)

        store.forget(PLAIN_KEY)
        store.forget(PLAIN_KEY)

        assertEquals(1, backend.submittedWrites)
        assertEquals(1, backend.scoresWrites)
        val reloaded = CompareScoreStore(backend)
        assertFalse(reloaded.isSubmitted(PLAIN_KEY))
        assertNull(reloaded.scoresFor(PLAIN_KEY))
        assertTrue(reloaded.isSubmitted(SLASH_KEY))
        assertEquals(store.scoresFor(SLASH_KEY), reloaded.scoresFor(SLASH_KEY))

        store.forget(EXTRAS_KEY)

        assertEquals(1, backend.submittedWrites)
        assertEquals(2, backend.scoresWrites)
    }

    @Test
    fun `legacy data is migrated once with swapped keys and unchanged scores`() {
        val backend = FakeBackend(
            legacySubmitted = setOf(PLAIN_STORED),
            legacyScores = """{"$PLAIN_STORED":{"mainScore":-7,"extraScores":{"reliability":3}}}"""
        )

        val store = CompareScoreStore(backend)
        val swapped = CompareKey(PLAIN_KEY.entityB, PLAIN_KEY.entityA)

        assertTrue(store.isSubmitted(swapped))
        assertFalse(store.isSubmitted(PLAIN_KEY))
        assertEquals(
            ComparisonScores(-7, mapOf("reliability" to 3)),
            store.scoresFor(swapped)
        )
        assertNull(store.scoresFor(PLAIN_KEY))

        val reloaded = CompareScoreStore(backend)
        assertTrue(reloaded.isSubmitted(swapped))
        assertFalse(reloaded.isSubmitted(PLAIN_KEY))
        assertEquals(1, backend.submittedWrites)
        assertEquals(1, backend.scoresWrites)
    }

    @Test
    fun `migration leaves the legacy data untouched`() {
        val legacyScores = """{"$PLAIN_STORED":{"mainScore":10}}"""
        val backend = FakeBackend(
            legacySubmitted = setOf(PLAIN_STORED),
            legacyScores = legacyScores
        )

        CompareScoreStore(backend)

        assertEquals(setOf(PLAIN_STORED), backend.legacySubmitted)
        assertEquals(legacyScores, backend.legacyScores)
    }

    @Test
    fun `a current store is not swapped again`() {
        val backend = FakeBackend(
            submitted = setOf(PLAIN_STORED),
            scores = """{"$PLAIN_STORED":{"mainScore":10}}""",
            legacySubmitted = setOf(SLASH_STORED),
            legacyScores = """{"$SLASH_STORED":{"mainScore":1}}"""
        )

        val store = CompareScoreStore(backend)

        assertTrue(store.isSubmitted(PLAIN_KEY))
        assertFalse(store.isSubmitted(SLASH_KEY))
        assertEquals(0, backend.submittedWrites)
        assertEquals(0, backend.scoresWrites)
    }

    @Test
    fun `an empty store is a no-op`() {
        val backend = FakeBackend()

        CompareScoreStore(backend)

        assertEquals(0, backend.submittedWrites)
        assertEquals(0, backend.scoresWrites)
    }

    private companion object {
        val SLASH_KEY = CompareKey("yt:aaa???", "yt:b>>>")
        val PLAIN_KEY = CompareKey("yt:dQw4w9WgXcQ", "yt:jNQXAC9IVRw")
        val EXTRAS_KEY = CompareKey("yt:only", "yt:extras")

        const val SLASH_STORED = "eXQ6YWFhPz8/|eXQ6Yj4+Pg=="
        const val PLAIN_STORED = "eXQ6ZFF3NHc5V2dYY1E=|eXQ6ak5RWEFDOUlWUnc="

        const val LEGACY_SCORES = """{"eXQ6YWFhPz8\/|eXQ6Yj4+Pg==":{"mainScore":-7,""" +
            """"extraScores":{"reliability":3,"pedagogy":0}},""" +
            """"eXQ6ZFF3NHc5V2dYY1E=|eXQ6ak5RWEFDOUlWUnc=":{"mainScore":10},""" +
            """"eXQ6b25seQ==|eXQ6ZXh0cmFz":{"extraScores":{"importance":5}}}"""
    }
}
