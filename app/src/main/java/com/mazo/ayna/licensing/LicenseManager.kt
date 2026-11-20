package com.mazo.ayna.licensing

import android.content.Context
import android.provider.Settings
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mazo.ayna.core.Prefs
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Owns the licence state for this device.
 *
 * Device id = Settings.Secure.ANDROID_ID. First-install time is stored once in
 * DataStore "ayna_prefs" (via [Prefs.installTime]) and never overwritten.
 *
 * refresh() asks the server first; offline it falls back to local trial math so
 * the app keeps working without a connection. A device Maaz already unlocked (or
 * with a payment pending) is never downgraded by the offline fallback.
 */
class LicenseManager(
    context: Context,
    private val api: AynaApi,
    private val prefs: Prefs = Prefs(context.applicationContext),
    private val configRepository: RemoteConfigRepository? = null
) {
    private val appContext = context.applicationContext

    companion object {
        private val FALLBACK_DEVICE_ID = stringPreferencesKey("fallback_device_id")
        private val MILLIS_PER_DAY = TimeUnit.DAYS.toMillis(1)
    }

    /** Stable device id: ANDROID_ID, or a generated UUID persisted on first use. */
    suspend fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            appContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return androidId
        }
        val stored = prefs.getString(FALLBACK_DEVICE_ID, "")
        if (stored.isNotBlank()) return stored
        val generated = UUID.randomUUID().toString()
        prefs.setString(FALLBACK_DEVICE_ID, generated)
        return generated
    }

    /**
     * Current licence state. Server first; offline fallback to local trial math:
     * trialDaysLeft = trialDays - whole days since first install.
     */
    suspend fun refresh(): LicenseState {
        val trialDays = configRepository?.fetch()?.trialDays
            ?: prefs.getInt(Prefs.TRIAL_DAYS, PaymentConstants.TRIAL_DAYS_DEFAULT)
        return try {
            val response = api.getLicense(deviceId())
            val state = LicenseState(
                status = LicenseStatus.fromServer(response.status),
                trialDaysLeft = response.trialDaysLeft.coerceAtLeast(0),
                expiryDate = response.expiryDate
            )
            prefs.setString(Prefs.LICENSE_STATUS, state.status.name)
            prefs.setLong(Prefs.LAST_LICENSE_CHECK, System.currentTimeMillis())
            state
        } catch (_: Exception) {
            localState(trialDays)
        }
    }

    /** Offline state from local trial math + last known server status. */
    private suspend fun localState(trialDays: Int): LicenseState {
        // Never downgrade a device the server already unlocked / is verifying.
        when (prefs.getString(Prefs.LICENSE_STATUS, "")) {
            LicenseStatus.UNLOCKED.name ->
                return LicenseState(LicenseStatus.UNLOCKED, trialDaysLeft = 0)
            LicenseStatus.PAYMENT_PENDING.name ->
                return LicenseState(LicenseStatus.PAYMENT_PENDING, trialDaysLeft = 0)
        }
        val installTime = prefs.installTime()
        val daysSinceInstall =
            TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - installTime)
        val daysLeft = (trialDays - daysSinceInstall).toInt().coerceAtLeast(0)
        return if (daysLeft > 0) {
            LicenseState(LicenseStatus.TRIAL_ACTIVE, trialDaysLeft = daysLeft)
        } else {
            LicenseState(LicenseStatus.TRIAL_EXPIRED, trialDaysLeft = 0)
        }
    }

    /** True only when the licence is permanently unlocked (payment verified). */
    suspend fun isUnlocked(): Boolean = refresh().status == LicenseStatus.UNLOCKED

    /** Whole days of free trial left (0 when expired, unlocked, or payment pending). */
    suspend fun daysLeft(): Int = refresh().trialDaysLeft
}
