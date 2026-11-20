# AYNA — Build Contract (shared by all builders)

App: **Ayna — ONE app, AYNA ONLY** (Maaz, 2026-11-19 ~23:49 PKT: "Zara nahi
hona chahiye, sab kuch Ayna mein hi hona chahiye" — Zara mode removed).
Ayna alone does EVERYTHING in the one app:
- **Ayna** (آئینہ — mirror), AI voice assistant + companion. Brand: **Axon /
  Product of Mazo Company**.
- Work, study and homework, WhatsApp / calls / SMS / reminders, live info,
  AND warm, playful, romantic, flirty-but-tasteful talk (18+ only, never
  explicit). Emotionally intelligent, remembers the user's name/favorites/
  dates, Urdu / Roman Urdu / English, human-like, never claims to be human,
  never pretends to have a physical body.
Founder: **Maaz Arif**. Package: `com.mazo.ayna`. ApplicationId: `com.mazo.ayna`.
Version: 1.0.0 (versionCode 1). minSdk 26, targetSdk 34, compileSdk 34.
Kotlin 1.9.22, AGP 8.3.2, Gradle 8.5, Compose BOM 2024.02.00.
Language of UI copy: Roman Urdu first, English where natural. Urdu script only for the
name آئینہ and fixed religious/educational terms.

## Design (match the reference images exactly)
- Face asset: `R.drawable.ayna_face` (webp, `res/drawable-nodpi/ayna_face.webp`) — the FINAL
  face #3: beautiful human girl, long dark hair, warm smile. NEVER robot-like, never
  redrawn/replaced.
- Theme: dark purple. Background `#12061F`, surface `#241040`, card `#2D1548`,
  primary violet `#A855F7`, secondary `#8B5CF6`, accent pink `#EC4899`,
  text white `#FFFFFF`, muted `#B8A9D9`. Mic button: glowing purple ring.
- Main screen (see `media-generation-ayna-app-screen-*.webp`): circular face photo with
  purple ring at top, "Ayna" title, "AI Voice Assistant • Listening...", purple waveform,
  chips WhatsApp / Call / Reminder, big glowing mic button at bottom.
- Install screen (see `media-generation-ayna-install-screen-mazo-*.webp`): "Ayna / AI Voice
  Assistant", Install button, "What is Ayna?" text, footer "Product of Mazo Company".

## Core facts (do not invent others)
- Brain: Gemini (free API key, entered by the user in Settings, stored ONLY in
  EncryptedSharedPreferences "ayna_secure"). NEVER hardcode any key/token/password/OTP.
- Voice: female. Languages: Urdu, English, Roman Urdu. Pashto NOT guaranteed.
- Wake words: "Ayna", "Hello Ayna".
- Android = full control (AccessibilityService tap inside WhatsApp, overlay bubble,
  call handling). iOS = voice chat + answers + reminders ONLY (Apple blocks the rest).
- Offline: MAX offline bank (Room + JSON seed in `assets/offline/`). Online needed for
  smart/live answers (Gemini) and live info (weather/news).

## Ayna voice — LOCKED (Maaz, 2026-11-19)
- Ayna's voice is voice #1: **Aria (avocado_v2:MAI_01)**, warm/sweet female, Urdu.
  That id belongs to Muse's internal Shortwave TTS service (no public endpoint),
  so no phone and no deployable server can call it. On-device Ayna therefore
  speaks through **Gemini TTS** (server-side synthesis, model
  `gemini-2.5-flash-preview-tts`, fixed female voice `Aoede`, language `ur`)
  using the user's own free Gemini key — NEVER the phone's built-in TTS voice
  (that was the bug Maaz reported 2026-11-19 21:38). If a public MAI_01
  provider ever exists, only `Constants.AYNA_TTS_VOICE` changes.
- Voice-first (Siri-like): wake words "Ayna" / "Hello Ayna", mic button,
  voice replies; chat UI also available. After a spoken reply the app listens
  again (voice loop) until the user stops it.

## Onboarding (one-time only, first launch after install)
1. Branding FIRST (silent, 2 minutes): face + "Made by Maaz Arif" +
   "Product of Mazo Company", nothing spoken, progress bar filling over
   exactly 120_000 ms, then auto-advance. No skip on this phase.
2. Name — "Hey handsome! ❤️ What should I call you?" (stored in Prefs.USER_NAME)
3. Greeting — voice greeting in Ayna's locked voice
4. What it can do — everything in one app (WhatsApp, calls, SMS, reminders,
   live info, work, study, romantic chat + voice companion)
5. Permissions — mic, overlay, accessibility, notifications
6. Start — straight to main screen, intro never shows again.
NO mood selection anywhere (no Sweet/Flirty/Bold picker, ever).

## Licensing & payment (Maaz's exact details — use verbatim)
- Trial: **10 days free** from first install (trial_days from server config, default 10).
- Price: **Rs 300 one-time**, permanent after payment (price_pkr from server config,
  default 300). NO card anywhere. NO Google Play billing (direct APK distribution).
- **One payment unlocks Ayna (Rs 300, permanent).** The paywall says so.
- Paywall (shown when trial ends): shows EXACTLY:
  - Easypaisa number: **0308 9303647**
  - Account name: **Maaz Arif**
  - Amount: **Rs 300**
  - "Copy number" button + short Roman Urdu instructions.
- Payment flow: buyer pays Rs 300 to that Easypaisa number (in the Easypaisa app),
  returns to Ayna, submits **Transaction ID** (+ optional screenshot) in the app →
  server records payment as `pending_verification` → Maaz verifies in admin panel →
  server activates licence → app unlocks permanently. App polls licence status.
- Licence check: device id = ANDROID_ID; `GET /api/v1/license?device_id=...` against
  server base URL `BuildConfig.AYNA_API_URL` (gradle property `aynaApiUrl`,
  default `https://api.ayna.app` — placeholder until Maaz deploys the server).
- Trial/price/config are server-driven (RemoteConfig) so Maaz can change them anytime.

## Admin panel — MAAZ ONLY
- Admin panel is WEB ONLY (`server/admin/`). Login: **Maaz's number + OTP sent to 0308**.
- The buyer app MUST NOT contain any admin button, admin screen, admin link, or admin
  entry point of any kind. No hidden gestures, no debug menu leading to admin.
- Admin can: change trial days / price, verify payments, activate/revoke devices,
  publish offline-knowledge updates, see device count (scale target 10,000 users).

## File ownership (avoid edit conflicts)
- Core/scaffold (main agent): gradle files, AndroidManifest, MainActivity, Application,
  `core/`, `brain/`, `service/`, `util/`, `res/values/`, `res/xml/`.
- UI subagent: `ui/` package only (Onboarding, MainScreen, OverlayBubble, Paywall,
  Settings, theme, components). Do not touch other packages.
- Offline subagent: `offline/` package + `assets/offline/` only.
- Licensing subagent: `licensing/` package + `server/` only.
- Voice: `brain/AynaVoice.kt`, `brain/AynaVoiceLoop.kt` only.
- Everyone: read this CONTRACT first; keep imports to the agreed packages; no secrets.
