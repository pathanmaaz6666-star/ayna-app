package com.mazo.ayna.brain

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mazo.ayna.util.Constants
import com.mazo.ayna.util.SecurePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Ayna's voice — server-side Gemini TTS, fixed female voice Aoede.
 *
 * Maaz locked voice #1, Aria (avocado_v2:MAI_01), for Ayna. That voice lives
 * behind Muse's internal Shortwave TTS service, which has NO public endpoint —
 * no phone can call it. So on the phone Ayna speaks through Gemini TTS
 * (server-side synthesis, model gemini-2.5-flash-preview-tts, fixed female
 * voice Aoede, Urdu / Roman Urdu / English), using the user's own free Gemini
 * key from [SecurePrefs]. If a public MAI_01 provider ever exists, only
 * [Constants.AYNA_TTS_VOICE] changes.
 *
 * Hard rule: Ayna NEVER falls back to Android's built-in TextToSpeech (the
 * phone's default voice). When the key is missing, the phone is offline, or
 * the request fails, [speak] returns false and Ayna stays silent for that
 * reply instead of speaking in the wrong voice.
 */
class AynaVoice(private val context: Context) {

    companion object {
        /** Gemini TTS returns 24 kHz, 16-bit, mono PCM. */
        private const val SAMPLE_RATE_HZ = 24000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var track: AudioTrack? = null
    @Volatile
    private var stopped = false

    /** True when a Gemini key is saved — the only thing voice needs. */
    val isAvailable: Boolean
        get() = SecurePrefs.getGeminiApiKey(context) != null

    /**
     * Speak [text] in the fixed Ayna voice. Suspends until playback finishes.
     * Returns true when the whole reply was played, false when anything failed
     * (no key, offline, server error) — the caller then stays silent.
     */
    suspend fun speak(text: String): Boolean {
        val clean = text.trim()
        if (clean.isBlank()) return false
        val apiKey = SecurePrefs.getGeminiApiKey(context) ?: return false
        val pcm = withContext(Dispatchers.IO) { synthesize(clean, apiKey) } ?: return false
        return play(pcm)
    }

    /**
     * Fire-and-forget [speak]: speaks on a background coroutine and runs
     * [onDone] on the main thread afterwards (even on failure).
     */
    fun speakAsync(text: String, onDone: () -> Unit = {}) {
        scope.launch {
            runCatching { speak(text) }
            onDone()
        }
    }

    /** Stop any playback in progress. */
    fun stop() {
        stopped = true
        try {
            track?.stop()
        } catch (_: Exception) {
        }
    }

    /** Release the audio track. */
    fun release() {
        stop()
        try {
            track?.release()
        } catch (_: Exception) {
        }
        track = null
    }

    /** Release everything. Call from onDestroy. */
    fun shutdown() {
        release()
        scope.cancel()
    }

    /** One Gemini TTS call. Returns raw 24 kHz PCM bytes, or null on failure. */
    private fun synthesize(text: String, apiKey: String): ByteArray? {
        val body = gson.toJson(
            mapOf(
                "contents" to listOf(
                    mapOf("parts" to listOf(mapOf("text" to text)))
                ),
                "generationConfig" to mapOf(
                    "responseModalities" to listOf("AUDIO"),
                    "speechConfig" to mapOf(
                        "voiceConfig" to mapOf(
                            "prebuiltVoiceConfig" to mapOf(
                                "voiceName" to Constants.AYNA_TTS_VOICE
                            )
                        )
                    )
                )
            )
        )
        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/${Constants.AYNA_TTS_MODEL}:generateContent?key=$apiKey"
        return try {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val parsed = gson.fromJson(resp.body?.string(), TtsResponse::class.java)
                val base64 = parsed?.candidates?.firstOrNull()
                    ?.content?.parts?.firstOrNull { it.inlineData != null }
                    ?.inlineData?.data ?: return null
                Base64.decode(base64, Base64.DEFAULT)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Play raw PCM through AudioTrack. True only when fully played. */
    private suspend fun play(pcm: ByteArray): Boolean = withContext(Dispatchers.IO) {
        stopped = false
        val frames = pcm.size / 2
        if (frames <= 0) return@withContext false
        val newTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE_HZ)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size)
                .build()
        } catch (_: Exception) {
            return@withContext false
        }
        track = newTrack
        var completed = false
        try {
            if (newTrack.write(pcm, 0, pcm.size) < 0) return@withContext false
            newTrack.play()
            while (!stopped && newTrack.playbackHeadPosition < frames) {
                Thread.sleep(50)
            }
            completed = !stopped && newTrack.playbackHeadPosition >= frames
        } catch (_: Exception) {
            completed = false
        } finally {
            try {
                newTrack.stop()
            } catch (_: Exception) {
            }
            try {
                newTrack.release()
            } catch (_: Exception) {
            }
            if (track === newTrack) track = null
        }
        completed
    }

    private data class TtsResponse(
        @SerializedName("candidates") val candidates: List<Candidate>?
    ) {
        data class Candidate(@SerializedName("content") val content: Content?)
        data class Content(@SerializedName("parts") val parts: List<Part>?)
        data class Part(@SerializedName("inlineData") val inlineData: InlineData?)
        data class InlineData(
            @SerializedName("mimeType") val mimeType: String?,
            @SerializedName("data") val data: String?
        )
    }
}
