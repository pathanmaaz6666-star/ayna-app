package com.mazo.ayna

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.mazo.ayna.brain.AynaMemory
import com.mazo.ayna.brain.AynaVoice
import com.mazo.ayna.brain.AynaVoiceLoop
import com.mazo.ayna.brain.CommandResult
import com.mazo.ayna.brain.GeminiRestBrain
import com.mazo.ayna.brain.VoiceCommandRouter
import com.mazo.ayna.core.Prefs
import com.mazo.ayna.licensing.AynaApi
import com.mazo.ayna.licensing.AynaApiClient
import com.mazo.ayna.licensing.LicenseManager
import com.mazo.ayna.licensing.LicenseState
import com.mazo.ayna.licensing.LicenseStatus
import com.mazo.ayna.licensing.PaymentRepository
import com.mazo.ayna.licensing.RemoteConfigRepository
import com.mazo.ayna.offline.KnowledgeRepository
import com.mazo.ayna.offline.OfflineBank
import com.mazo.ayna.service.AynaAccessibilityService
import com.mazo.ayna.service.ContactLookup
import com.mazo.ayna.service.ReminderScheduler
import com.mazo.ayna.service.WakeWordService
import com.mazo.ayna.ui.AynaColors
import com.mazo.ayna.ui.AynaTheme
import com.mazo.ayna.ui.ChatMessage
import com.mazo.ayna.ui.ChatScreen
import com.mazo.ayna.ui.MainScreen
import com.mazo.ayna.ui.MainUiState
import com.mazo.ayna.ui.OnboardingFlow
import com.mazo.ayna.ui.PaywallScreen
import com.mazo.ayna.ui.PaywallState
import com.mazo.ayna.ui.SettingsScreen
import com.mazo.ayna.util.Constants
import com.mazo.ayna.util.SecurePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * MainActivity — wires everything together:
 * onboarding (once) -> licence check -> main screen / paywall.
 *
 * Screens: Loading | Onboarding | Main | Chat | Settings | Paywall.
 * Ayna is ONE app that does everything: work, study, romance and flirty
 * 18+ talk (tasteful, never explicit) — there are no modes.
 * Mic: tap-to-talk via SpeechRecognizer; on the Chat screen the mic starts
 * the Siri-like continuous voice loop (listen → reply → speak, Gemini TTS
 * voice Aoede). Wake word ("Ayna") arrives as an intent extra
 * (EXTRA_WAKE_WORD) from WakeWordService and auto-starts listening.
 * Chips (WhatsApp / Call / Reminder) run a short two-turn voice flow:
 * pehle naam, phir message — then the AccessibilityService taps inside WhatsApp.
 * Single-utterance commands ("Ali ko WhatsApp message bhejo ke ...") are
 * handled directly by VoiceCommandRouter.
 *
 * NO admin entry anywhere in this app: no admin button, screen, link or
 * hidden menu. Admin lives only in the web panel (server/admin/).
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_WAKE_WORD = "wake_word"
        private const val PERMISSIONS_PROMPTED_KEY = "permissions_prompted"
        private const val DEFAULT_LANGUAGE = "Roman Urdu"
    }

    private lateinit var prefs: Prefs
    private lateinit var offlineBank: OfflineBank
    private lateinit var knowledgeRepo: KnowledgeRepository
    private lateinit var voice: AynaVoice
    private lateinit var brain: GeminiRestBrain
    private lateinit var router: VoiceCommandRouter
    /** Siri-like continuous voice loop for the Chat screen (listen → reply → speak). */
    private var aynaVoiceLoop: AynaVoiceLoop? = null
    private lateinit var licenseManager: LicenseManager
    private lateinit var paymentRepo: PaymentRepository
    private lateinit var remoteConfig: RemoteConfigRepository
    private lateinit var api: AynaApi

    private var recognizer: SpeechRecognizer? = null
    private var screen by mutableStateOf<Screen>(Screen.Loading)
    private var uiState by mutableStateOf(MainUiState())
    private var paywallState by mutableStateOf(PaywallState.SHOW)
    private var apiKey by mutableStateOf("")
    private var language by mutableStateOf(DEFAULT_LANGUAGE)
    private var wakeWordEnabled by mutableStateOf(true)
    private var trialDaysLeft by mutableStateOf<Int?>(null)
    private var licenseState: LicenseState? = null

    private var userName by mutableStateOf("")
    /** Ayna chat (opened from the main screen's Chat button). */
    private var aynaChatMessages by mutableStateOf<List<ChatMessage>>(emptyList())
    private var aynaTyping by mutableStateOf(false)
    /** Ayna's replies are spoken through Gemini TTS (server-side, fixed
     *  female voice Aoede) — never the phone's default voice.
     *  [voiceEnabled] = speak her text replies; on the Chat screen the mic
     *  starts the continuous [AynaVoiceLoop] (Siri-like). */
    private var voiceEnabled by mutableStateOf(true)

    /** Multi-turn voice flow state (chip taps). Null = normal single-command mode. */
    private var pendingCommand: PendingCommand? = null

    private enum class VoiceFlow { WHATSAPP, CALL, REMINDER }

    private sealed interface PendingCommand {
        data class AwaitingContactName(val flow: VoiceFlow) : PendingCommand
        data class AwaitingMessage(val contactName: String) : PendingCommand
        object AwaitingReminderText : PendingCommand
    }

    private sealed interface Screen {
        object Loading : Screen
        object Onboarding : Screen
        object Main : Screen
        object Chat : Screen
        object Settings : Screen
        object Paywall : Screen
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
            lifecycleScope.launch { maybePromptOverlayAndAccessibility() }
        }

    // ------------------------------------------------------------------ setup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = Prefs(applicationContext)
        offlineBank = OfflineBank(applicationContext)
        knowledgeRepo = KnowledgeRepository(offlineBank)
        voice = AynaVoice(applicationContext)
        api = AynaApiClient.create()
        remoteConfig = RemoteConfigRepository(applicationContext, api)
        licenseManager = LicenseManager(applicationContext, api, prefs, remoteConfig)
        paymentRepo = PaymentRepository(applicationContext, api)
        brain = GeminiRestBrain(applicationContext)
        router = VoiceCommandRouter(
            context = applicationContext,
            offlineBank = offlineBank,
            brain = brain,
            prefs = prefs,
            scope = lifecycleScope
        )

        setContent {
            AynaTheme {
                when (screen) {
                    Screen.Loading -> LoadingScreen()
                    Screen.Onboarding -> OnboardingFlow(
                        onFinished = ::onOnboardingFinished,
                        onNameEntered = { name ->
                            userName = name
                            lifecycleScope.launch {
                                prefs.setString(Prefs.USER_NAME, name)
                            }
                        }
                    )
                    Screen.Main -> MainScreen(
                        state = uiState,
                        onMicClick = ::onMicTap,
                        onWhatsAppClick = { startVoiceFlow(VoiceFlow.WHATSAPP) },
                        onCallClick = { startVoiceFlow(VoiceFlow.CALL) },
                        onReminderClick = { startVoiceFlow(VoiceFlow.REMINDER) },
                        onSettingsClick = { screen = Screen.Settings },
                        onChatClick = { screen = Screen.Chat }
                    )
                    Screen.Chat -> ChatScreen(
                        messages = aynaChatMessages,
                        onSend = ::sendAynaChatMessage,
                        onMicClick = ::onMicTap,
                        listening = uiState.listening,
                        voiceEnabled = voiceEnabled,
                        onVoiceToggle = {
                            if (!voice.isAvailable) {
                                Toast.makeText(
                                    this,
                                    getString(R.string.ayna_voice_pending),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                voiceEnabled = !voiceEnabled
                                if (!voiceEnabled) stopAynaVoiceLoop()
                            }
                        },
                        title = getString(R.string.app_name),
                        accentColor = AynaColors.Primary,
                        typing = aynaTyping
                    )
                    Screen.Settings -> SettingsScreen(
                        apiKey = apiKey,
                        onApiKeyChange = { newKey ->
                            apiKey = newKey
                            SecurePrefs.setGeminiApiKey(applicationContext, newKey)
                        },
                        language = language,
                        onLanguageChange = { newLang ->
                            language = newLang
                            lifecycleScope.launch { prefs.setString(Prefs.LANGUAGE, newLang) }
                        },
                        wakeWordEnabled = wakeWordEnabled,
                        onWakeWordChange = { enabled ->
                            wakeWordEnabled = enabled
                            lifecycleScope.launch {
                                prefs.setBoolean(Prefs.WAKE_WORD_ENABLED, enabled)
                                if (enabled) WakeWordService.start(applicationContext)
                                else WakeWordService.stop(applicationContext)
                            }
                        },
                        trialDaysLeft = trialDaysLeft,
                        onBackClick = { screen = Screen.Main }
                    )
                    Screen.Paywall -> PaywallScreen(
                        state = paywallState,
                        onCopyNumber = ::copyEasypaisaNumber,
                        onSubmit = ::submitPayment
                    )
                }
            }
        }

        lifecycleScope.launch { initialise() }
        onBackPressedDispatcher.addCallback(this) {
            if (screen == Screen.Chat || screen == Screen.Settings) {
                stopAynaVoiceLoop()
                screen = Screen.Main
            } else finish()
        }
        if (intent?.getBooleanExtra(EXTRA_WAKE_WORD, false) == true) {
            lifecycleScope.launch {
                // Wait until the main screen is up, then start listening.
                while (screen != Screen.Main) delay(300)
                startListening()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.getBooleanExtra(EXTRA_WAKE_WORD, false) == true && screen == Screen.Main) {
            startListening()
        }
    }

    override fun onDestroy() {
        aynaVoiceLoop?.stop()
        voice.shutdown()
        recognizer?.destroy()
        super.onDestroy()
    }

    // -------------------------------------------------------------- lifecycle

    /** First-launch flow: onboarding? licence? then main or paywall. */
    private suspend fun initialise() {
        apiKey = SecurePrefs.getGeminiApiKey(applicationContext) ?: ""
        language = prefs.getString(Prefs.LANGUAGE, DEFAULT_LANGUAGE)
        wakeWordEnabled = prefs.getBoolean(Prefs.WAKE_WORD_ENABLED, true)
        userName = prefs.getString(Prefs.USER_NAME, "")
        loadAynaHistory()

        licenseState = runCatching { licenseManager.refresh() }.getOrNull()
        trialDaysLeft = licenseState?.trialDaysLeft
        uiState = uiState.copy(trialDaysLeft = trialDaysLeft)

        if (!prefs.isOnboardingDone()) {
            screen = Screen.Onboarding
            return
        }
        routeAfterLicence()
        // Background: server config + knowledge-bundle update (never blocks UI).
        lifecycleScope.launch { refreshRemoteKnowledge() }
        startWakeWordIfEnabled()
    }

    private suspend fun routeAfterLicence() {
        when (licenseState?.status) {
            LicenseStatus.TRIAL_EXPIRED -> {
                paywallState = if (paymentRepo.paymentStatus().isPending) {
                    PaywallState.PENDING
                } else {
                    PaywallState.SHOW
                }
                screen = Screen.Paywall
                if (paywallState == PaywallState.PENDING) pollPaymentStatus()
            }
            LicenseStatus.PAYMENT_PENDING -> {
                paywallState = PaywallState.PENDING
                screen = Screen.Paywall
                pollPaymentStatus()
            }
            else -> {
                screen = Screen.Main
                maybePromptOverlayAndAccessibility()
            }
        }
    }

    private fun onOnboardingFinished() {
        lifecycleScope.launch {
            prefs.setOnboardingDone()
            seedAynaGreetingIfEmpty()
            requestRuntimePermissions()
            routeAfterLicence()
            startWakeWordIfEnabled()
        }
    }

    /** Load Ayna's saved chat history into the on-screen message list. */
    private suspend fun loadAynaHistory() {
        val history = AynaMemory(applicationContext).loadHistory()
        aynaChatMessages = history.map { turn ->
            ChatMessage(role = if (turn.role == "user") "user" else "ai", text = turn.text)
        }
    }

    /** First Ayna message after onboarding (only when there is no history). */
    private suspend fun seedAynaGreetingIfEmpty() {
        if (AynaMemory(applicationContext).loadHistory().isNotEmpty()) return
        val greeting = getString(R.string.ayna_first_greeting, userName.ifBlank { "Handsome" })
        aynaChatMessages = listOf(ChatMessage(role = "ai", text = greeting))
        AynaMemory(applicationContext).appendHistory("ayna", greeting)
    }

    private fun startWakeWordIfEnabled() {
        if (wakeWordEnabled) WakeWordService.start(applicationContext)
    }

    /** Pull the server knowledge bundle when Maaz published a newer version. */
    private suspend fun refreshRemoteKnowledge() {
        runCatching {
            val config = remoteConfig.fetch()
            val localVersion = offlineBank.bundleVersion()
            if (config.knowledgeBundleVersion > localVersion) {
                val bundle = api.getKnowledgeBundle(localVersion)
                if (bundle.version > localVersion) {
                    knowledgeRepo.applyServerBundle(bundle)
                }
            }
        }
    }

    // ------------------------------------------------------------ permissions

    private fun requestRuntimePermissions() {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            needed += Manifest.permission.POST_NOTIFICATIONS
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        else lifecycleScope.launch { maybePromptOverlayAndAccessibility() }
    }

    /** Overlay bubble + AccessibilityService are optional but unlock the full
     *  hands-free experience. Asked once (tracked in prefs), never forced. */
    private suspend fun maybePromptOverlayAndAccessibility() {
        if (prefs.getBoolean(PERMISSIONS_PROMPTED_KEY, false)) return
        prefs.setBoolean(PERMISSIONS_PROMPTED_KEY, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }
        if (!AynaAccessibilityService.isEnabled(this)) {
            AynaAccessibilityService.openAccessibilitySettings(this)
        }
    }

    // ------------------------------------------------------------------- mic

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun onMicTap() {
        if (screen == Screen.Chat) {
            // Chat screen mic = Siri-like continuous voice loop.
            if (aynaVoiceLoop?.isRunning == true) {
                stopAynaVoiceLoop()
                return
            }
            if (!voice.isAvailable) {
                Toast.makeText(
                    this,
                    getString(R.string.ayna_voice_pending),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                startAynaVoiceLoop()
                return
            }
        }
        if (uiState.listening) {
            recognizer?.stopListening()
            return
        }
        pendingCommand = null
        startListening()
    }

    private fun recognitionLocale(): Locale = when (language) {
        "English" -> Locale("en", "US")
        else -> Locale("ur", "PK") // Urdu + Roman Urdu both recognised as ur-PK
    }

    private fun startListening() {
        if (!hasMicPermission()) {
            requestRuntimePermissions()
            Toast.makeText(this, getString(R.string.mic_permission_needed), Toast.LENGTH_SHORT).show()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            uiState = uiState.copy(statusText = getString(R.string.speech_not_available))
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLocale().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(AynaRecognitionListener())
            }
        }
        uiState = uiState.copy(listening = true, statusText = getString(R.string.listening))
        recognizer?.startListening(intent)
    }

    private inner class AynaRecognitionListener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            uiState = uiState.copy(
                listening = false,
                statusText = getString(R.string.did_not_hear)
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) uiState = uiState.copy(statusText = text)
        }

        override fun onResults(results: Bundle?) {
            uiState = uiState.copy(listening = false)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) handleHeard(text)
            else uiState = uiState.copy(statusText = getString(R.string.did_not_hear))
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** One heard utterance: Ayna chat screen → Ayna chat; otherwise advance
     *  a two-turn chip flow, or route it. */
    private fun handleHeard(text: String) {
        uiState = uiState.copy(statusText = text)
        if (screen == Screen.Chat) {
            sendAynaChatMessage(text)
            return
        }
        when (val pending = pendingCommand) {
            is PendingCommand.AwaitingContactName -> {
                when (pending.flow) {
                    VoiceFlow.CALL -> {
                        // Call needs no message — place it now.
                        pendingCommand = null
                        placeWhatsAppCall(text)
                    }
                    VoiceFlow.WHATSAPP -> {
                        pendingCommand = PendingCommand.AwaitingMessage(contactName = text)
                        voice.speakAsync(getString(R.string.ask_message_text)) { startListening() }
                    }
                    VoiceFlow.REMINDER -> Unit // unreachable
                }
            }
            is PendingCommand.AwaitingMessage -> {
                pendingCommand = null
                sendWhatsAppMessage(pending.contactName, text)
            }
            is PendingCommand.AwaitingReminderText -> {
                pendingCommand = null
                lifecycleScope.launch {
                    ReminderScheduler.schedule(
                        context = applicationContext,
                        delayMs = 3_600_000L, // 1 hour — v1 default
                        note = text
                    )
                }
                val reply = getString(R.string.reminder_set_1h)
                uiState = uiState.copy(lastAnswer = reply)
                voice.speakAsync(reply)
            }
            null -> routeSingleCommand(text)
        }
    }

    /** Single-utterance command -> the router (offline bank, Gemini, or an action). */
    private fun routeSingleCommand(text: String) {
        router.handle(text) { result -> handleRouterResult(result, inChat = false) }
    }

    /** One router result, shown either on the main screen or in the Ayna chat. */
    private fun handleRouterResult(result: CommandResult, inChat: Boolean) {
        val text = when (result) {
            is CommandResult.Spoken -> result.text
            is CommandResult.Action -> result.text
            is CommandResult.NeedsInput -> result.text
        }
        if (inChat) {
            aynaChatMessages = aynaChatMessages + ChatMessage(role = "ai", text = text)
        } else {
            uiState = uiState.copy(lastAnswer = text)
        }
        if (voiceEnabled) voice.speakAsync(text)
        if (result is CommandResult.Action) result.action()
    }

    /** Typed or spoken text in the Ayna chat screen. */
    private fun sendAynaChatMessage(text: String) {
        val clean = text.trim()
        if (clean.isBlank()) return
        aynaChatMessages = aynaChatMessages + ChatMessage(role = "user", text = clean)
        aynaTyping = true
        router.handle(clean) { result ->
            aynaTyping = false
            handleRouterResult(result, inChat = true)
        }
    }

    /** Start Ayna's Siri-like voice loop: she listens, answers and speaks
     *  back, on repeat until the mic is tapped again. */
    private fun startAynaVoiceLoop() {
        if (aynaVoiceLoop?.isRunning == true) return
        voiceEnabled = true
        aynaVoiceLoop = AynaVoiceLoop(
            context = applicationContext,
            brain = brain,
            voice = voice,
            scope = lifecycleScope,
            languageProvider = { language },
            onUserHeard = { text ->
                aynaChatMessages = aynaChatMessages + ChatMessage(role = "user", text = text)
            },
            onAynaReply = { reply ->
                aynaChatMessages = aynaChatMessages + ChatMessage(role = "ai", text = reply)
            },
            onStateChange = { state ->
                uiState = uiState.copy(
                    listening = state == AynaVoiceLoop.State.LISTENING
                )
            },
            onError = {
                uiState = uiState.copy(
                    listening = false,
                    statusText = getString(R.string.did_not_hear)
                )
            }
        )
        aynaVoiceLoop?.start()
    }

    private fun stopAynaVoiceLoop() {
        aynaVoiceLoop?.stop()
        aynaVoiceLoop = null
        uiState = uiState.copy(listening = false)
    }

    /** Chip tap: ask for the name first (WhatsApp/Call) or the reminder text. */
    private fun startVoiceFlow(flow: VoiceFlow) {
        when (flow) {
            VoiceFlow.WHATSAPP -> {
                pendingCommand = PendingCommand.AwaitingContactName(VoiceFlow.WHATSAPP)
                voice.speakAsync(getString(R.string.ask_contact_name)) { startListening() }
            }
            VoiceFlow.CALL -> {
                pendingCommand = PendingCommand.AwaitingContactName(VoiceFlow.CALL)
                voice.speakAsync(getString(R.string.ask_contact_name_call)) { startListening() }
            }
            VoiceFlow.REMINDER -> {
                pendingCommand = PendingCommand.AwaitingReminderText
                voice.speakAsync(getString(R.string.ask_reminder_text)) { startListening() }
            }
        }
    }

    private fun sendWhatsAppMessage(contactName: String, message: String) {
        val found = ContactLookup.findPhoneNumber(applicationContext, contactName) != null
        AynaAccessibilityService.sendWhatsAppMessageByName(
            applicationContext, contactName, message
        )
        val reply = if (found) getString(R.string.whatsapp_sent)
        else getString(R.string.whatsapp_failed)
        uiState = uiState.copy(lastAnswer = reply)
        voice.speakAsync(reply)
    }

    private fun placeWhatsAppCall(contactName: String) {
        val placed = AynaAccessibilityService.placeWhatsAppCallByName(
            applicationContext, contactName
        )
        val reply = if (placed) getString(R.string.call_placed, contactName)
        else getString(R.string.call_failed, contactName)
        uiState = uiState.copy(lastAnswer = reply)
        voice.speakAsync(reply)
    }

    // ---------------------------------------------------------------- paywall

    private fun copyEasypaisaNumber() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Easypaisa", Constants.EASYPAISA_NUMBER)
        )
        Toast.makeText(this, getString(R.string.number_copied), Toast.LENGTH_SHORT).show()
    }

    private fun submitPayment(txnId: String, screenshotUri: Uri?) {
        paywallState = PaywallState.SUBMITTING
        lifecycleScope.launch {
            val screenshotBytes = withContext(Dispatchers.IO) {
                runCatching {
                    screenshotUri?.let { contentResolver.openInputStream(it)?.readBytes() }
                }.getOrNull()
            }
            val result = paymentRepo.submitPayment(txnId, screenshotBytes)
            if (result.isSuccess) {
                paywallState = PaywallState.PENDING
                pollPaymentStatus()
            } else {
                paywallState = PaywallState.SHOW
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.payment_submit_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Polls the server for Maaz's verification (max ~10 minutes, then stops). */
    private fun pollPaymentStatus() {
        lifecycleScope.launch {
            repeat(10) {
                delay(60_000)
                val status = runCatching { paymentRepo.paymentStatus() }.getOrNull()
                when {
                    status?.isVerified == true -> {
                        licenseState = runCatching { licenseManager.refresh() }.getOrNull()
                        trialDaysLeft = licenseState?.trialDaysLeft
                        paywallState = PaywallState.UNLOCKED
                        screen = Screen.Main
                        return@launch
                    }
                    status?.isRejected == true -> {
                        paywallState = PaywallState.SHOW
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.payment_rejected),
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------- compose UI

    /** Tiny loading placeholder while licence + onboarding are checked. */
    @Composable
    private fun LoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AynaColors.Background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ayna",
                color = AynaColors.Text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
