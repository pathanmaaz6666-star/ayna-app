"""Ayna licence + payment + admin server.

FastAPI + stdlib sqlite3. No heavy ORM, no secrets in code.

Payment terms (Maaz, verbatim):
  Easypaisa 0308 9303647 — Maaz Arif — Rs 300 one-time, permanent after payment.
  Trial: 10 days free from first install.

Flow: trial ends -> buyer pays in the Easypaisa app -> submits Transaction ID
(+ optional screenshot) in Ayna -> server records payment as
``pending_verification`` -> Maaz verifies in the web-only admin panel
-> server activates the licence -> app unlocks permanently.

Admin: WEB ONLY (server/admin/). Login = Maaz's number + OTP sent to 0308.
Admin auth = ``X-Admin-Token`` header with a random per-login session token
(12h expiry). OTPs are stored ONLY as SHA-256 hashes, never in plain text,
and the code itself is never logged.
"""

import hashlib
import hmac
import json
import logging
import os
import secrets
import sqlite3
from datetime import datetime, timedelta, timezone
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

log = logging.getLogger("ayna")

# ---------------------------------------------------------------------------
# Configuration (env only — no secrets hardcoded)
# ---------------------------------------------------------------------------

BASE_DIR = Path(__file__).resolve().parent.parent
DATABASE_PATH = os.environ.get("DATABASE_PATH", str(BASE_DIR / "app" / "ayna.db"))
# Maaz's number, digits only, without leading 0 / country code: 3089303647.
ADMIN_PHONE = os.environ.get("ADMIN_PHONE", "03089303647")

DEFAULT_TRIAL_DAYS = 10
DEFAULT_PRICE_PKR = 300
OTP_TTL = timedelta(minutes=10)
SESSION_TTL = timedelta(hours=12)
MILLIS_PER_DAY = 86_400_000

# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DATABASE_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


