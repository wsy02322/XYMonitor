#!/usr/bin/env bash
# 一键部署（VPS 用 root）：
#   curl -fsSL https://raw.githubusercontent.com/wsy02322/XYMonitor/cursor/xianyu-monitor-5624/server/install.sh | bash
set -euo pipefail

DEST="${XY_HOME:-/opt/xymonitor}"
REPO="${XY_REPO:-https://github.com/wsy02322/XYMonitor.git}"
REF="${XY_REF:-cursor/xianyu-monitor-5624}"
SERVICE="/etc/systemd/system/xymonitor.service"

port_free() {
  python3 - "$1" <<'PY'
import socket, sys
port = int(sys.argv[1])
s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
try:
    s.bind(("0.0.0.0", port))
except OSError:
    sys.exit(1)
finally:
    s.close()
PY
}

read_env_port() {
  local file="$1"
  [[ -f "$file" ]] || return 1
  grep -E '^XY_PORT=' "$file" | tail -1 | cut -d= -f2- | tr -d '[:space:]'
}

pick_port() {
  python3 - <<'PY'
import socket
# 8787 / 8788 这台机器上可能已被占用，默认不用。
skip = {8787, 8788}
candidates = (
    list(range(18787, 18820))
    + list(range(28787, 28810))
    + [19870, 26981, 40187, 41287, 50187, 51234, 52080, 58080]
)
for port in candidates:
    if port in skip:
        continue
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        s.bind(("0.0.0.0", port))
    except OSError:
        s.close()
        continue
    s.close()
    print(port)
    break
else:
    raise SystemExit("没有找到空闲端口")
PY
}

write_env() {
  local port="$1"
  if [[ ! -f "$DEST/env" ]]; then
    cat > "$DEST/env" <<EOF
XY_HOST=0.0.0.0
XY_PORT=$port
XY_TOKEN_FILE=$DEST/.token
XY_STATE_FILE=$DEST/data/state.json
# TELEGRAM_BOT_TOKEN=
# TELEGRAM_CHAT_ID=
EOF
    chmod 600 "$DEST/env"
    return
  fi
  if grep -qE '^XY_PORT=' "$DEST/env"; then
    sed -i "s/^XY_PORT=.*/XY_PORT=$port/" "$DEST/env"
  else
    printf '\nXY_PORT=%s\n' "$port" >> "$DEST/env"
  fi
}

if [[ "$(id -u)" -ne 0 ]]; then
  echo "请用 root 执行" >&2
  exit 1
fi

need_pkgs=()
command -v python3 >/dev/null 2>&1 || need_pkgs+=(python3)
command -v git >/dev/null 2>&1 || need_pkgs+=(git)
command -v curl >/dev/null 2>&1 || need_pkgs+=(curl)
if [[ "${#need_pkgs[@]}" -gt 0 ]]; then
  if command -v apt-get >/dev/null 2>&1; then
    apt-get update -y
    apt-get install -y "${need_pkgs[@]}"
  elif command -v dnf >/dev/null 2>&1; then
    dnf install -y "${need_pkgs[@]}"
  elif command -v yum >/dev/null 2>&1; then
    yum install -y "${need_pkgs[@]}"
  else
    echo "请先安装: ${need_pkgs[*]}" >&2
    exit 1
  fi
fi

PY_VER="$(python3 -c 'import sys; print("%d.%d" % sys.version_info[:2])')"
PY_OK="$(python3 -c 'import sys; print(int(sys.version_info >= (3, 10)))')"
if [[ "$PY_OK" != "1" ]]; then
  echo "当前 python3 是 ${PY_VER}，需要 3.10+" >&2
  exit 1
fi

SCRIPT="${BASH_SOURCE[0]:-$0}"
SRC=""
if [[ -n "$SCRIPT" && "$SCRIPT" != "bash" && "$SCRIPT" != "-bash" && -f "$(dirname "$SCRIPT")/app.py" ]]; then
  SRC="$(cd "$(dirname "$SCRIPT")" && pwd)"
else
  STAGING="$(mktemp -d /tmp/xymonitor-src.XXXXXX)"
  git clone --depth 1 --branch "$REF" "$REPO" "$STAGING"
  SRC="$STAGING/server"
  trap 'rm -rf "$STAGING"' EXIT
fi

mkdir -p "$DEST/data"
for f in app.py httpd.py inspector.py monitor.py mtop.py telegram.py xianyu.py; do
  install -m 0644 "$SRC/$f" "$DEST/$f"
done

if [[ ! -f "$DEST/.token" ]]; then
  python3 -c 'import secrets; print(secrets.token_urlsafe(24))' > "$DEST/.token"
  chmod 600 "$DEST/.token"
fi

if systemctl is-active --quiet xymonitor 2>/dev/null; then
  systemctl stop xymonitor
fi

PORT=""
if [[ -n "${XY_PORT:-}" ]]; then
  PORT="$XY_PORT"
elif [[ -f "$DEST/env" ]]; then
  PORT="$(read_env_port "$DEST/env" || true)"
fi
if [[ -z "$PORT" ]]; then
  PORT="$(pick_port)"
elif ! port_free "$PORT"; then
  if [[ -n "${XY_PORT:-}" ]]; then
    echo "端口 $PORT 已被占用，换一个再装，或先关掉占用它的程序" >&2
    exit 1
  fi
  echo "原端口 $PORT 已被占用，改选空闲端口"
  PORT="$(pick_port)"
fi
write_env "$PORT"

cat > "$SERVICE" <<EOF
[Unit]
Description=XYMonitor
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
WorkingDirectory=$DEST
EnvironmentFile=-$DEST/env
ExecStart=$(command -v python3) $DEST/app.py
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q "Status: active"; then
  ufw allow "${PORT}/tcp" || true
fi
if command -v firewall-cmd >/dev/null 2>&1 && firewall-cmd --state 2>/dev/null | grep -q running; then
  firewall-cmd --permanent --add-port="${PORT}/tcp" || true
  firewall-cmd --reload || true
fi

systemctl daemon-reload
systemctl enable xymonitor
systemctl restart xymonitor
sleep 1
systemctl --no-pager --full status xymonitor || true

TOKEN="$(tr -d '[:space:]' < "$DEST/.token")"
IP="$(curl -4 -fsS --max-time 5 ifconfig.me 2>/dev/null || curl -4 -fsS --max-time 5 icanhazip.com 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}')"
IP="${IP:-你的VPS公网IP}"

cat <<EOF

已启动。填进 App：
  地址  http://${IP}:${PORT}
  密钥  ${TOKEN}

常用命令：
  systemctl status xymonitor
  journalctl -u xymonitor -f
  cat $DEST/.token

云厂商安全组也要放行 TCP ${PORT}。
EOF
