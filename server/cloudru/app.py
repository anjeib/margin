"""Margin's door on Cloud.ru (12 окт 2026): no key lives in the app.

The phone sends the requests it used to send to Yandex — same addresses (?op=…), same bodies, its invite code in
«X-Margin-Code». The door checks the code and answers from Cloud.ru Foundation Models (models hosted by Cloud.ru
itself, in Russia — never the «partner» ones that go to other providers): GigaChat reads the words, Whisper the voice,
DeepSeek-OCR the photos. When Cloud.ru doesn't answer, the same request goes to Yandex AI Studio, if its key is set —
two providers, one door.

?op=chat    POST  OpenAI-compatible chat completion (the app's model name is replaced here)
?op=models  GET   the code check (free)
?op=stt     POST  16 kHz mono LPCM; &lang=ru-RU → {"result": "…"} as SpeechKit answered
?op=ocr     POST  Yandex Vision body (base64 JPEG in «content») → {"result": {"textAnnotation": {"fullText": "…"}}}

Runs as a container (Cloud.ru Container Apps): python:3.12-alpine, nothing but the standard library.
"""
import base64
import http.server
import io
import json
import os
import re
import urllib.error
import urllib.parse
import urllib.request
import uuid
import wave

CR = os.environ.get("CLOUDRU_URL", "https://foundation-models.api.cloud.ru/v1")
CR_KEY = os.environ.get("CLOUDRU_KEY", "")
CHAT_MODEL = os.environ.get("CHAT_MODEL", "GigaChat/GigaChat-3-Pro")
STT_MODEL = os.environ.get("STT_MODEL", "openai/whisper-large-v3")
OCR_MODEL = os.environ.get("OCR_MODEL", "deepseek-ai/DeepSeek-OCR-2")
# The reserve: Yandex AI Studio by an API key of the service account (optional).
YC_KEY = os.environ.get("YC_KEY", "")
YC_FOLDER = os.environ.get("YC_FOLDER", "")
# Invite codes, separated by spaces or commas; remove one and restart to switch that tester off.
CODES = {c for c in re.split(r"[\s,;]+", os.environ.get("MARGIN_CODES", "")) if c}
BLOCKED = {c for c in re.split(r"[\s,;]+", os.environ.get("BLOCKED_DEVICES", "")) if c}
MAX_BODY = 3_000_000


def call(url, body, headers, timeout, method="POST"):
    """(status, text); a network failure is (0, why)."""
    req = urllib.request.Request(url, data=body if method == "POST" else None, method=method)
    for k, v in headers.items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # timeouts, DNS, refused
        return 0, str(e)[:200]


def failed(status):
    """Worth asking the reserve: the provider is down, overloaded or out of reach — not «your request is wrong»."""
    return status == 0 or status == 429 or status >= 500


def cr_headers(ctype="application/json"):
    h = {"Authorization": "Bearer " + CR_KEY}
    if ctype:
        h["Content-Type"] = ctype
    return h


def yc_headers(ctype="application/json"):
    h = {"Authorization": "Api-Key " + YC_KEY, "x-folder-id": YC_FOLDER, "OpenAI-Project": YC_FOLDER, "x-data-logging-enabled": "false"}
    if ctype:
        h["Content-Type"] = ctype
    return h


# ---------------- the words ----------------

def tidy(messages):
    """GigaChat takes a talk only in turns: one user line, then the answer. The app sends the last exchanges as they were
    (two of the user's lines in a row, or the assistant first) — Yandex took that, GigaChat says 400. Lines of one side
    in a row are joined; after the instructions the talk starts with the user."""
    out = []
    for m in messages:
        role, content = m.get("role"), m.get("content")
        if role not in ("system", "user", "assistant"):
            continue
        if out and out[-1]["role"] == role and isinstance(content, str) and isinstance(out[-1].get("content"), str):
            out[-1] = dict(out[-1], content=out[-1]["content"] + "\n\n" + content)
        else:
            out.append(dict(m))
    start = 1 if out and out[0]["role"] == "system" else 0
    while len(out) > start and out[start]["role"] == "assistant":
        out.pop(start)
    return out


def chat(body):
    data = json.loads(body)
    asked = data.get("model", "")
    if CR_KEY:
        mine = dict(data, model=CHAT_MODEL, messages=tidy(data.get("messages", [])))
        status, text = call(CR + "/chat/completions", json.dumps(mine, ensure_ascii=False).encode(), cr_headers(), 60)
        # A model without a JSON mode says so with 400/422: the same words without it.
        if status in (400, 422) and "response_format" in mine:
            mine.pop("response_format")
            status, text = call(CR + "/chat/completions", json.dumps(mine, ensure_ascii=False).encode(), cr_headers(), 60)
        if not failed(status) or not YC_KEY:
            return status, text, "cr"
    data["model"] = asked.replace("gpt://-/", f"gpt://{YC_FOLDER}/", 1)
    status, text = call("https://llm.api.cloud.yandex.net/v1/chat/completions", json.dumps(data, ensure_ascii=False).encode(), yc_headers(), 110)
    return status, text, "yc"