def init_db() -> None:
    Path(DATABASE_PATH).parent.mkdir(parents=True, exist_ok=True)
    with _connect() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS devices (
                device_id       TEXT PRIMARY KEY,
                created_at      TEXT NOT NULL,
                trial_started_at TEXT NOT NULL,
                status          TEXT NOT NULL DEFAULT 'TRIAL_ACTIVE',
                activated_at    TEXT
            );
            CREATE TABLE IF NOT EXISTS payments (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id       TEXT NOT NULL REFERENCES devices(device_id),
                txn_id          TEXT NOT NULL,
                screenshot_b64  TEXT,
                status          TEXT NOT NULL DEFAULT 'pending_verification',
                created_at      TEXT NOT NULL,
                verified_at     TEXT,
                UNIQUE(device_id, txn_id)
            );
            CREATE TABLE IF NOT EXISTS config (
                key   TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS knowledge (
                id              INTEGER PRIMARY KEY AUTOINCREMENT,
                category        TEXT NOT NULL,
                language        TEXT NOT NULL,
                keywords_json   TEXT NOT NULL DEFAULT '[]',
                question        TEXT NOT NULL,
                answer          TEXT NOT NULL,
                version         INTEGER NOT NULL DEFAULT 1,
                deleted         INTEGER NOT NULL DEFAULT 0
            );
            CREATE TABLE IF NOT EXISTS admin_sessions (
                token_hash TEXT PRIMARY KEY,
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS otp_codes (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                phone      TEXT NOT NULL,
                code_hash  TEXT NOT NULL,
                created_at TEXT NOT NULL,
                expires_at TEXT NOT NULL,
                used       INTEGER NOT NULL DEFAULT 0
            );
            """
        )
        # Seed default config on first start (Maaz's terms).
        conn.execute(
            "INSERT OR IGNORE INTO config(key, value) VALUES ('trial_days', ?)",
            (str(DEFAULT_TRIAL_DAYS),),
        )
        conn.execute(
            "INSERT OR IGNORE INTO config(key, value) VALUES ('price_pkr', ?)",
            (str(DEFAULT_PRICE_PKR),),
        )
        conn.execute(
            "INSERT OR IGNORE INTO config(key, value) VALUES ('min_version', '1')",
        )
        conn.execute(
            "INSERT OR IGNORE INTO config(key, value) "
            "VALUES ('knowledge_bundle_version', '1')",
        )
        conn.commit()


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _get_config(conn: sqlite3.Connection) -> dict:
    rows = conn.execute("SELECT key, value FROM config").fetchall()
    cfg = {row["key"]: row["value"] for row in rows}
    return {
        "trial_days": int(cfg.get("trial_days", DEFAULT_TRIAL_DAYS)),
        "price_pkr": int(cfg.get("price_pkr", DEFAULT_PRICE_PKR)),
        "min_version": int(cfg.get("min_version", 1)),
        "knowledge_bundle_version": int(cfg.get("knowledge_bundle_version", 1)),
    }


def _normalise_phone(phone: str) -> str:
    """Digits only, without leading 0 or +92 country code: 3089303647."""
    digits = "".join(ch for ch in phone if ch.isdigit())
    if digits.startswith("92"):
        digits = digits[2:]
    if digits.startswith("0"):
        digits = digits[1:]
    return digits


# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = FastAPI(title="Ayna licence server", version="1.0.0")


@app.on_event("startup")
def _startup() -> None:
    init_db()


# ---------------------------------------------------------------------------
# Schemas
# ---------------------------------------------------------------------------


class ConfigUpdate(BaseModel):
    trial_days: int | None = Field(default=None, ge=1, le=365)
    price_pkr: int | None = Field(default=None, ge=0)
    min_version: int | None = Field(default=None, ge=1)


class PaymentIn(BaseModel):
    txn_id: str = Field(min_length=1, max_length=100)
    device_id: str = Field(min_length=1, max_length=200)
    screenshot_b64: str | None = None


class OtpRequest(BaseModel):
    phone: str = Field(min_length=10, max_length=20)


class OtpVerify(BaseModel):
    phone: str = Field(min_length=10, max_length=20)
    code: str = Field(min_length=6, max_length=6, pattern=r"^\d{6}$")


class PaymentDecision(BaseModel):
    decision: str = "verify"  # "verify" | "reject"


class KnowledgeIn(BaseModel):
    id: int | None = None
    category: str = Field(min_length=1, max_length=100)
    language: str = Field(min_length=1, max_length=50)
    keywords: list[str] = []
    question: str = Field(min_length=1)
    answer: str = Field(min_length=1)
    deleted: bool = False


# ---------------------------------------------------------------------------
# Admin auth: X-Admin-Token header, random per-login token, 12h expiry.
# Only the token's SHA-256 hash is stored.
# ---------------------------------------------------------------------------


def require_admin(x_admin_token: str | None = Header(default=None)) -> None:
    if not x_admin_token:
        raise HTTPException(status_code=401, detail="Admin token required")
    token_hash = hashlib.sha256(x_admin_token.encode()).hexdigest()
    with _connect() as conn:
        row = conn.execute(
            "SELECT expires_at FROM admin_sessions WHERE token_hash = ?",
            (token_hash,),
        ).fetchone()
    if row is None:
        raise HTTPException(status_code=401, detail="Invalid admin token")
    if datetime.fromisoformat(row["expires_at"].replace("Z", "+00:00")) < _now():
        raise HTTPException(status_code=401, detail="Admin token expired")


# ---------------------------------------------------------------------------
# Public API: config / licence / payments / knowledge bundle
# ---------------------------------------------------------------------------


@app.get("/api/v1/config")
def get_config() -> dict:
    with _connect() as conn:
        return _get_config(conn)


@app.put("/api/v1/config")
def update_config(update: ConfigUpdate, _admin: None = Depends(require_admin)) -> dict:
    with _connect() as conn:
        if update.trial_days is not None:
            conn.execute(
                "INSERT INTO config(key, value) VALUES ('trial_days', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(update.trial_days),),
            )
        if update.price_pkr is not None:
            conn.execute(
                "INSERT INTO config(key, value) VALUES ('price_pkr', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(update.price_pkr),),
            )
        if update.min_version is not None:
            conn.execute(
                "INSERT INTO config(key, value) VALUES ('min_version', ?) "
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                (str(update.min_version),),
            )
        conn.commit()
        return _get_config(conn)


@app.get("/api/v1/license")
def get_license(device_id: str) -> dict:
    device_id = device_id.strip()
    if not device_id:
        raise HTTPException(status_code=400, detail="device_id required")
    now = _now()
    with _connect() as conn:
        cfg = _get_config(conn)
        row = conn.execute(
            "SELECT * FROM devices WHERE device_id = ?", (device_id,)
        ).fetchone()
        if row is None:
            conn.execute(
                "INSERT INTO devices(device_id, created_at, trial_started_at, status)"
                " VALUES (?, ?, ?, 'TRIAL_ACTIVE')",
                (device_id, _iso(now), _iso(now)),
            )
            conn.commit()
            trial_started = now
            status = "TRIAL_ACTIVE"
            activated_at = None
        else:
            trial_started = datetime.fromisoformat(
                row["trial_started_at"].replace("Z", "+00:00")
            )
            status = row["status"]
            activated_at = row["activated_at"]
        pending = conn.execute(
            "SELECT id FROM payments WHERE device_id = ?"
            " AND status = 'pending_verification' LIMIT 1",
            (device_id,),
        ).fetchone()

    trial_days = cfg["trial_days"]
    expiry = trial_started + timedelta(days=trial_days)
    # Whole days remaining, counting a partial last day as 1.
    if expiry > now:
        days_left = -(-int((expiry - now).total_seconds()) // 86_400)
    else:
        days_left = 0

    if status == "UNLOCKED":
        final_status = "UNLOCKED"
    elif pending is not None:
        final_status = "PAYMENT_PENDING"
    elif expiry <= now:
        final_status = "TRIAL_EXPIRED"
    else:
        final_status = "TRIAL_ACTIVE"

    return {
        "device_id": device_id,
        "status": final_status,
        "trial_days_left": days_left if final_status in ("TRIAL_ACTIVE", "TRIAL_EXPIRED") else 0,
        "expiry_date": _iso(expiry),
        "activated_at": activated_at,
    }


@app.post("/api/v1/payments", status_code=201)
def submit_payment(payment: PaymentIn) -> dict:
    txn_id = payment.txn_id.strip()
    device_id = payment.device_id.strip()
    if not txn_id or not device_id:
        raise HTTPException(status_code=400, detail="txn_id and device_id required")
    now = _now()
    with _connect() as conn:
        conn.execute(
            "INSERT OR IGNORE INTO devices(device_id, created_at, trial_started_at,"
            " status) VALUES (?, ?, ?, 'TRIAL_ACTIVE')",
            (device_id, _iso(now), _iso(now)),
        )
        existing = conn.execute(
            "SELECT id, status FROM payments WHERE device_id = ? AND txn_id = ?",
            (device_id, txn_id),
        ).fetchone()
        if existing is not None:
            # Idempotent re-submit of the same Transaction ID.
            return {"id": existing["id"], "status": existing["status"]}
        cur = conn.execute(
            "INSERT INTO payments(device_id, txn_id, screenshot_b64, status,"
            " created_at) VALUES (?, ?, ?, 'pending_verification', ?)",
            (device_id, txn_id, payment.screenshot_b64, _iso(now)),
        )
        conn.commit()
        return {"id": cur.lastrowid, "status": "pending_verification"}


@app.get("/api/v1/payments/status")
def get_payment_status(device_id: str) -> dict:
    with _connect() as conn:
        row = conn.execute(
            "SELECT txn_id, status, verified_at FROM payments"
            " WHERE device_id = ? ORDER BY id DESC LIMIT 1",
            (device_id.strip(),),
        ).fetchone()
    if row is None:
        return {"status": "none", "txn_id": None, "verified_at": None}
    return {
        "status": row["status"],
        "txn_id": row["txn_id"],
        "verified_at": row["verified_at"],
    }


@app.get("/api/v1/knowledge/bundle")
def get_knowledge_bundle(version: int = 0) -> dict:
    with _connect() as conn:
        cfg = _get_config(conn)
        rows = conn.execute(
            "SELECT id, category, language, keywords_json, question, answer, version"
            " FROM knowledge WHERE deleted = 0 ORDER BY id"
        ).fetchall()
        deleted_rows = conn.execute(
            "SELECT id FROM knowledge WHERE deleted = 1"
        ).fetchall()
    return {
        "version": cfg["knowledge_bundle_version"],
        "deleted": [row["id"] for row in deleted_rows],
        "items": [
            {
                "id": row["id"],
                "category": row["category"],
                "language": row["language"],
                "keywords": json.loads(row["keywords_json"]),
                "question": row["question"],
                "answer": row["answer"],
                "version": row["version"],
            }
            for row in rows
        ],
    }


# ---------------------------------------------------------------------------
# Admin: OTP login (Maaz's number only). OTPs are stored ONLY as SHA-256
# hashes; the code itself is never logged and never stored in plain text.
# ---------------------------------------------------------------------------


@app.post("/api/v1/admin/request-otp")
def request_otp(req: OtpRequest) -> dict:
    phone = _normalise_phone(req.phone)
    # Generic reply either way: never reveal whether a number is the admin one.
    if phone != _normalise_phone(ADMIN_PHONE):
        return {"detail": "If this number is registered, an OTP was sent."}
    code = f"{secrets.randbelow(900_000) + 100_000:06d}"
    code_hash = hashlib.sha256(code.encode()).hexdigest()
    now = _now()
    with _connect() as conn:
        conn.execute(
            "UPDATE otp_codes SET used = 1 WHERE phone = ? AND used = 0", (phone,)
        )
        conn.execute(
            "INSERT INTO otp_codes(phone, code_hash, created_at, expires_at, used)"
            " VALUES (?, ?, ?, ?, 0)",
            (phone, code_hash, _iso(now), _iso(now + OTP_TTL)),
        )
        conn.commit()
    # TODO(SMS provider hook): send `code` to 0308 via the SMS provider here.
    # The code must NEVER be logged. Example once a provider is chosen:
    #   sms_provider.send(to="+92" + phone, body=f"Ayna admin login code: {code}")
    log.info("OTP for %s would be sent via SMS provider", phone)
    return {"detail": "If this number is registered, an OTP was sent."}


@app.post("/api/v1/admin/verify-otp")
def verify_otp(req: OtpVerify) -> dict:
    phone = _normalise_phone(req.phone)
    code_hash = hashlib.sha256(req.code.encode()).hexdigest()
    now = _now()
    with _connect() as conn:
        row = conn.execute(
            "SELECT id, code_hash, expires_at FROM otp_codes"
            " WHERE phone = ? AND used = 0 ORDER BY id DESC LIMIT 1",
            (phone,),
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=401, detail="Invalid or expired OTP")
        if datetime.fromisoformat(row["expires_at"].replace("Z", "+00:00")) < now:
            raise HTTPException(status_code=401, detail="Invalid or expired OTP")
        # Constant-time comparison of hashes.
        if not hmac.compare_digest(row["code_hash"], code_hash):
            raise HTTPException(status_code=401, detail="Invalid or expired OTP")
        conn.execute("UPDATE otp_codes SET used = 1 WHERE id = ?", (row["id"],))
        token = secrets.token_urlsafe(32)
        token_hash = hashlib.sha256(token.encode()).hexdigest()
        conn.execute(
            "INSERT INTO admin_sessions(token_hash, created_at, expires_at)"
            " VALUES (?, ?, ?)",
            (token_hash, _iso(now), _iso(now + SESSION_TTL)),
        )
        conn.commit()
    return {"token": token, "expires_in_hours": 12}


# ---------------------------------------------------------------------------
# Admin: overview / payments / devices / knowledge
# ---------------------------------------------------------------------------


@app.get("/api/v1/admin/overview")
def admin_overview(_admin: None = Depends(require_admin)) -> dict:
    with _connect() as conn:
        devices = conn.execute("SELECT COUNT(*) c FROM devices").fetchone()["c"]
        paid = conn.execute(
            "SELECT COUNT(*) c FROM devices WHERE status = 'UNLOCKED'"
        ).fetchone()["c"]
        pending = conn.execute(
            "SELECT COUNT(*) c FROM payments WHERE status = 'pending_verification'"
        ).fetchone()["c"]
        verified = conn.execute(
            "SELECT COUNT(*) c FROM payments WHERE status = 'verified'"
        ).fetchone()["c"]
    return {
        "devices": devices,
        "paid_devices": paid,
        "pending_payments": pending,
        "verified_payments": verified,
    }


@app.get("/api/v1/admin/payments")
def admin_payments(
    status: str | None = None, _admin: None = Depends(require_admin)
) -> dict:
    with _connect() as conn:
        if status:
            rows = conn.execute(
                "SELECT id, device_id, txn_id, status, created_at, verified_at"
                " FROM payments WHERE status = ? ORDER BY id DESC",
                (status,),
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT id, device_id, txn_id, status, created_at, verified_at"
                " FROM payments ORDER BY id DESC"
            ).fetchall()
    return {
        "payments": [
            {
                "id": row["id"],
                "device_id": row["device_id"],
                "txn_id": row["txn_id"],
                "status": row["status"],
                "created_at": row["created_at"],
                "verified_at": row["verified_at"],
            }
            for row in rows
        ]
    }


@app.post("/api/v1/admin/payments/{payment_id}/verify")
def admin_verify_payment(
    payment_id: int,
    decision: PaymentDecision,
    _admin: None = Depends(require_admin),
) -> dict:
    if decision.decision not in ("verify", "reject"):
        raise HTTPException(status_code=400, detail="decision must be verify|reject")
    now = _now()
    with _connect() as conn:
        row = conn.execute(
            "SELECT device_id, status FROM payments WHERE id = ?", (payment_id,)
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=404, detail="Payment not found")
        if decision.decision == "verify":
            conn.execute(
                "UPDATE payments SET status = 'verified', verified_at = ?"
                " WHERE id = ?",
                (_iso(now), payment_id),
            )
            conn.execute(
                "UPDATE devices SET status = 'UNLOCKED', activated_at = ?"
                " WHERE device_id = ?",
                (_iso(now), row["device_id"]),
            )
            new_status = "verified"
        else:
            conn.execute(
                "UPDATE payments SET status = 'rejected', verified_at = ?"
                " WHERE id = ?",
                (_iso(now), payment_id),
            )
            new_status = "rejected"
        conn.commit()
    return {"id": payment_id, "status": new_status}


@app.get("/api/v1/admin/devices")
def admin_devices(_admin: None = Depends(require_admin)) -> dict:
    with _connect() as conn:
        rows = conn.execute(
            "SELECT device_id, created_at, trial_started_at, status, activated_at"
            " FROM devices ORDER BY created_at DESC"
        ).fetchall()
    return {
        "devices": [
            {
                "device_id": row["device_id"],
                "created_at": row["created_at"],
                "trial_started_at": row["trial_started_at"],
                "status": row["status"],
                "activated_at": row["activated_at"],
            }
            for row in rows
        ]
    }


@app.get("/api/v1/admin/knowledge")
def admin_knowledge(_admin: None = Depends(require_admin)) -> dict:
    with _connect() as conn:
        rows = conn.execute(
            "SELECT id, category, language, keywords_json, question, answer,"
            " version, deleted FROM knowledge ORDER BY id"
        ).fetchall()
    return {
        "knowledge": [
            {
                "id": row["id"],
                "category": row["category"],
                "language": row["language"],
                "keywords": json.loads(row["keywords_json"]),
                "question": row["question"],
                "answer": row["answer"],
                "version": row["version"],
                "deleted": bool(row["deleted"]),
            }
            for row in rows
        ]
    }


@app.post("/api/v1/admin/knowledge")
def admin_upsert_knowledge(
    item: KnowledgeIn, _admin: None = Depends(require_admin)
) -> dict:
    with _connect() as conn:
        cfg = _get_config(conn)
        new_version = cfg["knowledge_bundle_version"] + 1
        keywords_json = json.dumps(item.keywords, ensure_ascii=False)
        if item.id is not None:
            row = conn.execute(
                "SELECT id FROM knowledge WHERE id = ?", (item.id,)
            ).fetchone()
            if row is None:
                raise HTTPException(status_code=404, detail="Knowledge item not found")
            conn.execute(
                "UPDATE knowledge SET category = ?, language = ?,"
                " keywords_json = ?, question = ?, answer = ?, version = ?,"
                " deleted = ? WHERE id = ?",
                (
                    item.category,
                    item.language,
                    keywords_json,
                    item.question,
                    item.answer,
                    new_version,
                    1 if item.deleted else 0,
                    item.id,
                ),
            )
            item_id = item.id
        else:
            cur = conn.execute(
                "INSERT INTO knowledge(category, language, keywords_json, question,"
                " answer, version, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                (
                    item.category,
                    item.language,
                    keywords_json,
                    item.question,
                    item.answer,
                    new_version,
                    1 if item.deleted else 0,
                ),
            )
            item_id = cur.lastrowid
        # Bump the bundle version so apps fetch the update.
        conn.execute(
            "INSERT INTO config(key, value) VALUES ('knowledge_bundle_version', ?)"
            " ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (str(new_version),),
        )
        conn.commit()
    return {"id": item_id, "version": new_version}


# ---------------------------------------------------------------------------
# Admin web UI (static, served at /admin/)
# ---------------------------------------------------------------------------

ADMIN_DIR = BASE_DIR / "admin"


@app.get("/", include_in_schema=False)
def _root() -> RedirectResponse:
    return RedirectResponse(url="/admin/")


app.mount(
    "/admin",
    StaticFiles(directory=str(ADMIN_DIR), html=True),
    name="admin",
)
