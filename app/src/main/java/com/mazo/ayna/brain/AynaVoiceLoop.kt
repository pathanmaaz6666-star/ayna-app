package com.mazo.ayna.brain

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.mazo.ayna.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Siri-like continuous voice loop for Ayna chat: listen → think → speak → listen.
 *
 * The loop drives [GeminiRestBrain] for replies and [AynaVoice] (Gemini TTS, fixed
 * female voice) for speech. It NEVER uses Android's built-in TextToSpeech.
 * [onUserHeard] / [onAynaReply] report each turn so the chat screen can show
 * the bubbles; the loop itself does the talking. [onStateChange] reports the
 * loop state so the UI can show the mic as listening / thinking / speaking.
 *
 * SpeechRecognizer must run on the main thread — all recognizer calls go
 * through [mainHandler]. The [scope] (the activity's lifecycleScope) runs the
 * brain + voice work; [AynaVoice.speak] already moves its network and audio
 * work to Dispatchers.IO, so the main thread is never blocked.
 *
 * Call [stop] when leaving the chat screen and from onDestroy.
 */
class AynaVoiceLoop(
    private val context: Context,
    private val brain: GeminiRestBrain,
    private val voice: AynaVoice,
    private val scope: CoroutineScope,
    private val languageProvider: () -> String,
    private val onUserHeard: (String) -> Unit,
    private val onAynaReply: (String) -> Unit,
    private val onStateChange: (State) -> Unit,
    private val onError: (String) -> Unit
) {

    enum class State { IDLE, LISTENING, THINKING, SPEAKING }

    companion object {
        private const val MAX_CONSECUTIVE_ERRORS = 3
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var consecutiveErrors = 0

    @Volatile
    var isRunning: Boolean = false
        private set

    private var state: State = State.IDLE
        set(value) {
            field = value
            onStateChange(value)
        }

    /** Start the loop. No-op when already running or recognition is unavailable. */
    fun start() {
        if (isRunning) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("speech_not_available")
            return
        }
        isRunning = true
        consecutiveErrors = 0
        mainHandler.post { listen() }
    }

    /** Stop the loop, the recognizer and any speech in progress. */
    fun stop() {
        isRunning = false
        voice.stop()
        mainHandler.post {
            try {
                recognizer?.stopListening()
            } catch (_: Exception) {
            }
            try {
                recognizer?.destroy()
            } catch (_: Exception) {
            }
            recognizer = null
            state = State.IDLE
        }
    }

    private fun recognitionLocale(): Locale = when (languageProvider()) {
        "English" -> Locale("en", "US")
        else -> Locale("ur", "PK") // Urdu + Roman Urdu both recognised as ur-PK
    }

    private fun listen() {
        if (!isRunning) return
        state = State.LISTENING
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(AynaRecognitionListener())
            }
        }
        try {
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            handleListenError()
        }
    }

    /** One heard utterance: think, answer, speak, then listen again. */
    private fun handleHeard(text: String) {
        if (!isRunning) return
        consecutiveErrors = 0
        onUserHeard(text)
        state = State.THINKING
        scope.launch {
            val reply = brain.answer(text, languageProvider())
                ?: context.getString(R.string.ayna_offline)
            if (!isRunning) return@launch
            onAynaReply(reply)
            state = State.SPEAKING
            voice.speak(reply)
            if (isRunning) mainHandler.post { listen() }
        }
    }

    private fun handleListenError() {
        if (!isRunning) return
        consecutiveErrors++
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
            onError("did_not_hear")
            stop()
        } else {
            mainHandler.post { listen() }
        }
    }

    private inner class AynaRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            if (!isRunning) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> handleListenError()
                else -> {
                    onError("speech_error")
                    stop()
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) handleHeard(text) else handleListenError()
        }
    }
}