# ---------------- the voice ----------------

def wav_of(pcm):
    out = io.BytesIO()
    with wave.open(out, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(16000)
        w.writeframes(pcm)
    return out.getvalue()


def multipart(fields, file_name, file_bytes, file_type):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{file_name}"\r\nContent-Type: {file_type}\r\n\r\n'.encode())
    parts.append(file_bytes)
    parts.append(f"\r\n--{boundary}--\r\n".encode())
    return b"".join(parts), "multipart/form-data; boundary=" + boundary


def stt(pcm, lang):
    if CR_KEY:
        body, ctype = multipart({"model": STT_MODEL, "language": lang[:2]}, "voice.wav", wav_of(pcm), "audio/wav")
        status, text = call(CR + "/audio/transcriptions", body, cr_headers(ctype), 60)
        if status == 200:
            said = json.loads(text).get("text", "").strip()
            return 200, json.dumps({"result": said}, ensure_ascii=False), "cr"
        if not failed(status) or not YC_KEY:
            return status, text, "cr"
    url = f"https://stt.api.cloud.yandex.net/speech/v1/stt:recognize?folderId={YC_FOLDER}&lang={lang}&format=lpcm&sampleRateHertz=16000"
    status, text = call(url, pcm, yc_headers(None), 60)
    return status, text, "yc"


# ---------------- the photos ----------------

def ocr(body):
    data = json.loads(body)
    if CR_KEY:
        image = "data:image/jpeg;base64," + data.get("content", "")
        ask = {"model": OCR_MODEL, "max_tokens": 2000, "messages": [{"role": "user", "content": [
            {"type": "image_url", "image_url": {"url": image}}, {"type": "text", "text": "Free OCR."}]}]}
        status, text = call(CR + "/chat/completions", json.dumps(ask).encode(), cr_headers(), 60)
        if status == 200:
            read = json.loads(text)["choices"][0]["message"]["content"] or ""
            # Plain lines, as Vision gave them: no markdown the model may add around them.
            read = re.sub(r"^```\w*\s*|\s*```$", "", read.strip()).strip()
            return 200, json.dumps({"result": {"textAnnotation": {"fullText": read}}}, ensure_ascii=False), "cr"
        if not failed(status) or not YC_KEY:
            return status, text, "cr"
    status, text = call("https://ocr.api.cloud.yandex.net/ocr/v1/recognizeText", body, yc_headers(), 60)
    return status, text, "yc"


# ---------------- the door ----------------

class Door(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def answer(self, status, text, ctype="application/json"):
        raw = text.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, *args):  # the standard access log would print addresses; one line per call below instead
        pass

    def handle_one(self, method):
        url = urllib.parse.urlparse(self.path)
        query = dict(urllib.parse.parse_qsl(url.query))
        if url.path in ("/healthz", "/health"):
            return self.answer(200, '{"ok":true}')
        code = self.headers.get("X-Margin-Code", "")
        if code not in CODES:
            return self.answer(401, '{"error":"invite"}')
        device = self.headers.get("X-Margin-Device", "")[:36]
        if device and device in BLOCKED:
            return self.answer(403, '{"error":"device"}')
        op = query.get("op", "")
        size = int(self.headers.get("Content-Length") or 0)
        if size > MAX_BODY:
            return self.answer(413, '{"error":"too big"}')
        body = self.rfile.read(size) if size else b""
        try:
            if op == "models":
                status, text, via = 200, '{"data":[]}', "-"
            elif op == "chat" and method == "POST":
                status, text, via = chat(body)
            elif op == "stt" and method == "POST":
                lang = query.get("lang") if query.get("lang") in ("ru-RU", "en-US") else "ru-RU"
                status, text, via = stt(body, lang)
            elif op == "ocr" and method == "POST":
                status, text, via = ocr(body)
            else:
                return self.answer(404, '{"error":"op"}')
        except Exception as e:  # a broken body or answer: say so, never crash the door
            status, text, via = 502, json.dumps({"error": str(e)[:200]}), "-"
        if status == 0:
            status, text = 502, json.dumps({"error": text})
        # Who used how much, without any of the words: enough to see a code being abused or a provider failing.
        print(json.dumps({"code": code[:4] + "…", "device": device[:8], "op": op, "via": via, "status": status, "in": len(body), "out": len(text)}), flush=True)
        self.answer(status, text)

    def do_GET(self):
        self.handle_one("GET")

    def do_POST(self):
        self.handle_one("POST")


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "8080"))
    print(json.dumps({"door": "up", "port": port, "chat": CHAT_MODEL, "reserve": bool(YC_KEY)}), flush=True)
    http.server.ThreadingHTTPServer(("0.0.0.0", port), Door).serve_forever()
