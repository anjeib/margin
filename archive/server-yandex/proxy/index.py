"""Margin's door to Yandex AI Studio: no key lives in the app.

The phone sends the same requests it used to send to Yandex, to this function, with its invite code in
«X-Margin-Code». The function checks the code, puts the folder in, and calls Yandex as the service account
it runs under (an IAM token from the cloud — no API key stored anywhere). «x-data-logging-enabled: false»
goes on every call: Yandex keeps nothing of what was sent.

?op=chat    POST  OpenAI-compatible chat completion; the model is «gpt://-/<name>/latest»
?op=models  GET   the key check (free)
?op=stt     POST  SpeechKit v1, 16 kHz LPCM; &lang=ru-RU
?op=ocr     POST  Vision OCR, JSON body as Yandex takes it
"""
import base64
import json
import os
import re
import urllib.error
import urllib.request

FOLDER = os.environ["FOLDER_ID"]
# Invite codes, separated by spaces or commas; remove one and redeploy to switch that tester off.
CODES = {c for c in re.split(r"[\s,;]+", os.environ.get("MARGIN_CODES", "")) if c}
# Installs switched off one by one (the shared alpha code stays on for everyone else).
BLOCKED = {c for c in re.split(r"[\s,;]+", os.environ.get("BLOCKED_DEVICES", "")) if c}
MAX_BODY = 3_000_000

TARGETS = {
    "chat": ("POST", "https://llm.api.cloud.yandex.net/v1/chat/completions"),
    "models": ("GET", "https://llm.api.cloud.yandex.net/v1/models"),
    "stt": ("POST", "https://stt.api.cloud.yandex.net/speech/v1/stt:recognize"),
    "ocr": ("POST", "https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText"),
}


def reply(code, body, ctype="application/json"):
    return {"statusCode": code, "headers": {"Content-Type": ctype}, "body": body}


def handler(event, context):
    headers = {k.lower(): v for k, v in (event.get("headers") or {}).items()}
    query = event.get("queryStringParameters") or {}
    code = headers.get("x-margin-code", "")
    if code not in CODES:
        return reply(401, '{"error":"invite"}')
    device = headers.get("x-margin-device", "")[:36]
    if device and device in BLOCKED:
        return reply(403, '{"error":"device"}')
    op = query.get("op", "")
    if op not in TARGETS:
        return reply(404, '{"error":"op"}')
    method, url = TARGETS[op]

    raw = event.get("body") or ""
    body = base64.b64decode(raw) if event.get("isBase64Encoded") else raw.encode()
    if len(body) > MAX_BODY:
        return reply(413, '{"error":"too big"}')

    if op == "chat":
        # The app does not know the folder: «gpt://-/model/latest» gets it here.
        data = json.loads(body)
        data["model"] = data.get("model", "").replace("gpt://-/", f"gpt://{FOLDER}/", 1)
        body = json.dumps(data, ensure_ascii=False).encode()
    if op == "stt":
        lang = query.get("lang", "ru-RU") if query.get("lang") in ("ru-RU", "en-US") else "ru-RU"
        url += f"?folderId={FOLDER}&lang={lang}&format=lpcm&sampleRateHertz=16000"

    req = urllib.request.Request(url, data=body if method == "POST" else None, method=method)
    req.add_header("Authorization", "Bearer " + context.token["access_token"])
    req.add_header("x-data-logging-enabled", "false")
    req.add_header("x-folder-id", FOLDER)
    req.add_header("OpenAI-Project", FOLDER)
    if op != "stt":
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=110) as r:
            status, text = r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        status, text = e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # network trouble on the way to Yandex
        return reply(502, json.dumps({"error": str(e)[:200]}))
    # Who used how much, without any of the words: enough to see a code being abused.
    print(json.dumps({"code": code[:4] + "…", "device": device[:8], "op": op, "status": status, "in": len(body), "out": len(text)}))
    return reply(status, text)
