package com.mazo.ayna.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mazo.ayna.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * THE single shared DataStore for "ayna_prefs".
 * Never declare a second preferencesDataStore delegate with this name —
 * two delegates for the same file crash DataStore at runtime.
 */
internal val Context.aynaPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.PREFS_NAME)

/** Typed access to Ayna's DataStore preferences. */
class Prefs(private val context: Context) {

    companion object {
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val INSTALL_TIME = longPreferencesKey("install_time")
        val WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        val OVERLAY_ENABLED = booleanPreferencesKey("overlay_enabled")
        val LANGUAGE = stringPreferencesKey("language")
        val KNOWLEDGE_BUNDLE_VERSION = intPreferencesKey("knowledge_bundle_version")
        val TRIAL_DAYS = intPreferencesKey("trial_days")
        val PRICE_PKR = intPreferencesKey("price_pkr")
        val LICENSE_STATUS = stringPreferencesKey("license_status")
        val LAST_LICENSE_CHECK = longPreferencesKey("last_license_check")
        /** User's name, asked once in the first-launch intro (Ayna asks it). */
        val USER_NAME = stringPreferencesKey("user_name")
    }

    suspend fun isOnboardingDone(): Boolean =
        context.aynaPrefsDataStore.data.map { it[ONBOARDING_DONE] ?: false }.first()

    suspend fun setOnboardingDone() {
        context.aynaPrefsDataStore.edit { it[ONBOARDING_DONE] = true }
    }

    /** First-install timestamp; set once, never overwritten. */
    suspend fun installTime(): Long {
        val existing = context.aynaPrefsDataStore.data.map { it[INSTALL_TIME] ?: 0L }.first()
        if (existing != 0L) return existing
        val now = System.currentTimeMillis()
        context.aynaPrefsDataStore.edit { it[INSTALL_TIME] = now }
        return now
    }

    suspend fun getInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, default: Int): Int =
        context.aynaPrefsDataStore.data.map { it[key] ?: default }.first()

    suspend fun setInt(key: androidx.datastore.preferences.core.Preferences.Key<Int>, value: Int) {
        context.aynaPrefsDataStore.edit { it[key] = value }
    }

    suspend fun getString(key: androidx.datastore.preferences.core.Preferences.Key<String>, default: String): String =
        context.aynaPrefsDataStore.data.map { it[key] ?: default }.first()

    suspend fun setString(key: androidx.datastore.preferences.core.Preferences.Key<String>, value: String) {
        context.aynaPrefsDataStore.edit { it[key] = value }
    }

    suspend fun getBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, default: Boolean): Boolean =
        context.aynaPrefsDataStore.data.map { it[key] ?: default }.first()

    suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.aynaPrefsDataStore.edit { it[key] = value }
    }

    suspend fun getLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, default: Long): Long =
        context.aynaPrefsDataStore.data.map { it[key] ?: default }.first()

    suspend fun setLong(key: androidx.datastore.preferences.core.Preferences.Key<Long>, value: Long) {
        context.aynaPrefsDataStore.edit { it[key] = value }
    }
}
