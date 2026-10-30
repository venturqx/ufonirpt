package org.schabi.newpipe.fragments.detail

import android.content.SharedPreferences
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonParserException
import com.grack.nanojson.JsonWriter
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.encodeUtf8

internal data class CompareKey(val lastUid: String, val currentUid: String)

internal data class ComparisonScores(
    val mainScore: Int? = null,
    val extraScores: Map<String, Int> = emptyMap()
)

internal interface CompareScoreBackend {
    fun readSubmitted(): Set<String>

    fun writeSubmitted(pairs: Set<String>)

    fun readScores(): String?

    fun writeScores(json: String)
}

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

    private companion object {
        const val PREF_SUBMITTED_COMPARISONS = "compare_submitted_pairs_v1"
        const val PREF_COMPARISON_SCORES = "compare_submitted_scores_v1"
    }
}

internal class CompareScoreStore(private val backend: CompareScoreBackend) {
    private val submitted = backend.readSubmitted()
        .mapNotNullTo(LinkedHashSet<CompareKey>(), ::parseCompareKey)
    private val scores = parseScores(backend.readScores()).toMutableMap()

    fun isSubmitted(key: CompareKey): Boolean = key in submitted

    fun markSubmitted(key: CompareKey) {
        if (submitted.add(key)) {
            saveSubmitted()
        }
    }

    fun scoresFor(key: CompareKey): ComparisonScores? = scores[key]

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

    fun forget(key: CompareKey) {
        if (submitted.remove(key)) {
            saveSubmitted()
        }
        if (scores.remove(key) != null) {
            saveScores()
        }
    }

    private fun saveSubmitted() {
        backend.writeSubmitted(submitted.map(CompareKey::toStorage).toSet())
    }

    private fun saveScores() {
        backend.writeScores(serializeScores(scores))
    }
}

private fun CompareKey.toStorage(): String {
    return "${lastUid.encodeUtf8().base64()}|${currentUid.encodeUtf8().base64()}"
}

private fun parseCompareKey(value: String): CompareKey? {
    val parts = value.split('|', limit = 2)
    if (parts.size != 2) {
        return null
    }
    val lastUid = parts[0].decodeBase64()?.utf8() ?: return null
    val currentUid = parts[1].decodeBase64()?.utf8() ?: return null
    return CompareKey(lastUid, currentUid)
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
