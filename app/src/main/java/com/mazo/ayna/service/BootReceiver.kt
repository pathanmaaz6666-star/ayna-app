package com.mazo.ayna.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mazo.ayna.core.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restarts the wake-word listener after the phone reboots (if enabled). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        scope.launch {
            val prefs = Prefs(context)
            if (prefs.getBoolean(Prefs.WAKE_WORD_ENABLED, true)) {
                WakeWordService.start(context)
            }
        }
    }
}
