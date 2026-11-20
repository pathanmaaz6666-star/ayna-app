# Ayna — Architecture

## Voice flow (online)

```
Mic / wake word ("Ayna")
  → SpeechRecognizer transcript
  → VoiceCommandRouter
      ├─ WhatsApp / SMS / call / reminder  → on-device action
      ├─ greeting / identity               → canned answer
      ├─ offline bank (Room, instant)      → answer (no data needed)
      └─ Gemini REST brain (needs key + data) → answer
  → AynaVoice (Gemini TTS, fixed female voice Aoede) speaks the answer
  → Main screen shows the answer + waveform
```

## Modules

| Package | Job |
|---|---|
| `ui` | Onboarding (one-time), MainScreen (face + waveform + chips + mic), PaywallScreen (Easypaisa 0308 9303647 / Maaz Arif / Rs 300), SettingsScreen (Gemini key, language, wake word), OverlayBubble, TrialBanner |
| `core` | `Prefs` — DataStore (`ayna_prefs`): onboarding flag, install time, language, trial/licence cache |
| `brain` | `GeminiBrain` interface; `GeminiRestBrain` (generateContent REST, free key) — the ONE brain: work, study, romance, flirty 18+ talk (tasteful, never explicit) + `AynaMemory` (name/favorites/dates/facts in DataStore JSON, last 30 turns); `GeminiLiveBrain` (roadmap stub — Live WebSocket); `AynaVoice` (Gemini TTS, fixed female voice Aoede, Urdu — NEVER Android TTS); `AynaVoiceLoop` (continuous listen → reply → speak); `VoiceCommandRouter` (intent routing) |
| `offline` | Room `KnowledgeDatabase` + `OfflineBank` (seed from `assets/offline/knowledge.json`, keyword scoring) + `KnowledgeRepository` (bundle updates from server) |
| `licensing` | `LicenseManager` (trial math + server check), `RemoteConfig` (trial days / price from server), `AynaApi` (Retrofit), `PaymentRepository` (TXN ID + screenshot submit) |
| `service` | `WakeWordService` (foreground, SpeechRecognizer loop), `OverlayService` (TYPE_APPLICATION_OVERLAY bubble), `AynaAccessibilityService` (tap inside WhatsApp), `ContactLookup`, `BootReceiver`, `ReminderScheduler` (WorkManager + AlarmManager) |
| `util` | `Constants` (incl. payment details), `SecurePrefs` (EncryptedSharedPreferences for the Gemini key) |
| `server` | FastAPI + SQLite: config, licence, payments, knowledge bundles, admin OTP login, admin panel (static HTML) |

## Licence state machine

```
[first install] → TRIAL_ACTIVE (10 days, server config can change it)
  → trial ends → TRIAL_EXPIRED → paywall shown
  → buyer pays Rs 300 (Easypaisa 0308 9303647, Maaz Arif) + submits TXN ID
  → PAYMENT_PENDING → Maaz verifies in admin panel → UNLOCKED (permanent)
  → Maaz can revoke from the panel → TRIAL_EXPIRED again
```

Licence is checked against the server when online; offline it falls back to
local trial math (install time + trial days). The server is the source of truth.

## Config flow (Maaz's control)

```
Maaz (admin panel, OTP to 0308)
  → PUT /api/v1/config (trial_days, price_pkr, knowledge_bundle_version)
  → app fetches RemoteConfig (24h cache) → new trial/price apply to all buyers
  → POST /api/v1/admin/knowledge → new bundle version → apps download + apply
```

## Data that never leaves the phone

- Gemini API key (EncryptedSharedPreferences).
- Voice transcripts (processed on-device, sent to Gemini only as the prompt text).
- Contacts (only looked up locally to resolve a spoken name to a number).

## What the server stores

- Device IDs (ANDROID_ID), licence status, payment TXN IDs (+ screenshots),
  knowledge entries, admin sessions (random tokens). No message contents,
  no call logs, no contacts.

## One app, one Ayna (AYNA-ONLY, 2026-11-19 ~23:50 PKT, Maaz's order)

No modes, no second persona. Ayna alone does everything — work, study,
romance, flirty 18+ talk (tasteful, never explicit) — in one app, one
payment (Rs 300, 10-day trial). The user's name is asked once in the
first-launch intro (Prefs `USER_NAME`); Ayna remembers it plus favourites,
dates and facts in [AynaMemory].

```text
Mic / wake word ("Ayna")
  → SpeechRecognizer transcript
  → main screen mic → VoiceCommandRouter (commands, offline bank, brain)
  → chat screen mic → AynaVoiceLoop (Siri-like, continuous)
      → GeminiRestBrain (unified persona + AynaMemory)
      → AynaVoice (Gemini TTS, fixed female voice Aoede, Urdu)
      → ChatScreen shows the conversation
  → after a spoken reply, the voice loop listens again (Siri-like)
```

## Modules (Ayna-only)

| Package | Job |
|---|---|
| `brain/AynaMemory` | Ayna's memory: name/favorites/dates/facts in DataStore JSON, chat history (last 30, DataStore JSON) |
| `brain/AynaVoice` | Ayna's speech via Gemini TTS (model gemini-2.5-flash-preview-tts, voice Aoede, Urdu), PCM playback via AudioTrack. NEVER Android TTS |
| `brain/AynaVoiceLoop` | Continuous listen → reply → listen loop for the chat screen |
| `ui/ChatScreen` | Ayna's chat UI (bubbles, input, mic, voice toggle, typing indicator) |

## First launch (intro)

Branding first (silent, 2 minutes: face + "Made by Maaz Arif" + "Product of
Mazo Company", progress bar, nothing spoken) → ask name → voice greeting →
features (everything Ayna can do) → permissions → Start. No mood selection anywhere.
