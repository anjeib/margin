"""Upload a new Margin version to RuStore through the Public API and (optionally) send it to moderation.

Usage:
  python3 rustore/upload.py <apk> --whats-new "<text>"            # creates a draft and uploads the APK
  python3 rustore/upload.py <apk> --whats-new "<text>" --submit   # ...and sends it to moderation

Key: ~/Desktop/Проекты/API Rustore.md (Key ID on its own line + the base64 private key), or
rustore/api-key.json (never commit or share it):
  {"keyId": "<Key ID from the console>", "privateKeyFile": "<file with the base64 private key>"}
  or "privateKey": "<base64 private key>" instead of the file.
Create it in the RuStore console: Company → API RuStore → generate a key, give it access to Margin.
Only the Python standard library and the `openssl` command are used.
"""
import argparse
import base64
import datetime
import json
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
import uuid
from pathlib import Path

API = "https://public-api.rustore.ru"
PACKAGE = "ru.margin.app"
HERE = Path(__file__).resolve().parent
KEY_NOTE = HERE.parent.parent / "API Rustore.md"  # ~/Desktop/Проекты/API Rustore.md


def call(method, path, token=None, body=None, files=None):
    headers = {}
    data = None
    if token:
        headers["Public-Token"] = token
    if files:
        boundary = uuid.uuid4().hex
        parts = []
        for field, p in files.items():
            parts.append(
                f'--{boundary}\r\nContent-Disposition: form-data; name="{field}"; filename="{p.name}"\r\n'
                f"Content-Type: {'image/jpeg' if p.suffix == '.jpg' else 'image/png' if p.suffix == '.png' else 'application/vnd.android.package-archive'}\r\n\r\n".encode()
                + p.read_bytes() + b"\r\n"
            )
        data = b"".join(parts) + f"--{boundary}--\r\n".encode()
        headers["Content-Type"] = f"multipart/form-data; boundary={boundary}"
    elif body is not None:
        data = json.dumps(body, ensure_ascii=False).encode()
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(API + path, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            raw = r.read().decode() or "{}"
    except urllib.error.HTTPError as e:
        raw = e.read().decode() or "{}"
    out = json.loads(raw)
    if out.get("code") not in (None, "OK"):
        raise SystemExit(f"{method} {path} -> {out.get('code')}: {out.get('message')}")
    return out.get("body")


def token():
    cfg = HERE / "api-key.json"
    if cfg.exists():
        key = json.loads(cfg.read_text())
        if "privateKey" not in key:
            key["privateKey"] = "".join(Path(key["privateKeyFile"]).read_text().split())
    else:
        # the owner's note: Key ID on its own line, the base64 private key as the longest piece of text
        words = KEY_NOTE.read_text().split()
        private = max(words, key=len)
        ids = [w for w in words if w != private and len(w) >= 4]
        if not ids:
            raise SystemExit(f"No Key ID in {KEY_NOTE}: add it on its own line next to the key")
        key = {"keyId": ids[0], "privateKey": private}
    ts = datetime.datetime.now(datetime.timezone.utc).astimezone().isoformat(timespec="milliseconds")
    pem = "-----BEGIN PRIVATE KEY-----\n" + "\n".join(
        key["privateKey"][i:i + 64] for i in range(0, len(key["privateKey"]), 64)
    ) + "\n-----END PRIVATE KEY-----\n"
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=True) as f:
        f.write(pem)
        f.flush()
        sig = subprocess.run(
            ["openssl", "dgst", "-sha512", "-sign", f.name],
            input=(key["keyId"] + ts).encode(), capture_output=True, check=True,
        ).stdout
    body = call("POST", "/public/auth/", body={
        "keyId": key["keyId"], "timestamp": ts, "signature": base64.b64encode(sig).decode(),
    })
    return body["jwe"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk", type=Path)
    ap.add_argument("--whats-new", required=True)
    ap.add_argument("--submit", action="store_true", help="send the draft to moderation")
    a = ap.parse_args()

    t = token()
    print("auth ok")
    # a draft copies the page from the current version; only "what's new" changes
    version = call("POST", f"/public/v1/application/{PACKAGE}/version", t, body={
        "whatsNew": a.whats_new, "publishType": "INSTANTLY",
    })
    print("draft", version)
    call("POST", f"/public/v1/application/{PACKAGE}/version/{version}/apk?isMainApk=true&servicesType=Unknown",
         t, files={"file": a.apk})
    print("apk uploaded:", a.apk.name)
    if a.submit:
        call("POST", f"/public/v1/application/{PACKAGE}/version/{version}/commit", t)
        print("sent to moderation")
    else:
        print("draft left unsent: check it in the console or run again with --submit")


if __name__ == "__main__":
    sys.exit(main())
