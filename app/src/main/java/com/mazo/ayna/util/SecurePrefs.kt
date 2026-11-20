package com.mazo.ayna.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted storage for the user's own Gemini API key.
 * The key is typed in by the user in Settings and never leaves the phone,
 * except to call Google's Gemini API. No key is ever hardcoded.
 */
object SecurePrefs {

    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        Constants.SECURE_PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getGeminiApiKey(context: Context): String? =
        prefs(context).getString(KEY_GEMINI_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setGeminiApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
    }

    fun clearGeminiApiKey(context: Context) {
        prefs(context).edit().remove(KEY_GEMINI_API_KEY).apply()
    }
}
