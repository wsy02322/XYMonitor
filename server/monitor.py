import json
import os
import random
import threading
import time
from pathlib import Path
from typing import Callable

from inspector import InspectOutcome, compare, fail
from xianyu import XianyuClient

MIN_SECONDS = 30
MAX_SECONDS = 3600
DEFAULT_A = 180
DEFAULT_B = 240


def clamp_seconds(value: int) -> int:
    return max(MIN_SECONDS, min(MAX_SECONDS, int(value)))


def next_delay_ms(a_sec: int, b_sec: int) -> int:
    low = clamp_seconds(min(a_sec, b_sec)) * 1000
    high = clamp_seconds(max(a_sec, b_sec)) * 1000
    if low >= high:
        return low
    return random.randint(low, high)


def now_ms() -> int:
    return int(time.time() * 1000)


class Monitor:
    def __init__(
        self,
        state_path: str | None = None,
        fetch_first_id: Callable[[str], str] | None = None,
        notify: Callable[[str], bool] | None = None,
    ):
        self._lock = threading.RLock()
        self._state_path = Path(state_path or os.environ.get("XY_STATE_FILE", "data/state.json"))
        self._fetch_first_id = fetch_first_id or (lambda user_id: XianyuClient().fetch_first_card_id(user_id))
        self._notify = notify
        self._stop = threading.Event()
        self._wake = threading.Event()
        self._first_done = threading.Event()
        self._thread: threading.Thread | None = None
        self.running = False
        self.user_id = ""
        self.interval_a = DEFAULT_A
        self.interval_b = DEFAULT_B
        self.last_first_id = ""
        self.last_status = "未开始"
        self.last_error = ""
        self.last_kind = "idle"
        self.checked_at = 0
        self.pending_alert = False
        self.pending_item_id = ""
        self.pending_error = False
        self._load()

    def snapshot(self) -> dict:
        with self._lock:
            item_id = self.pending_item_id or self.last_first_id
            return {
                "ok": True,
                "running": self.running,
                "userId": self.user_id,
                "firstId": self.last_first_id,
                "kind": self.last_kind,
                "changed": self.last_kind == "changed",
                "baseline": self.last_kind == "baseline",
                "pendingAlert": self.pending_alert,
                "pendingError": self.pending_error,
                "error": self.last_error,
                "status": self.last_status,
                "checkedAt": self.checked_at,
                "serverTime": now_ms(),
                "itemId": item_id,
            }

    def start(self, user_id: str, interval_a: int, interval_b: int) -> dict:
        user_id = str(user_id).strip()
        if not user_id.isdigit():
            raise ValueError("请填写数字 userId")
        with self._lock:
            user_changed = self.user_id != user_id
            already = self.running and not user_changed
            self.user_id = user_id
            self.interval_a = clamp_seconds(interval_a)
            self.interval_b = clamp_seconds(interval_b)
            if user_changed:
                self.last_first_id = ""
                self.pending_alert = False
                self.pending_item_id = ""
                self.pending_error = False
                self.last_error = ""
                self.last_status = "启动中"
                self.last_kind = "idle"
                self.checked_at = 0
            self.running = True
            self._stop.clear()
            self._save()
            if already:
                self._ensure_thread()
                return self.snapshot()
            self._first_done.clear()
            self._wake.set()
            self._ensure_thread()
        self._first_done.wait(timeout=15)
        return self.snapshot()

    def stop(self) -> dict:
        with self._lock:
            self.running = False
            self.last_status = "已停止"
            self._stop.set()
            self._wake.set()
            self._save()
        thread = self._thread
        if thread is not None and thread.is_alive() and thread is not threading.current_thread():
            thread.join(timeout=2)
        return self.snapshot()

    def ack(self, item_id: str = "", error: bool = False) -> dict:
        with self._lock:
            if error:
                self.pending_error = False
            if item_id:
                if not self.pending_item_id or item_id == self.pending_item_id:
                    self.pending_alert = False
                    self.pending_item_id = ""
            elif not error:
                self.pending_alert = False
                self.pending_item_id = ""
            self._save()
        return self.snapshot()

    def inspect_once_for_test(self) -> dict:
        self._inspect_once()
        return self.snapshot()

    def _ensure_thread(self) -> None:
        thread = self._thread
        if thread is not None and thread.is_alive():
            return
        self._thread = threading.Thread(target=self._loop, name="xy-poll", daemon=True)
        self._thread.start()

    def _loop(self) -> None:
        while not self._stop.is_set():
            with self._lock:
                running = self.running
                user_id = self.user_id
            if running and user_id:
                self._inspect_once()
                self._first_done.set()
            if self._stop.is_set():
                break
            with self._lock:
                delay_ms = next_delay_ms(self.interval_a, self.interval_b) if self.running else 1000
            self._wake.clear()
            self._wake.wait(timeout=max(delay_ms, 1) / 1000.0)

    def _inspect_once(self) -> None:
        with self._lock:
            user_id = self.user_id
            previous = self.last_first_id
        if not user_id:
            return
        try:
            current = self._fetch_first_id(user_id)
            outcome = compare(previous, current)
        except Exception as error:
            outcome = fail(str(error) or error.__class__.__name__)
        notify_text = self._apply_outcome(outcome)
        if notify_text and self._notify:
            try:
                self._notify(notify_text)
            except Exception:
                pass

    def _apply_outcome(self, outcome: InspectOutcome) -> str | None:
        with self._lock:
            self.checked_at = now_ms()
            if outcome.ok:
                self.last_first_id = outcome.first_id
                self.last_error = ""
                if outcome.baseline:
                    self.last_kind = "baseline"
                    self.last_status = f"已记下第一件 {outcome.first_id}"
                    notify = None
                elif outcome.changed:
                    self.last_kind = "changed"
                    self.last_status = f"第一件变为 {outcome.first_id}"
                    self.pending_alert = True
                    self.pending_item_id = outcome.first_id
                    notify = f"闲鱼上新 {outcome.first_id}"
                else:
                    self.last_kind = "unchanged"
                    self.last_status = f"第一件未变 {outcome.first_id}"
                    notify = None
            else:
                self.last_kind = "error"
                self.last_error = outcome.error or "巡检失败"
                self.last_status = "巡检失败"
                self.pending_error = True
                notify = None
            self._save()
            return notify

    def _load(self) -> None:
        path = self._state_path
        if not path.is_file():
            return
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception:
            return
        self.user_id = str(data.get("userId") or "")
        self.interval_a = clamp_seconds(data.get("intervalA") or DEFAULT_A)
        self.interval_b = clamp_seconds(data.get("intervalB") or DEFAULT_B)
        self.last_first_id = str(data.get("lastFirstId") or "")
        self.last_status = str(data.get("lastStatus") or self.last_status)
        self.last_error = str(data.get("lastError") or "")
        self.last_kind = str(data.get("lastKind") or "idle")
        self.checked_at = int(data.get("checkedAt") or 0)
        self.pending_alert = bool(data.get("pendingAlert"))
        self.pending_item_id = str(data.get("pendingItemId") or "")
        self.pending_error = bool(data.get("pendingError"))

    def _save(self) -> None:
        path = self._state_path
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            payload = {
                "userId": self.user_id,
                "intervalA": self.interval_a,
                "intervalB": self.interval_b,
                "lastFirstId": self.last_first_id,
                "lastStatus": self.last_status,
                "lastError": self.last_error,
                "lastKind": self.last_kind,
                "checkedAt": self.checked_at,
                "pendingAlert": self.pending_alert,
                "pendingItemId": self.pending_item_id,
                "pendingError": self.pending_error,
            }
            tmp = path.with_suffix(".tmp")
            tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
            tmp.replace(path)
        except Exception:
            pass
