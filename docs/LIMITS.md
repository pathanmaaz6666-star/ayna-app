# Ayna — Honest limits (read before promising anything)

## Integration status (2026-11-19 ~21:15 PKT, verified)
- **MainActivity.kt is written and wired**: onboarding (once) → licence check →
  main / paywall / settings. Tap-to-talk via SpeechRecognizer, two-turn voice
  flows for the WhatsApp / Call / Reminder chips (pehle naam, phir message),
  single-utterance commands routed by VoiceCommandRouter (offline bank first,
  then Gemini REST). Paywall submits the Easypaisa TXN ID + optional screenshot
  and polls the server for Maaz's verification.
- **Fixed during integration** (all verified, not assumed):
  - Duplicate DataStore delegates for "ayna_prefs" (Prefs.kt + OfflineBank.kt)
    would have crashed DataStore at runtime — now ONE shared delegate in
    core/Prefs.kt.
  - Router now warms the offline cache (`refreshCache()`) before answering,
    so first-launch questions work even before the background seed finishes.
  - Added `AynaAccessibilityService.placeWhatsAppCallByName()` — outgoing
    WhatsApp voice call (open chat via wa.me, tap the "Voice call" button).
    The router's single-utterance call path uses the phone dialer instead.
  - Server knowledge bundle now returns `deleted` ids; Android
    `KnowledgeBundleResponse` + `KnowledgeRepository.applyServerBundle()`
    apply them. Test updated accordingly.
  - 15 missing strings added to strings.xml; Maaz's chosen face (#3) is
    bundled at `res/drawable-nodpi/ayna_face.webp` (1280×1920, 322 KB).
- **Validation actually run**: all XML parses; knowledge.json = 166 entries;
  all 54 R.string references resolve; all manifest classes resolve; all
  `com.mazo.ayna.*` imports resolve; braces balanced in all 34 Kotlin files;
  **server tests: 18/18 pass** (pytest, fastapi 0.165.0).
- **Still not compiled**: Gradle daemon IPC is blocked in this VM (see below).
  The next step is a real `./gradlew assembleDebug` on a normal machine —
  that is the first true compile of the full project, and it may surface
  errors this static check could not (Compose/Room codegen, resource linking).

## Building the APK
- **This agent VM cannot build the APK.** The Gradle daemon needs loopback TCP
  between its client and daemon processes, and the sandbox blocks that
  ("Other TCP connections is turned off", other_tcp = Deny). Proven with a
  minimal Java server/client test: the client receives the sandbox policy
  message instead of the server's reply. JDK 17, the Android SDK
  (platform-tools, android-34, build-tools 34.0.0) and Gradle 8.5 are all
  installed and working — only the daemon IPC is blocked.
- **Fix (either):** (a) enable it in Muse settings → Permissions → Direct
  network protocols → other_tcp → Ask; or (b) build on a normal machine with
  `./scripts/build.sh` (Android Studio / any laptop with JDK 17 + SDK).
- No build was faked. The source in this repo is complete; the APK step is
  documented, not pretended.

## Android (full control) — what is real
- WhatsApp messages by voice: real, via AccessibilityService taps on the real
  WhatsApp UI (wa.me link + Send button). WhatsApp's UI can change with
  updates — the flow may need maintenance.
- WhatsApp call accept/reject: real, by tapping the incoming-call screen.
- Phone call accept/reject: only if Ayna is set as the **default dialer**
  (Android RoleManager). Without that role, Ayna can dial out but cannot
  programmatically accept/reject calls. The app asks for the dialer role.
- Overlay bubble: real, needs "Draw over other apps" permission.
- Wake word: uses Android SpeechRecognizer in a loop (not a hardware hotword);
  needs internet for Google's recognizer unless an offline pack is installed,
  and uses more battery than a hardware hotword.
- Voice output: Android TTS (female voice picked when available). It is not
  a cloned voice. Gemini TTS is the upgrade path.
- Brain: Gemini REST (generateContent) with the user's free key. Gemini **Live**
  (voice-to-voice streaming) is a documented stub, not wired yet.

## iPhone / iPad — what Apple blocks
- Cannot tap inside WhatsApp, cannot accept/reject calls, cannot overlay on
  other apps, cannot run an always-on wake word. iOS Ayna = voice chat +
  answers + reminders only. No workaround exists without jailbreak (never).
- The iOS app is not in this repo (Android first). It needs a separate
  Swift/SwiftUI build reusing the same server + offline bank format.

