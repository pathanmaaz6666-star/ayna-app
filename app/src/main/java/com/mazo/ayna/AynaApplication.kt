package com.mazo.ayna

import android.app.Application
import com.mazo.ayna.offline.OfflineBank

/**
 * Ayna application. Seeds the offline knowledge bank on first launch.
 * No secrets are stored or read here.
 */
class AynaApplication : Application() {

    lateinit var offlineBank: OfflineBank
        private set

    override fun onCreate() {
        super.onCreate()
        offlineBank = OfflineBank(applicationContext)
        // Seed offline bank in background; first launch takes a moment.
        Thread {
            try {
                kotlinx.coroutines.runBlocking { offlineBank.seedIfEmpty() }
            } catch (_: Exception) {
                // Seeding failure is non-fatal; online brain still works.
            }
        }.start()
    }
}
