"""Tests for the Ayna licence/payment/admin server.

Run from server/:  python -m pytest tests/ -v

Notes on the OTP tests: the real OTP is generated server-side and is never
returned or logged (by design), so the "correct code" path cannot be tested
through request-otp alone. Instead these tests insert a row into the test DB
with the SHA-256 hash of a KNOWN code ("123456") and then call verify-otp
with that code — the same hash comparison the real flow uses. The wrong-code
path is tested end-to-end (request-otp -> verify-otp with a wrong code).
"""

import hashlib
import os
import sqlite3
import sys
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

os.environ["DATABASE_PATH"] = "/tmp/ayna-test.db"
os.environ["ADMIN_PHONE"] = "03089303647"

import app.main as main  # noqa: E402

ADMIN_PHONE = "0308 9303647"
DEVICE = "test-device-001"


@pytest.fixture(autouse=True)
def fresh_db(tmp_path, monkeypatch):
    db = str(tmp_path / "ayna-test.db")
    monkeypatch.setenv("DATABASE_PATH", db)
    main.DATABASE_PATH = db
    main.init_db()
    yield
    main.DATABASE_PATH = "/tmp/ayna-test.db"


@pytest.fixture()
def client():
    with TestClient(main.app) as c:
        yield c


def _otp_row(phone: str, code: str):
    """Insert an OTP row for a KNOWN code (hash only), as the real flow would."""
    code_hash = hashlib.sha256(code.encode()).hexdigest()
    now = datetime.now(timezone.utc)
    conn = sqlite3.connect(os.environ["DATABASE_PATH"])
    conn.execute(
        "INSERT INTO otp_codes(phone, code_hash, created_at, expires_at, used)"
        " VALUES (?, ?, ?, ?, 0)",
        (
            main._normalise_phone(phone),
            code_hash,
            main._iso(now),
            main._iso(now + timedelta(minutes=10)),
        ),
    )
    conn.commit()
    conn.close()


def _admin_token(client: TestClient) -> str:
    _otp_row(ADMIN_PHONE, "123456")
    res = client.post(
        "/api/v1/admin/verify-otp", json={"phone": ADMIN_PHONE, "code": "123456"}
    )
    assert res.status_code == 200, res.text
    return res.json()["token"]


# ---------------------------------------------------------------------------
# Config defaults
# ---------------------------------------------------------------------------


def test_config_defaults(client):
    res = client.get("/api/v1/config")
    assert res.status_code == 200
    cfg = res.json()
    assert cfg["trial_days"] == 10
    assert cfg["price_pkr"] == 300
    assert cfg["min_version"] == 1
    assert cfg["knowledge_bundle_version"] == 1


def test_config_update_requires_admin(client):
    res = client.put("/api/v1/config", json={"trial_days": 14})
    assert res.status_code == 401


def test_config_update_as_admin(client):
    token = _admin_token(client)
    res = client.put(
        "/api/v1/config",
        json={"trial_days": 14, "price_pkr": 500},
        headers={"X-Admin-Token": token},
    )
    assert res.status_code == 200
    assert res.json()["trial_days"] == 14
    assert res.json()["price_pkr"] == 500


# ---------------------------------------------------------------------------
# Licence trial math
# ---------------------------------------------------------------------------


def test_license_new_device_trial_active(client):
    res = client.get("/api/v1/license", params={"device_id": DEVICE})
    assert res.status_code == 200
    body = res.json()
    assert body["status"] == "TRIAL_ACTIVE"
    assert body["trial_days_left"] == 10
    assert body["expiry_date"]


def test_license_trial_expired_after_trial_days(client):
    # Backdate the trial start by 11 days -> expired.
    client.get("/api/v1/license", params={"device_id": DEVICE})
    past = datetime.now(timezone.utc) - timedelta(days=11)
    conn = sqlite3.connect(os.environ["DATABASE_PATH"])
    conn.execute(
        "UPDATE devices SET trial_started_at = ? WHERE device_id = ?",
        (main._iso(past), DEVICE),
    )
    conn.commit()
    conn.close()
    res = client.get("/api/v1/license", params={"device_id": DEVICE})
    assert res.json()["status"] == "TRIAL_EXPIRED"
    assert res.json()["trial_days_left"] == 0


def test_license_requires_device_id(client):
    res = client.get("/api/v1/license", params={"device_id": "  "})
    assert res.status_code == 400


# ---------------------------------------------------------------------------
# Payments
# ---------------------------------------------------------------------------


def test_payment_submit_is_pending(client):
    res = client.post(
        "/api/v1/payments", json={"txn_id": "EASY123456", "device_id": DEVICE}
    )
    assert res.status_code == 201
    assert res.json()["status"] == "pending_verification"

    status = client.get("/api/v1/payments/status", params={"device_id": DEVICE})
    assert status.json()["status"] == "pending_verification"
    assert status.json()["txn_id"] == "EASY123456"

    # Licence now shows PAYMENT_PENDING.
    lic = client.get("/api/v1/license", params={"device_id": DEVICE})
    assert lic.json()["status"] == "PAYMENT_PENDING"


def test_payment_status_none_for_unknown_device(client):
    res = client.get("/api/v1/payments/status", params={"device_id": "nobody"})
    assert res.json()["status"] == "none"


