# Ayna — ONE app, AYNA ONLY (آئینہ)

**Ayna** is Maaz Arif's AI voice assistant and companion (Product of Mazo
Company, brand: Axon). One app, one payment, no modes: Ayna alone does
EVERYTHING — work, study, WhatsApp, calls, SMS, reminders, weather,
education, and warm, romantic, flirty 18+ talk (tasteful, never explicit) —
chat + voice like Siri, after install.

- **Ayna:** a beautiful girl on the screen who listens, talks, and does
  things by voice — WhatsApp messages, calls, SMS, reminders, weather,
  education, work, study, romance, and everything, like Siri — but in Urdu,
  English, and Roman Urdu. She remembers your name, favourites and dates,
  reacts to your mood, and talks like a real person (never claims to be
  human, never pretends to have a body). Voice replies use her locked voice
  #1 (Aria, warm/sweet female, Urdu) via server-side TTS — never the phone's
  default voice.

- **Name:** Ayna (آئینہ — mirror)
- **Face:** the FINAL face #3 (beautiful human girl, not robot-like)
- **Platform:** Android first (full control), iPhone/iPad too (limited by Apple)
- **Brain:** Gemini (free API key, no card)
- **Price:** one-time **Rs 300**, **10-day free trial**, permanent after payment
- **Payment:** Easypaisa **0308 9303647** (Maaz Arif) — buyer pays, submits the
  transaction ID in the app, Maaz verifies, licence unlocks permanently.
- **Admin:** web panel only, Maaz's number + OTP to 0308. Buyer apps have no admin UI.

## What is in this repo

```
ayna-app/
├── CONTRACT.md            # shared build contract (read first)
├── README.md              # this file
├── docs/
│   ├── ARCHITECTURE.md    # module map + data flow
│   └── LIMITS.md          # honest limits (read before promising anything)
├── scripts/build.sh       # build the APK on a normal machine
├── gradle.properties      # versions, proxy, server URL placeholder
├── app/                   # Android app (Kotlin + Jetpack Compose)
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mazo/ayna/
│       │   ├── MainActivity.kt        # navigation: onboarding → main / paywall / settings
│       │   ├── AynaApplication.kt
│       │   ├── ui/                    # onboarding, main screen, paywall, settings
│       │   ├── core/                  # DataStore prefs
│       │   ├── brain/                 # Gemini REST brain, voice (TTS), command router
│       │   ├── offline/               # Room knowledge bank + JSON seed
│       │   ├── licensing/             # trial, licence check, payment submit
│       │   ├── service/               # wake word, overlay bubble, accessibility, reminders
│       │   └── util/                  # constants, encrypted prefs
│       ├── res/                       # colors, strings, themes, ayna_face
│       └── assets/offline/knowledge.json
└── server/                # licence + admin server (FastAPI + SQLite)
    ├── app/main.py        # config, licence, payments, knowledge, admin OTP
    ├── admin/index.html   # Maaz's control panel (web only)
    ├── requirements.txt
    └── tests/test_api.py
```

## Build the APK (on a normal machine)

The agent VM cannot run the Gradle daemon (its loopback TCP is blocked by the
sandbox — see `docs/LIMITS.md`). Build on a laptop / Android Studio / build server:

```bash
./scripts/build.sh            # debug APK → app/build/outputs/apk/debug/app-debug.apk
./scripts/build.sh release    # release APK (unsigned unless signing is configured)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requirements: JDK 17, Android SDK with `platforms;android-34` and
`build-tools;34.0.0`. First build downloads Gradle 8.5 + dependencies
(Google Maven / Maven Central) — internet needed, no account, no card.

## First run (what the buyer sees)

1. Install the APK → **one-time intro** (never again): branding first —
   "Made by Maaz Arif" + "Product of Mazo Company", silent for 2 minutes —
   then your name, a voice greeting, what the app can do, permissions, Start.
2. Main screen: Ayna's face, waveform, WhatsApp / Call / Reminder chips, mic
   button, and the Chat button at the top.
3. Say **"Ayna"** (wake word) or tap the mic. Speak in Urdu / English / Roman Urdu.
   On the Chat screen the mic starts the Siri-like continuous voice loop.
4. **10-day free trial**, then the paywall: Easypaisa 0308 9303647 (Maaz Arif),
   Rs 300 → pay → submit transaction ID → Maaz verifies → permanent unlock
   of Ayna.

## Configuration (no secrets in code)

- **Gemini API key:** the user types their own free key in Settings. It is stored
  only in EncryptedSharedPreferences on the phone. The app never ships with a key.
- **Server URL:** gradle property `aynaApiUrl` (default `https://api.ayna.app` —
  placeholder until Maaz deploys `server/`). Change it before a release build.
- **Admin OTP:** the server sends an OTP to 0308 via an SMS provider hook
  (`server/app/main.py`, clearly marked TODO). No OTP is ever hardcoded.

## Run the server (Maaz's admin panel)

```bash
cd server
pip install -r requirements.txt
ADMIN_PHONE=03089303647 uvicorn app.main:app --host 0.0.0.0 --port 8000
# Open http://<server>:8000/admin/ — login with number + OTP to 0308.
```

The panel controls: trial days, price, payment verification, device
activation/revocation, offline-knowledge publishing. Changes reach all buyers
at once (licence + config are checked from the server). Scale target: 10,000 users
(SQLite is fine to start; move to Postgres before 10,000 users).

## Honest limits

Read `docs/LIMITS.md` before promising anything to a buyer. Short version:
Android can do everything (WhatsApp tap, overlay, calls via default-dialer role);
iPhone/iPad cannot tap inside WhatsApp, cannot overlay, cannot auto accept/reject
calls (Apple blocks it) — iOS Ayna is voice chat + answers + reminders only.
The brain needs internet for smart answers; the offline bank covers basics.
