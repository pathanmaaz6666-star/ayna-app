package com.mazo.ayna.offline

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.mazo.ayna.core.aynaPrefsDataStore
import com.mazo.ayna.licensing.KnowledgeBundleResponse

/**
 * Thin layer over [OfflineBank] for callers: plain-text answers and
 * server-published knowledge-bundle updates. No network here — the
 * caller fetches the bundle JSON; this class only parses and applies it.
 */
class KnowledgeRepository(
    private val bank: OfflineBank
) {
    companion object {
        private val BUNDLE_VERSION_KEY = intPreferencesKey("knowledge_bundle_version")
    }

    /**
     * Apply a server bundle response directly. Server ids are integers;
     * local ids are strings, so they are stringified (never collide with
     * the bundled seed ids like "edu_001").
     */
    suspend fun applyServerBundle(response: KnowledgeBundleResponse): Int {
        val entries = response.items.map {
            SeedEntry(
                id = it.id.toString(),
                category = it.category,
                language = it.language,
                keywords = it.keywords,
                question = it.question,
                answer = it.answer
            )
        }
        val dao = KnowledgeDatabase.getInstance(bank.appContext).knowledgeDao()
        val entities = entries.mapNotNull { it.toEntity(response.version) }
        if (entities.isNotEmpty()) dao.insertAll(entities)
        var applied = entities.size
        if (response.deleted.isNotEmpty()) {
            dao.deleteByIds(response.deleted)
            applied += response.deleted.size
        }
        bank.appContext.aynaPrefsDataStore.edit { prefs ->
            prefs[BUNDLE_VERSION_KEY] = response.version
        }
        bank.refreshCache()
        return applied
    }

    /** Answer text for [query], or null when the offline bank has no match. */
    suspend fun answerOrNull(query: String): String? {
        return bank.answer(query)?.answer
    }

    /**
     * Apply a server-published bundle: upsert its entries by id, delete the
     * ids listed under "deleted", store the new bundle version.
     * Returns the number of rows applied (upserted + deleted).
     */
    suspend fun applyBundle(json: String, version: Int): Int {
        val bundle = bank.parseBundle(json) ?: return 0
        val dao = KnowledgeDatabase.getInstance(bank.appContext).knowledgeDao()
        val entities = bundle.entries.mapNotNull { it.toEntity(version) }
        if (entities.isNotEmpty()) dao.insertAll(entities)
        var applied = entities.size
        if (bundle.deleted.isNotEmpty()) {
            dao.deleteByIds(bundle.deleted)
            applied += bundle.deleted.size
        }
        bank.appContext.aynaPrefsDataStore.edit { prefs ->
            prefs[BUNDLE_VERSION_KEY] = version
        }
        bank.refreshCache()
        return applied
    }
}
