# Ayna licence + payment + admin server

FastAPI + stdlib `sqlite3`. No heavy ORM, no secrets in code.

Payment terms (Maaz, verbatim): **Easypaisa 0308 9303647 — Maaz Arif —
Rs 300 one-time, permanent after payment. Trial: 10 days free from first install.**

## Run

```bash
cd server
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Admin panel: http://localhost:8000/admin/ (Maaz only — phone + OTP to 0308).

## Environment

| Variable | Default | Meaning |
| --- | --- | --- |
| `ADMIN_PHONE` | `03089303647` | Maaz's number; the only number that can request an admin OTP. |
| `DATABASE_PATH` | `server/app/ayna.db` | SQLite file (created on first start). |
| `PORT` | `8000` | Pass to uvicorn with `--port`. |

## Deploy notes (VPS)

1. Copy this `server/` directory to the VPS, install requirements in a venv.
2. Run behind a reverse proxy with HTTPS (Caddy or nginx) — the app talks to
   this server over the public internet, and the admin OTP login must never run
   over plain HTTP. Example with systemd:
   `uvicorn app.main:app --host 127.0.0.1 --port 8000` + Caddy reverse-proxy
   to `https://api.ayna.app`.
3. Set `ADMIN_PHONE=03089303647` (already the default) and `DATABASE_PATH` to a
   path outside the web root. Back up `ayna.db` daily (it holds every device
   and payment record).
4. Point the Android app at the server with the gradle property `aynaApiUrl`
   (default `https://api.ayna.app`).
5. Put the server behind the proxy only — never expose uvicorn directly.

## How the OTP provider hook works

`POST /api/v1/admin/request-otp` generates a 6-digit code, stores **only its
SHA-256 hash** in the `otp_codes` table (10-minute expiry, single use), and
logs `OTP for {phone} would be sent via SMS provider`. The code itself is
never logged and never stored in plain text.

Right now there is **no real SMS provider wired in** — that is a clearly
marked `TODO(SMS provider hook)` in `app/main.py`. To finish it, pick a
provider that can send SMS to Pakistani numbers (e.g. Twilio, or a local
provider such as Jazz/Telenor bulk SMS / MessageBird), add its SDK or HTTP
call at the TODO, and send the code to `+92` + the normalised phone. Until
that is done, **nobody can log in to the admin panel** — the OTP is generated
and hashed but never delivered.

`POST /api/v1/admin/verify-otp` compares hashes in constant time
(`hmac.compare_digest`) and issues a random session token (12h expiry) sent
back to the browser as the `X-Admin-Token` header value. Only the token's
SHA-256 hash is stored server-side. The admin page keeps the token in memory
only — never in `localStorage`.

## Scale note

SQLite is fine to start (a few thousand devices, one writer at a time — this
server opens a short connection per request). **Move to Postgres before
10,000 users**: concurrent payment submissions and admin writes will start
to hit SQLite's single-writer lock, and a VPS crash risks losing the only
copy of the licence database. The table shapes map 1:1 to Postgres; only the
`sqlite3` connection layer in `app/main.py` needs replacing.

## API

Public:

- `GET /api/v1/config` — `{trial_days, price_pkr, min_version, knowledge_bundle_version}`
- `GET /api/v1/license?device_id=...` — `{device_id, status, trial_days_left, expiry_date, activated_at}`; status is `TRIAL_ACTIVE | TRIAL_EXPIRED | PAYMENT_PENDING | UNLOCKED`
- `POST /api/v1/payments` — `{txn_id, device_id, screenshot_b64?}` → `{id, status: "pending_verification"}`
- `GET /api/v1/payments/status?device_id=...` — `{status, txn_id, verified_at}`
- `GET /api/v1/knowledge/bundle?version=...` — `{version, items: [...]}` (full current bundle)

Admin (header `X-Admin-Token`):

- `POST /api/v1/admin/request-otp` — `{phone}` (Maaz's number only)
- `POST /api/v1/admin/verify-otp` — `{phone, code}` → `{token, expires_in_hours}`
- `PUT /api/v1/config` — `{trial_days?, price_pkr?, min_version?}`
- `GET /api/v1/admin/overview` — `{devices, paid_devices, pending_payments, verified_payments}`
- `GET /api/v1/admin/payments[?status=...]`
- `POST /api/v1/admin/payments/{id}/verify` — `{decision: "verify"|"reject"}`; verify sets the payment `verified` and the device `UNLOCKED`
- `GET /api/v1/admin/devices`
- `GET /api/v1/admin/knowledge`
- `POST /api/v1/admin/knowledge` — upsert `{id?, category, language, keywords[], question, answer, deleted?}`; bumps the bundle version

## Tests

```bash
pip install pytest httpx
python -m pytest tests/ -v
```
