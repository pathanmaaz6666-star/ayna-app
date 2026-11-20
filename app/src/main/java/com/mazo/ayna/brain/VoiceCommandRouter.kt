package com.mazo.ayna.brain

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import com.mazo.ayna.core.Prefs
import com.mazo.ayna.offline.OfflineBank
import com.mazo.ayna.service.AynaAccessibilityService
import com.mazo.ayna.service.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** What Ayna decided to do with a voice command. */
sealed class CommandResult {
    data class Spoken(val text: String) : CommandResult()
    data class Action(val text: String, val action: () -> Unit) : CommandResult()
    data class NeedsInput(val text: String) : CommandResult()
}

/**
 * Routes a transcribed voice command to the right action:
 * offline bank first (fast + free), then Gemini when online.
 * WhatsApp / SMS / calls / reminders are executed on-device.
 *
 * Honest limits (also documented in README):
 * - Phone call accept/reject works only if Ayna is the default dialer
 *   (RoleManager) — otherwise Ayna can only dial out and show the call screen.
 * - WhatsApp calls are answered/rejected by tapping the incoming-call screen
 *   via the AccessibilityService.
 * - WhatsApp messages are sent by opening wa.me/<number> and tapping Send
 *   via the AccessibilityService. WhatsApp's UI can change with updates.
 */
class VoiceCommandRouter(
    private val context: Context,
    private val offlineBank: OfflineBank,
    private val brain: GeminiBrain,
    private val prefs: Prefs,
    private val scope: CoroutineScope
) {

    fun handle(transcript: String, onResult: (CommandResult) -> Unit) {
        val text = transcript.trim()
        val lower = text.lowercase()

        // --- WhatsApp message: "WhatsApp par Ali ko message bhejo ke ..." ---
        val waMatch = Regex("(?:whatsapp|watsapp)(?: par)?\\s+(.+?)\\s+ko\\s+(?:message|msg|paigham)\\s+(?:bhejo|bhej do|send karo)\\s*(?:ke|:)?\\s*(.*)", RegexOption.IGNORE_CASE).find(text)
        if (waMatch != null) {
            val name = waMatch.groupValues[1].trim()
            val message = waMatch.groupValues[2].trim()
            if (message.isEmpty()) {
                onResult(CommandResult.NeedsInput("Kis ko kya message bhejna hai? Pehle naam, phir message bata dein."))
            } else {
                onResult(CommandResult.Action("Theek hai, $name ko WhatsApp message bhej rahi hoon.") {
                    AynaAccessibilityService.sendWhatsAppMessageByName(context, name, message)
                })
            }
            return
        }

        // --- SMS: "Ali ko SMS bhejo ..." ---
        val smsMatch = Regex("(?:sms|message)\\s+(?:bhejo|send karo)\\s+(.+?)\\s+ko\\s+(.*)", RegexOption.IGNORE_CASE).find(text)
        if (smsMatch != null) {
            val target = smsMatch.groupValues[1].trim()
            val message = smsMatch.groupValues[2].trim()
            onResult(CommandResult.Action("SMS bhej rahi hoon.") {
                try {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault().sendTextMessage(target, null, message, null, null)
                } catch (_: Exception) {
                    // Number may be a contact name — open SMS app instead.
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$target")).apply {
                        putExtra("sms_body", message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                }
            })
            return
        }

        // --- Call: "Ali ko call karo / call lagao" ---
        val callMatch = Regex("(?:call|phone)\\s+(?:karo|lagao|kar do)\\s+(.+)", RegexOption.IGNORE_CASE).find(text)
        if (callMatch != null) {
            val target = callMatch.groupValues[1].trim()
            onResult(CommandResult.Action("$target ko call laga rahi hoon.") {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            })
            return
        }

        // --- Reminder: "10 minute baad yaad dilao ke ..." / "subah 7 baje yaad dilao" ---
        val remMatch = Regex("(\\d+)\\s*(minute|min|mint|ghanta|ghante|hour)\\s*(?:baad)?\\s*yaad\\s*dilao\\s*(?:ke|:)?\\s*(.*)", RegexOption.IGNORE_CASE).find(text)
        if (remMatch != null) {
            val amount = remMatch.groupValues[1].toIntOrNull() ?: 0
            val unit = remMatch.groupValues[2].lowercase()
            val note = remMatch.groupValues[3].trim().ifEmpty { "Reminder" }
            val delayMs = if (unit.startsWith("ghant") || unit.startsWith("hour")) amount * 3_600_000L else amount * 60_000L
            ReminderScheduler.schedule(context, delayMs, note)
            onResult(CommandResult.Spoken("Theek hai, $amount $unit baad yaad dila doon gi: $note"))
            return
        }

        // --- Greeting / identity ---
        if (lower.contains("assalam") || lower == "hello" || lower == "hi" || lower == "salam") {
            onResult(CommandResult.Spoken("Walaikum Assalam! Main Ayna hoon, aap ki AI assistant. Kya kar sakti hoon aap ke liye?"))
            return
        }
        if (lower.contains("tum kaun") || lower.contains("tumhara naam") || lower.contains("who are you") || lower.contains("kis ne banaya")) {
            onResult(CommandResult.Spoken("Main Ayna hoon — mujhe Maaz Arif ne banaya hai. Main aap ki voice assistant hoon: WhatsApp, calls, reminders aur maloomat — sab kuch bol kar."))
            return
        }

        // --- Offline bank first (fast, free, works without data) ---
        scope.launch(Dispatchers.IO) {
            // Make sure the in-memory cache is warm (seed runs in background
            // on first launch, so the cache may still be empty).
            runCatching { offlineBank.refreshCache() }
            val local = offlineBank.answer(text)
            if (local != null) {
                onResult(CommandResult.Spoken(local.answer))
                return@launch
            }
            // --- Online brain ---
            val language = prefs.getString(Prefs.LANGUAGE, "Roman Urdu")
            val reply = brain.answer(text, language)
            if (reply != null) {
                onResult(CommandResult.Spoken(reply))
            } else {
                onResult(
                    CommandResult.Spoken(
                        "Internet nahi hai aur is sawal ka jawab mere offline bank mein nahi hai. " +
                            "Data on karein ya apni Gemini API key Settings mein daalein, phir main jawab de sakoon gi."
                    )
                )
            }
        }
    }
}
