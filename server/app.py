import os
import secrets
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from httpd import serve
from monitor import Monitor
from telegram import send as telegram_send


def load_token() -> str:
    env = os.environ.get("XY_TOKEN", "").strip()
    if env:
        return env
    path = Path(os.environ.get("XY_TOKEN_FILE", str(ROOT / ".token")))
    if path.is_file():
        token = path.read_text(encoding="utf-8").strip()
        if token:
            return token
    token = secrets.token_urlsafe(24)
    path.write_text(token + "\n", encoding="utf-8")
    print(f"已生成密钥，写入 {path}", flush=True)
    return token


def main() -> None:
    host = os.environ.get("XY_HOST", "0.0.0.0")
    port = int(os.environ.get("XY_PORT", "8787"))
    state = os.environ.get("XY_STATE_FILE", str(ROOT / "data" / "state.json"))
    token = load_token()
    monitor = Monitor(state_path=state, notify=telegram_send)
    server = serve(host, port, monitor, token)
    print(f"XYMonitor 服务监听 http://{host}:{port}", flush=True)
    print("把地址和密钥填进 App，点开始监控。邀请码以后再做。", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("停止", flush=True)
        server.shutdown()


if __name__ == "__main__":
    main()
