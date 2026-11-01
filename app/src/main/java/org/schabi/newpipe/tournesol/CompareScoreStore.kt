package org.schabi.newpipe.tournesol

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

internal data class CompareKey(val entityA: String, val entityB: String)

internal data class ComparisonScores(
    val mainScore: Int? = null,
    val extraScores: Map<String, Int> = emptyMap()
)

internal data class CompareScoreSnapshot(val submitted: Set<String>, val scores: String?)

internal interface CompareScoreBackend {
    fun readSubmitted(): Set<String>

    fun writeSubmitted(pairs: Set<String>)

    fun readScores(): String?

    fun writeScores(json: String)
}

private const val PREF_SUBMITTED_COMPARISONS = "compare_submitted_pairs_v2"
private const val PREF_COMPARISON_SCORES = "compare_submitted_scores_v2"
private const val LEGACY_PREF_SUBMITTED_COMPARISONS = "compare_submitted_pairs_v1"
private const val LEGACY_PREF_COMPARISON_SCORES = "compare_submitted_scores_v1"

internal class CompareScorePrefs(
    private val prefs: SharedPreferences
) : CompareScoreBackend {
    override fun readSubmitted(): Set<String> {
        return prefs.getStringSet(PREF_SUBMITTED_COMPARISONS, emptySet()).orEmpty()
    }

    override fun writeSubmitted(pairs: Set<String>) {
        prefs.edit().putStringSet(PREF_SUBMITTED_COMPARISONS, pairs).apply()
    }

    override fun readScores(): String? = prefs.getString(PREF_COMPARISON_SCORES, null)

    override fun writeScores(json: String) {
        prefs.edit().putString(PREF_COMPARISON_SCORES, json).apply()
    }

    fun hasCurrentData(): Boolean = prefs.contains(PREF_SUBMITTED_COMPARISONS) || prefs.contains(PREF_COMPARISON_SCORES)

    fun commit(snapshot: CompareScoreSnapshot): Boolean {
        return prefs.edit()
            .putStringSet(PREF_SUBMITTED_COMPARISONS, snapshot.submitted)
            .putString(PREF_COMPARISON_SCORES, snapshot.scores)
            .commit()
    }
}

internal class CompareScoreLegacyPrefs(
    private val prefs: SharedPreferences
) {
    fun readV2(): CompareScoreSnapshot? = read(PREF_SUBMITTED_COMPARISONS, PREF_COMPARISON_SCORES)

    fun readV1(): CompareScoreSnapshot? = read(LEGACY_PREF_SUBMITTED_COMPARISONS, LEGACY_PREF_COMPARISON_SCORES)

    fun delete() {
        val stale = KEYS.filter { prefs.contains(it) }
        if (stale.isEmpty()) {
            return
        }
        val editor = prefs.edit()
        stale.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun read(submittedKey: String, scoresKey: String): CompareScoreSnapshot? {
        if (!prefs.contains(submittedKey) && !prefs.contains(scoresKey)) {
            return null
        }
        return CompareScoreSnapshot(
            submitted = prefs.getStringSet(submittedKey, emptySet()).orEmpty(),
            scores = prefs.getString(scoresKey, null)
        )
    }

    private companion object {
        val KEYS = listOf(
            PREF_SUBMITTED_COMPARISONS,
            PREF_COMPARISON_SCORES,
            LEGACY_PREF_SUBMITTED_COMPARISONS,
            LEGACY_PREF_COMPARISON_SCORES
        )
    }
}

internal class CompareScoreStore(private val backend: CompareScoreBackend) {
    private val submitted = backend.readSubmitted()
        .mapNotNullTo(LinkedHashSet<CompareKey>(), ::parseCompareKey)
    private val scores = parseScores(backend.readScores()).toMutableMap()

    @Synchronized
    fun isSubmitted(key: CompareKey): Boolean = key in submitted

    @Synchronized
    fun markSubmitted(key: CompareKey) {
        if (submitted.add(key)) {
            saveSubmitted()
        }
    }

    @Synchronized
    fun scoresFor(key: CompareKey): ComparisonScores? = scores[key]

    @Synchronized
    fun recordScores(
        key: CompareKey,
        mainScore: Int? = null,
        extraScores: Map<String, Int>? = null
    ): ComparisonScores? {
        val existing = scores[key]
        val mergedMain = mainScore ?: existing?.mainScore
        val mergedExtra = extraScores ?: existing?.extraScores.orEmpty()
        if (mergedMain == null && mergedExtra.isEmpty()) {
            return null
        }
        val merged = ComparisonScores(mainScore = mergedMain, extraScores = mergedExtra)
        scores[key] = merged
        saveScores()
        return merged
    }

    @Synchronized
    fun forget(key: CompareKey) {
        if (submitted.remove(key)) {
            saveSubmitted()
        }
        if (scores.remove(key) != null) {
            saveScores()
        }
    }

    @Synchronized
    fun clear() {
        submitted.clear()
        scores.clear()
        saveSubmitted()
        saveScores()
    }

    private fun saveSubmitted() {
        backend.writeSubmitted(submitted.map(CompareKey::toStorage).toSet())
    }

    private fun saveScores() {
        backend.writeScores(serializeScores(scores))
    }

    companion object {
        private const val PREFS_FILE = "compare_scores"

        private var shared: CompareScoreStore? = null

        @Synchronized
        fun shared(context: Context): CompareScoreStore = shared ?: create(context).also { shared = it }

        private fun create(context: Context): CompareScoreStore {
            val current = CompareScorePrefs(context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE))
            val legacy = CompareScoreLegacyPrefs(PreferenceManager.getDefaultSharedPreferences(context))
            migrateCompareScores(legacy, current)
            return CompareScoreStore(current)
        }
    }
}

