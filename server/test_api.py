import json
import os
import sys
import tempfile
import threading
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from httpd import serve
from monitor import Monitor
import urllib.error
import urllib.request


class ApiTest(unittest.TestCase):
    def setUp(self):
        fd, name = tempfile.mkstemp(suffix=".json")
        os.close(fd)
        self.state = Path(name)
        self.addCleanup(lambda: self.state.exists() and self.state.unlink())
        self.items = ["111"]
        self.monitor = Monitor(
            state_path=str(self.state),
            fetch_first_id=lambda _: self.items[-1],
        )
        self.server = serve("127.0.0.1", 0, self.monitor, "secret")
        self.port = self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.server.shutdown)
        self.addCleanup(self.monitor.stop)

    def _request(self, method: str, path: str, body=None, token: str = "secret"):
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}{path}",
            data=data,
            method=method,
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(req, timeout=8) as response:
                return response.status, json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            return error.code, json.loads(error.read().decode("utf-8"))

    def test_health_does_not_need_token(self):
        req = urllib.request.Request(f"http://127.0.0.1:{self.port}/health")
        with urllib.request.urlopen(req, timeout=8) as response:
            payload = json.loads(response.read().decode("utf-8"))
        self.assertTrue(payload["ok"])

    def test_pending_requires_token(self):
        status, payload = self._request("GET", "/pending", token="wrong")
        self.assertEqual(401, status)
        self.assertFalse(payload["ok"])

    def test_start_pending_ack(self):
        status, snap = self._request(
            "POST",
            "/start",
            {"userId": "1666703902", "intervalA": 30, "intervalB": 30},
        )
        self.assertEqual(200, status)
        self.assertEqual("111", snap["firstId"])
        self.assertFalse(snap["pendingAlert"])
        self.items.append("222")
        self.monitor.inspect_once_for_test()
        status, snap = self._request("GET", "/pending")
        self.assertTrue(snap["pendingAlert"])
        self.assertEqual("222", snap["itemId"])
        status, snap = self._request("POST", "/ack", {"itemId": "222"})
        self.assertFalse(snap["pendingAlert"])
        self._request("POST", "/stop", {})
        self.assertFalse(self.monitor.snapshot()["running"])
