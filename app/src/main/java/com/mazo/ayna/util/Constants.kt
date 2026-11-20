package com.mazo.ayna.util

/** App-wide constants. No secrets here — ever. */
object Constants {
    const val APP_NAME = "Ayna"
    const val BRAND = "Axon"
    const val COMPANY = "Product of Mazo Company"
    const val FOUNDER = "Maaz Arif"

    /** Payment details from Maaz (2026-11-19). Shown on the paywall verbatim. */
    const val EASYPAISA_NUMBER = "0308 9303647"
    const val EASYPAISA_ACCOUNT_NAME = "Maaz Arif"
    const val PRICE_PKR = 300
    const val TRIAL_DAYS_DEFAULT = 10

    const val WAKE_WORD = "Ayna"
    const val WAKE_WORD_ALT = "Hello Ayna"

    /**
     * Ayna's voice, LOCKED by Maaz (2026-11-19): voice #1, Aria
     * (avocado_v2:MAI_01), warm/sweet female, speaking Urdu.
     * That voice id belongs to Muse's internal Shortwave TTS service, which
     * has NO public endpoint — no phone and no deployable server can call it.
     * So on-device Ayna speaks through Gemini TTS (server-side synthesis,
     * never the phone's default voice) with the fixed female voice below.
     * If a public MAI_01 provider ever exists, only AYNA_TTS_VOICE changes.
     */
    const val AYNA_VOICE_LOCKED_ID = "avocado_v2:MAI_01"
    const val AYNA_TTS_MODEL = "gemini-2.5-flash-preview-tts"
    const val AYNA_TTS_VOICE = "Aoede"

    /** Supported languages. Pashto is NOT guaranteed. */
    val LANGUAGES = listOf("Roman Urdu", "Urdu", "English")

    const val PREFS_NAME = "ayna_prefs"
    const val SECURE_PREFS_NAME = "ayna_secure"

    const val WHATSAPP_PACKAGE = "com.whatsapp"
    const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

    /** Gemini model used by the REST brain (free tier). */
    const val GEMINI_MODEL = "gemini-2.5-flash"
}
