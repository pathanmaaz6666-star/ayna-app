package com.mazo.ayna.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.mazo.ayna.MainActivity
import com.mazo.ayna.R
import com.mazo.ayna.core.Prefs
import com.mazo.ayna.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Always-listening wake-word service ("Ayna" / "Hello Ayna").
 *
 * How it works: a foreground service runs Android's SpeechRecognizer in a
 * loop and watches partial transcripts for the wake word. When heard, it
 * opens the Ayna overlay (and the main screen).
 *
 * Honest limits: true always-on hotword detection needs a dedicated hotword
 * model; this implementation uses the system speech recognizer, which needs
 * internet for Google's online recognition (or an offline pack) and uses
 * more battery than a hardware hotword. The service restarts listening
 * automatically after errors. Users can turn the wake word off in Settings.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!listening) startListening()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        scope.launch {
            val prefs = Prefs(applicationContext)
            if (!prefs.getBoolean(Prefs.WAKE_WORD_ENABLED, true)) return@launch
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(applicationContext).apply {
                    setRecognitionListener(WakeListener())
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 30_000L)
                    }
                    listening = true
                    startListening(intent)
                }
            } catch (_: Exception) {
                listening = false
            }
        }
    }

    private fun stopListening() {
        listening = false
        try {
            recognizer?.stopListening()
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private inner class WakeListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = restart()
        override fun onError(error: Int) = restart()
        override fun onResults(results: Bundle?) {
            checkTranscript(results)
            restart()
        }

        override fun onPartialResults(partialResults: Bundle?) = checkTranscript(partialResults)

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun checkTranscript(results: Bundle?) {
            val heard = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.joinToString(" ")?.lowercase() ?: return
            if (heard.contains("ayna") || heard.contains("hello ayna") || heard.contains("aina")) {
                onWakeWordHeard()
            }
        }

        private fun restart() {
            stopListening()
            startListening()
        }
    }

    private fun onWakeWordHeard() {
        stopListening()
        OverlayService.start(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_WAKE_WORD, true)
        }
        startActivity(intent)
        startListening()
    }

    private fun buildNotification(): Notification {
        val channelId = "ayna_wake_word"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Ayna", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, channelId)
            .setContentTitle("Ayna")
            .setContentText(getString(R.string.notification_wake_word))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WakeWordService::class.java))
        }
    }
}
