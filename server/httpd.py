import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

from monitor import Monitor


class Handler(BaseHTTPRequestHandler):
    monitor: Monitor
    token: str = ""

    def log_message(self, fmt: str, *args) -> None:
        print(f"{self.log_date_time_string()} {fmt % args}")

    def do_GET(self) -> None:
        path = urlparse(self.path).path.rstrip("/") or "/"
        if path == "/health":
            self._send(200, {"ok": True})
            return
        if not self._authorized():
            return
        if path in ("/pending", "/api/pending"):
            self._send(200, self.monitor.snapshot())
            return
        self._send(404, {"ok": False, "error": "not found"})

    def do_POST(self) -> None:
        path = urlparse(self.path).path.rstrip("/") or "/"
        if not self._authorized():
            return
        body = self._read_json()
        if path in ("/start", "/api/start"):
            try:
                snap = self.monitor.start(
                    str(body.get("userId") or ""),
                    int(body.get("intervalA") or 180),
                    int(body.get("intervalB") or 240),
                )
            except ValueError as error:
                self._send(400, {"ok": False, "error": str(error)})
                return
            self._send(200, snap)
            return
        if path in ("/stop", "/api/stop"):
            self._send(200, self.monitor.stop())
            return
        if path in ("/ack", "/api/ack"):
            item_id = str(body.get("itemId") or "")
            error = bool(body.get("error"))
            self._send(200, self.monitor.ack(item_id=item_id, error=error))
            return
        self._send(404, {"ok": False, "error": "not found"})

    def _authorized(self) -> bool:
        got = self.headers.get("Authorization", "")
        if got.lower().startswith("bearer "):
            got = got[7:]
        if not got:
            got = self.headers.get("X-Token", "")
        if got.strip() != self.token:
            self._send(401, {"ok": False, "error": "unauthorized"})
            return False
        return True

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length") or 0)
        raw = self.rfile.read(length) if length > 0 else b""
        if not raw:
            return {}
        try:
            data = json.loads(raw.decode("utf-8"))
        except Exception:
            return {}
        return data if isinstance(data, dict) else {}

    def _send(self, status: int, payload: dict) -> None:
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)


def serve(host: str, port: int, monitor: Monitor, token: str) -> ThreadingHTTPServer:
    Handler.monitor = monitor
    Handler.token = token
    server = ThreadingHTTPServer((host, port), Handler)
    return server
