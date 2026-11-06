#!/usr/bin/env python3
"""End-to-end check of the Mira -> NflixAuth announcement contract, for a staging or test server.

Run by the admin on a trusted host. Secrets are read from files, never from the command line, and
never printed.

  NFLIX_URL=https://nflix.example        base URL of the Jellyfin server running NflixAuth >= 1.4.0
  MIRA_KEY_ID=nflix-1                    key id configured in the plugin
  MIRA_SECRET_FILE=/path/secret.hex      hex secret (root-only file)
  JELLYFIN_TOKEN_FILE=/path/token        access token of the *test* account (group mira_notice_test)

Use a staging Jellyfin by default. Running it against production requires the admin's explicit
approval, and even then only with the isolated mira_notice_test group and test account.

Every announcement it creates targets only the group mira_notice_test and expires in 15 minutes,
then is deleted, so regular users never see it.
"""
import hashlib
import hmac
import json
import os
import secrets
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone

BASE = os.environ["NFLIX_URL"].rstrip("/")
if "nflix.miracloud.fr" in BASE and os.environ.get("ALLOW_PRODUCTION") != "yes-admin-approved":
    sys.exit("Refus : production. Utilisez le staging, ou ALLOW_PRODUCTION=yes-admin-approved après accord de l'admin.")
KEY_ID = os.environ.get("MIRA_KEY_ID", "nflix-1")
with open(os.environ["MIRA_SECRET_FILE"], encoding="ascii") as f:
    SECRET = bytes.fromhex(f.read().strip())
with open(os.environ["JELLYFIN_TOKEN_FILE"], encoding="ascii") as f:
    USER_TOKEN = f.read().strip()

failures = 0


def check(name, ok, detail=""):
    global failures
    print(("OK   " if ok else "FAIL ") + name + (f" ({detail})" if detail and not ok else ""))
    if not ok:
        failures += 1


def signed(method, path, body=b"", nonce=None, timestamp=None, key_id=KEY_ID, secret=SECRET):
    nonce = nonce or secrets.token_urlsafe(24)
    timestamp = str(timestamp or int(time.time()))
    canonical = "\n".join([key_id, timestamp, nonce, method, path, hashlib.sha256(body).hexdigest()])
    signature = hmac.new(secret, canonical.encode(), hashlib.sha256).hexdigest()
    headers = {
        "Content-Type": "application/json",
        "X-Mira-Key-Id": key_id,
        "X-Mira-Timestamp": timestamp,
        "X-Mira-Nonce": nonce,
        "X-Mira-Signature": "v1=" + signature,
    }
    return headers, nonce, timestamp


def call(method, path, body=b"", headers=None):
    request = urllib.request.Request(BASE + path, data=body if method in ("PUT", "POST") else None, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.status, json.loads(response.read() or b"null")
    except urllib.error.HTTPError as error:
        try:
            return error.code, json.loads(error.read() or b"null")
        except ValueError:
            return error.code, None


def announcement(ident, updated, **extra):
    now = datetime.now(timezone.utc)
    data = {
        "id": ident,
        "targets": ["mira", "nflix"],
        "title": "Test d'intégration Mira",
        "message": "Annonce de test, visible uniquement par le groupe mira_notice_test.",
        "level": "info",
        "category": "announcement",
        "actionLabel": "Statut",
        "actionUrl": "https://miracloud.fr",
        "startsAt": now.isoformat(),
        "expiresAt": (now + timedelta(minutes=15)).isoformat(),
        "pinned": False,
        "showPopup": False,
        "dismissible": True,
        "audience": {"groups": ["mira_notice_test"]},
        "updatedAt": updated.isoformat(),
    }
    data.update(extra)
    return json.dumps(data).encode()


def feed_ids():
    status, items = call("GET", "/NflixAuth/Announcements/Feed", headers={"Authorization": f'MediaBrowser Token="{USER_TOKEN}"'})
    return status, {item["Id"] for item in items or []}


ident = "e2e-" + secrets.token_hex(6)
path = f"/NflixAuth/Announcements/{ident}"
t0 = datetime.now(timezone.utc).replace(microsecond=0)

status, caps = call("GET", "/NflixAuth/Announcements/Capabilities")
check("capabilities", status == 200 and caps and caps.get("app") == "nflix" and caps.get("enabled") is True, f"{status} {caps}")

body = announcement(ident, t0)
check("unsigned PUT refused", call("PUT", path, body, {"Content-Type": "application/json"})[0] == 401)
headers, nonce, ts = signed("PUT", path, body, key_id=KEY_ID, secret=secrets.token_bytes(32))
check("wrong secret refused", call("PUT", path, body, headers)[0] == 401)
headers, nonce, ts = signed("PUT", path, body, timestamp=int(time.time()) - 400)
status, result = call("PUT", path, body, headers)
check("stale timestamp refused as clock_skew", status == 401 and result and result.get("code") == "clock_skew" and "serverTime" in result, f"{status} {result}")
headers, nonce, ts = signed("PUT", path, body, timestamp=int(time.time()) - 400, secret=secrets.token_bytes(32))
status, result = call("PUT", path, body, headers)
check("bad signature never reported as clock_skew", status == 401 and (result or {}).get("code") != "clock_skew", f"{status} {result}")

no_target = announcement(ident, t0, targets=["mira"])
check("missing nflix target refused", call("PUT", path, no_target, signed("PUT", path, no_target)[0])[0] == 422)
http_link = announcement(ident, t0, actionUrl="http://miracloud.fr")
check("http link refused", call("PUT", path, http_link, signed("PUT", path, http_link)[0])[0] == 422)

headers, nonce, ts = signed("PUT", path, body)
status, result = call("PUT", path, body, headers)
check("signed PUT created", status == 201 and result and result.get("status") == "created", f"{status} {result}")
check("replayed request refused", call("PUT", path, body, headers)[0] == 401)
status, result = call("PUT", path, body, signed("PUT", path, body)[0])
check("same version is idempotent", status == 200 and result and result.get("status") == "unchanged", f"{status} {result}")

status, ids = feed_ids()
check("feed shows mira:" + ident, status == 200 and f"mira:{ident}" in ids, str(status))

delete_headers = signed("DELETE", path)[0]
status, result = call("DELETE", path, b"", delete_headers)
check("signed DELETE", status == 200 and result and result.get("status") == "deleted", f"{status} {result}")
status, result = call("PUT", path, body, signed("PUT", path, body)[0])
check("older PUT cannot recreate deleted announcement", status == 200 and result and result.get("status") == "unchanged", f"{status} {result}")
status, ids = feed_ids()
check("feed no longer shows it", status == 200 and f"mira:{ident}" not in ids, str(status))

print("\nRésultat :", "tout est conforme" if failures == 0 else f"{failures} échec(s)")
sys.exit(1 if failures else 0)
