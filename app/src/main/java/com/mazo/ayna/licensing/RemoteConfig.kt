package com.mazo.ayna.licensing

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import com.mazo.ayna.core.Prefs
import java.util.concurrent.TimeUnit

/**
 * Server-driven remote config (trial days, price, min version, knowledge bundle
 * version). Cached in DataStore "ayna_prefs" with a 24h TTL; on any failure the
 * cached values — or Maaz's defaults (10 days, Rs 300) — are returned so the
 * app never blocks on config.
 */
class RemoteConfigRepository(
    private val api: AynaApi,
    private val prefs: Prefs
) {
    constructor(context: Context, api: AynaApi) : this(api, Prefs(context.applicationContext))

    companion object {
        private val CONFIG_FETCHED_AT = longPreferencesKey("remote_config_fetched_at")
        private val CONFIG_TTL_MILLIS = TimeUnit.HOURS.toMillis(24)
    }

    suspend fun fetch(): RemoteConfig {
        val cached = readCached()
        val fetchedAt = prefs.getLong(CONFIG_FETCHED_AT, 0L)
        val fresh = fetchedAt != 0L &&
            System.currentTimeMillis() - fetchedAt < CONFIG_TTL_MILLIS
        if (fresh) return cached
        return try {
            val remote = api.getConfig()
            prefs.setInt(Prefs.TRIAL_DAYS, remote.trialDays)
            prefs.setInt(Prefs.PRICE_PKR, remote.pricePkr)
            prefs.setInt(Prefs.KNOWLEDGE_BUNDLE_VERSION, remote.knowledgeBundleVersion)
            prefs.setLong(CONFIG_FETCHED_AT, System.currentTimeMillis())
            remote
        } catch (_: Exception) {
            cached
        }
    }

    /** Last cached (or default) config without a network call. */
    suspend fun readCached(): RemoteConfig = RemoteConfig(
        trialDays = prefs.getInt(Prefs.TRIAL_DAYS, PaymentConstants.TRIAL_DAYS_DEFAULT),
        pricePkr = prefs.getInt(Prefs.PRICE_PKR, PaymentConstants.PRICE_PKR),
        minVersion = 1,
        knowledgeBundleVersion = prefs.getInt(Prefs.KNOWLEDGE_BUNDLE_VERSION, 1)
    )
}
