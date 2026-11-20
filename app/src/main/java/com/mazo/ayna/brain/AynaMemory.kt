package com.mazo.ayna.brain

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mazo.ayna.core.Prefs

/**
 * Ayna's memory — what she remembers about the user.
 *
 * Persisted in the shared DataStore ("ayna_prefs") as JSON strings, via
 * [Prefs]. Nothing leaves the phone except the Gemini request itself.
 * Caps: 30 free facts, 20 favourites.
 */
class AynaMemory(private val context: Context) {

    companion object {
        val MEMORY_KEY = stringPreferencesKey("ayna_memory_json")
        val HISTORY_KEY = stringPreferencesKey("ayna_chat_history")
        const val MAX_FACTS = 30
        const val MAX_FAVOURITES = 20
        const val MAX_HISTORY_TURNS = 30
        const val HISTORY_CONTEXT_TURNS = 10
    }

    private val prefs = Prefs(context)
    private val gson = Gson()

    data class MemoryData(
        val name: String = "",
        val favourites: Map<String, String> = emptyMap(),
        val dates: Map<String, String> = emptyMap(),
        val facts: List<String> = emptyList()
    )

    data class ChatTurn(val role: String, val text: String)

    suspend fun load(): MemoryData {
        val json = prefs.getString(MEMORY_KEY, "")
        if (json.isBlank()) return MemoryData()
        return runCatching { gson.fromJson(json, MemoryData::class.java) }.getOrNull()
            ?: MemoryData()
    }

    suspend fun save(data: MemoryData) {
        prefs.setString(MEMORY_KEY, gson.toJson(data))
    }

    /**
     * Pull simple facts out of a user message with regexes (Roman Urdu +
     * English). Anything it cannot parse is simply ignored — never guessed.
     */
    suspend fun rememberFrom(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        var data = load()
        var changed = false

        // Name: "mera naam Maaz hai" / "my name is Maaz"
        matchFirst(clean, """(?i)mera naam\s+(.+?)\s+hai\b""", """(?i)my name is\s+(.+?)$""")?.let {
            val name = it.trim().trimEnd('.', '!', '?')
            if (name.isNotBlank() && name.length <= 40) {
                data = data.copy(name = name)
                changed = true
            }
        }

        // Favourites: "mera favourite colour red hai" / "my favourite food is biryani"
        matchGroups(
            clean,
            """(?i)mer[ai] favourite\s+(\w+)\s+(.+?)\s+hai\b""",
            """(?i)my favou?rite\s+(\w+)\s+is\s+(.+?)$"""
        )?.let { (key, value) ->
            val k = key.trim().lowercase()
            val v = value.trim().trimEnd('.', '!', '?')
            if (k.isNotBlank() && v.isNotBlank()) {
                val favs = data.favourites.toMutableMap()
                favs[k] = v
                // Cap at 20: drop the oldest entry.
                while (favs.size > MAX_FAVOURITES) favs.remove(favs.keys.first())
                data = data.copy(favourites = favs)
                changed = true
            }
        }

        // Birthday: "meri birthday 5 November hai" / "my birthday is 5 November"
        matchFirst(
            clean,
            """(?i)mer[ai] birthday\s+(.+?)\s+hai\b""",
            """(?i)my birthday is\s+(.+?)$"""
        )?.let {
            val v = it.trim().trimEnd('.', '!', '?')
            if (v.isNotBlank()) {
                data = data.copy(dates = data.dates + ("birthday" to v))
                changed = true
            }
        }

        // Likes: "mujhe chai pasand hai" / "mujhe cricket pasand hai"
        matchFirst(clean, """(?i)mujhe\s+(.+?)\s+pasand hai\b""")?.let {
            val v = it.trim()
            if (v.isNotBlank()) {
                val facts = (data.facts + "likes $v").distinct().takeLast(MAX_FACTS)
                data = data.copy(facts = facts)
                changed = true
            }
        }

        if (changed) save(data)
    }

    /** Last [MAX_HISTORY_TURNS] chat turns, oldest first. */
    suspend fun loadHistory(): List<ChatTurn> {
        val json = prefs.getString(HISTORY_KEY, "")
        if (json.isBlank()) return emptyList()
        val type = object : TypeToken<List<ChatTurn>>() {}.type
        return runCatching { gson.fromJson<List<ChatTurn>>(json, type) }.getOrNull()
            ?: emptyList()
    }

    suspend fun appendHistory(role: String, text: String) {
        val updated = (loadHistory() + ChatTurn(role, text)).takeLast(MAX_HISTORY_TURNS)
        prefs.setString(HISTORY_KEY, gson.toJson(updated))
    }

    suspend fun clearHistory() {
        prefs.setString(HISTORY_KEY, "")
    }

    suspend fun clearAll() {
        prefs.setString(MEMORY_KEY, "")
        prefs.setString(HISTORY_KEY, "")
    }

    private fun matchFirst(text: String, vararg patterns: String): String? {
        for (pattern in patterns) {
            val m = Regex(pattern).find(text)
            if (m != null && m.groupValues.size > 1) return m.groupValues[1]
        }
        return null
    }

    private fun matchGroups(text: String, vararg patterns: String): Pair<String, String>? {
        for (pattern in patterns) {
            val m = Regex(pattern).find(text)
            if (m != null && m.groupValues.size > 2) {
                return m.groupValues[1] to m.groupValues[2]
            }
        }
        return null
    }
}