private fun CompareKey.swapped(): CompareKey = CompareKey(entityB, entityA)

internal fun migrateCompareScores(legacy: CompareScoreLegacyPrefs, current: CompareScorePrefs) {
    if (!current.hasCurrentData()) {
        val snapshot = legacy.readV2() ?: legacy.readV1()?.withSwappedKeys() ?: return
        if (!current.commit(snapshot)) {
            return
        }
    }
    legacy.delete()
}

private fun CompareScoreSnapshot.withSwappedKeys(): CompareScoreSnapshot = CompareScoreSnapshot(
    submitted = submitted.mapNotNull(::parseCompareKey).map { it.swapped().toStorage() }.toSet(),
    scores = serializeScores(parseScores(scores).mapKeys { it.key.swapped() })
)

private fun CompareKey.toStorage(): String {
    return "${entityA.encodeUtf8().base64()}|${entityB.encodeUtf8().base64()}"
}

private fun parseCompareKey(value: String): CompareKey? {
    val parts = value.split('|', limit = 2)
    if (parts.size != 2) {
        return null
    }
    val entityA = parts[0].decodeBase64()?.utf8() ?: return null
    val entityB = parts[1].decodeBase64()?.utf8() ?: return null
    return CompareKey(entityA, entityB)
}

private fun parseScores(raw: String?): Map<CompareKey, ComparisonScores> {
    if (raw.isNullOrBlank()) {
        return emptyMap()
    }
    val root = try {
        JsonParser.`object`().from(raw)
    } catch (_: JsonParserException) {
        return emptyMap()
    } catch (_: IndexOutOfBoundsException) {
        return emptyMap()
    }
    val parsed = LinkedHashMap<CompareKey, ComparisonScores>()
    for ((storedKey, value) in root.entries) {
        val key = parseCompareKey(storedKey) ?: continue
        val entry = value as? JsonObject ?: continue
        parsed[key] = parseEntry(entry) ?: continue
    }
    return parsed
}

private fun parseEntry(entry: JsonObject): ComparisonScores? {
    val mainScore = entry["mainScore"]
    if (mainScore != null && mainScore !is Number) {
        return null
    }
    val extras = entry["extraScores"] ?: JsonObject()
    if (extras !is JsonObject) {
        return null
    }
    val extraScores = LinkedHashMap<String, Int>()
    for ((criteria, value) in extras.entries) {
        extraScores[criteria] = (value as? Number)?.toInt() ?: return null
    }
    return ComparisonScores(
        mainScore = (mainScore as? Number)?.toInt(),
        extraScores = extraScores
    )
}

private fun serializeScores(scores: Map<CompareKey, ComparisonScores>): String {
    val writer = JsonWriter.string().`object`()
    scores.forEach { (key, entry) ->
        writer.`object`(key.toStorage())
        entry.mainScore?.let { writer.value("mainScore", it) }
        if (entry.extraScores.isNotEmpty()) {
            writer.`object`("extraScores")
            entry.extraScores.forEach { (criteria, value) ->
                writer.value(criteria, value)
            }
            writer.end()
        }
        writer.end()
    }
    return writer.end().done()
}
