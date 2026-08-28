import http.client
import json
import socket
import ssl
import time
import urllib.parse
from typing import Callable

from mtop import API, HOST, VERSION, APP_KEY, is_success, is_token_error, parse_first_card_id, request_data, sign

CONNECT_TIMEOUT_S = 8
READ_TIMEOUT_S = 8
MAX_ATTEMPTS = 3
DNS_BACKOFF_S = 1.5
CONNECT_RETRY_S = 0.4
USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 13; XYMonitor) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
)


class XianyuError(RuntimeError):
    pass


def _now_ms() -> int:
    return int(time.time() * 1000)


def _is_retryable(error: BaseException) -> bool:
    if isinstance(error, (TimeoutError, socket.timeout, socket.gaierror, ConnectionError, OSError)):
        return True
    text = str(error)
    return (
        "Failed to connect" in text
        or "timeout" in text.lower()
        or "Unable to resolve" in text
        or "timed out" in text.lower()
    )


def _is_dns_failure(error: BaseException) -> bool:
    if isinstance(error, socket.gaierror):
        return True
    text = str(error)
    return "Unable to resolve" in text or "Name or service not known" in text or "No address associated" in text


class XianyuClient:
    def __init__(self, now_ms: Callable[[], int] | None = None):
        self._now_ms = now_ms or _now_ms
        self._cookies: dict[str, str] = {}

    def fetch_first_card_id(self, user_id: str) -> str:
        last: BaseException | None = None
        for index in range(MAX_ATTEMPTS):
            try:
                return self._fetch_once(user_id)
            except Exception as error:
                last = error
                attempt = index + 1
                if attempt >= MAX_ATTEMPTS or not _is_retryable(error):
                    break
                delay = DNS_BACKOFF_S if _is_dns_failure(error) else CONNECT_RETRY_S
                time.sleep(delay)
        raise last or XianyuError("接口调用失败")

    def _fetch_once(self, user_id: str) -> str:
        first = json.loads(self._request(user_id))
        root = json.loads(self._request(user_id)) if is_token_error(first) else first
        if is_token_error(root):
            raise XianyuError("令牌无效")
        if not is_success(root):
            raise XianyuError(self._ret(root) or "接口调用失败")
        item_id = parse_first_card_id(root)
        if not item_id:
            raise XianyuError("第一页没有商品")
        return item_id

    def _ret(self, root: dict) -> str:
        from mtop import ret_text

        return ret_text(root)

    def _request(self, user_id: str) -> str:
        data = request_data(user_id)
        t = str(self._now_ms())
        token = self._token()
        query = urllib.parse.urlencode(
            {
                "jsv": "2.7.2",
                "appKey": APP_KEY,
                "t": t,
                "sign": sign(t, token, data),
                "v": VERSION,
                "type": "originaljson",
                "accountSite": "xianyu",
                "dataType": "json",
                "timeout": str(int(CONNECT_TIMEOUT_S * 1000)),
                "api": API,
                "sessionOption": "AutoLoginOnly",
                "spm_cnt": "a21ybx.item.0.0",
            }
        )
        path = f"/h5/{API}/{VERSION}/?{query}"
        body = urllib.parse.urlencode({"data": data}).encode("utf-8")
        headers = {
            "Host": HOST,
            "Accept": "application/json",
            "Content-Type": "application/x-www-form-urlencoded",
            "Origin": "https://www.goofish.com",
            "Referer": "https://www.goofish.com/",
            "User-Agent": USER_AGENT,
        }
        cookie = self._cookie_header()
        if cookie:
            headers["Cookie"] = cookie
        status, raw, response_headers = self._post(path, body, headers)
        self._store_cookies(response_headers)
        if status < 200 or status > 299:
            raise XianyuError(f"HTTP {status}")
        if not raw.strip():
            raise XianyuError("空响应")
        return raw

    def _token(self) -> str:
        raw = self._cookies.get("_m_h5_tk", "")
        return raw.split("_", 1)[0]

    def _cookie_header(self) -> str:
        return "; ".join(f"{k}={v}" for k, v in self._cookies.items())

    def _store_cookies(self, headers: list[tuple[str, str]]) -> None:
        for key, value in headers:
            if key.lower() != "set-cookie":
                continue
            pair = value.split(";", 1)[0].strip()
            if "=" not in pair:
                continue
            name, cookie_value = pair.split("=", 1)
            name = name.strip()
            if name:
                self._cookies[name] = cookie_value.strip()

    def _post(self, path: str, body: bytes, headers: dict[str, str]) -> tuple[int, str, list[tuple[str, str]]]:
        context = ssl.create_default_context()
        conn = IPv4HTTPSConnection(HOST, timeout=CONNECT_TIMEOUT_S, context=context)
        try:
            conn.request("POST", path, body=body, headers=headers)
            response = conn.getresponse()
            raw = response.read().decode("utf-8", errors="replace")
            return response.status, raw, response.getheaders()
        finally:
            conn.close()


class IPv4HTTPSConnection(http.client.HTTPSConnection):
    def connect(self) -> None:
        infos = socket.getaddrinfo(self.host, self.port, socket.AF_INET, socket.SOCK_STREAM)
        if not infos:
            raise socket.gaierror("无IPv4地址")
        family, socktype, proto, _, sockaddr = infos[0]
        sock = socket.socket(family, socktype, proto)
        sock.settimeout(self.timeout)
        sock.connect(sockaddr)
        context = self._context
        if context is None:
            context = ssl.create_default_context()
        self.sock = context.wrap_socket(sock, server_hostname=self.host)
        self.sock.settimeout(READ_TIMEOUT_S)