def test_payment_submit_idempotent_same_txn(client):
    first = client.post(
        "/api/v1/payments", json={"txn_id": "EASY999", "device_id": DEVICE}
    )
    second = client.post(
        "/api/v1/payments", json={"txn_id": "EASY999", "device_id": DEVICE}
    )
    assert first.json()["id"] == second.json()["id"]
    assert second.json()["status"] == "pending_verification"


# ---------------------------------------------------------------------------
# Admin OTP flow
# ---------------------------------------------------------------------------


def test_otp_wrong_code_fails(client):
    client.post("/api/v1/admin/request-otp", json={"phone": ADMIN_PHONE})
    res = client.post(
        "/api/v1/admin/verify-otp", json={"phone": ADMIN_PHONE, "code": "000000"}
    )
    assert res.status_code == 401


def test_otp_correct_code_issues_token(client):
    # Known-code row stands in for the SMS-delivered code (see module docstring).
    _otp_row(ADMIN_PHONE, "123456")
    res = client.post(
        "/api/v1/admin/verify-otp", json={"phone": ADMIN_PHONE, "code": "123456"}
    )
    assert res.status_code == 200
    assert res.json()["token"]
    assert res.json()["expires_in_hours"] == 12


def test_otp_code_is_single_use(client):
    _otp_row(ADMIN_PHONE, "123456")
    first = client.post(
        "/api/v1/admin/verify-otp", json={"phone": ADMIN_PHONE, "code": "123456"}
    )
    assert first.status_code == 200
    second = client.post(
        "/api/v1/admin/verify-otp", json={"phone": ADMIN_PHONE, "code": "123456"}
    )
    assert second.status_code == 401


def test_otp_request_stores_hash_only(client):
    client.post("/api/v1/admin/request-otp", json={"phone": ADMIN_PHONE})
    conn = sqlite3.connect(os.environ["DATABASE_PATH"])
    row = conn.execute(
        "SELECT code_hash FROM otp_codes WHERE phone = ?",
        (main._normalise_phone(ADMIN_PHONE),),
    ).fetchone()
    conn.close()
    assert row is not None
    # 64 hex chars = SHA-256 hash, never the 6-digit code itself.
    assert len(row[0]) == 64 and row[0] != "123456"
    assert all(c in "0123456789abcdef" for c in row[0])


def test_otp_request_non_admin_number_creates_no_code(client):
    res = client.post("/api/v1/admin/request-otp", json={"phone": "0300 1111111"})
    assert res.status_code == 200  # generic reply, no enumeration
    conn = sqlite3.connect(os.environ["DATABASE_PATH"])
    count = conn.execute("SELECT COUNT(*) FROM otp_codes").fetchone()[0]
    conn.close()
    assert count == 0


def test_admin_endpoints_reject_bad_token(client):
    res = client.get(
        "/api/v1/admin/payments", headers={"X-Admin-Token": "bogus-token"}
    )
    assert res.status_code == 401


# ---------------------------------------------------------------------------
# Payment verify -> licence UNLOCKED
# ---------------------------------------------------------------------------


def test_payment_verify_unlocks_licence(client):
    created = client.post(
        "/api/v1/payments", json={"txn_id": "EASY777", "device_id": DEVICE}
    )
    payment_id = created.json()["id"]
    token = _admin_token(client)

    res = client.post(
        f"/api/v1/admin/payments/{payment_id}/verify",
        json={"decision": "verify"},
        headers={"X-Admin-Token": token},
    )
    assert res.status_code == 200
    assert res.json()["status"] == "verified"

    lic = client.get("/api/v1/license", params={"device_id": DEVICE})
    assert lic.json()["status"] == "UNLOCKED"
    assert lic.json()["activated_at"]

    status = client.get("/api/v1/payments/status", params={"device_id": DEVICE})
    assert status.json()["status"] == "verified"


def test_payment_reject_does_not_unlock(client):
    created = client.post(
        "/api/v1/payments", json={"txn_id": "EASY888", "device_id": DEVICE}
    )
    token = _admin_token(client)
    res = client.post(
        f"/api/v1/admin/payments/{created.json()['id']}/verify",
        json={"decision": "reject"},
        headers={"X-Admin-Token": token},
    )
    assert res.json()["status"] == "rejected"
    lic = client.get("/api/v1/license", params={"device_id": DEVICE})
    assert lic.json()["status"] == "TRIAL_ACTIVE"


# ---------------------------------------------------------------------------
# Knowledge bundle
# ---------------------------------------------------------------------------


def test_knowledge_bundle_empty_then_publish_bumps_version(client):
    bundle = client.get("/api/v1/knowledge/bundle", params={"version": 0})
    assert bundle.json() == {"version": 1, "items": [], "deleted": []}

    token = _admin_token(client)
    res = client.post(
        "/api/v1/admin/knowledge",
        json={
            "category": "Islam",
            "language": "Roman Urdu",
            "keywords": ["namaz", "wazu"],
            "question": "Wazu kaise karte hain?",
            "answer": "Pehle niyyat karein, phir...",
        },
        headers={"X-Admin-Token": token},
    )
    assert res.status_code == 200
    assert res.json()["version"] == 2

    bundle = client.get("/api/v1/knowledge/bundle", params={"version": 1})
    assert bundle.json()["version"] == 2
    assert len(bundle.json()["items"]) == 1
    assert bundle.json()["items"][0]["question"] == "Wazu kaise karte hain?"
