package com.mazo.ayna.offline

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.mazo.ayna.core.aynaPrefsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Shared Gson for the offline package (seed parsing + keyword JSON). */
private val offlineGson = Gson()

/** Convert a raw seed/bundle entry to a Room entity. Null when id/question/answer is blank. */
internal fun SeedEntry.toEntity(version: Int): KnowledgeEntity? {
    if (id.isBlank() || question.isBlank() || answer.isBlank()) return null
    return KnowledgeEntity(
        id = id,
        category = category.ifBlank { "general" },
        language = language,
        keywordsJson = offlineGson.toJson(keywords),
        question = question,
        answer = answer,
        version = version
    )
}

/** Raw shape of one entry in assets/offline/knowledge.json and in server bundles. */
data class SeedEntry(
    @SerializedName("id") val id: String = "",
    @SerializedName("category") val category: String = "",
    @SerializedName("language") val language: String = "roman",
    @SerializedName("keywords") val keywords: List<String> = emptyList(),
    @SerializedName("question") val question: String = "",
    @SerializedName("answer") val answer: String = ""
)

/** Raw shape of a knowledge bundle: seed file or server update. */
data class SeedBundle(
    @SerializedName("entries") val entries: List<SeedEntry> = emptyList(),
    @SerializedName("deleted") val deleted: List<String> = emptyList(),
    @SerializedName("version") val version: Int = 1
)

/**
 * Offline knowledge bank: seeds Room from assets/offline/knowledge.json
 * on first run and answers Roman Urdu / Urdu script / English queries
 * with a keyword scorer. No network.
 */
class OfflineBank(context: Context) {

    internal val appContext = context.applicationContext
    private val dao: KnowledgeDao = KnowledgeDatabase.getInstance(appContext).knowledgeDao()

    @Volatile
    private var cachedEntries: List<KnowledgeEntity>? = null

    companion object {
        const val SEED_ASSET_PATH = "offline/knowledge.json"
        const val BUNDLE_VERSION = 1
        private val BUNDLE_VERSION_KEY = intPreferencesKey("knowledge_bundle_version")

        /** Minimum score for [answer] to return a match. */
        const val MIN_SCORE = 3
    }

    /** Seed from the bundled JSON only when the table is empty. Safe to call on every launch. */
    suspend fun seedIfEmpty() {
        if (dao.count() > 0) return
        val json = try {
            appContext.assets.open(SEED_ASSET_PATH).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            return
        }
        val bundle = parseBundle(json) ?: return
        val entities = bundle.entries.mapNotNull { it.toEntity(bundle.version) }
        if (entities.isEmpty()) return
        dao.insertAll(entities)
        cachedEntries = dao.getAll()
        appContext.aynaPrefsDataStore.edit { prefs ->
            prefs[BUNDLE_VERSION_KEY] = bundle.version
        }
    }

    /** Best matching entry for [query], or null when nothing scores above the threshold. */
    fun answer(query: String): KnowledgeEntity? {
        val scored = scoreAll(query)
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= MIN_SCORE) best.first else null
    }

    /** All entries of one category, in seed order. */
    suspend fun byCategory(category: String): List<KnowledgeEntity> {
        return dao.searchByCategory(category)
    }

    /** Top 5 entries by match score for free text, best first. Empty when nothing matches. */
    fun search(text: String): List<KnowledgeEntity> {
        return scoreAll(text)
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first }
    }

    /** Current stored bundle version (0 when never seeded). */
    suspend fun bundleVersion(): Int {
        return appContext.aynaPrefsDataStore.data.map { it[BUNDLE_VERSION_KEY] ?: 0 }.first()
    }

    /** Score every cached entry against [query]; returns (entry, score) pairs. */
    private fun scoreAll(query: String): List<Pair<KnowledgeEntity, Int>> {
        val entries = cachedEntries ?: run {
            // Synchronous callers (answer/search) use the in-memory cache;
            // fall back to an empty list until seedIfEmpty() has run once.
            return emptyList()
        }
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        val queryWords = normalizedQuery.split(" ").filter { it.length >= 2 }
        return entries.map { entry ->
            entry to scoreEntry(entry, normalizedQuery, queryWords)
        }
    }

    /**
     * Score = sum of matched keyword lengths (longer, more specific keywords
     * weigh more) + 2 per query word found in the stored question.
     * A keyword counts only when it appears in the query, so generic words
     * in the question alone cannot trigger a match.
     */
    private fun scoreEntry(
        entry: KnowledgeEntity,
        normalizedQuery: String,
        queryWords: List<String>
    ): Int {
        var score = 0
        var keywordHit = false
        for (keyword in parseKeywords(entry.keywordsJson)) {
            val normalizedKeyword = normalize(keyword)
            if (normalizedKeyword.isBlank()) continue
            if (normalizedQuery.contains(normalizedKeyword)) {
                keywordHit = true
                score += normalizedKeyword.length
            }
        }
        if (!keywordHit) return 0
        val normalizedQuestion = normalize(entry.question)
        for (word in queryWords) {
            if (normalizedQuestion.contains(word)) score += 2
        }
        return score
    }

    private fun parseKeywords(keywordsJson: String): List<String> {
        return try {
            offlineGson.fromJson<List<String>>(
                keywordsJson,
                object : TypeToken<List<String>>() {}.type
            ) ?: emptyList()
        } catch (e: JsonSyntaxException) {
            emptyList()
        }
    }

    internal fun parseBundle(json: String): SeedBundle? {
        return try {
            offlineGson.fromJson(json, SeedBundle::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    /** Lowercase, trim, strip punctuation. Keeps Unicode letters so Urdu script survives. */
    internal fun normalize(text: String): String {
        return text.lowercase()
            .trim()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Refresh the in-memory cache from Room (call after a bundle update). */
    suspend fun refreshCache() {
        cachedEntries = dao.getAll()
    }
}