## Offline
- The offline bank answers common questions without data. Smart answers,
  live weather/news, and fresh information need internet + a Gemini key.
- Live weather uses an online API when data is on; offline it explains
  seasons/weather in general terms only — it never invents a forecast.

## Selling / admin
- Payments are manual (Easypaisa → TXN ID → Maaz verifies). There is no
  automatic payment confirmation and no card processing.
- The licence server must be deployed before selling (`server/`); the app
  ships with a placeholder URL (`https://api.ayna.app`).
- The admin OTP needs a real SMS provider wired into the marked TODO hook;
  the server currently stubs it (logs that an OTP would be sent, never the code).
- Play Store is deliberately NOT the first channel: Accessibility-based
  WhatsApp automation is a common Play policy rejection. Direct APK first.

## Known v1 gaps (after integration)
- Reminder chip defaults to 1 hour — it does not ask "kab?" yet. (Saying
  "10 minute baad yaad dilao" in one utterance IS parsed by the router.)
- Outgoing WhatsApp call taps the "Voice call" button by its label — if
  WhatsApp renames it, the tap silently does nothing (the chat still opens).
- WhatsApp-send confirmation is based on the contact being found, not on the
  actual Send tap (the tap happens 2.5 s later, async).
- Wake-word listening uses SpeechRecognizer in a loop; it is not a hardware
  hotword and needs internet for Google's recognizer.
- The Gemini key is entered by the buyer in Settings and stored only in
  EncryptedSharedPreferences; there is no shared/built-in key (deliberate).

## Ayna-only refactor (2026-11-19 ~23:50 PKT, Maaz's order)
- **Removed:** the Zara mode entirely — mode switch (Ayna | Zara pills),
  `core/AppMode` + `Prefs.APP_MODE`, `brain/ZaraBrain.kt`,
  `ui/ZaraScreens.kt`, wake word "Zara", and every user-visible "Zara"
  string. Ayna alone does everything now: work, study, romance and flirty
  18+ talk (tasteful, never explicit), in one app, one Rs 300 payment.
- **Kept, renamed to Ayna:** `brain/AynaMemory.kt` (was ZaraMemory — same
  caps: 30 facts / 20 favourites / last 30 turns), `brain/AynaVoice.kt`
  (was ZaraVoice — Gemini TTS, fixed female voice Aoede), `brain/
  AynaVoiceLoop.kt` (was ZaraVoiceLoop — Siri-like listen → reply → speak).
  The warm/romantic/flirty persona from ZaraBrain is merged into
  `GeminiRestBrain` (the ONE brain). The old Android-TTS `AynaVoice` is
  gone — Ayna speaks only through Gemini TTS now, never the phone's
  default voice.
- **Ayna voice — honest limit:** Maaz locked voice #1, Aria
  (avocado_v2:MAI_01). That voice lives behind Muse's internal Shortwave TTS
  service (localhost proxy, no public endpoint), so no phone and no
  deployable server can call it. On-device Ayna therefore speaks via Gemini
  TTS (server-side synthesis, fixed female voice Aoede, Urdu) — never the
  phone's built-in TTS. If a public MAI_01 provider ever exists, only
  `Constants.AYNA_TTS_VOICE` changes.
- **Voice layer status:** AynaVoice.kt (Gemini TTS, fixed female voice
  Aoede, 24 kHz PCM via AudioTrack) + AynaVoiceLoop.kt (Siri-like listen →
  reply → speak loop) are wired into MainActivity (voice toggle, mic on the
  Chat screen = loop on/off, spoken replies when voice is on, loop stops
  when leaving the chat / onDestroy). Voice needs the user's free Gemini key
  (Settings) — without it Ayna stays silent instead of speaking in the
  wrong voice. Ayna NEVER falls back to the phone's default voice. Not yet
  compile-tested: the first real `./gradlew assembleDebug` on a normal
  machine is still the proof point.
- **Integration done (2026-11-19 ~23:50 PKT, verified):** MainActivity wires
  the single Ayna chat (brain + memory + history), onboarding name capture,
  Ayna greeting seed, wake-word "Ayna" only, and back-button handling.
  Static verification passed: all XML parses, all R.string refs resolve
  (0 missing), all imports resolve, braces balanced in all Kotlin files,
  server tests 18/18 pass, no secrets in code.
- **Still not compiled:** Gradle daemon IPC is blocked in this VM (see
  above). The Ayna-only project needs its first `./gradlew assembleDebug`
  on a normal machine, which may surface errors static checks could not.
