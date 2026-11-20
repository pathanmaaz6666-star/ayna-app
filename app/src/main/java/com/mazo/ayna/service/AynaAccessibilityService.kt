package com.mazo.ayna.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URLEncoder

/**
 * Ayna's hands: taps inside other apps (no root).
 *
 * What it can do:
 * - Send a WhatsApp message: opens wa.me/<number>?text=..., waits for the chat,
 *   puts the text in the message box and taps the Send button.
 * - Accept / reject an incoming WhatsApp call by tapping the on-screen buttons.
 *
 * Honest limits: this drives WhatsApp's real UI, so if WhatsApp changes its
 * screen layout or button labels, the flow needs an update. It never reads
 * message contents and never sends anything the user did not ask for.
 * The user must enable "Ayna — WhatsApp helper" in Android Settings >
 * Accessibility (the app deep-links them there from onboarding).
 */
class AynaAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: AynaAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.contains(context.packageName)
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /** Send a WhatsApp message to a phone number (digits, with country code). */
        fun sendWhatsAppMessage(number: String, message: String) {
            val svc = instance ?: return
            val digits = number.filter { it.isDigit() }
            val url = "https://wa.me/$digits?text=" + URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            svc.startActivity(intent)
            // Give WhatsApp time to open, then type + send.
            Handler(Looper.getMainLooper()).postDelayed({
                instance?.run {
                    // Type into the message box (wa.me pre-fills it, but type anyway).
                    findByViewId("com.whatsapp:id/entry")?.let { box ->
                        val args = android.os.Bundle().apply {
                            putCharSequence(
                                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                message
                            )
                        }
                        box.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                    // Tap Send (content-description is "Send" in WhatsApp).
                    clickByDescription("Send") || clickByText("Send")
                }
            }, 2500)
        }

        /** Send to a contact by display name (opens WhatsApp search is not reliable,
         *  so this resolves the name through the phone's contacts first). */
        fun sendWhatsAppMessageByName(context: Context, name: String, message: String) {
            val number = ContactLookup.findPhoneNumber(context, name)
            if (number != null) {
                sendWhatsAppMessage(number, message)
            } else {
                // Fall back: open WhatsApp so the user can pick the chat.
                val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                intent?.let { context.startActivity(it) }
            }
        }

        fun acceptWhatsAppCall() {
            instance?.run {
                clickByText("Accept") || clickByDescription("Accept") ||
                    clickByText("Answer") || clickByDescription("Answer")
            }
        }

        fun rejectWhatsAppCall() {
            instance?.run {
                clickByText("Decline") || clickByDescription("Decline") ||
                    clickByText("Reject") || clickByDescription("Reject")
            }
        }

        /**
         * Place an outgoing WhatsApp voice call to a contact by name.
         * Opens the chat via wa.me, then taps the voice-call button.
         * Returns false when the contact is not found or the service is not
         * enabled — the caller must tell the user (never pretend it worked).
         */
        fun placeWhatsAppCallByName(context: Context, name: String): Boolean {
            val number = ContactLookup.findPhoneNumber(context, name) ?: return false
            val svc = instance ?: return false
            val digits = number.filter { it.isDigit() }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            svc.startActivity(intent)
            // Give WhatsApp time to open the chat, then tap the call button.
            Handler(Looper.getMainLooper()).postDelayed({
                instance?.run {
                    clickByDescription("Voice call") || clickByDescription("Call")
                }
            }, 2500)
            return true
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance == this) instance = null
        super.onDestroy()
    }

    private fun clickByDescription(desc: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(desc)
        for (node in nodes) {
            if (node.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true) {
                if (clickNode(node)) return true
            }
            if (node.isClickable && node.text?.toString()?.equals(desc, ignoreCase = true) == true) {
                if (clickNode(node)) return true
            }
        }
        // Fallback: walk the tree for a node whose description matches.
        return findAndClick(root) {
            it.contentDescription?.toString()?.equals(desc, ignoreCase = true) == true
        }
    }

    private fun clickByText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            if (node.isClickable && clickNode(node)) return true
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && clickNode(parent)) return true
                parent = parent.parent
            }
        }
        return false
    }

    private fun findByViewId(viewId: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByViewId(viewId).firstOrNull()
    }

    private fun findAndClick(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): Boolean {
        if (match(node)) return clickNode(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                if (findAndClick(child, match)) return true
            }
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            parent = parent.parent
        }
        return false
    }
}
